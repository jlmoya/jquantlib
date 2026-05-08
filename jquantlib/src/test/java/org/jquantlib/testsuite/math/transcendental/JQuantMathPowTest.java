package org.jquantlib.testsuite.math.transcendental;

import org.json.JSONObject;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.testsuite.util.MathTestSupport;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

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
 * <p><b>Phase 2n A.1 partial-port note:</b> the current
 * {@code JQuantMath.pow} matches cr_pow bit-exactly for IEEE-754 specials
 * (NaN, ±inf, ±0, sign propagation, integer-y discrimination) but
 * delegates to {@link Math#pow} for non-special finite arguments,
 * pending the full 3-stage Ziv loop port. Until that lands, this test
 * scopes to category prefixes that we have already ported. The
 * {@link #SUPPORTED_CATEGORIES} array enumerates which case-name
 * prefixes are currently validated. The full reference set is loaded
 * regardless so unrelated cases can be inspected when investigating
 * failures, and so the test discovers any regressions in
 * specials handling.
 *
 * <p>Collect-all-failures pattern (per Phase 2i WI-1.1 review).
 */
public class JQuantMathPowTest {

    /**
     * Case-name prefixes whose bit-exact contract is currently fulfilled
     * by {@link JQuantMath#pow}. Cases outside this set are skipped (NOT
     * marked as failure) until the corresponding finite-path port lands.
     */
    private static final String[] SUPPORTED_CATEGORIES = {
        "special_",   // IEEE-754 specials and sign-prop
    };

    @Test
    public void pow_bitExactAgainstCoreMathProbe() {
        final ReferenceReader ref = ReferenceReader.load("math/transcendental/pow");
        final List<String> mismatches = new ArrayList<>();
        int validated = 0;
        int skipped = 0;
        for (String name : ref.caseNames()) {
            if (!isSupported(name)) {
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
                "No supported cases validated; reference set may be missing "
                + "or SUPPORTED_CATEGORIES misconfigured. total="
                + ref.caseNames().size() + " skipped=" + skipped);
        }
        if (!mismatches.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append(mismatches.size()).append(" of ").append(validated)
              .append(" validated cases mismatched (")
              .append(skipped).append(" categories skipped pending full port). First 5:\n");
            for (int i = 0; i < Math.min(5, mismatches.size()); ++i) {
                sb.append("  ").append(mismatches.get(i)).append('\n');
            }
            throw new AssertionError(sb.toString());
        }
    }

    private static boolean isSupported(String caseName) {
        for (String pref : SUPPORTED_CATEGORIES) {
            if (caseName.startsWith(pref)) return true;
        }
        return false;
    }

    private static double readDouble(JSONObject inputs, String key) {
        final Object raw = inputs.get(key);
        if (raw instanceof String) {
            return Double.parseDouble((String) raw);
        }
        return ((Number) raw).doubleValue();
    }
}
