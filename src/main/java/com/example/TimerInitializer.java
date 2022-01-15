package com.example;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class TimerInitializer implements AutoCloseable {

    private final Timer timer;

    private final AtomicInteger count = new AtomicInteger(0);

    public TimerInitializer() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                count.incrementAndGet();
            }
        }, 0,1000);
    }

    public int get() {
        return count.get();
    }

    public void reset() {
        count.set(0);
    }

    @Override
    public void close() {
        timer.cancel();
    }
}
