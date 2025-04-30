import java.util.*;
import java.util.concurrent.*;

public class Test {
    private static final int NUM_THREADS = 4;
    private static final int NUM_OPERATIONS = 100000;

    public static void main(String[] args) throws InterruptedException {
        benchmarkMap(new ConcurrentSkipListMap<>(), "ConcurrentSkipListMap");
        benchmarkMap(new TreeMap<>(), "TreeMap");
    }

    private static void benchmarkMap(Map<Integer, Integer> map, String mapName) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        long startTime = System.nanoTime();

        for (int i = 0; i < NUM_OPERATIONS; i++) {
            final int key = i;
            executor.execute(() -> map.put(key, key));
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        long endTime = System.nanoTime();

        System.out.println(mapName + " - Insert Time: " + (endTime - startTime) / 1_000_000 + " ms");

        // Lookup Benchmark
        startTime = System.nanoTime();
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            map.get(i);
        }
        endTime = System.nanoTime();
        System.out.println(mapName + " - Lookup Time: " + (endTime - startTime) / 1_000_000 + " ms");

        // Iteration Benchmark
        startTime = System.nanoTime();
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            entry.getKey();
        }
        endTime = System.nanoTime();
        System.out.println(mapName + " - Iteration Time: " + (endTime - startTime) / 1_000_000 + " ms");
    }
}