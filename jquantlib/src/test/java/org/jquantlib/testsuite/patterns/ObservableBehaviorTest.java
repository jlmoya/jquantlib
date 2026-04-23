// jquantlib/src/test/java/org/jquantlib/testsuite/patterns/ObservableBehaviorTest.java
package org.jquantlib.testsuite.patterns;

import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Cross-validates Java Observable/Observer notification semantics against
 * QuantLib C++ v1.42.1 reference values (observable_probe).
 *
 * Audit findings (2026-04-23): no divergences. Java Observer.update() has no
 * args (matches C++ virtual void update()). Notification semantics are
 * identical: notify all registered observers once per notifyObservers() call,
 * zero calls to deregistered observers, cumulative count matches notifies*1.
 */
public class ObservableBehaviorTest {

    private static final ReferenceReader REF = ReferenceReader.load("patterns/observable");

    /**
     * Creates a fresh DefaultObservable backed by a minimal Observable owner.
     * DefaultObservable uses the delegation pattern: it requires a reference to
     * the "outer" Observable so it can pass it to observers. Since these tests
     * own the observable directly (not via delegation from another class), we
     * supply a do-nothing owner that forwards nothing -- the tests only check
     * the count, not which observable was passed in the notification.
     */
    private static Observable makeObservable() {
        // DelegatingOwner: an Observable that owns a DefaultObservable internally.
        // Using this pattern mirrors how production classes (e.g. Quote) do it.
        return new Observable() {
            private final DefaultObservable delegate = new DefaultObservable(this);

            @Override public void addObserver(Observer observer) { delegate.addObserver(observer); }
            @Override public int countObservers() { return delegate.countObservers(); }
            @Override public List<Observer> getObservers() { return delegate.getObservers(); }
            @Override public void deleteObserver(Observer observer) { delegate.deleteObserver(observer); }
            @Override public void deleteObservers() { delegate.deleteObservers(); }
            @Override public void notifyObservers() { delegate.notifyObservers(); }
            @Override public void notifyObservers(Object arg) { delegate.notifyObservers(arg); }
        };
    }

    @Test
    public void singleNotify() {
        final Observable obs = makeObservable();
        final AtomicInteger count = new AtomicInteger();
        final Observer o = makeObserver(count);
        obs.addObserver(o);
        obs.notifyObservers();
        assertEquals(REF.getCase("singleNotify").expectedLong(), count.get());
    }

    @Test
    public void deregisteredThenNotify() {
        final Observable obs = makeObservable();
        final AtomicInteger count = new AtomicInteger();
        final Observer o = makeObserver(count);
        obs.addObserver(o);
        obs.deleteObserver(o);
        obs.notifyObservers();
        assertEquals(REF.getCase("deregisteredThenNotify").expectedLong(), count.get());
    }

    @Test
    public void multipleNotify() {
        final Observable obs = makeObservable();
        final AtomicInteger count = new AtomicInteger();
        final Observer o = makeObserver(count);
        obs.addObserver(o);
        for (int i = 0; i < 5; i++) obs.notifyObservers();
        assertEquals(REF.getCase("multipleNotify").expectedLong(), count.get());
    }

    // Java Observer is a @FunctionalInterface-style interface with a single
    // abstract method update(). Using anonymous class for Java 11 compatibility.
    private static Observer makeObserver(final AtomicInteger count) {
        return new Observer() {
            @Override
            public void update() {
                count.incrementAndGet();
            }
        };
    }
}
