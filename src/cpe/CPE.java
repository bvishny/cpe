package cpe;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// SnakeYAML
import org.yaml.snakeyaml.Yaml;

public class CPE {

  // Instance fields
  protected static volatile CPE instance = null;
  protected static volatile HashMap<String, Checkpoint> checkpoints;
	
  private CPE() {
    this.checkpoints = new HashMap<String, Checkpoint>();
  }
    
	protected Context getContext(String name, Object ctx) {
		return new Context(this, ctx, name);
	}
    
  public static CPE getInstance() {
    if (instance == null) {
      synchronized (CPE.class) {
        if (instance == null) {
          instance = new CPE();
        }
      }
    }
    return instance;
  }
    
  // Important! This must be called after getInstance() the first time
  // Reads yaml file, initializes checkpoints with required # threads, retry, and timeout settings
  protected static void loadConfig(String configFile) { 
   InputStream conf;
   try {
	   conf = new FileInputStream(configFile);
   } catch (FileNotFoundException e) {
	   System.out.println("Invalid config file: " + configFile);
	   return;
   }
   Yaml yaml = new Yaml();
   Map<String, Object> data = (Map<String, Object>) yaml.load(conf);
   
   for (Map.Entry<String, Object> entry : data.entrySet()) {
	    String name = entry.getKey();
	    Map<String, Object> value = (Map<String, Object>) entry.getValue();
	    String testClassName = (String) value.get("testClassName");
	    Integer numRequired = (Integer) value.get("numRequired");
	    HashMap<String, HashMap<String, String>> settings = (HashMap<String, HashMap<String, String>>) value.get("contexts");
	    checkpoints.put(name, new Checkpoint(numRequired));
	    checkpoints.get(name).addSettings(testClassName, settings);
	}
  }

  public Checkpoint getCheckpoint(String ctxName) {
    return this.checkpoints.get(ctxName);
  }
  
}
