#Question 1:
To make the *SimpleDeque* thread safe we can apply the Java Monitor Pattern,
that is that any mutable state is put in private fields and are guarded by the
objects own lock. 

So in our *SimpleDeque* we ensure that *items*, *bottom* and *top* are private
and are *GuardedBy("this")*. We then make the public method's *push*, *pop* and
*steal* *syncronized*. These changes makes the class threadsafe by ensuring that
only a single thread will ever be allowed to make updates to its internal
mutable state. 

The *GuardedBy("this")* annotations are added to all internal state that needs
to be guarded by object lock, this is to communicate to others that any updates
made by these fields must only be done when the *this* lock is taken, it is also
useful in combination with tools that can be used to detect if the correct lock
is always taken when access are made to the field.

```java
@GuardedBy("this")
private long bottom = 0, top = 0;
@GuardedBy("this")
private final T[] items;

public synchronized void push(T item) {...}
public synchronized T pop() {...}
public synchronized T steal() {...}
```

#Question 2
The SortTask is a immutable object, this is done by making the public fields *final*. By being immutable we ensure that after its creation no one can update it, so we can safely pass it around threads. Any thread that needs to do an "update" of the *SortTask* will instead have to create a new instance with the updated values.

#Question 3
```java
private static void sqmtWorkers(Deque<SortTask> queue, int threadCount) {
    //Initialize ongoing counter with the size of the queue
    //We assume the queue only has a single task
    LongAdder ongoing = new LongAdder();
    ongoing.increment();

    //Creating threads:
    Thread[] threads = new Thread[threadCount];
    for(int t = 0; t < threadCount; t++){
        threads[t] = new Thread(()->{
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
        });
        //Start the thread
        threads[t].start();
    }

    //Wait for the threads to finish
    for(int t = 0; t < threadCount; t++){
        try{
            threads[t].join();
        }catch(InterruptedException e){}
    }
}
```

```
Before:
34 13 35 6 35 24 21 38 17 27 28 3 28 7 19 29 37 24 18 20

After:
3 6 7 13 17 18 19 20 21 24 24 27 28 28 29 34 35 35 37 38
```

#Question 4
I have used a few helper functions:
From the course: assertEquals, assertTrue.
Own: assertNull, awaitBarrier. Can be found in appendix
\ref{appendix_helper_methods}

```java
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
```

```java
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
```

#Question 5
With 20 million integers:

Threads  Time (Seconds)
-------  -------- 
1        5.163053139
2        7.496664894
3        6.961242125
4        7.567593447
5        5.036839369
6        4.811132867
7        4.600989057
8        4.53502937

```java
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
```

#Question 6
