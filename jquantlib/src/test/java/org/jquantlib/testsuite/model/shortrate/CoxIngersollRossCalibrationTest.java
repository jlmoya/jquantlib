/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for CoxIngersollRoss arguments_-indirection. See phase2b-design §3.3 WI-3.
 */
package org.jquantlib.testsuite.model.shortrate;

import org.jquantlib.model.shortrate.onefactormodels.CoxIngersollRoss;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Cross-validated tests for CoxIngersollRoss's discountBond fingerprint
 * against v1.42.1 — verifies that the Phase 2b WI-3 Parameter-→arguments_
 * indirection routes theta()/k()/sigma()/x0() reads through arguments_.get(i).
 *
 * Uses discountBond rather than discountBondOption because the latter
 * depends on Java's NonCentralChiSquaredDistribution, which diverges
 * from C++ v1.42.1 by ~1.5e-12 (a separate latent bug, deferred to
 * Phase 2c).
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
        }
    }
}
