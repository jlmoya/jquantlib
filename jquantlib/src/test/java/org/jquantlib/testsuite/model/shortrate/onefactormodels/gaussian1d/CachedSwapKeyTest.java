// jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/onefactormodels/gaussian1d/CachedSwapKeyTest.java
//
// Phase 2 L4-B+C — verify CachedSwapKey equality semantics.
//
// Cross-check that two distinct SwapIndex instances with the same name compare
// equal under CachedSwapKey, matching the C++ struct's
//   index->name() == o.index->name()
// semantics (gaussian1dmodel.hpp line 161 @ v1.42.1).
package org.jquantlib.testsuite.model.shortrate.onefactormodels.gaussian1d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.indexes.EuriborSwapIsdaFixA;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.CachedSwapKey;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.CachedSwapKeyHasher;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Unit test for {@link CachedSwapKey} and {@link CachedSwapKeyHasher} — verifies that
 * the C++ equality contract (compare SwapIndex by {@code name()}, not by reference)
 * is preserved.
 */
public class CachedSwapKeyTest {

    @Test
    public void testEqualityByIndexName() {
        final Period tenor = new Period(10, TimeUnit.Years);
        final Date fixing = new Date(15, Month.May, 2026);
        // Two distinct SwapIndex instances with the same conventions/name.
        final SwapIndex idxA = new EuriborSwapIsdaFixA(tenor);
        final SwapIndex idxB = new EuriborSwapIsdaFixA(tenor);

        // Sanity: distinct objects but same name.
        assertEquals("indices should share name", idxA.name(), idxB.name());
        assertTrue("indices should be distinct objects", idxA != idxB);

        final CachedSwapKey kA = new CachedSwapKey(idxA, fixing, tenor);
        final CachedSwapKey kB = new CachedSwapKey(idxB, fixing, tenor);

        // C++ semantics: equal because index names match.
        assertEquals("CachedSwapKey should compare by index name", kA, kB);
        assertEquals("hashCode contract: equal keys → equal hashes", kA.hashCode(), kB.hashCode());
        assertEquals("CachedSwapKeyHasher.hash agrees with hashCode()",
                CachedSwapKeyHasher.hash(kA), kA.hashCode());
    }

    @Test
    public void testInequalityDifferentTenor() {
        final Period t1 = new Period(5, TimeUnit.Years);
        final Period t2 = new Period(10, TimeUnit.Years);
        final Date fixing = new Date(15, Month.May, 2026);
        final SwapIndex idx5y = new EuriborSwapIsdaFixA(t1);
        final SwapIndex idx10y = new EuriborSwapIsdaFixA(t2);

        // Different tenor → different index name → unequal keys.
        final CachedSwapKey k5 = new CachedSwapKey(idx5y, fixing, t1);
        final CachedSwapKey k10 = new CachedSwapKey(idx10y, fixing, t2);
        assertNotEquals("Keys with different tenors must not be equal", k5, k10);
    }

    @Test
    public void testInequalityDifferentFixing() {
        final Period tenor = new Period(10, TimeUnit.Years);
        final SwapIndex idx = new EuriborSwapIsdaFixA(tenor);
        final Date d1 = new Date(15, Month.May, 2026);
        final Date d2 = new Date(16, Month.May, 2026);

        final CachedSwapKey kA = new CachedSwapKey(idx, d1, tenor);
        final CachedSwapKey kB = new CachedSwapKey(idx, d2, tenor);
        assertNotEquals("Different fixing → unequal", kA, kB);
    }
}
