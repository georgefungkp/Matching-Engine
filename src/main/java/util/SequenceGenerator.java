package util;

import java.util.concurrent.atomic.AtomicInteger;

public class SequenceGenerator {
    private final AtomicInteger counter = new AtomicInteger();

    public int getNextSequence() {
        return counter.incrementAndGet();
    }
}