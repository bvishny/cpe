package cpe;

import java.util.HashMap;
import java.util.Date;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ContextCheckpoint {
  protected int retriesCheck; // Retries remaining for "initial check"
  protected int retriesSync; // Retries remaining for "sync phase"
  protected Date deadline; // The deadline to move on
  protected boolean satisfied;
  protected Context context;
  protected Checkpoint checkpoint;
  protected String methodName;

  public ContextCheckpoint(Context context, Checkpoint checkpoint) {
    this.context = context;
    this.checkpoint = checkpoint;

    HashMap<String, String> settings = this.checkpoint.getSettings(context.getName());

    String timeoutMSStr = settings.get("timeoutMS");
    String retriesCheckStr = settings.get("retriesCheck");
    String retriesSyncStr = settings.get("retriesSync");
    assert(timeoutMSStr != null && retriesSyncStr != null && retriesCheckStr != null);
    int timeoutMS = Integer.parseInt(timeoutMSStr);

    this.retriesCheck = Integer.parseInt(retriesCheckStr);
    this.retriesSync = Integer.parseInt(retriesSyncStr);
    this.methodName = settings.get("methodName");

    this.deadline = new Date(System.currentTimeMillis() + timeoutMS);
    this.satisfied = false;
  }
    
  public boolean canPreCheck() {
    return this.retriesCheck > 0 && new Date().before(this.deadline);
  }
    
  public boolean canSyncCheck() {
    return this.retriesSync > 0 && new Date().before(this.deadline);
  }
    
  public boolean wasSatisfied() {
    return this.satisfied;
  }
    
  // Determines if predicate satisfied
  public Boolean isSatisfied() {
    Method method;
    Boolean result = false;
	try {
		method = Class.forName(this.checkpoint.testClassName).getMethod(this.methodName, Object.class);
		result = ((Boolean) method.invoke(null, this.context.ctx));
	} catch (SecurityException e) {
		e.printStackTrace();
	} catch (NoSuchMethodException e) {
		e.printStackTrace();
	} catch (ClassNotFoundException e) {
		e.printStackTrace();
	} catch (IllegalAccessException e) {
		e.printStackTrace();
	} catch (IllegalArgumentException e) {
		e.printStackTrace();
	} catch (InvocationTargetException e) {
		e.printStackTrace();
	} 
    return result;
  }
    
  public boolean check(boolean isCheckPhase) {
    
    boolean result = this.isSatisfied();

    if (isCheckPhase) {
      this.retriesCheck -= 1;
    } else {
      this.retriesSync -= 1;
      if (!result && this.satisfied)  {
        this.checkpoint.addSatisfied(-1);
      } else if (result && !this.satisfied) {
        this.checkpoint.addSatisfied(1);
      }
    }

    this.satisfied = result;
    return result;
  }
}

