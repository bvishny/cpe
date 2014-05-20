[intro]: Intro

CPE: Concurrent, Blocking Assertions
===

__Authors__: Benjamin Vishny and Liam Elberty

* [Intro][intro]
* [Example][example]
* [Internals][internals]
* [Special Thanks][thanks]

Ever struggled to replicate bugs in concurrent programs? Coordinating and timing the execution of multiple threads is the only way to replicate most concurrent bugs, yet few tools exist to do this. 

Our CPE library allows users to block execution of multiple threads until a global condition is satisfied. Upon satisfaction, all threads are simultaneously released. It is a hybrid between a barrier and an assertion. All calling threads will block until a condition is locally satisfied in some required number of threads, or timeouts/retries are exhausted. The method call returns a boolean indicating whether conditions were satisfied or the call was prematurely ended. This allows outcome-specific code to be run. 

Our work is based on [this paper](https://www.usenix.org/system/files/conference/hotpar12/hotpar12-final54.pdf, "CPE Paper") by Gottschlich, Pokam, and Pereira of Intel. Building upon the original authors' work, we hope to make the following improvements: 

*   Improved organization and reduced verbosity of testing code
*   Stricter, barrier-like guarantees for condition satisfaction and thread release
*   Reduced wait times when condition not immediately satisfied

__Improved Organization__: As the authors' paper is implemented in C, it requires creation of an object containing application state for every CPE method call. In contrast, our implementation only requires one line of code per class file to configure state and the method call __checkpoint(checkpointName)__ when testing predicates. This is due to our organization and the reflection facilities available in Java. A YAML config file specifies a list of __Checkpoint__ names - each Checkpoint is akin to a test case or bug to replicate. Each Checkpoint has multiple Contexts, each __Context__ specifies a different function to be called to check for satisfaction based on the calling class. These functions are housed in separate test files as one would normally write unit tests. 

__Stricter Guarantees__: Our code ensures that conditions are not only locally satisfied in the minimum number of threads, but also synchronizes condition checking to ensure that conditions are true at the same time. Further we guarantee that satisfied threads __resume execution at the same time__. In contrast, the authors' code would individually release threads once a certain number of threads had separately satisfied the condition. 

__Reduced Wait Times__: Like the original paper, we recheck conditions in each thread until a maximum number of tries or a timeout is reached. Whereas the paper asks the user to specify the backoff time, our code is designed for situations where the time required to create a condition is unpredictable, such as an operation over a network. Rather, we retry every time a new thread calls 'checkpoint', so as to retry as soon as possible without requiring users to speculate about timing. This is based on the assumption that all threads capable of altering state examined by the test will call 'checkpoint'.


[example]: Example
Example
===

Our CPE library thrives in situations where the time to replication is unpredictable. In this example, two threads - threadA and threadB - both download data over a network and write to a shared buffer. They then output the data downloaded. If one thread reads while the other writes a race will occur. However due to the differences in network request time, their actions are unlikely to overlap and the bug is difficult to replicate. We block both so they write to the shared buffer simultaneously, thus replicating the bug. 

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

    package cpesample;

    import java.lang.*;

    public class ThreadA extends Thread {
        private Context ctx;
        private GlobalState gs;

        public ThreadA(GlobalState gs) {
            this.gs = gs;
            this.ctx = CPE.getInstance().context("A", this);
        }

        public void run() {
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

    package cpesample;

    import java.lang.*;

    public class ThreadB extends Thread {
        private Context ctx;
        private GlobalState gs;

        public ThreadB(GlobalState gs) {
            this.gs = gs;
            this.ctx = CPE.getInstance().context("B", this);
        }

        public void run() {
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

__Runner.java__

    package cpesample;

    public class Runner {
        public static void main(String args[]) {
            CPE cpe = CPE.getInstance();
            cpe.loadConfig("cpeConfig.yaml");

            GlobalState gs = new GlobalState();
            ThreadA a = new ThreadA(gs);
            ThreadB b = new ThreadB(gs);

            // Run threads
            a.start();
            b.start();

            // Wait for end
            a.join();
            b.join();
        }
    }

[internals]: Internals
Internals
===
When 'checkpoint' is called, two 'phases' are run to ensure conditions are simultaneously satisfied and remain so before release.

__Check Phase__: Each thread individually checks for local satisfaction by passing a Java Object representing its state into a boolean function defined in a test file. Note that threads check one at a time in this phase. If the condition is locally satisfied, it moves on. Otherwise, it retries the next time a new thread calls 'checkpoint' until either the timeout or number of retries specific to the Check Phase are exceeded. If a timeout or retry limit is exceeded, checkpoint immediately returns false.

__Sync Phase__: All threads that have previously locally satisfied the condition recheck the condition to ensure that state has not changed since the Check Phase and that all threads are in agreement. The use of a Cyclic Barrier before the Sync Phase ensures that no thread will be able to recheck a condition until at least the required number of threads is in the Sync Phase and individual threads cannot execute any outside code. This maximizes the chances of concurrent agreement. A Cyclic Barrier was chosen because if less than the required number of threads are locally satisfied in the Sync Phase they will retry until the Sync Phase-specific retry limit is exceeded or the overall timeout is hit. Seperate retry settings are provided for the Sync Phase because once a thread enters the sync phase it is likely to end up satisfied even if it failed the recheck due to outside interference at some point. 

Once all threads have performed a recheck and the checkpoint is globally satisfied, a second barrier is used to ensure that they exit the checkpoint simultaneously. 

__Condition Checking__: Each 'checkpoint' call is aware of a 'Context' for the thread calling it, that is some Java Object enscapulating local state, and the classpath plus name of a function that can check the condition given this Object. The classpath and method name are embedded in the YAML file. Reflection is used to to invoke the method on the Object and get a boolean result. 

__Thread Awareness__: A separate Condition variable and Reentrant lock are used for each Checkpoint so all waiting threads for a given checkpoint can be signalled when a new thread enters. Since a signal indicates a retry should occur, not necessarily that the condition is true, the problem of false wakeups need not be handled. 

[thanks]: Thanks
Special Thanks
===
Special thanks to:

* [Professor Maurice Herlihy](http://cs.brown.edu/~mph/, "Maurice Herligy") of Brown University for mentoring us. 
* Justin E. Gottschlich, Gilles A. Pokam, and Cristiano L. Pereira of Intel for their work on the original paper
* The maintainers of [SnakeYAML](https://code.google.com/p/snakeyaml/, "SnakeYAML") which we use to parse YAML. SnakeYAML is licensed under the Apache 2.0 License.
