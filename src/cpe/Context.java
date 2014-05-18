package cpe;

public class Context {
  protected Object ctx; // State information, passed into Predicate Function
  protected CPE cpe; // Reference to Test Singleton
  protected String contextName;
    
  public Context(CPE cpe, Object ctx, String contextName) {
    this.cpe = cpe;
    this.ctx = ctx;
    this.cpe = CPE.getInstance();
    this.contextName = contextName;
  }

  public String getName() { return this.contextName; }
    
  public boolean checkpoint(String name) {

    Checkpoint cp = this.cpe.getCheckpoint(name);

    ContextCheckpoint cc = cp.getContextCheckpoint(this);

    // Signal all waiting threads upon new thread entry
    cp.cond.signalAll();
        
    // Precheck
    try {
      cp.lock.lock();
      do {
        // If precheck succeeds proceed on to sync phase
        if (cc.check(true)) break;
        // Otherwise wait for new thread to enter
        try {
          cp.cond.awaitUntil(cc.deadline);
        } catch(InterruptedException e) {}
      } while (cc.canPreCheck());
      
    } finally {
      cp.lock.unlock();
    }
        
    // If precheck exceeds retries or times out return false
    if (!cc.wasSatisfied()) return false;
        
    // Otherwise continue on to sync phase
    do {
      // CHECK BARRIER
      cp.barrierUntilDeadline(true, cc.deadline);
      try {
        cp.lock.lock();
        cc.check(false);
      } finally {
        cp.lock.unlock();
      }
      // SYNC BARRIER
      cp.barrierUntilDeadline(false, cc.deadline);
            
      // Check for global satisfaction
      if (cp.isSatisfied()) return true;
            
      //If this round failed wait to try again
      // BEGIN COND HACK
      try {
        cp.lock.lock();
        cp.cond.awaitUntil(cc.deadline);
      } catch(InterruptedException e) {}
      finally {
        cp.lock.unlock();
      }
      // END COND HACK
    } while (cc.canSyncCheck());
        
    // If exited then retries exceeded or timeout
    return false;
  }
}
