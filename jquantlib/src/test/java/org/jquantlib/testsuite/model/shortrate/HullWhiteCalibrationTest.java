/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for HullWhite arguments_-indirection. See phase2b-design §3.3 WI-3.
 */
package org.jquantlib.testsuite.model.shortrate;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.instruments.Option;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

public class HullWhiteCalibrationTest {

    @Test
    public void roundTripFingerprint_matchesCpp() {
        final ReferenceReader reader = ReferenceReader.load("model/shortrate/hullwhite_calibration");
        final Case c = reader.getCase("hullwhite_round_trip");
        final JSONObject in = c.inputs();

        final Date today = new Date(22, Month.April, 2026);
        new Settings().setEvaluationDate(today);
        final YieldTermStructure rCurve = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(in.getDouble("r_curve"))),
                new Actual365Fixed());

        final HullWhite model = new HullWhite(
                new Handle<YieldTermStructure>(rCurve),
                in.getDouble("a"), in.getDouble("sigma"));

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

    @Test
    public void convexityBiasFingerprint_matchesCpp() {
        final ReferenceReader reader = ReferenceReader.load("model/shortrate/hullwhite_calibration");
        final Case c = reader.getCase("hullwhite_convexity_bias");
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray samples = exp.getJSONArray("samples");
        for (int i = 0; i < samples.length(); i++) {
            final JSONObject s = samples.getJSONObject(i);
            final double fp = s.getDouble("futurePrice");
            final double t = s.getDouble("t");
            final double T = s.getDouble("T");
            final double sigma = s.getDouble("sigma");
            final double a = s.getDouble("a");
            final double expBias = s.getDouble("bias");
            final double gotBias = HullWhite.convexityBias(fp, t, T, sigma, a);
            if (!Tolerance.tight(gotBias, expBias)) {
                fail("convexityBias[" + i + "] (a=" + a + "): exp=" + expBias + " got=" + gotBias);
            }
        }
    }

    @Test
    public void fwdStartingBondOptionFingerprint_matchesCpp() {
        final ReferenceReader reader = ReferenceReader.load("model/shortrate/hullwhite_calibration");
        final Case c = reader.getCase("hullwhite_fwd_starting_bond_option");
        final JSONObject in = c.inputs();

        final Date today = new Date(22, Month.April, 2026);
        new Settings().setEvaluationDate(today);
        final YieldTermStructure rCurve = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(in.getDouble("r_curve"))),
                new Actual365Fixed());

        final HullWhite model = new HullWhite(
                new Handle<YieldTermStructure>(rCurve),
                in.getDouble("a"), in.getDouble("sigma"));

        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray samples = exp.getJSONArray("samples");
        for (int i = 0; i < samples.length(); i++) {
            final JSONObject s = samples.getJSONObject(i);
            final double strike = s.getDouble("strike");
            final double mat = s.getDouble("maturity");
            final double bStart = s.getDouble("bondStart");
            final double bMat = s.getDouble("bondMaturity");
            final double expCall = s.getDouble("call");
            final double expPut = s.getDouble("put");
            final double gotCall = model.discountBondOption(Option.Type.Call, strike, mat, bStart, bMat);
            final double gotPut = model.discountBondOption(Option.Type.Put, strike, mat, bStart, bMat);
            if (!Tolerance.tight(gotCall, expCall)) {
                fail("5-arg call[" + i + "]: exp=" + expCall + " got=" + gotCall);
            }
            if (!Tolerance.tight(gotPut, expPut)) {
                fail("5-arg put[" + i + "]: exp=" + expPut + " got=" + gotPut);
            }
        }
    }
}
