/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.swaption;

import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.model.shortrate.twofactormodels.G2;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.pricingengines.swaption.FdG2SwaptionEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Phase 2h WI-3 fingerprint test for {@link FdG2SwaptionEngine}.
 *
 * <p>Cross-validates {@code Swaption.NPV()} on a 5Y x 5Y ATM payer
 * swaption priced under a G2++ (a=0.1, sigma=0.01, b=0.1, eta=0.005,
 * rho=-0.5) on a 50x50x50 mesh (no damping steps) against the C++
 * v1.42.1 probe (see
 * {@code migration-harness/cpp/probes/pricingengines/swaption/fdg2swaptionengine_probe.cpp}).
 *
 * <p><strong>Tolerance tier — tight</strong> (abs {@code 1e-14} + rel
 * {@code 1e-12}). Justification: the Java port reproduces the C++
 * Hundsdorfer-Verwer 2D ADI rollback to bit-exact precision modulo IEEE-754
 * summation noise. The dominant arithmetic operations are linear-algebra
 * solves and elementary functions on the OU mesh; these match libc++
 * to &lt;= 1 ULP per QuantLib v1.42.1 fixture. Empirically the residual on
 * this fixture is around {@code 1e-14} absolute (rel ~5e-15), comfortably
 * inside the tight tier. The framework class
 * {@link org.jquantlib.math.interpolations.BicubicSplineInterpolation}
 * was historically broken for {@code Matrix.rangeRow()}-backed views —
 * see Phase 2h WI-3 align commit which copies row data into a dense
 * {@link org.jquantlib.math.matrixutilities.Array} before passing to
 * {@link org.jquantlib.math.interpolations.CubicInterpolation}. Without
 * that fix this test would fail with a ~7x factor divergence rather than
 * the current bit-level agreement.
 */
public class FdG2SwaptionEngineTest {

    @Test
    public void atmPayerSwaption_fdG2NpvMatchesCpp() {
        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/swaption/fdg2swaptionengine");
        final Case ref = reader.getCase("atm_payer_5y5y_fd_g2");
        final JSONObject in = ref.inputs();
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        // ---- Fixture (must mirror fdg2swaptionengine_probe.cpp) ----
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final double flatRate = in.getDouble("flat_rate");
        final double g2A = in.getDouble("g2_a");
        final double g2Sigma = in.getDouble("g2_sigma");
        final double g2B = in.getDouble("g2_b");
        final double g2Eta = in.getDouble("g2_eta");
        final double g2Rho = in.getDouble("g2_rho");
        final int tGrid = in.getInt("t_grid");
        final int xGrid = in.getInt("x_grid");
        final int yGrid = in.getInt("y_grid");
        final int dampingSteps = in.getInt("damping_steps");
        final double nominal = in.getDouble("nominal");
        final double dummyRate = in.getDouble("dummy_fixed_rate");

        final YieldTermStructure flat = new FlatForward(
                eval, new Handle<Quote>(new SimpleQuote(flatRate)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(flat);

        final Euribor3M idx = new Euribor3M(ts);

        final Date exerciseDate = cal.advance(eval,
                new Period(in.getInt("exercise_years"), TimeUnit.Years),
                BusinessDayConvention.Following);
        final Exercise exercise = new EuropeanExercise(exerciseDate);
        final Date startDate = cal.advance(exerciseDate, 2, TimeUnit.Days,
                BusinessDayConvention.Following, false);
        final Date maturity = cal.advance(startDate,
                new Period(in.getInt("swap_years"), TimeUnit.Years),
                BusinessDayConvention.Following);

        final DayCounter fixedDc = new Thirty360(Thirty360.Convention.European);

        final Schedule fixedSchedule = new Schedule(
                startDate, maturity, new Period(1, TimeUnit.Years), cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);
        final Schedule floatSchedule = new Schedule(
                startDate, maturity,
                new Period(in.getInt("float_tenor_months"), TimeUnit.Months),
                cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);

        // Step 1: dummy swap to read par rate.
        final VanillaSwap swap0 = new VanillaSwap(
                VanillaSwap.Type.Payer, nominal, fixedSchedule, dummyRate, fixedDc,
                floatSchedule, idx, 0.0, dc);
        swap0.setPricingEngine(new DiscountingSwapEngine(ts));
        final double atmRate = swap0.fairRate();

        // Step 2: ATM swap + swaption priced via G2 FD engine.
        final VanillaSwap swap = new VanillaSwap(
                VanillaSwap.Type.Payer, nominal, fixedSchedule, atmRate, fixedDc,
                floatSchedule, idx, 0.0, dc);
        final Swaption swaption = new Swaption(swap, exercise);

        final G2 g2 = new G2(ts, g2A, g2Sigma, g2B, g2Eta, g2Rho);
        swaption.setPricingEngine(new FdG2SwaptionEngine(
                g2, tGrid, xGrid, yGrid, dampingSteps));
        final double npv = swaption.NPV();

        // ---- Cross-validate ----
        final double expNpv = exp.getDouble("swaption_npv_fd_g2");
        if (!Tolerance.tight(npv, expNpv)) {
            fail("swaption.NPV() (G2 FD): exp=" + expNpv + " got=" + npv
                    + " absDiff=" + Math.abs(npv - expNpv));
        }
    }
}
