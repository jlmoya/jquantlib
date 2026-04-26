/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.model.shortrate.calibrationhelpers;

import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.math.Constants;
import org.jquantlib.model.BlackCalibrationHelper.CalibrationErrorType;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.model.shortrate.calibrationhelpers.SwaptionHelper;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.pricingengines.swaption.TreeSwaptionEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Phase 2e WI-3 fingerprint test for {@link SwaptionHelper}.
 *
 * <p>Cross-validates the helper's {@code marketValue()} / {@code blackPrice(vol)}
 * / {@code modelValue()} on a 5Y x 5Y ATM swaption built from a Period
 * (5Y maturity, 5Y length, vol=20%, ATM strike from the curve) under a
 * HullWhite (a=0.1, sigma=0.01) tree (100 steps). C++ probe at
 * {@code migration-harness/cpp/probes/model/shortrate/calibrationhelpers/swaptionhelper_probe.cpp}.
 *
 * <p><strong>Tolerance tier — TIGHT</strong> for {@code marketValue} and
 * {@code blackPrice(vol)} (closed-form Black76); <strong>LOOSE</strong> for
 * {@code modelValue} (HW tree pricing — Brent-solver-noise floor; same
 * precedent as {@link org.jquantlib.testsuite.pricingengines.swaption.TreeSwaptionEngineTest}).
 */
public class SwaptionHelperTest {

    private static final double LOOSE_TOL = 1.0e-8;

    @Test
    public void atm5y5yHwTree_helperMatchesCpp() {
        final ReferenceReader reader = ReferenceReader.load(
                "model/shortrate/calibrationhelpers/swaptionhelper");
        final Case ref = reader.getCase("atm_5y5y_hw_tree");
        final JSONObject in = ref.inputs();
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final DayCounter dc = new Actual365Fixed();
        final double flatRate = in.getDouble("flat_rate");
        final double vol = in.getDouble("vol");
        final double hwA = in.getDouble("hw_a");
        final double hwSigma = in.getDouble("hw_sigma");
        final int timeSteps = in.getInt("time_steps");
        final double nominal = in.getDouble("nominal");

        final YieldTermStructure flat = new FlatForward(
                eval, new Handle<Quote>(new SimpleQuote(flatRate)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(flat);

        final Euribor3M idx = new Euribor3M(ts);
        final Handle<Quote> volHandle = new Handle<Quote>(new SimpleQuote(vol));

        final DayCounter fixedDc = new Thirty360(Thirty360.Convention.European);
        final Period maturity = new Period(in.getInt("maturity_years"), TimeUnit.Years);
        final Period length = new Period(in.getInt("length_years"), TimeUnit.Years);
        final Period fixedLegTenor = new Period(1, TimeUnit.Years);

        final SwaptionHelper helper = new SwaptionHelper(maturity, length,
                volHandle, idx, fixedLegTenor, fixedDc, dc, ts,
                CalibrationErrorType.RelativePriceError,
                Constants.NULL_REAL, nominal,
                VolatilityType.ShiftedLognormal, 0.0);
        final HullWhite hw = new HullWhite(ts, hwA, hwSigma);
        helper.setPricingEngine(new TreeSwaptionEngine(hw, timeSteps, ts));

        final double marketValue = helper.marketValue();
        final double blackPriceAtVol = helper.blackPrice(vol);
        final double modelValue = helper.modelValue();

        final double expMarketValue = exp.getDouble("market_value");
        final double expBlackPriceAtVol = exp.getDouble("black_price_at_vol");
        final double expModelValue = exp.getDouble("model_value");

        // Tight tier for marketValue / blackPrice — closed-form Black76.
        if (!Tolerance.tight(marketValue, expMarketValue)) {
            fail("marketValue: exp=" + expMarketValue + " got=" + marketValue);
        }
        if (!Tolerance.tight(blackPriceAtVol, expBlackPriceAtVol)) {
            fail("blackPrice(vol): exp=" + expBlackPriceAtVol
                    + " got=" + blackPriceAtVol);
        }
        // Loose tier for modelValue — HW tree.
        if (Math.abs(modelValue - expModelValue) > LOOSE_TOL
                && Math.abs((modelValue - expModelValue) / expModelValue) > LOOSE_TOL) {
            fail("modelValue (HW tree): exp=" + expModelValue + " got=" + modelValue);
        }
    }
}
