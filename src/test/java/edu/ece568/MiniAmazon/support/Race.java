package edu.ece568.MiniAmazon.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Runs tasks on separate threads released at the same instant, and reports how each ended. */
public final class Race {
    private Race() {
    }

    public record Result(int succeeded, List<Throwable> failures) {
        public long failuresOf(Class<? extends Throwable> type) {
            return failures.stream().filter(type::isInstance).count();
        }
    }

    public static Result run(List<? extends Callable<?>> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (Callable<?> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();

        int succeeded = 0;
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get(60, TimeUnit.SECONDS);
                succeeded++;
            } catch (Exception e) {
                failures.add(e.getCause() != null ? e.getCause() : e);
            }
        }
        pool.shutdownNow();
        return new Result(succeeded, failures);
    }
}
