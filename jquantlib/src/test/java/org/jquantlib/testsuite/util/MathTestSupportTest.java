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
    public void parseHexBitsRoundtrip() {
        final long bits = MathTestSupport.parseHexBits("0x4005bf0a8b145769");
        if (bits != 0x4005bf0a8b145769L) {
            throw new AssertionError("parse failed: 0x" + Long.toHexString(bits));
        }
    }
}
