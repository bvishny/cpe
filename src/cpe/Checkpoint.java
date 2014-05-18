package cpe;

import java.util.HashMap;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.*;
import java.util.concurrent.CyclicBarrier;
import java.util.Date;

public class Checkpoint {
  protected int threadsSatisfied; 
  protected int threadsRequired;
  protected CyclicBarrier checkBarrier;
  protected CyclicBarrier syncBarrier;
  protected String testClassName; // The name of the Test Class

  final Lock lock;
  final Condition cond;

  // Maps context name to a hashmap of settings
  protected HashMap<String, HashMap<String, String>> settings;
    
  public Checkpoint(int threadsRequired) {
    this.threadsSatisfied = 0;
    this.threadsRequired = threadsRequired;
    this.checkBarrier = new CyclicBarrier(threadsRequired);
    this.syncBarrier = new CyclicBarrier(threadsRequired);
    this.lock = new ReentrantLock();
    this.cond = lock.newCondition();
    this.testClassName = "";
  }
	
  public void barrierUntilDeadline(boolean isCheckPhase, Date deadline) {
    CyclicBarrier bar = (isCheckPhase) ? this.checkBarrier : this.syncBarrier;

    try {
		bar.await(deadline.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
	} catch (InterruptedException e) {
		e.printStackTrace();
	} catch (BrokenBarrierException e) {
		e.printStackTrace();
	} catch (TimeoutException e) {
		return;
	}
  }
	
  public boolean isSatisfied() {
    return this.threadsSatisfied == this.threadsRequired;
  }
    
  public void addSatisfied(int amount) {
    this.threadsSatisfied += amount;
  }

  public HashMap<String, String> getSettings(String ctxName) {
    return settings.get(ctxName);
  }

  // Add settings from config to settings
  public void addSettings(String testClassName, HashMap<String, HashMap<String, String>> settings) { 
	  this.testClassName = testClassName;
	  this.settings = settings;
  }

  public ContextCheckpoint getContextCheckpoint(Context ctx) { 
    return new ContextCheckpoint(ctx, this); 
  }

}
