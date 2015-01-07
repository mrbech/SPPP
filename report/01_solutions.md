#Question 1:
To make the *SimpleDeque* thread safe we can apply the Java Monitor Pattern,
that is that any mutable state is put in private fields and are guarded by the
objects own lock. 

So in our *SimpleDeque* we ensure that *items*, *bottom* and *top* are private
and are *@GuardedBy("this")*. We then make the public method's *push*, *pop* and
*steal* *syncronized*. These changes makes the class threadsafe by ensuring that
only a single thread will ever be allowed to make updates to its internal
mutable state. 

The *@GuardedBy("this")* annotations are added to all internal state that needs
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

