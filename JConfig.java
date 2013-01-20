import java.util.concurrent.Semaphore;

public class JConfig {
  private volatile boolean independent = false;
  private volatile int proc_num;
  private volatile int proc_index_count = 0;
  private volatile int last_block_index = 0;

  private static final int block_size = 128 * 1024;

  private Semaphore sem_read = new Semaphore(1, true);

  public boolean getIndependent() { return this.independent; }
  public int getProcNum() { return this.proc_num; }
  public static int getBlockSize() { return block_size; }

  public void setIndependent(boolean val) { this.independent = val; }
  public void setProcNum(int val) { this.proc_num = val; }
  
  public synchronized int incrProcCount() {
    proc_index_count++;
    return proc_index_count;
  }
  
  public void acquireRead() throws InterruptedException { sem_read.acquire(); }

  public void releaseRead() throws InterruptedException { sem_read.release(); }

  public synchronized void setLastBlockIndex(int i) { last_block_index = i; } 

  public synchronized int getLastBlockIndex() { return last_block_index; }
}