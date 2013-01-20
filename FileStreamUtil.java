import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.util.zip.Deflater;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PushbackInputStream;

public class FileStreamUtil {
  public final static int gzip_ident = 0x8b1f;
  public final static int header_size = 10;
  public final static int tail_size = 8;

  public static void printErrorAndExit(Throwable e)
  {
    System.err.println(e.toString());
    System.exit(1);
  }

  public static void printCustomError(String e)
  {
    System.err.println(e);
    System.exit(1);
  }

  public static int putHeader(OutputStream out) throws IOException { 
    int compr_level = Deflater.DEFAULT_COMPRESSION;
    int compr_id = compr_level == 9 ? 2 : (compr_level == 1 ? 4 : 0);

    byte[] header = {
      (byte) gzip_ident,           // Identifier
      (byte) (gzip_ident >> 8),
      Deflater.DEFLATED,           // Compression method
      0, 0, 0, 0, 0,               // Assume no filename, modification time, or flags 
      (byte) compr_id,             // Default compression level
      (byte)0x03 };                // Assume Linux

    out.write(header);

    return header_size;
  }

  public static int putTail(OutputStream out, int crcval, int size_uncomp)
  {
     ByteBuffer buffer = ByteBuffer.allocate(tail_size);
     buffer.order(ByteOrder.LITTLE_ENDIAN);
     buffer.putInt(crcval);
     buffer.putInt(size_uncomp);
     try { out.write(buffer.array(), 0, tail_size); }
     catch (Throwable e) {
       FileStreamUtil.printErrorAndExit(e);
     }

     return tail_size;
  }

  public static boolean hasMoreBytes(PushbackInputStream in)
  {
    try {
      int result = in.read();
      if (result >= 0) {
        in.unread(result);
        return true;
      } else return false;
    } catch (Throwable e) { return false; }
  }
}
