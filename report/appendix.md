\pagebreak
\appendix

#Helper methods
\label{appendix_helper_methods}
```
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
```
