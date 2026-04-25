/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for CoxIngersollRoss arguments_-indirection (Phase 2b WI-3) and
 the discountBondOption stub-fill (Phase 2c WI-1). See phase2b-design
 §3.3 WI-3 and phase2c-design §3.1.
 */
package org.jquantlib.testsuite.model.shortrate;

import org.jquantlib.instruments.Option;
import org.jquantlib.model.shortrate.onefactormodels.CoxIngersollRoss;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Cross-validated tests for CoxIngersollRoss against v1.42.1.
 *
 * <p>Verifies (a) the Phase 2b WI-3 Parameter-→arguments_ indirection
 * routes theta()/k()/sigma()/x0() reads through arguments_.get(i)
 * (via discountBond), and (b) the Phase 2c WI-1 stub-fill of
 * discountBondOption produces the C++ values for both Call and Put,
 * which exercises the new NonCentralCumulativeChiSquaredDistribution.
 */
public class CoxIngersollRossCalibrationTest {

    @Test
    public void discountBondFingerprint_matchesCpp() {
        final ReferenceReader reader = ReferenceReader.load("model/shortrate/coxingersollross_calibration");
        final Case c = reader.getCase("cir_discountbond_fingerprint");
        final JSONObject in = c.inputs();

        final double r0 = in.getDouble("r0");
        // CIR Java ctor order: (r0, theta, k, sigma).
        final CoxIngersollRoss model = new CoxIngersollRoss(
                r0, in.getDouble("theta"), in.getDouble("k"), in.getDouble("sigma"));
        final double strike = in.getDouble("strike");

        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray samples = exp.getJSONArray("samples");
        for (int i = 0; i < samples.length(); i++) {
            final JSONObject s = samples.getJSONObject(i);
            final double t = s.getDouble("t");
            final double T = s.getDouble("T");
            final double expDiscount = s.getDouble("discountBond");
            final double gotDiscount = model.discountBond(t, T, r0);
            if (!Tolerance.tight(gotDiscount, expDiscount)) {
                fail("discountBond[" + i + "] (t=" + t + ", T=" + T + "): exp="
                        + expDiscount + " got=" + gotDiscount);
            }

            // discountBondOption chains the chi-squared CDF through a
            // dozen arithmetic operations on (rho, psi, b, z). The CDF
            // itself matches C++ at tight tier (see
            // NonCentralCumulativeChiSquaredDistributionTest), but the
            // chained intermediates accumulate ~3.5e-14 of IEEE 754
            // rounding drift between Java and C++. That is below the
            // tight-tier hybrid bound `1e-14 + 1e-12 * |cpp|` for very
            // small option values (e.g. 0.023 → bound ≈ 3.3e-14), so
            // we use a fixed 1e-13 absolute floor — still two orders
            // tighter than the loose tier and well below the ~1.5e-12
            // algorithmic drift the original Phase 2c WI-1 motivation
            // was eliminating.
            final double optionTol = 1.0e-13;
            final double expCall = s.getDouble("discountBondOptionCall");
            final double gotCall = model.discountBondOption(Option.Type.Call, strike, t, T);
            if (Math.abs(gotCall - expCall) > optionTol) {
                fail("discountBondOptionCall[" + i + "] (t=" + t + ", T=" + T
                        + "): exp=" + expCall + " got=" + gotCall
                        + " diff=" + Math.abs(gotCall - expCall));
            }

            final double expPut = s.getDouble("discountBondOptionPut");
            final double gotPut = model.discountBondOption(Option.Type.Put, strike, t, T);
            if (Math.abs(gotPut - expPut) > optionTol) {
                fail("discountBondOptionPut[" + i + "] (t=" + t + ", T=" + T
                        + "): exp=" + expPut + " got=" + gotPut
                        + " diff=" + Math.abs(gotPut - expPut));
            }
        }
    }
}
