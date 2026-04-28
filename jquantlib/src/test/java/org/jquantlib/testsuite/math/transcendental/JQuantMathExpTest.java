package org.jquantlib.testsuite.math.transcendental;

import org.json.JSONObject;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.testsuite.util.MathTestSupport;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.junit.Test;

/**
 * Phase 2i WI-1.1 — bit-exact validation of {@link JQuantMath#exp(double)}
 * against C++ libc++ {@code std::exp} via the probe at
 * {@code migration-harness/references/math/transcendental/exp.json}.
 *
 * <p>EXACT tier: comparison is on raw {@code long} bit patterns
 * (NaN-payload-canonicalised) — see Phase 2i design §4.
 */
public class JQuantMathExpTest {

    @Test
    public void exp_bitExactAgainstCppProbe() {
        final ReferenceReader ref = ReferenceReader.load("math/transcendental/exp");
        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case c = ref.getCase(name);
            // Non-finite x is emitted by the probe as a JSON string
            // ("NaN"/"Infinity"/"-Infinity") because nlohmann/json serialises
            // raw non-finite doubles as null. Recover via Double.parseDouble.
            final Object xRaw = c.inputs().get("x");
            final double x = (xRaw instanceof String)
                ? Double.parseDouble((String) xRaw)
                : ((Number) xRaw).doubleValue();
            final long expectedBits = MathTestSupport.parseHexBits(
                ((JSONObject) c.expectedRaw()).getString("y_bits"));
            final double actual = JQuantMath.exp(x);
            try {
                MathTestSupport.assertBitsEqual(expectedBits, actual);
            } catch (AssertionError ae) {
                throw new AssertionError("case=" + c.name() + " x=" + x + ": " + ae.getMessage(), ae);
            }
        }
    }
}
