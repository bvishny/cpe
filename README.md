CPE: Concurrent, Blocking Assertions
===

__Authors__: Benjamin Vishny and Liam Elberty

Ever struggled to replicate bugs in concurrent programs? Coordinating the execution of multiple threads is the only way to replicate most concurrent bugs, yet few tools exist to do this. 

Our CPE library allows multiple threads to block until a global condition is satisfied, then simultaneously resume execution. It is a hybrid between a barrier and an assertion. All calling threads will wait until a condition is locally satisfied in some required number of threads, or a timeout is reached. The method call returns __true__ if conditions are satisfied and __false__ if the timeout is reached. This allows outcome-specific code to be run. 

Our work is based on [this paper](https://www.usenix.org/system/files/conference/hotpar12/hotpar12-final54.pdf, "CPE Paper") by Gottschlich, Pokam, and Pereira of Intel. Building upon the original authors' work, we hope to make the following improvements: 

*   Improved organization & reduced verbosity of testing code
*   Stricter, barrier-like guarantees for condition satisfaction & thread release
*   Reduced wait times when condition not immediately satisfied

__Improved Organization__: The authors' paper requires creation of objects containing state and method calls with several arguments when testing predicates. In contrast, our implementation only requires one line of code per class file to configure state and the method call __checkpoint(checkpointName)__ when testing predicates. This is due to our organization and the reflection facilities available in Java. A YAML config file specifies a list of Checkpoint names - each Checkpoint is akin to a test case or bug to replicate. Each Checkpoint has multiple Contexts, each Context specifies a different function to be called to check for satisfaction based on the calling class. These functions are housed in separate test files as one would normally write unit tests. 

__Stricter Guarantees__: Our code ensures that conditions are not only true for the minimum number of threads, but that conditions are true __at the same time__ and that all satisfied threads __resume execution at the same time__. In contrast, the authors' code would individually release threads once a certain number of threads had separately satisfied the condition. 

__Reduced Wait Times__: 


__GlobalState.java__

    package cpesample;

    public class GlobalState {
        public Boolean isInconsistentState; 
        public ByteBuffer temporaryBuffer;

        public GlobalState() {
            this.isInconsistentState = false;
            // Allocate up to 5 MB temporary buffer
            this.temporaryBuffer = ByteBuffer.allocate(5 * 1024 * 1024);
        }

        public byte[] loadDataFromRemoteResource(String resourceName) {
            // Network operation code goes here
        }
    }

__ThreadA.java__

    package cpesample

    public class ThreadA {
        private Context ctx;
        private GlobalState gs;

        public ThreadA(GlobalState gs) {
            this.gs = gs;
            this.ctx = CPE.getInstance().context("A", this);
        }

        public void doStuffA() {
            byte[] remoteData = gs.loadDataFromRemoteResource("resourceA");

            gs.isInconsistentState = true;

            // Wait for another thread to simultaneously write to shared buffer
            if ctx.checkpoint("sharedBufferRace") {
                // Proceed with unsafe operations
                gs.temporaryBuffer = remoteData;
                String decoded = new String(bytes, "UTF-8");
                System.out.println(decoded);
            } else {
                // Otherwise execute not satisfied code
                System.out.println("A: Unable to replicate data race");
            }
        }
    }

__ThreadB.java__

    package cpesample

    public class ThreadB {
        private Context ctx;
        private GlobalState gs;

        public ThreadB(GlobalState gs) {
            this.gs = gs;
            this.ctx = CPE.getInstance().context("B", this);
        }

        public void doStuffB() {
            byte[] remoteData = gs.loadDataFromRemoteResource("resourceB");

            gs.isInconsistentState = true;

            // Wait for another thread to simultaneously write to shared buffer
            if ctx.checkpoint("sharedBufferRace") {
                // Proceed with unsafe operations
                gs.temporaryBuffer = remoteData;
                String decoded = new String(bytes, "UTF-8");
                System.out.println(decoded);
            } else {
                // Otherwise execute not satisfied code
                System.out.println("B: Unable to replicate data race");
            }
        }
    }

__cpeConfig.yaml__

    ---
    sharedBufferRace:
      testClassName: cpesample.CPETests
      numRequired: 2
      contexts:
        A:
          timeoutMS: 5000
          retriesCheck: 5
          retriesSync: 10
          methodName: testContextA
        B:
          timeoutMS: 2000
          retriesCheck: 3
          retriesSync: 6
          methodName: testContextB


__CPETests.java__

    package cpesample;

    public class CPETests {
        public Boolean testContextA(Object state) {
            return ((GlobalState) state.gs).isInconsistentState;
        }

        public Boolean testContextA(Object state) {
            return ((GlobalState) state.gs).isInconsistentState;
        }
    }