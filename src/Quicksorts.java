// For PCPP exam January 2015
// sestoft@itu.dk * 2015-01-03

// Several versions of sequential and parallel quicksort: 
// A: sequential recursive
// B: sequential using work a deque as a stack

// To do by students:
// C: single-queue multi-threaded with shared lock-based queue
// D: multi-queue multi-threaded with thread-local lock-based queues and stealing
// E: as D but with thread-local lock-free queues and stealing

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import javax.annotation.concurrent.GuardedBy;
import java.util.Random;

public class Quicksorts {
    final static int size = 1_000_000; // Number of integers to sort

    public static void main(String[] args) throws Exception {
        //sequentialRecursive();
        //singleQueueSingleThread();
        //singleQueueMultiThread(8);
        multiQueueMultiThread(8);
        //multiQueueMultiThreadCL(8);
        //Tests.benchmarkSingleQueueMultiThread();
        //Tests.benchMarkMultiQueueMultiThread();
        //Tests.benchMarkMultiCLQueueMultiThread();
        //Tests.runTests();
    }



    // ----------------------------------------------------------------------
    // Testing

    static class Tests {
        // ----------------------------------------------------------------------
        // Question 6
        private static void benchMarkMultiQueueMultiThread() {
            System.out.println("Threads\tTime");
            for(int i = 1; i<9; i++){
                SimpleDeque<SortTask>[] queues = new SimpleDeque[i];
                for(int t = 0; t < i; t++){
                    queues[t] = new SimpleDeque<SortTask>(100000);
                }
                double time = mqmtBenchMarkVersion(i, queues);
                System.out.println(i + "\t" + time);
            }
        }
        // ----------------------------------------------------------------------
        // Question 11
        private static void benchMarkMultiCLQueueMultiThread() {
            System.out.println("Threads\tTime");
            for(int i = 1; i<9; i++){
                ChaseLevDeque<SortTask>[] queues = new ChaseLevDeque[i];
                for(int t = 0; t < i; t++){
                    queues[t] = new ChaseLevDeque<SortTask>(100000);
                }
                double time = mqmtBenchMarkVersion(i, queues);
                System.out.println(i + "\t" + time);
            }
        }


        private static double mqmtBenchMarkVersion(int threadCount, Deque<SortTask>[] queues) {
            int[] array = IntArrayUtil.randomIntArray(20_000_000);
            queues[0].push(new SortTask(array, 0, array.length-1));
            CyclicBarrier barrier = new CyclicBarrier(threadCount+1);

            //Initialize ongoing counter with the size of the queue
            //We assume the queue only has a single task
            LongAdder ongoing = new LongAdder();
            ongoing.increment();

            //Creating threads:
            Thread[] threads = new Thread[threadCount];
            for(int t = 0; t < threadCount; t++){
                final int myNumber = t;
                threads[t] = new Thread(()->{
                    awaitBarrier(barrier);
                    mqmtWorker(queues, myNumber, ongoing);
                    awaitBarrier(barrier);
                });
                //Start the thread
                threads[t].start();
            }

            //Waiting for threads
            awaitBarrier(barrier);
            //Threads started
            Timer t = new Timer();
            awaitBarrier(barrier);
            //Threads done
            return t.check();
        }
        // ----------------------------------------------------------------------
        // Question 5
        public static void benchmarkSingleQueueMultiThread(){
            System.out.println("Threads\tTime");
            for(int i = 1; i<9; i++){
                double time = sqmtBenchMarkVersion(i);
                System.out.println(i + "\t" + time);
            }
        }

        public static double sqmtBenchMarkVersion(int threadCount){
            SimpleDeque<SortTask> queue = new SimpleDeque<SortTask>(100000);
            int[] array = IntArrayUtil.randomIntArray(20_000_000);
            queue.push(new SortTask(array, 0, array.length-1));
            CyclicBarrier barrier = new CyclicBarrier(threadCount+1);

            //Initialize ongoing counter with the size of the queue
            //We assume the queue only has a single task
            LongAdder ongoing = new LongAdder();
            ongoing.increment();

            //Creating threads:
            Thread[] threads = new Thread[threadCount];
            for(int t = 0; t < threadCount; t++){
                threads[t] = new Thread(()->{
                    awaitBarrier(barrier);
                    sqmtWorker(queue, ongoing);
                    awaitBarrier(barrier);
                });
                //Start the thread
                threads[t].start();
            }

            //Waiting for threads
            awaitBarrier(barrier);
            //Threads started
            Timer t = new Timer();
            awaitBarrier(barrier);
            //Threads done
            return t.check();

        }
        static void runTests() throws Exception{
            runTestsSimpleDeque();
            //runTestChaseLevDeque();
        }

        // ----------------------------------------------------------------------
        // Question 9
        static void runTestChaseLevDeque() throws Exception {
            System.out.println("Running ChaseLevDeque Tests");
            ChaseLevDeque<Integer> cl = new ChaseLevDeque<Integer>(100_000_000);
            sequentialDequeTest(cl);
            for(int i = 0; i < 100; i++){
                try{
                ChaseLevDeque<Integer> cl2 = new ChaseLevDeque<Integer>(100_000_000);
                parallelCLDequeTest(cl2, 10);
                }catch(Exception e){
                    System.out.println("Failed: " + e);
                }
            }
            System.out.println("ChaseLevDeque Tests Completed");
        }

        static void parallelCLDequeTest(Deque<Integer> queue, int threadCount) throws Exception {
            CyclicBarrier barrier = new CyclicBarrier(threadCount+2);
            int pushedSum = 0;
            
            //Start pushing and popping thread
            LongAdder pushed = new LongAdder();
            LongAdder popped = new LongAdder();
            new Thread(()->{
                awaitBarrier(barrier);
                long p = 0;
                long pop = 0;
                for(int i = 0; i < 400_000_000; i++){
                    Random random = new Random();
                    if((random.nextInt() % 2) == 0){
                        int r = random.nextInt() % 100;
                        p += r;
                        queue.push(r);
                    }else{
                        Integer pp = queue.pop();
                        if(pp != null){
                            pop += pp;
                        }
                    }

                }
                pushed.add(p);
                popped.add(pop);
                awaitBarrier(barrier);
            }).start();

            //Start stealing threads
            LongAdder stolen = new LongAdder();
            for(int t = 0; t < threadCount; t++){
                final int lt = t;
                new Thread(()->{
                    awaitBarrier(barrier);
                    long s = 0;
                    for(int i = 0; i < 400_000_000; i++){
                        Integer p = queue.steal();
                        if(p != null){
                            s += p;
                        }
                    }
                    stolen.add(s);
                    awaitBarrier(barrier);
                }).start();;
            }

            //Start test
            awaitBarrier(barrier);
            //Wait for the test to stop
            awaitBarrier(barrier);

            //Get the remaining sum
            long remaining = 0;
            Integer p = queue.pop();
            while(p != null){
                remaining += p;
                p = queue.pop();
            }

            //Get the sum of the threads
            long pushedsum = pushed.sum();
            long retrievedsum = remaining + popped.sum() + stolen.sum();

            //Check that sum matches
            assertEquals(retrievedsum, pushedsum);
        }

        // ----------------------------------------------------------------------
        // Question 4
        static void runTestsSimpleDeque() throws Exception {
            //Test SimpleDeque
            System.out.println("Running SimpleDeque Tests");
            SimpleDeque<Integer> simple = new SimpleDeque<Integer>(100_000_000);
            sequentialDequeTest(simple);
            SimpleDeque<Integer> simple2 = new SimpleDeque<Integer>(100_000_000);
            parallelDequeTest(simple2, 10);
            System.out.println("SimpleDeque Tests Completed");
        }

        static void sequentialDequeTest(Deque<Integer> queue) throws Exception{
            //Check that it only returns null
            assertNull(queue.pop());
            assertNull(queue.steal());

            //Check that pop/push works on single insert
            queue.push(42);
            assertEquals(42, queue.pop());
            assertNull(queue.pop());

            //Check that steal work on single insert
            queue.push(43);
            assertEquals(43, queue.steal());
            assertNull(queue.pop());

            queue.push(44);
            queue.push(45);
            queue.push(46);

            //Check that steal takes from the back
            assertEquals(44, queue.steal());

            //Check that pop takes from the front
            assertEquals(46, queue.pop());
            
        }

        static void parallelDequeTest(Deque<Integer> queue, int threadCount) throws Exception {
            CyclicBarrier barrier = new CyclicBarrier((threadCount*3)+1);
            int pushedSum = 0;

            //Start pushing threads
            LongAdder pushed = new LongAdder();
            for(int t = 0; t < threadCount; t++){
                final int lt = t;
                new Thread(()->{
                    awaitBarrier(barrier);
                    long p = 0;
                    for(int i = 0; i < 1_000_000; i++){
                        Random random = new Random();
                        int r = random.nextInt() % 1000;
                        p += r;
                        queue.push(r);
                    }
                    pushed.add(p);
                    awaitBarrier(barrier);
                }).start();
            }

            //Start pop threads
            LongAdder popped = new LongAdder();
            for(int t = 0; t < threadCount; t++){
                final int lt = t;
                new Thread(()->{
                    awaitBarrier(barrier);
                    long pop = 0;
                    for(int i = 0; i < 1_000_000; i++){
                        Integer p = queue.pop();
                        if(p != null){
                            pop += p;
                        }
                    }
                    popped.add(pop);
                    awaitBarrier(barrier);
                }).start();
            }
            //Start stealing threads
            LongAdder stolen = new LongAdder();
            for(int t = 0; t < threadCount; t++){
                final int lt = t;
                new Thread(()->{
                    awaitBarrier(barrier);
                    long s = 0;
                    for(int i = 0; i < 1_000_000; i++){
                        Integer p = queue.steal();
                        if(p != null){
                            s += p;
                        }
                    }
                    stolen.add(s);
                    awaitBarrier(barrier);
                }).start();;
            }

            //Start test
            awaitBarrier(barrier);
            //Wait for the test to stop
            awaitBarrier(barrier);

            //Get the remaining sum
            long remaining = 0;
            Integer p = queue.pop();
            while(p != null){
                remaining += p;
                p = queue.pop();
            }

            //Get the sum of the threads
            long pushedsum = pushed.sum();
            long retrievedsum = remaining + popped.sum() + stolen.sum();

            //Check that sum matches
            assertEquals(retrievedsum, pushedsum);
        }

        static void assertEquals(long x, long y) throws Exception {
            if (x != y) 
                throw new Exception(String.format("ERROR: %d not equal to %d%n", x, y));
        }

        public static void assertTrue(boolean b) throws Exception {
            if (!b) 
                throw new Exception(String.format("ERROR: assertTrue"));
        }

        public static void assertNull(Object o) throws Exception {
            if(o != null)
                throw new Exception(String.format("ERROR: assertNull"));
        }

        /**
         * What is up with this checked exception madness
         */
        public static void awaitBarrier(CyclicBarrier c){
            try{
                c.await();
            }catch(Exception e){
                throw new RuntimeException(e);
            }
        }
    }


    // ----------------------------------------------------------------------
    // Version A: Standard sequential quicksort using recursion

    private static void sequentialRecursive() {
        int[] arr = IntArrayUtil.randomIntArray(size);
        qsort(arr, 0, arr.length-1);
        System.out.println(IntArrayUtil.isSorted(arr));
    }

    // Sort arr[a..b] endpoints inclusive
    private static void qsort(int[] arr, int a, int b) {
        if (a < b) { 
            int i = a, j = b;
            int x = arr[(i+j) / 2];         
            do {                            
                while (arr[i] < x) i++;       
                while (arr[j] > x) j--;       
                if (i <= j) {
                    swap(arr, i, j);
                    i++; j--;
                }                             
            } while (i <= j); 
            qsort(arr, a, j); 
            qsort(arr, i, b); 
        }
    }

    // Swap arr[s] and arr[t]
    private static void swap(int[] arr, int s, int t) {
        int tmp = arr[s];  arr[s] = arr[t];  arr[t] = tmp;
    }

    // ----------------------------------------------------------------------
    // Version B: Single-queue single-thread setup; sequential quicksort using queue

    private static void singleQueueSingleThread() {
        SimpleDeque<SortTask> queue = new SimpleDeque<SortTask>(100000);
        int[] arr = IntArrayUtil.randomIntArray(size);
        queue.push(new SortTask(arr, 0, arr.length-1));
        sqstWorker(queue);
        System.out.println(IntArrayUtil.isSorted(arr));
    }

    private static void sqstWorker(Deque<SortTask> queue) {
        SortTask task;
        while (null != (task = queue.pop())) {
            final int[] arr = task.arr;
            final int a = task.a, b = task.b;
            if (a < b) { 
                int i = a, j = b;
                int x = arr[(i+j) / 2];         
                do {                            
                    while (arr[i] < x) i++;       
                    while (arr[j] > x) j--;       
                    if (i <= j) {
                        swap(arr, i, j);
                        i++; j--;
                    }                             
                } while (i <= j); 
                queue.push(new SortTask(arr, a, j)); 
                queue.push(new SortTask(arr, i, b));               
            }
        }
    }

    // ---------------------------------------------------------------------- 
    // Version C: Single-queue multi-thread setup 

    private static void singleQueueMultiThread(final int threadCount) {
        System.out.println("Running singleQueueMultiThread");
        SimpleDeque<SortTask> queue = new SimpleDeque<SortTask>(100000);
        //int[] arr = IntArrayUtil.randomIntArray(size);
        System.out.println("Before:");
        int[] arr = IntArrayUtil.randomIntArray(20);

        IntArrayUtil.printout(arr, 20);

        queue.push(new SortTask(arr, 0, arr.length-1));
        sqmtWorkers(queue, threadCount);

        System.out.println("After:");
        IntArrayUtil.printout(arr, 20);

        System.out.println("Sorted:");
        System.out.println(IntArrayUtil.isSorted(arr));
    }

    /**
     * Function for starting singleQueueMultiThread workers
     */
    private static void sqmtWorkers(Deque<SortTask> queue, int threadCount) {
        //Initialize ongoing counter with the size of the queue
        //We assume the queue only has a single task
        LongAdder ongoing = new LongAdder();
        ongoing.increment();

        //Creating threads:
        Thread[] threads = new Thread[threadCount];
        for(int t = 0; t < threadCount; t++){
            //Start the thread
            threads[t] = new Thread(()-> sqmtWorker(queue, ongoing));
            threads[t].start();
        }

        //Wait for the threads to finish
        for(int t = 0; t < threadCount; t++){
            try{
                threads[t].join();
            }catch(InterruptedException e){}
        }
    }

    /**
     * Function for a singleQueueMultiThread worker
     */
    private static void sqmtWorker(Deque<SortTask> queue, LongAdder ongoing){
        SortTask task;
        while (null != (task = getTask(queue, ongoing))) {
            //We have a task now partition!
            final int[] arr = task.arr;
            final int a = task.a, b = task.b;
            if (a < b) { 
                int i = a, j = b;
                int x = arr[(i+j) / 2];         
                do {                            
                    while (arr[i] < x) i++;       
                    while (arr[j] > x) j--;       
                    if (i <= j) {
                        swap(arr, i, j);
                        i++; j--;
                    }                             
                } while (i <= j); 

                //Increment the counter when pushing
                queue.push(new SortTask(arr, a, j));
                ongoing.increment();
                queue.push(new SortTask(arr, i, b));
                ongoing.increment();
            }
            //We have sorted something, time to decrement
            ongoing.decrement();
        }
    }

    // Tries to get a sorting task.  If task queue is empty but some
    // tasks are not yet processed, yield and then try again.

    private static SortTask getTask(final Deque<SortTask> queue, LongAdder ongoing) {
        SortTask task;
        while (null == (task = queue.pop())) {
            if (ongoing.longValue() > 0) 
                Thread.yield();
            else 
                return null;
        }
        return task;
    }


    // ----------------------------------------------------------------------
    // Version D: Multi-queue multi-thread setup, thread-local queues

    private static void multiQueueMultiThread(final int threadCount) {
        System.out.println("Running multiQueueMultiThread");
        SimpleDeque<SortTask>[] queues = new SimpleDeque[threadCount];
        for(int i = 0; i < threadCount; i++){
            queues[i] = new SimpleDeque<SortTask>(100000);
        }
        //int[] arr = IntArrayUtil.randomIntArray(size);
        System.out.println("Before:");
        int[] arr = IntArrayUtil.randomIntArray(20);
        IntArrayUtil.printout(arr, 20);


        queues[0].push(new SortTask(arr, 0, arr.length-1));
        mqmtWorkers(queues, threadCount);

        System.out.println("After:");
        IntArrayUtil.printout(arr, 20);
        
        System.out.println("Sorted:");
        System.out.println(IntArrayUtil.isSorted(arr));
    }

    // Version E: Multi-queue multi-thread setup, thread-local queues

    private static void multiQueueMultiThreadCL(final int threadCount) {
        System.out.println("Running multiQueueMultiThreadCL");
        ChaseLevDeque<SortTask>[] queues = new ChaseLevDeque[threadCount];
        for(int i = 0; i < threadCount; i++){
            queues[i] = new ChaseLevDeque<SortTask>(100000);
        }
        //int[] arr = IntArrayUtil.randomIntArray(size);
        System.out.println("Before:");
        int[] arr = IntArrayUtil.randomIntArray(20);
        IntArrayUtil.printout(arr, 20);


        queues[0].push(new SortTask(arr, 0, arr.length-1));
        mqmtWorkers(queues, threadCount);

        System.out.println("After:");
        IntArrayUtil.printout(arr, 20);

        System.out.println("Sorted:");
        System.out.println(IntArrayUtil.isSorted(arr));
    }

    private static void mqmtWorkers(Deque<SortTask>[] queues, int threadCount) {
        //Initialize ongoing counter with the size of the queue
        //We assume the queue only has a single task
        LongAdder ongoing = new LongAdder();
        ongoing.increment();

        //Creating threads:
        Thread[] threads = new Thread[threadCount];
        for(int t = 0; t < threadCount; t++){
            //Start worker thread
            final int myNumber = t;
            threads[t] = new Thread(()-> mqmtWorker(queues, myNumber, ongoing));
            threads[t].start();
        }

        //Wait for the threads to finish
        for(int t = 0; t < threadCount; t++){
            try{
                threads[t].join();
            }catch(InterruptedException e){}
        }
    }

    private static void mqmtWorker(Deque<SortTask>[] queues, int myNumber,
            LongAdder ongoing){
            SortTask task;
            while (null != (task = getTask(myNumber, queues, ongoing))) {
                //We have a task now partition!
                final int[] arr = task.arr;
                final int a = task.a, b = task.b;
                if (a < b) { 
                    int i = a, j = b;
                    int x = arr[(i+j) / 2];         
                    do {                            
                        while (arr[i] < x) i++;       
                        while (arr[j] > x) j--;       
                        if (i <= j) {
                            swap(arr, i, j);
                            i++; j--;
                        }                             
                    } while (i <= j); 

                    //Increment the counter when pushing
                    queues[myNumber].push(new SortTask(arr, a, j));
                    ongoing.increment();
                    queues[myNumber].push(new SortTask(arr, i, b));
                    ongoing.increment();
                }
                //We have sorted something, time to decrement
                ongoing.decrement();
            }
    } 

    // Tries to get a sorting task.  If task queue is empty, repeatedly
    // try to steal, cyclically, from other threads and if that fails,
    // yield and then try again, while some sort tasks are not processed.

    private static SortTask getTask(final int myNumber, final Deque<SortTask>[] queues, 
            LongAdder ongoing) {
        final int threadCount = queues.length;
        SortTask task = queues[myNumber].pop();
        if (null != task) 
            return task;
        else {
            do {
                //Lets try to steal a task from someone...
                for(int i = 0; i < queues.length; i++){
                    if(i != myNumber){
                        task = queues[i].steal();
                        if(task != null){
                            return task;
                        }
                    }
                }
                Thread.yield();
            } while (ongoing.longValue() > 0);
            return null;
        }
    }
}

// ----------------------------------------------------------------------
// SortTask class, Deque<T> interface, SimpleDeque<T> 

// Represents the task of sorting arr[a..b]
class SortTask {
    public final int[] arr;
    public final int a, b;

    public SortTask(int[] arr, int a, int b) {
        this.arr = arr; 
        this.a = a;
        this.b = b;
    }
}

interface Deque<T> {
    void push(T item);    // at bottom
    T pop();              // from bottom
    T steal();            // from top
}

class SimpleDeque<T> implements Deque<T> {
    // The queue's items are in items[top%S...(bottom-1)%S], where S ==
    // items.length; items[bottom%S] is where the next push will happen;
    // items[(bottom-1)%S] is where the next pop will happen;
    // items[top%S] is where the next steal will happen; the queue is
    // empty if top == bottom, non-empty if top < bottom, and full if
    // bottom - top == items.length.  The top field can only increase.
    @GuardedBy("this")
    private long bottom = 0, top = 0;
    @GuardedBy("this")
    private final T[] items;

    public SimpleDeque(int size) {
        this.items = makeArray(size);
    }

    @SuppressWarnings("unchecked") 
    private static <T> T[] makeArray(int size) {
        // Java's @$#@?!! type system requires this unsafe cast    
        return (T[])new Object[size];
    }

    private static int index(long i, int n) {
        return (int)(i % (long)n);
    }

    public synchronized void push(T item) { // at bottom
        final long size = bottom - top;
        if (size == items.length) 
            throw new RuntimeException("queue overflow");
        items[index(bottom++, items.length)] = item;
    }

    public synchronized T pop() { // from bottom
        final long afterSize = bottom - 1 - top;
        if (afterSize < 0) 
            return null;
        else
            return items[index(--bottom, items.length)];
    }

    public synchronized T steal() { // from top
        final long size = bottom - top;
        if (size <= 0) 
            return null;
        else
            return items[index(top++, items.length)];
    }
}

// ----------------------------------------------------------------------

// A lock-free queue simplified from Chase and Lev: Dynamic circular
// work-stealing queue, SPAA 2005.  We simplify it by not reallocating
// the array; hence this queue may overflow.  This is close in spirit
// to the original ABP work-stealing queue (Arora, Blumofe, Plaxton:
// Thread scheduling for multiprogrammed multiprocessors, 2000,
// section 3) but in that paper an "age" tag needs to be added to the
// top pointer to avoid the ABA problem (see ABP section 3.3).  This
// is not necessary in the Chase-Lev dequeue design, where the top
// index never assumes the same value twice.

// PSEUDOCODE for ChaseLevDeque class:

class ChaseLevDeque<T> implements Deque<T> {
    volatile long bottom = 0; 
    final AtomicLong top = new AtomicLong(0);
    final T[] items;

    public ChaseLevDeque(int size) {
        this.items = makeArray(size);
    }

    @SuppressWarnings("unchecked") 
    private static <T> T[] makeArray(int size) {
        // Java's @$#@?!! type system requires this unsafe cast    
        return (T[])new Object[size];
    }

    private static int index(long i, int n) {
        return (int)(i % (long)n);
    }

    public void push(T item) { // at bottom
        final long b = bottom, t = top.get(), size = b - t;
        if (size == items.length) 
            throw new RuntimeException("queue overflow");
        items[index(b, items.length)] = item;
        bottom = b+1;
    }

    public T pop() { // from bottom
        final long b = bottom - 1;
        bottom = b;
        final long t = top.get(), afterSize = b - t;

        if (afterSize < 0) { // empty before call
            bottom = t;
            return null;
        } else {
            T result = items[index(b, items.length)];
            if (afterSize > 0) // non-empty after call
                return result;
            else {		// became empty, update both top and bottom
                if (!top.compareAndSet(t, t+1)) // somebody stole result
                    result = null;
                bottom = t+1;
                return result;
            }
        }
    }

    public T steal() { // from top
        final long t = top.get(), b = bottom, size = b - t;
        if (size <= 0)
            return null;
        else {
            T result = items[index(t, items.length)];
            if (top.compareAndSet(t, t+1))
                return result;
            else 
                return null;
        }
    }
}

// ----------------------------------------------------------------------

class IntArrayUtil {
    public static int[] randomIntArray(final int n) {
        int[] arr = new int[n];
        for (int i = 0; i < n; i++)
            arr[i] = (int) (Math.random() * n * 2);
        return arr;
    }

    public static void printout(final int[] arr, final int n) {
        for (int i=0; i < n; i++)
            System.out.print(arr[i] + " ");
        System.out.println("");
    }

    public static boolean isSorted(final int[] arr) {
        for (int i=1; i<arr.length; i++)
            if (arr[i-1] > arr[i])
                return false;
        return true;
    }
}
