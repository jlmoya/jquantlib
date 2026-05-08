package org.jquantlib.testsuite.math.transcendental;

import org.json.JSONObject;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.testsuite.util.MathTestSupport;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 2n A.1 — bit-exact validation of {@link JQuantMath#pow(double, double)}
 * against CORE-MATH cr_pow via the probe at
 * {@code migration-harness/references/math/transcendental/pow.json}.
 *
 * <p>Reference set: 2,763 cases covering specials, integer powers, dense
 * fractional grid, SABR pricing path, vanilla engines, AdaptiveRungeKutta
 * exponents, InterestRate compounding, large stress, subnormal/boundary
 * cases, hard-rounding-boundary cases, and small integer powers.
 *
 * <p><b>Phase 2n A.1.c status:</b> Stage 1 (fast path) + Stage 2 (Dint64
 * accurate path) are complete. All 2,763 reference cases are bit-exact
 * against CORE-MATH cr_pow. Stage 3 (Qint64 + exact_pow) is deferred to
 * Phase 2o and triggers only when Stage 2's 28-ulp rounding test fails
 * — extremely rare in practice; when triggered we currently fall back
 * to {@link Math#pow} which may differ by ≤ 1 ULP on those edge cases.
 *
 * <p>Collect-all-failures pattern (per Phase 2i WI-1.1 review).
 */
public class JQuantMathPowTest {

    /**
     * Specific reference cases that the Stage 2 28-ulp rounding test
     * fails to disambiguate, falling through to {@code Math.pow}. Empty
     * after Stage 2 lands — Stage 2 is sufficient for all current
     * reference-set cases; Stage 3 closure (Phase 2o) is reserved for
     * inputs that strictly need >256-bit precision.
     */
    private static final Set<String> KNOWN_STAGE1_FALLTHROUGH = new HashSet<>(Arrays.asList(
        // (empty — Stage 2 catches all current reference cases.)
    ));

    @Test
    public void pow_bitExactAgainstCoreMathProbe() {
        final ReferenceReader ref = ReferenceReader.load("math/transcendental/pow");
        final List<String> mismatches = new ArrayList<>();
        int validated = 0;
        int skipped = 0;
        for (String name : ref.caseNames()) {
            if (KNOWN_STAGE1_FALLTHROUGH.contains(name)) {
                skipped++;
                continue;
            }
            validated++;
            final ReferenceReader.Case c = ref.getCase(name);
            final double x = readDouble(c.inputs(), "x");
            final double y = readDouble(c.inputs(), "y");
            final long expectedBits = MathTestSupport.parseHexBits(
                ((JSONObject) c.expectedRaw()).getString("y_bits"));
            final double actual = JQuantMath.pow(x, y);
            if (!MathTestSupport.bitsEqual(expectedBits, actual)) {
                mismatches.add(String.format(
                    "case=%s x=%s y=%s expected=0x%016x actual=0x%016x (=%s)",
                    name, x, y, expectedBits, Double.doubleToRawLongBits(actual), actual));
            }
        }
        if (validated == 0) {
            throw new AssertionError(
                "No cases validated; reference set may be missing. total="
                + ref.caseNames().size() + " skipped=" + skipped);
        }
        if (!mismatches.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append(mismatches.size()).append(" of ").append(validated)
              .append(" validated cases mismatched (")
              .append(skipped).append(" cases scoped out pending Stage 3 port). First 5:\n");
            for (int i = 0; i < Math.min(5, mismatches.size()); ++i) {
                sb.append("  ").append(mismatches.get(i)).append('\n');
            }
            throw new AssertionError(sb.toString());
        }
    }

    private static double readDouble(JSONObject inputs, String key) {
        final Object raw = inputs.get(key);
        if (raw instanceof String) {
            return Double.parseDouble((String) raw);
        }
        return ((Number) raw).doubleValue();
    }
}
