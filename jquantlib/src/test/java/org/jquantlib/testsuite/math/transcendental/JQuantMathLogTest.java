package org.jquantlib.testsuite.math.transcendental;

import org.json.JSONObject;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.testsuite.util.MathTestSupport;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2i.6 WI-1 — bit-exact validation of {@link JQuantMath#log(double)}
 * against CORE-MATH cr_log via the probe at
 * {@code migration-harness/references/math/transcendental/log.json}.
 *
 * <p>Collect-all-failures pattern (per Phase 2i WI-1.1 review): iterate
 * every probe case before reporting; on failure show count + first 5.
 */
public class JQuantMathLogTest {

    @Test
    public void log_bitExactAgainstCoreMathProbe() {
        final ReferenceReader ref = ReferenceReader.load("math/transcendental/log");
        final List<String> mismatches = new ArrayList<>();
        for (String name : ref.caseNames()) {
            final ReferenceReader.Case c = ref.getCase(name);
            // Inputs may encode NaN/Infinity as JSON strings.
            final double x;
            final Object xRaw = c.inputs().get("x");
            if (xRaw instanceof String) {
                x = Double.parseDouble((String) xRaw);
            } else {
                x = ((Number) xRaw).doubleValue();
            }
            final long expectedBits = MathTestSupport.parseHexBits(
                ((JSONObject) c.expectedRaw()).getString("y_bits"));
            final double actual = JQuantMath.log(x);
            if (!MathTestSupport.bitsEqual(expectedBits, actual)) {
                mismatches.add(String.format(
                    "case=%s x=%s expected=0x%016x actual=0x%016x (=%s)",
                    name, x, expectedBits, Double.doubleToRawLongBits(actual), actual));
            }
        }
        if (!mismatches.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append(mismatches.size()).append(" of ").append(ref.caseNames().size())
              .append(" cases mismatched. First 5:\n");
            for (int i = 0; i < Math.min(5, mismatches.size()); ++i) {
                sb.append("  ").append(mismatches.get(i)).append('\n');
            }
            throw new AssertionError(sb.toString());
        }
    }
}
