import java.util.zip.CRC32;

import java.util.LinkedList;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import java.io.InputStream;
import java.io.OutputStream;

import java.io.PushbackInputStream;
import java.io.BufferedInputStream;
import java.io.FilterOutputStream;
import java.io.ByteArrayOutputStream;

public class ParallelOutStream { 
  // Constants
  private static final int header_size = 10;
  private static final int tail_size = 8;
  private static final int dict_size = 32*1024;

  private JConfig config;
  private CRC32 crc = new CRC32();
  private OutputStream out; 
  private PushbackInputStream in;

  public ParallelOutStream(OutputStream s_out, InputStream s_in, JConfig jconfig)
  {
    out = s_out;
    in = new PushbackInputStream(s_in);
    config = jconfig;
  }

  public void compressInput() {
    // Instantiate thread pool to hold write tasks
    int nproc = config.getProcNum();
    ArrayBlockingQueue<Runnable> queue =
      new ArrayBlockingQueue<Runnable>(nproc);
    ThreadPoolExecutor pool = 
      new ThreadPoolExecutor(nproc,  // Core pool size
                             nproc,  // Maximum pool size
                             1,     // Keep alive
                             TimeUnit.SECONDS,
                             queue); // Queue to hold tasks

    // Hash maps to retrieve read/writes from child threads
    float load_factor = 0.75f;
    ConcurrentMap<Integer,byte[]> map_compressed = 
      new ConcurrentHashMap<Integer,byte[]>(nproc, load_factor, nproc);
    ConcurrentMap<Integer,byte[]> map_uncompressed =
      new ConcurrentHashMap<Integer,byte[]>(nproc, load_factor, nproc);
    ConcurrentMap<Integer,byte[]> map_dictionaries = 
      new ConcurrentHashMap<Integer,byte[]>(nproc, load_factor, nproc);

    ByteArrayOutputStream uncompressed = new ByteArrayOutputStream();
    LinkedList<Runnable> threads = new LinkedList<Runnable>();
   
    try {
 
    // Write header to output
    FileStreamUtil.putHeader(out);

    // Read from standard input P - 1 blocks, where P = number
    //   of concurrent processors
    int block_size = config.getBlockSize();
    byte[] buffer = new byte[block_size];
    byte[] dictionary = null;
    int read_size = 0;
    int readfull_size = 0;
    boolean first_block = true;
    boolean last_block = true;
    int thread_index = 0;
    int index = 0;

    while ((thread_index < (nproc - 1)) &&
           ((read_size = in.read(buffer, readfull_size, block_size - readfull_size)) >= 0)) {
      readfull_size += read_size;
      if ((readfull_size < block_size) && (FileStreamUtil.hasMoreBytes(in)))
        continue;
      read_size = readfull_size;
      readfull_size = 0;

      uncompressed.write(buffer, 0, read_size);

      byte[] readbytes = new byte[read_size];
      System.arraycopy(buffer, 0, readbytes, 0, read_size);

      last_block = !FileStreamUtil.hasMoreBytes(in);

      index = config.incrProcCount();

      // Create, but do not start, a new thread assigned to this block
      threads.add(new WriteTask(config,
                                readbytes,
                                map_dictionaries,
                                index,
                                read_size,
                                first_block,
                                last_block,
                                in,
                                map_compressed,
                                map_uncompressed));
      
      if (read_size == block_size && !last_block && !config.getIndependent()) {
        dictionary = new byte[dict_size];
        System.arraycopy(buffer, block_size - dict_size, dictionary, 0, dict_size);
        map_dictionaries.put(index, dictionary); 
      }

      thread_index++;

      first_block = false;
    }

    // Add each thread to executor pool and begin execution on each thread
    while(threads.size() > 0)
      pool.execute(threads.remove());

    // While threads are executing, check both compressed and uncompressed
    // maps for buffers. Collect these in increasing order, until the
    // maps are empty and no more threads are running in the pool
    
    int c_iter = 1; // Start at thread index 1
    int u_iter = nproc; // Start at first unread block index
    boolean got_last = false; // Fixes a race condition where the last
                              // block of compressed data is not obtained

    while (((map_compressed.size() > 0) ||
           (map_uncompressed.size() > 0) ||
           (pool.getActiveCount() > 0))
           && !got_last) {
      if (map_compressed.containsKey(c_iter))
      {
        byte[] comp_bytes = map_compressed.get(c_iter);
        // Write compressed data to output, in order
        out.write(comp_bytes);
        map_compressed.remove(c_iter);

        if (config.getLastBlockIndex() == c_iter)
          got_last = true;
        
        c_iter++;
      }

      if (map_uncompressed.containsKey(u_iter))
      {
        byte[] uncomp_bytes = map_uncompressed.get(u_iter);
        // Write uncompressed data to buffer, to help construct the tail
        uncompressed.write(uncomp_bytes);
        map_uncompressed.remove(u_iter);
        u_iter++;
      }
    }
    pool.shutdown();

    // Compute checksum of uncompressed data
    byte[] totalfile = uncompressed.toByteArray();
    if (totalfile.length == 0)
    {
      // Print empty block
      byte[] empty = {0x03, 0x00};
      out.write(empty, 0, 2);
    }
    crc.update(totalfile);
    int crcval = (int)crc.getValue(); // Trunicated
 
    // Print tail 
    FileStreamUtil.putTail(out, crcval, totalfile.length);

    } catch (Throwable e)
    {
      pool.shutdownNow(); // Force quit threads
      FileStreamUtil.printErrorAndExit(e);
    }
  }
}
