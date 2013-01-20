import java.io.InputStream;
import java.io.OutputStream;

public class Jpigz {
  private Runtime runtime;
  
  public Jpigz(String[] args) { // Constructor
    runtime = Runtime.getRuntime();
    JConfig config = new JConfig();
    // Set defaults
    runtime = Runtime.getRuntime();
    config.setProcNum(runtime.availableProcessors()); // Default # Processors
    
    parseOptions(args, config);
    if (config.getProcNum() == 1)
    { // Compress in linear time
      SingleOutStream single_out = new SingleOutStream(System.out, System.in, config);
      single_out.compressInput();
    }
    else
    { // Compress in parallel
      ParallelOutStream parallel_out = new ParallelOutStream(System.out, System.in, config);
      parallel_out.compressInput(); 
    }
  }

  public void parseOptions(String[] opt, JConfig config)
  {
    for (int i = 0; i < opt.length; i++)
    {
      if (opt[i].equals("-p")) { // Set Max Processors
        if (i != opt.length - 1) {
          int nproc = 0, maxproc = config.getProcNum();
          try {
            nproc = Integer.parseInt(opt[i + 1]);
            config.setProcNum(nproc);
          } catch (NumberFormatException e) {
            FileStreamUtil.printCustomError("Error: Option following -p must be an integer");
          }
        }
        else FileStreamUtil.printCustomError("Error: Option following -p must be an integer");
      }
            
      else if (opt[i].equals("-i"))
        config.setIndependent(true);
    } 
  }

  public static void main(String[] args)
  {
    new Jpigz(args);
  }
}