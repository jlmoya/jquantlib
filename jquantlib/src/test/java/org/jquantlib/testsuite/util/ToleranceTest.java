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
        // Additive hybrid: at cppValue=0, budget = TIGHT_ABS + 0 = 1e-14.
        assertTrue(Tolerance.tight(1e-15, 0.0));
        assertTrue(Tolerance.tight(1e-20 + 1e-15, 1e-20));
        assertFalse(Tolerance.tight(1e-12, 0.0));
    }

    @Test
    public void tightIsContinuousAcrossRegimeTransition() {
        // The old step-function implementation had a threshold at 1e-2;
        // the hybrid form is continuous. At cppValue = 1e-2, budget is
        // TIGHT_ABS + TIGHT_REL * 1e-2 = 1e-14 + 1e-14 = 2e-14.
        // Just below and just above should behave the same way.
        final double belowHinge = 9.9e-3;
        final double aboveHinge = 1.1e-2;
        // Both pass at 1e-15 diff.
        assertTrue(Tolerance.tight(belowHinge + 1e-15, belowHinge));
        assertTrue(Tolerance.tight(aboveHinge + 1e-15, aboveHinge));
        // Both fail at 1e-13 diff.
        assertFalse(Tolerance.tight(belowHinge + 1e-13, belowHinge));
        assertFalse(Tolerance.tight(aboveHinge + 1e-13, aboveHinge));
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
