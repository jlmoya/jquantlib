// jquantlib/src/test/java/org/jquantlib/testsuite/patterns/HandleBehaviorTest.java
package org.jquantlib.testsuite.patterns;

import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Cross-validates Java Handle/RelinkableHandle semantics against QuantLib C++
 * v1.42.1 reference values (handle_probe).
 *
 * Audit findings (2026-04-23): no divergences. Java Handle.currentLink()
 * corresponds to C++ Handle::currentLink(). Java RelinkableHandle.linkTo()
 * corresponds to C++ RelinkableHandle::linkTo(). The Java base Handle.linkTo()
 * intentionally throws UnsupportedOperationException (only RelinkableHandle
 * supports it) — this matches C++ design intent.
 */
public class HandleBehaviorTest {

    private static final ReferenceReader REF = ReferenceReader.load("patterns/handle");

    @Test
    public void basicValue() {
        final SimpleQuote q = new SimpleQuote(1.23);
        // SimpleQuote extends Quote extends Observable; Handle<SimpleQuote> is valid.
        final Handle<SimpleQuote> h = new Handle<SimpleQuote>(q);
        assertTrue(Tolerance.tight(h.currentLink().value(),
                REF.getCase("basicValue").expectedDouble()));
    }

    @Test
    public void relinkChangesValue() {
        final SimpleQuote q1 = new SimpleQuote(1.00);
        final SimpleQuote q2 = new SimpleQuote(2.00);
        final RelinkableHandle<SimpleQuote> h = new RelinkableHandle<SimpleQuote>(q1);
        final double before = h.currentLink().value();
        h.linkTo(q2);
        final double after = h.currentLink().value();

        final JSONArray expected = REF.getCase("relinkChangesValue").expectedArray();
        assertTrue(Tolerance.tight(before, expected.getDouble(0)));
        assertTrue(Tolerance.tight(after,  expected.getDouble(1)));
    }
}
