import java.util.zip.CRC32;
import java.util.zip.Deflater;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.BufferedInputStream;
import java.io.FilterOutputStream;
import java.io.ByteArrayOutputStream;

public class SingleOutStream {
  // Constants
  private static final int header_size = 10;
  private static final int tail_size = 8;
  private static final int dict_size = 32*1024;

  private JConfig config;
  //Deflater deflater;
  private CRC32 crc = new CRC32();
  private OutputStream out; 
  private InputStream in;
  //private Deflater deflater;

  public SingleOutStream(OutputStream s_out, InputStream s_in, JConfig gconfig)
  {
    out = s_out;
    in = s_in;
    config = gconfig;
  }

  public void compressInput()
  {
    try {
    ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    ByteArrayOutputStream uncompressed = new ByteArrayOutputStream();
    PushbackInputStream in_b = new PushbackInputStream(in);
    Deflater defl;
 
    // Print header
    try {
      FileStreamUtil.putHeader(out);
    } catch (Throwable e) {
      FileStreamUtil.printErrorAndExit(e);
    }
    
    // Grab data from input and compress sequentially
    int block_size = config.getBlockSize();
    byte[] buffer = new byte[block_size];
    byte[] buffer_c = new byte[block_size * 2]; // Double for safety
    byte[] dictionary = new byte[dict_size]; // Last 32 KB of previous block
    boolean first_block = true;
    boolean last_block = false;
    boolean dict_set = false;
    int read_size = 0;
    int readfull_size = 0;
    int defl_len = 0;

    while ((read_size = in_b.read(buffer, readfull_size, block_size - readfull_size)) > 0)
    {
      readfull_size += read_size;
      if ((readfull_size < block_size) && FileStreamUtil.hasMoreBytes(in_b))
        continue;
      read_size = readfull_size;
      readfull_size = 0;
      
      defl = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
      
      if (!first_block)
        if (!config.getIndependent())
          {
              defl.setDictionary(dictionary, 0, dict_size);
              dict_set = true;
          }
      
      if (!FileStreamUtil.hasMoreBytes(in_b))
        last_block = true; 

      if (read_size == block_size && !last_block)
        System.arraycopy(buffer, block_size - dict_size, dictionary, 0, dict_size);

      uncompressed.write(buffer, 0, read_size);

      defl.setInput(buffer, 0, read_size);
      if (!last_block)
      {
        defl_len = defl.deflate(buffer_c, 0, buffer_c.length, Deflater.SYNC_FLUSH);
      }
      else
      {
        defl.finish();
        while (!defl.finished())
          defl_len = defl.deflate(buffer_c, 0, buffer_c.length, Deflater.NO_FLUSH);
      }
      out.write(buffer_c, 0, defl_len);

      first_block = false;
      if (last_block) break;

    }

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
    
    } catch (Throwable e) {
      FileStreamUtil.printErrorAndExit(e);
    }
  } 
}
