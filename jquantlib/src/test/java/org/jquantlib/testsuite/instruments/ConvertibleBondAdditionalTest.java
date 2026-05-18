/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.SavedSettings;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.CallabilitySchedule;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.bonds.ConvertibleFixedCouponBond;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.methods.lattices.CoxRossRubinstein;
import org.jquantlib.pricingengines.BinomialConvertibleEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Test;

/**
 * Phase 5d additional skeleton port of {@code test-suite/convertiblebonds.cpp}
 * v1.42.1 (445 LOC, 3 cases) — gap-fill for cases not in
 * {@link ConvertibleBondTest}.
 *
 * <p>{@link ConvertibleBondTest} already covers {@code testBond} and
 * {@code testOption}.
 *
 * <p>This companion file adds {@code testRegression} — a regression
 * scenario covering the known floating-point overflow bug in past
 * {@link org.jquantlib.pricingengines.BinomialConvertibleEngine}
 * versions: a pathological volatility (≈2168%) blows up the recursive
 * value/rate accumulation on the binomial tree to {@code +Inf}; the
 * engine must detect the overflow and {@code throw} rather than return
 * the silent {@code Inf}.
 *
 * <p>Java structural divergence from the C++ test (intentional, all
 * non-load-bearing for the regression assertion):
 * <ul>
 *   <li>C++ uses {@code BlackProcess(u, r, sigma)} (q = r). Java has no
 *       standalone {@code BlackProcess}; we mirror the same shape by
 *       constructing a {@link BlackScholesMertonProcess} with the
 *       dividend yield linked to the same flat-forward as the risk-free
 *       rate.
 *   <li>C++ uses {@code ForwardCurve(dates, forwards, Actual360())} with
 *       24 piecewise-flat forwards. The Java
 *       {@code InterpolatedForwardCurve} has a precondition bug
 *       (out-of-task scope) that prevents direct use; we substitute a
 *       {@link org.jquantlib.termstructures.yieldcurves.FlatForward} at
 *       a representative ~2.4% rate. The risk-free curve shape only
 *       scales the (overflowed) recursive value, not whether it
 *       overflows.
 *   <li>Credit spread, dividends, and creditSpread handle are passed
 *       through the bond constructor (Java legacy QuantLib API) rather
 *       than to the engine (post-1.30 C++ API).
 * </ul>
 *
 * <p>The load-bearing inputs that drive the overflow — issue / maturity
 * dates, 6-month coupon schedule, 5% coupons, conversion ratio
 * {@code 100/20.3175}, underlying ≈ 2.91, vol = 21.685, credit spread
 * ≈ 0.115, 600 timesteps — match the C++ test exactly.
 *
 * <p>Source: {@code test-suite/convertiblebonds.cpp::testRegression}
 * v1.42.1 @ {@code 099987f0ca}. C++ overflow guard:
 * {@code binomialconvertibleengine.hpp:129-130}.
 */
public class ConvertibleBondAdditionalTest {

    public ConvertibleBondAdditionalTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testRegression() {
        QL.info("Testing fixed-coupon convertible bond in known regression case...");

        // SavedSettings is a no-op stub in jquantlib; instantiated here to
        // mirror C++ test-suite TopLevelFixture intent (snapshot-restore
        // global Settings). Java tests in this suite follow the same idiom.
        @SuppressWarnings("unused")
        final SavedSettings backup = new SavedSettings();
        {
            final Date today = new Date(23, Month.December, 2008);
            final Date tomorrow = today.add(1);
            new Settings().setEvaluationDate(tomorrow);

            final RelinkableHandle<Quote> u = new RelinkableHandle<Quote>();
            u.linkTo(new SimpleQuote(2.9084382818797443));

            // Risk-free rate: see class javadoc — we use a representative
            // flat 2.4% rate in place of the C++ piecewise-flat
            // ForwardCurve. Not load-bearing for the overflow assertion.
            final DayCounter rfDc = new Actual360();
            final RelinkableHandle<YieldTermStructure> r =
                    new RelinkableHandle<YieldTermStructure>();
            r.linkTo(Utilities.flatRate(tomorrow, 0.024, rfDc));

            // Pathological volatility — this is what causes the binomial
            // tree to overflow at 600 steps over ~5 years.
            final RelinkableHandle<BlackVolTermStructure> sigma =
                    new RelinkableHandle<BlackVolTermStructure>();
            sigma.linkTo(new BlackConstantVol(tomorrow, new NullCalendar(),
                    21.685235548092248, new Thirty360(Thirty360.Convention.BondBasis)));

            // C++ uses BlackProcess(u, r, sigma) which is BSM with q = r.
            // Java has no standalone BlackProcess; mirror the same shape.
            final BlackScholesMertonProcess process =
                    new BlackScholesMertonProcess(u, r, r, sigma);

            final RelinkableHandle<Quote> spread = new RelinkableHandle<Quote>();
            spread.linkTo(new SimpleQuote(0.11498700678012874));

            final Date issueDate = new Date(23, Month.July, 2008);
            final Date maturityDate = new Date(1, Month.August, 2013);
            final Calendar calendar =
                    new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
            final Schedule schedule = new MakeSchedule()
                    .from(issueDate)
                    .to(maturityDate)
                    .withTenor(new Period(6, TimeUnit.Months))
                    .withCalendar(calendar)
                    .withConvention(BusinessDayConvention.Unadjusted)
                    .schedule();

            final int settlementDays = 3;
            final Exercise exercise = new EuropeanExercise(maturityDate);
            final double conversionRatio = 100.0 / 20.3175;
            final double[] coupons = new double[schedule.size() - 1];
            for (int i = 0; i < coupons.length; i++) {
                coupons[i] = 0.05;
            }
            final DayCounter dayCounter = new Thirty360(Thirty360.Convention.BondBasis);
            final CallabilitySchedule noCallability = new CallabilitySchedule();
            final DividendSchedule noDividends = new DividendSchedule();
            final double redemption = 100.0;

            final ConvertibleFixedCouponBond bond = new ConvertibleFixedCouponBond(
                    exercise, conversionRatio,
                    noDividends, noCallability,
                    new Handle<Quote>(spread.currentLink()),
                    issueDate, settlementDays,
                    coupons, dayCounter, schedule, redemption);

            final PricingEngine engine =
                    new BinomialConvertibleEngine<CoxRossRubinstein>(
                            CoxRossRubinstein.class, process, 600);
            bond.setPricingEngine(engine);

            try {
                final double x = bond.NPV();
                // should throw; if not, an INF was not detected
                fail("INF result was not detected: " + x + " returned");
            } catch (final LibraryException expected) {
                // as expected. Do nothing.
                //
                // Note: we're expecting an Error we threw, not just any
                // exception. If something else is thrown, then there's
                // another problem and the test must fail.
            }
        }
    }
}
