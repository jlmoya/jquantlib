package org.jquantlib.testsuite.math.transcendental;

import org.json.JSONObject;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.testsuite.util.MathTestSupport;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2i.5 WI-1.1 — bit-exact validation of {@link JQuantMath#cos(double)}
 * against the CORE-MATH {@code cr_cos} probe at
 * {@code migration-harness/references/math/transcendental/cos.json}.
 *
 * <p>EXACT tier: comparison is on raw {@code long} bit patterns
 * (NaN-payload-canonicalised).
 */
public class JQuantMathCosTest {

    @Test
    public void cos_bitExactAgainstCoreMathProbe() {
        final ReferenceReader ref = ReferenceReader.load("math/transcendental/cos");
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case c = ref.getCase(name);
            final Object xRaw = c.inputs().get("x");
            final double x = (xRaw instanceof String)
                ? Double.parseDouble((String) xRaw)
                : ((Number) xRaw).doubleValue();
            final long expectedBits = MathTestSupport.parseHexBits(
                ((JSONObject) c.expectedRaw()).getString("y_bits"));
            final double actual = JQuantMath.cos(x);

            if (!MathTestSupport.bitsEqual(expectedBits, actual)) {
                final long actualBits = Double.doubleToRawLongBits(actual);
                mismatches.add(String.format(
                    "case=%s x=%s expected=0x%016x actual=0x%016x",
                    c.name(), x, expectedBits, actualBits));
            }
        }

        if (!mismatches.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append(mismatches.size()).append(" bit mismatch(es) in cos probe");
            sb.append(" (showing first ").append(Math.min(5, mismatches.size())).append("):\n");
            for (int i = 0; i < Math.min(5, mismatches.size()); i++) {
                sb.append("  ").append(mismatches.get(i)).append('\n');
            }
            throw new AssertionError(sb.toString());
        }
    }
}
