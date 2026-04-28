package org.jquantlib.testsuite.math.transcendental;

import org.json.JSONObject;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.testsuite.util.MathTestSupport;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2i WI-1.1 — bit-exact validation of {@link JQuantMath#exp(double)}
 * against C++ libc++ {@code std::exp} via the probe at
 * {@code migration-harness/references/math/transcendental/exp.json}.
 *
 * <p>EXACT tier: comparison is on raw {@code long} bit patterns
 * (NaN-payload-canonicalised) — see Phase 2i design §4.
 *
 * <p>All cases are iterated before failure is reported; the first 5 mismatches
 * are listed in the failure message so clustering is visible without re-running.
 */
public class JQuantMathExpTest {

    @Test
    public void exp_bitExactAgainstCppProbe() {
        final ReferenceReader ref = ReferenceReader.load("math/transcendental/exp");
        final List<String> mismatches = new ArrayList<>();

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

            if (!MathTestSupport.bitsEqual(expectedBits, actual)) {
                final long actualBits = Double.doubleToRawLongBits(actual);
                mismatches.add(String.format(
                    "case=%s x=%s expected=0x%016x actual=0x%016x",
                    c.name(), x, expectedBits, actualBits));
            }
        }

        if (!mismatches.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append(mismatches.size()).append(" bit mismatch(es) in exp probe");
            sb.append(" (showing first ").append(Math.min(5, mismatches.size())).append("):\n");
            for (int i = 0; i < Math.min(5, mismatches.size()); i++) {
                sb.append("  ").append(mismatches.get(i)).append('\n');
            }
            throw new AssertionError(sb.toString());
        }
    }
}
