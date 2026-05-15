/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.MakeMultipleResetsSwap;
import org.jquantlib.instruments.MultipleResetsSwap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d skeleton port of {@code test-suite/multipleresetsswap.cpp}
 * v1.42.1 (159 LOC, 4 cases).
 *
 * <p>Exercises a swap whose floating leg is built from multiple-resets
 * coupons (compounded or averaged sub-period IBOR fixings) — tests fair
 * rate computation, consistency with a hand-built leg, averaging vs
 * compounding parity, and a rate-helper variant for curve bootstrapping.
 *
 * <p><strong>Phase Body-Fill (2026-05-14)</strong>: 3 of the 4 cases are
 * now bodied — {@link #testFairRate()}, {@link #testConsistencyWithLeg()},
 * {@link #testAveragingVsCompounding()} — exercising
 * {@link MakeMultipleResetsSwap} (Phase 5d.5-MR port) end-to-end against
 * a flat 5% Euribor3M curve.
 *
 * <p>{@code testRateHelper} remains deferred (Phase 5d.5-MR-RH carry):
 * Java does not yet have a {@code MultipleResetsSwapRateHelper} or the
 * {@code PiecewiseYieldCurve<Discount, LogLinear>} / multi-helper
 * bootstrap pipeline the C++ test exercises.
 *
 * <p>Source: {@code test-suite/multipleresetsswap.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class MultipleResetsSwapTest {

    private static final String REASON_RH =
            "Phase 5d.5-MR-RH carry: requires MultipleResetsSwapRateHelper "
          + "(no Java equivalent yet) and PiecewiseYieldCurve<Discount, "
          + "LogLinear> bootstrap pipeline.";

    /**
     * Mirrors C++ {@code multipleresetsswap.cpp::CommonVars}.
     */
    private static final class CommonVars {
        final Date today;
        final Calendar calendar;
        final DayCounter dayCount;
        final RelinkableHandle<YieldTermStructure> termStructure;
        final IborIndex euribor3m;

        CommonVars() {
            calendar = new Target();
            today = calendar.adjust(new Date(15, Month.January, 2024));
            new Settings().setEvaluationDate(today);
            dayCount = new Actual365Fixed();
            termStructure = new RelinkableHandle<YieldTermStructure>();
            termStructure.linkTo(Utilities.flatRate(today, 0.05, dayCount));
            euribor3m = new Euribor3M(termStructure);
            euribor3m.addFixing(new Date(11, Month.January, 2024), 0.05);
        }

        MultipleResetsSwap makeSwap(final double fixedRate,
                                    final RateAveraging.Type method) {
            return new MakeMultipleResetsSwap(
                    new Period(2, TimeUnit.Years), euribor3m, 2)
                    .withFixedRate(fixedRate)
                    .withSettlementDays(0)
                    .withNominal(1.0e6)
                    .withAveragingMethod(method)
                    .value();
        }

        MultipleResetsSwap makeSwap(final double fixedRate) {
            return makeSwap(fixedRate, RateAveraging.Type.Compound);
        }
    }

    /**
     * Port of C++ {@code multipleresetsswap.cpp::testFairRate}.
     */
    @Test
    public void testFairRate() {
        QL.info("Testing fair rate of multiple-resets swap...");

        final CommonVars vars = new CommonVars();

        final MultipleResetsSwap swap = vars.makeSwap(0.06);

        final double fair = swap.fairRate();
        // BOOST_REQUIRE(fair != Null<Rate>())
        if (Double.isNaN(fair)) {
            fail("fairRate() returned NaN");
        }

        // Rebuilding at the fair rate must give zero NPV.
        final MultipleResetsSwap fairSwap = vars.makeSwap(fair);
        if (Math.abs(fairSwap.NPV()) > 1.0e-8) {
            fail("rebuilt-at-fair-rate swap NPV not zero: " + fairSwap.NPV());
        }

        // Cross-check: fixed-leg NPV + floating-leg NPV equals total NPV.
        final double npvCheck = swap.fixedLegNPV() + swap.floatingLegNPV();
        if (Math.abs(npvCheck - swap.NPV()) > 1.0e-10) {
            fail("fixedLegNPV + floatingLegNPV != NPV:"
                    + " sum=" + npvCheck + " NPV=" + swap.NPV()
                    + " diff=" + (npvCheck - swap.NPV()));
        }

        // Omitting withFixedRate triggers auto-computation; NPV must be zero.
        final MultipleResetsSwap autoFair = new MakeMultipleResetsSwap(
                new Period(2, TimeUnit.Years), vars.euribor3m, 2)
                .withSettlementDays(0)
                .withNominal(1.0e6)
                .value();
        if (Math.abs(autoFair.NPV()) > 1.0e-8) {
            fail("auto-fair (no withFixedRate) NPV not zero: " + autoFair.NPV());
        }
    }

    /**
     * Port of C++ {@code multipleresetsswap.cpp::testConsistencyWithLeg}.
     */
    @Test
    public void testConsistencyWithLeg() {
        QL.info("Testing that multiple-resets swap NPV is consistent with legs NPV...");

        final CommonVars vars = new CommonVars();

        for (final VanillaSwap.Type type :
                new VanillaSwap.Type[] { VanillaSwap.Type.Payer, VanillaSwap.Type.Receiver }) {
            final MultipleResetsSwap swap = new MakeMultipleResetsSwap(
                    new Period(2, TimeUnit.Years), vars.euribor3m, 2)
                    .withFixedRate(0.05)
                    .withSettlementDays(0)
                    .withNominal(1.0e6)
                    .withType(type)
                    .value();

            final double legSum = swap.fixedLegNPV() + swap.floatingLegNPV();
            if (Math.abs(legSum - swap.NPV()) > 1.0e-10) {
                fail("type=" + type + ": fixedLegNPV + floatingLegNPV != NPV:"
                        + " sum=" + legSum + " NPV=" + swap.NPV()
                        + " diff=" + (legSum - swap.NPV()));
            }
        }
    }

    /**
     * Port of C++ {@code multipleresetsswap.cpp::testAveragingVsCompounding}.
     *
     * <p>Verifies the compounded-fixings fair rate differs (non-trivially)
     * from the simple-averaged fair rate — the two conventions should not
     * agree for the same instrument.
     */
    @Test
    public void testAveragingVsCompounding() {
        QL.info("Testing averaging vs compounding in multiple-resets swaps...");

        final CommonVars vars = new CommonVars();

        final double fixedRate = 0.05;
        final MultipleResetsSwap swapCompound =
                vars.makeSwap(fixedRate, RateAveraging.Type.Compound);
        final MultipleResetsSwap swapAverage =
                vars.makeSwap(fixedRate, RateAveraging.Type.Simple);

        if (Math.abs(swapCompound.fairRate() - swapAverage.fairRate()) <= 1.0e-10) {
            fail("compounded fair rate equals averaged fair rate "
                    + "(should differ): compound=" + swapCompound.fairRate()
                    + " average=" + swapAverage.fairRate());
        }
    }

    @Ignore(REASON_RH)
    @Test
    public void testRateHelper() { fail("not implemented"); }
}
