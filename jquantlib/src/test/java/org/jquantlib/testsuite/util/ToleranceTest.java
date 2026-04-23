package org.jquantlib.testsuite.util;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ToleranceTest {

    @Test
    public void tightAcceptsTinyRelativeError() {
        assertTrue(Tolerance.tight(1.0 + 1e-13, 1.0));
        assertTrue(Tolerance.tight(1.0 - 1e-13, 1.0));
    }

    @Test
    public void tightRejectsLooseRelativeError() {
        assertFalse(Tolerance.tight(1.0 + 1e-11, 1.0));
    }

    @Test
    public void tightUsesAbsoluteNearZero() {
        // Reference is below 1e-2 threshold, so 1e-14 absolute applies.
        assertTrue(Tolerance.tight(1e-15, 0.0));
        assertTrue(Tolerance.tight(1e-20 + 1e-15, 1e-20));
        assertFalse(Tolerance.tight(1e-12, 0.0));
    }

    @Test
    public void looseAccepts1e9RelativeError() {
        assertTrue(Tolerance.loose(1.0 + 1e-9, 1.0));
    }

    @Test
    public void looseRejects1e7RelativeError() {
        assertFalse(Tolerance.loose(1.0 + 1e-7, 1.0));
    }

    @Test
    public void exactIntRequiresEquality() {
        assertTrue(Tolerance.exact(42L, 42L));
        assertFalse(Tolerance.exact(42L, 43L));
    }

    @Test
    public void exactDoubleRequiresBitEquality() {
        assertTrue(Tolerance.exact(1.5, 1.5));
        // Math.nextUp produces the nearest representable double above 1.5,
        // which differs by 1 ULP (~2.22e-16). 1.5 + 1e-16 collapses to 1.5
        // in IEEE 754, so we use nextUp to guarantee a distinct bit pattern.
        assertFalse(Tolerance.exact(Math.nextUp(1.5), 1.5));
    }

    @Test
    public void withinHonorsCustomTolerance() {
        assertTrue(Tolerance.within(1.0 + 1e-5, 1.0, 1e-4, "demo"));
        assertFalse(Tolerance.within(1.0 + 1e-3, 1.0, 1e-4, "demo"));
    }
}
