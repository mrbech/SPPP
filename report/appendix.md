\pagebreak
\appendix

#Helper methods
\label{appendix_helper_methods}
```java
public static void assertNull(Object o) throws Exception {
    if(o != null)
        throw new Exception(String.format("ERROR: assertNull"));
}

public static void awaitBarrier(CyclicBarrier c){
    //What is up with this checked exception madness
    try{
        c.await();
    }catch(Exception e){
        throw new RuntimeException(e);
    }
}

static void assertEquals(long x, long y) throws Exception {
    if (x != y) 
        throw new Exception(String.format("ERROR: %d not equal to %d%n", x, y));
}

public static void assertTrue(boolean b) throws Exception {
    if (!b) 
        throw new Exception(String.format("ERROR: assertTrue"));
}
```

#Chase-Lev Output
Failed: java.lang.Exception: ERROR: -182875 not equal to -182817

Failed: java.lang.Exception: ERROR: 155085 not equal to 155018

Failed: java.lang.Exception: ERROR: 227353 not equal to 227306

Failed: java.lang.Exception: ERROR: -668438 not equal to -668421

Failed: java.lang.Exception: ERROR: -573121 not equal to -573045

Failed: java.lang.Exception: ERROR: -552821 not equal to -552897

Failed: java.lang.Exception: ERROR: 663331 not equal to 663407

Failed: java.lang.Exception: ERROR: 847382 not equal to 847403

Failed: java.lang.Exception: ERROR: -368363 not equal to -368236

Failed: java.lang.Exception: ERROR: -531851 not equal to -531807
