CPE: Concurrent, Blocking Assertions
===

__GlobalState.java__

    package cpesample;

    public class GlobalState {
        public Boolean isInconsistentState; 
        public ByteBuffer temporaryBuffer;

        public GlobalState() {
            this.isInconsistentState = false;
            // Allocate up to 5 MB temporary buffer
            this.bbuf = ByteBuffer.allocate(5 * 1024 * 1024);
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