package org.logdoc.fairhttp.service.http;

import org.logdoc.fairhttp.service.tools.ResourceConnect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ConcurrentModificationException;
import java.util.Set;
import java.util.concurrent.*;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.03.2024 12:33
 * fair-http-server ☭ sweat and blood
 */
public final class RCCycler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("FairServerMainLoop");
    private final ExecutorService executor;
    private final Set<ResourceConnect> rcs;
    private final Semaphore semaphore;
    private final int readMax;

    RCCycler(final int readMaxAtOnce, final ExecutorService executor) {
        this.executor = executor;
        rcs = ConcurrentHashMap.newKeySet();
        readMax = readMaxAtOnce;

        semaphore = new Semaphore(0, false);
    }

    @Override
    public void run() {
        try {
            if (rcs.isEmpty())
                synchronized (semaphore) {
                    semaphore.acquire();
                    semaphore.release();
                }

            synchronized (rcs) {
                final CompletableFuture<?>[] reads = new CompletableFuture[rcs.size()];
                int i = 0;

                for (final ResourceConnect rc : rcs)
                    if (rc.hasTask())
                        reads[i++] = CompletableFuture.runAsync(rc.getTask(), executor);

                CompletableFuture.allOf(reads).get();
            }
        } catch (final ConcurrentModificationException ignore) {
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
        }

        executor.submit(this);
    }

    void addRc(final ResourceConnect rc) {
        if (rc != null) {
            rcs.add(rc);
            semaphore.release();
        }
    }

    void removeRc(final ResourceConnect rc) {
        if (rc != null) {
            rcs.remove(rc);
            semaphore.acquireUninterruptibly();
        }
    }
}
