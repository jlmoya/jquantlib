package org.jquantlib.showcase.service;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.jquantlib.Settings;
import org.jquantlib.time.Date;

/**
 * Tiny concurrency guard around JQuantLib's <em>global</em> evaluation date.
 *
 * <p>{@code Settings.evaluationDate} is process-wide mutable state: every term
 * structure and instrument is priced relative to it. In a multi-request web app
 * two threads could otherwise interleave their {@code setEvaluationDate(...)}
 * calls and corrupt each other's pricing. Every pricing computation in this
 * showcase therefore runs through {@link #withEvaluationDate}, which serialises
 * the global-state mutation and the calculation behind a single lock.
 *
 * <p>A coarse global lock is perfectly adequate for a demo (pricing is fast and
 * concurrency is low) and buys correctness with trivial code.
 */
public final class Quant {

    private static final ReentrantLock LOCK = new ReentrantLock();

    private Quant() {
    }

    /**
     * Sets the global evaluation date and runs {@code body} while holding the
     * lock, guaranteeing no other pricing call observes a different date midway.
     */
    public static <T> T withEvaluationDate(final Date evaluationDate, final Supplier<T> body) {
        LOCK.lock();
        try {
            new Settings().setEvaluationDate(evaluationDate);
            return body.get();
        } finally {
            LOCK.unlock();
        }
    }
}
