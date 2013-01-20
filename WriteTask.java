import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import java.util.zip.Deflater;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.BufferedInputStream;
import java.io.FilterOutputStream;
import java.io.ByteArrayOutputStream;

public class WriteTask implements Runnable {
  // Constants
  private final int dict_size = 32 * 1024;

  // Invoked by pool
  private JConfig config;
  private byte[] buffer;
  private int buffer_size;
  private int block_index;
  private boolean is_first;
  private boolean is_last;
  private PushbackInputStream in;
  private ConcurrentMap<Integer,byte[]> map_compressed;
  private ConcurrentMap<Integer,byte[]> map_uncompressed;
  private ConcurrentMap<Integer,byte[]> map_dictionaries;

  // Local execution
  private Deflater defl;
  private ByteArrayOutputStream compressed;
  private boolean write_done = false;
  private byte[] buffer_c;

  public WriteTask(JConfig jconfig, byte[] buf, ConcurrentMap<Integer,byte[]> s_dict, int index, int bufsize,
                   boolean first, boolean last, PushbackInputStream s_in, 
                   ConcurrentMap<Integer,byte[]> s_map_c,
                   ConcurrentMap<Integer,byte[]> s_map_u)
  {
    this.config = jconfig;
    this.buffer = buf;
    this.buffer_size = bufsize;
    this.map_dictionaries = s_dict;
    this.block_index = index;
    this.is_first = first;
    this.is_last = last;
    this.in = s_in;
    this.map_compressed = s_map_c;
    this.map_uncompressed = s_map_u;

    compressed = new ByteArrayOutputStream();
    buffer_c = new byte[config.getBlockSize() * 2];
  }

  public void run()
  {
    boolean read_not_eof = true;

    while (read_not_eof)
    {
      compressBuffer();
      if (is_last) {
        break;      
      }
      // Have thread attempt to read more blocks from standard input after
      // completing last compression
      read_not_eof = readNextBlock();
    }
    write_done = true;
  }

  private void resetDeflater() throws IOException {
    defl = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
  }

  private void compressBuffer() {
    try {
      int defl_len = 0;
 
      resetDeflater();

      // Set dictionary
      if (!is_first && !config.getIndependent())
        defl.setDictionary(map_dictionaries.get(block_index-1));
 
      defl.setInput(buffer, 0, buffer_size);
      if (!is_last)
      {
        defl_len = defl.deflate(buffer_c, 0, buffer_c.length, Deflater.SYNC_FLUSH);
      }
      else
      {
        defl.finish();
        while (!defl.finished())
          defl_len = defl.deflate(buffer_c, 0, buffer_c.length, Deflater.NO_FLUSH);
      }
      compressed.write(buffer_c, 0, defl_len);

      // Push result to concurrent hash map of compressed data
      map_compressed.put(block_index, compressed.toByteArray());

      // Cleanup
      compressed.reset(); 
    } catch (Throwable e) {
      FileStreamUtil.printErrorAndExit(e);
    }
  }

  private synchronized boolean readNextBlock()
  {
    int read_size = 0;
    int readfull_size = 0;
    int block_size = config.getBlockSize();
    
    try { config.acquireRead(); } catch (InterruptedException e) {}

    try {
      while ((read_size = in.read(buffer, readfull_size, block_size - readfull_size)) > 0)
      {
        readfull_size += read_size;
        if (!FileStreamUtil.hasMoreBytes(in))
          break;
      }
      read_size = readfull_size;
      readfull_size = 0;
       
    } catch (Throwable e) {
      try { config.releaseRead(); } catch (InterruptedException e2) {}
      FileStreamUtil.printErrorAndExit(e);
    }

    if (read_size <= 0) {
      try { config.releaseRead(); } catch (InterruptedException e) {}
      return false;
    }

    int old = block_index;
    block_index = config.incrProcCount(); 
    buffer_size = read_size;
 
    // Add buffer to concurrent hash map of uncompressed bytes
    byte[] getinput = new byte[read_size];
    System.arraycopy(buffer, 0, getinput, 0, buffer_size);
    map_uncompressed.put(block_index, getinput);  

    if (!FileStreamUtil.hasMoreBytes(in))
    {
      is_last = true;
      config.setLastBlockIndex(block_index);
    }

    // Add dictionary to map
    if (read_size == block_size && !is_last && !config.getIndependent()) {
      byte[] dictionary = new byte[dict_size];
      System.arraycopy(buffer, block_size - dict_size, dictionary, 0, dict_size);
      map_dictionaries.put(block_index, dictionary);
    }

    is_first = false;

    try { config.releaseRead(); } catch (InterruptedException e) {}

    return true;
  }

  public boolean writeDone() { return this.write_done; }
}
