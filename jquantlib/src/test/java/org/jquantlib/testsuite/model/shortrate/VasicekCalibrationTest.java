/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for Vasicek arguments_-indirection. See phase2b-design §3.3 WI-3.
 */
package org.jquantlib.testsuite.model.shortrate;

import org.jquantlib.instruments.Option;
import org.jquantlib.model.shortrate.onefactormodels.Vasicek;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

public class VasicekCalibrationTest {

    @Test
    public void roundTripFingerprint_matchesCpp() {
        final ReferenceReader reader = ReferenceReader.load("model/shortrate/vasicek_calibration");
        final Case c = reader.getCase("vasicek_round_trip");
        final JSONObject in = c.inputs();
        final Vasicek model = new Vasicek(
                in.getDouble("r0"), in.getDouble("a"), in.getDouble("b"),
                in.getDouble("sigma"), in.getDouble("lambda"));

        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray samples = exp.getJSONArray("samples");
        for (int i = 0; i < samples.length(); i++) {
            final JSONObject s = samples.getJSONObject(i);
            final double strike = s.getDouble("strike");
            final double mat = s.getDouble("maturity");
            final double bMat = s.getDouble("bondMaturity");
            final double expCall = s.getDouble("call");
            final double expPut = s.getDouble("put");
            final double gotCall = model.discountBondOption(Option.Type.Call, strike, mat, bMat);
            final double gotPut = model.discountBondOption(Option.Type.Put, strike, mat, bMat);
            if (!Tolerance.tight(gotCall, expCall)) {
                fail("call[" + i + "]: exp=" + expCall + " got=" + gotCall);
            }
            if (!Tolerance.tight(gotPut, expPut)) {
                fail("put[" + i + "]: exp=" + expPut + " got=" + gotPut);
            }
        }
    }
}
