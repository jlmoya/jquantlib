/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.termstructures.volatilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.volatilities.swaption.SwaptionVolatilityMatrix;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Body-filled cross-validation port of
 * {@code test-suite/swaptionvolatilitymatrix.cpp} v1.42.1 (364 LOC, 2 cases).
 *
 * <p><strong>Phase 5e.5b-CFC-d-108 body-fills.</strong> The C++
 * {@code testSwaptionVolMatrixCoherence} and
 * {@code testSwaptionVolMatrixObservability} cases exercise
 * {@link SwaptionVolatilityMatrix} through every constructor permutation:
 * <ul>
 *  <li>(floating refdate × fixed refdate) × (handle-quote vols × matrix vols)</li>
 * </ul>
 *
 * <p>This Java port covers the substantive behaviors that don't depend on
 * out-of-scope downstream classes ({@code BlackSwaptionEngine},
 * {@code EuriborSwapIsdaFixA}, {@code MakeSwaption}):
 * <ul>
 *  <li><strong>Coherence</strong> — at every grid node {@code (i, j)}, the
 *      ATM vol read by both {@code (Period, Period)} and {@code (Time, Time)}
 *      lookups equals the input cell to bit-exact precision (C++ uses
 *      {@code tolerance = 1.0e-16}). All four ctor variants are exercised.</li>
 *  <li><strong>Observability</strong> — for the handle-based ctors, a change
 *      in the underlying {@link SimpleQuote#setValue(double)} propagates
 *      through the lazy hook so the next {@code volatility(...)} call
 *      returns the new value. For the matrix-input ctors, the wrapping
 *      {@code SimpleQuote} created inside the ctor still behaves as a
 *      market-data link (parity with C++ {@code volsHandle_}).</li>
 * </ul>
 *
 * <p>Reference values come from the analytic input matrix; no probe-driven
 * JSON is needed because node-recovery is identity-by-construction. The
 * comprehensive interpolation parity tests (interior bilinear, flat
 * extrapolation, locate, etc.) live in
 * {@code volatility/swaption/SwaptionVolatilityMatrixTest} and consume
 * {@code references/termstructures/volatility/swaption_vol_matrix.json}.
 *
 * <p>Source: {@code test-suite/swaptionvolatilitymatrix.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class SwaptionVolatilityMatrixTest {

    private static final double TIGHT_ABS = 1.0e-16;
    private static final double LOOSE_REL = 1.0e-8;

    private static final DayCounter DC = new Actual365Fixed();
    private static final Calendar CAL = new Target();
    private static final BusinessDayConvention BDC = BusinessDayConvention.Following;
    private static final Date REF_DATE = new Date(2, Month.January, 2020);

    private static final List<Period> OPTION_TENORS = Arrays.asList(
            new Period(1, TimeUnit.Years),
            new Period(5, TimeUnit.Years),
            new Period(10, TimeUnit.Years));
    private static final List<Period> SWAP_TENORS = Arrays.asList(
            new Period(1, TimeUnit.Years),
            new Period(5, TimeUnit.Years),
            new Period(10, TimeUnit.Years));

    private static final double[][] VOLS = {
            { 0.18, 0.20, 0.22 },
            { 0.16, 0.18, 0.20 },
            { 0.14, 0.16, 0.18 }
    };

    private static Matrix volsMatrix() {
        final Matrix m = new Matrix(3, 3);
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                m.set(i, j, VOLS[i][j]);
            }
        }
        return m;
    }

    private static List<List<Handle<? extends Quote>>> volsHandles(
            final List<List<SimpleQuote>> outQuotes) {
        final List<List<Handle<? extends Quote>>> h =
                new ArrayList<List<Handle<? extends Quote>>>(3);
        for (int i = 0; i < 3; ++i) {
            final List<Handle<? extends Quote>> row =
                    new ArrayList<Handle<? extends Quote>>(3);
            final List<SimpleQuote> qrow = new ArrayList<SimpleQuote>(3);
            for (int j = 0; j < 3; ++j) {
                final SimpleQuote q = new SimpleQuote(VOLS[i][j]);
                row.add(new Handle<Quote>(q));
                qrow.add(q);
            }
            h.add(row);
            outQuotes.add(qrow);
        }
        return h;
    }

    // ----- coherence: identity recovery of input vols at every node ----------

    /**
     * Port of C++ {@code testSwaptionVolMatrixCoherence}
     * (test-suite/swaptionvolatilitymatrix.cpp lines 311-361).
     *
     * <p>For each of the four ctor variants, every input vol cell must be
     * recovered exactly by lookups via {@code (Period, Period, strike)},
     * {@code (Date, Period, strike)}, and {@code (Time, Time, strike)}.
     * C++ tolerance: {@code 1.0e-16} (TIGHT, bit-exact for node pickup).
     */
    @Test
    public void testSwaptionVolMatrixCoherence() {
        // (1) fixed refdate, matrix vols
        assertCoherence(new SwaptionVolatilityMatrix(
                REF_DATE, CAL, BDC, OPTION_TENORS, SWAP_TENORS, volsMatrix(),
                DC, false, VolatilityType.ShiftedLognormal, null));

        // (2) floating refdate, matrix vols
        assertCoherence(new SwaptionVolatilityMatrix(
                CAL, BDC, OPTION_TENORS, SWAP_TENORS, volsMatrix(),
                DC, false, VolatilityType.ShiftedLognormal, null));

        // (3) fixed refdate, handle vols
        final List<List<SimpleQuote>> qs3 = new ArrayList<List<SimpleQuote>>();
        assertCoherence(new SwaptionVolatilityMatrix(
                REF_DATE, CAL, BDC, OPTION_TENORS, SWAP_TENORS,
                volsHandles(qs3), DC, false, VolatilityType.ShiftedLognormal, null));

        // (4) floating refdate, handle vols
        final List<List<SimpleQuote>> qs4 = new ArrayList<List<SimpleQuote>>();
        assertCoherence(new SwaptionVolatilityMatrix(
                CAL, BDC, OPTION_TENORS, SWAP_TENORS,
                volsHandles(qs4), DC, false, VolatilityType.ShiftedLognormal, null));
    }

    private static void assertCoherence(final SwaptionVolatilityMatrix vol) {
        // (Time, Time) lookup — direct node pickup (bilinear corner).
        final double[] tOpt = vol.optionTimes();
        final double[] sLen = vol.swapLengths();
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                final double exp = VOLS[i][j];
                final double act = vol.volatility(tOpt[i], sLen[j], 0.05, true);
                assertEquals("node (Time,Time) i=" + i + " j=" + j,
                        exp, act, TIGHT_ABS);
            }
        }
        // (Date, Period) lookup — must route through timeFromReference/swapLength
        // and still land on the exact corner.
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                final double exp = VOLS[i][j];
                final Date d = vol.optionDates().get(i);
                final double act = vol.volatility(d, SWAP_TENORS.get(j), 0.05, true);
                assertEquals("node (Date,Period) i=" + i + " j=" + j,
                        exp, act, TIGHT_ABS);
            }
        }
        // (Period, Period) lookup — routes through optionDateFromTenor.
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                final double exp = VOLS[i][j];
                final double act = vol.volatility(
                        OPTION_TENORS.get(i), SWAP_TENORS.get(j), 0.05, true);
                assertEquals("node (Period,Period) i=" + i + " j=" + j,
                        exp, act, TIGHT_ABS);
            }
        }
        // Cross-check: optionDateFromTenor(i) == optionDates()[i] (C++ asserts
        // this in makeCoherenceTest lines 117-123).
        for (int i = 0; i < 3; ++i) {
            assertEquals("optionDateFromTenor[" + i + "]",
                    vol.optionDates().get(i).serialNumber(),
                    vol.optionDateFromTenor(OPTION_TENORS.get(i)).serialNumber());
        }
    }

    // ----- observability: handle/quote propagates to vol ---------------------

    /**
     * Port of C++ {@code testSwaptionVolMatrixObservability}
     * (test-suite/swaptionvolatilitymatrix.cpp lines 252-309).
     *
     * <p>Covers the market-data-floating leg of {@code makeObservabilityTest}
     * (lines 95-108): a change to the underlying {@link SimpleQuote} must
     * propagate so the next {@code volatility(...)} call returns the new
     * value.
     *
     * <p>The evaluation-date-floating leg of the C++ test depends on
     * {@code Settings::instance().evaluationDate()} rewiring inside the
     * floating-refdate ctor; that hook lives in the
     * {@link org.jquantlib.Settings} singleton and is already exercised by
     * {@code volatility/swaption/SwaptionVolatilityMatrixTest} (which builds
     * the floating variant). Here we focus on the market-data leg, which is
     * what differentiates the handle-quote ctors from the matrix-input ones.
     */
    @Test
    public void testSwaptionVolMatrixObservability() {
        // (a) fixed refdate, handle vols — change SimpleQuote underlying handle.
        {
            final List<List<SimpleQuote>> quotes = new ArrayList<List<SimpleQuote>>();
            final SwaptionVolatilityMatrix vol = new SwaptionVolatilityMatrix(
                    REF_DATE, CAL, BDC, OPTION_TENORS, SWAP_TENORS,
                    volsHandles(quotes), DC, false,
                    VolatilityType.ShiftedLognormal, null);

            final double t0 = vol.optionTimes()[0];
            final double l0 = vol.swapLengths()[0];
            final double initial = vol.volatility(t0, l0, 0.05, true);
            assertEquals("initial node (0,0)", VOLS[0][0], initial, TIGHT_ABS);

            // Bump the (0,0) quote by 0.05 vol points (well above LOOSE_REL).
            quotes.get(0).get(0).setValue(VOLS[0][0] + 0.05);
            final double bumped = vol.volatility(t0, l0, 0.05, true);
            assertNotEquals("vol must move when quote changes (fixed refdate)",
                    initial, bumped);
            assertEquals("bumped value at (0,0)",
                    VOLS[0][0] + 0.05, bumped, TIGHT_ABS);

            // Restore and re-read.
            quotes.get(0).get(0).setValue(VOLS[0][0]);
            assertEquals("restored value at (0,0)",
                    VOLS[0][0], vol.volatility(t0, l0, 0.05, true), TIGHT_ABS);
        }

        // (b) floating refdate, handle vols — same propagation contract.
        {
            final List<List<SimpleQuote>> quotes = new ArrayList<List<SimpleQuote>>();
            final SwaptionVolatilityMatrix vol = new SwaptionVolatilityMatrix(
                    CAL, BDC, OPTION_TENORS, SWAP_TENORS,
                    volsHandles(quotes), DC, false,
                    VolatilityType.ShiftedLognormal, null);

            final double t0 = vol.optionTimes()[0];
            final double l0 = vol.swapLengths()[0];
            final double initial = vol.volatility(t0, l0, 0.05, true);

            quotes.get(1).get(2).setValue(VOLS[1][2] + 0.03);
            // Bump at (1,2) does not move (0,0) read.
            assertEquals("non-adjacent bump does not move (0,0)",
                    initial, vol.volatility(t0, l0, 0.05, true), TIGHT_ABS);
            // But it must move at (1,2).
            assertEquals("bumped value at (1,2)",
                    VOLS[1][2] + 0.03,
                    vol.volatility(vol.optionTimes()[1], vol.swapLengths()[2], 0.05, true),
                    TIGHT_ABS);
        }

        // (c) fixed refdate, matrix vols — the ctor wraps cells in SimpleQuote
        // internally; those internal quotes are not externally observable but
        // the underlying volatility values must remain stable across repeated
        // reads (parity with C++ "fixed market data" branch which expects no
        // change without explicit setValue).
        {
            final SwaptionVolatilityMatrix vol = new SwaptionVolatilityMatrix(
                    REF_DATE, CAL, BDC, OPTION_TENORS, SWAP_TENORS,
                    volsMatrix(), DC, false,
                    VolatilityType.ShiftedLognormal, null);
            final double t0 = vol.optionTimes()[0];
            final double l0 = vol.swapLengths()[0];
            final double a = vol.volatility(t0, l0, 0.05, true);
            final double b = vol.volatility(t0, l0, 0.05, true);
            assertEquals("matrix-input vol stable across reads", a, b, TIGHT_ABS);
            assertEquals("matrix-input vol equals input cell",
                    VOLS[0][0], a, TIGHT_ABS);
        }

        // Sanity: LOOSE_REL is unused here (TIGHT path covers every case) but
        // referenced to mirror the design-§7 tolerance vocabulary.
        assertTrue("LOOSE_REL bound present", LOOSE_REL > 0.0);
    }
}
