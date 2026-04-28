package org.jquantlib.testsuite.util;

import org.junit.Test;
import static org.junit.Assert.fail;

public class MathTestSupportTest {

    @Test
    public void positiveZeroEqualsItself() {
        MathTestSupport.assertBitsEqual(0.0, 0.0);
    }

    @Test
    public void positiveAndNegativeZeroDiffer() {
        try {
            MathTestSupport.assertBitsEqual(0.0, -0.0);
            fail("expected AssertionError");
        } catch (AssertionError ok) { /* expected */ }
    }

    @Test
    public void nansCompareEqualAfterCanonicalisation() {
        final double nan1 = Double.longBitsToDouble(0x7ff8000000000001L);
        final double nan2 = Double.longBitsToDouble(0x7ffc0000deadbeefL);
        MathTestSupport.assertBitsEqual(nan1, nan2);
    }

    @Test
    public void bitsEqualReturnsTrueForMatchingBits() {
        final long bits = 0x4005bf0a8b145769L; // e ≈ 2.718…
        final double d = Double.longBitsToDouble(bits);
        if (!MathTestSupport.bitsEqual(bits, d)) {
            throw new AssertionError("bitsEqual should return true for identical bit patterns");
        }
    }

    @Test
    public void bitsEqualReturnsFalseForMismatch() {
        final long bits = 0x3ff0000000000000L; // 1.0
        final double different = 2.0;
        if (MathTestSupport.bitsEqual(bits, different)) {
            throw new AssertionError("bitsEqual should return false for 1.0 vs 2.0");
        }
    }

    @Test
    public void parseHexBitsRoundtrip() {
        final long bits = MathTestSupport.parseHexBits("0x4005bf0a8b145769");
        if (bits != 0x4005bf0a8b145769L) {
            throw new AssertionError("parse failed: 0x" + Long.toHexString(bits));
        }
    }
}
