/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.credit.BaseCorrelationTermStructure;
import org.jquantlib.experimental.credit.BicubicBaseCorrelationTermStructure;
import org.jquantlib.experimental.credit.BilinearBaseCorrelationTermStructure;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Cross-validation tests for {@link BaseCorrelationTermStructure} +
 * {@link BilinearBaseCorrelationTermStructure} +
 * {@link BicubicBaseCorrelationTermStructure} against
 * QuantLib v1.42.1 via
 * migration-harness/references/experimental/credit-base-corr/base_correlation_term_structure.json
 * (Phase 4m.7c-c).
 *
 * <p>Tier rationale:
 * <ul>
 *   <li>Grid-point evaluation: TIGHT (1e-12 abs) — interpolator returns
 *       the input matrix value at knot intersections.</li>
 *   <li>Interior interpolation: TIGHT (1e-9 abs) — bilinear/bicubic eval
 *       on snapshotted matrix; output shouldn't drift from C++.</li>
 *   <li>correlationSize / settlement-day metadata: EXACT.</li>
 * </ul>
 */
public class BaseCorrelationTermStructureTest {

    private static final String REF_GROUP =
            "experimental/credit-base-corr/base_correlation_term_structure";
    private static final double TIGHT_GRID = 1.0e-12;
    private static final double TIGHT_INTERIOR = 1.0e-9;

    private static final Date AS_OF = new Date(15, Month.June, 2010);
    private static final Calendar CAL = new NullCalendar();
    private static final BusinessDayConvention BDC = BusinessDayConvention.ModifiedFollowing;
    private static final DayCounter DC = new Actual360();
    private static final int SETTLEMENT_DAYS = 0;

    // 4x4 grid (matches the C++ probe defaults)
    private static final List<Period> TENORS_4 = Arrays.asList(
            new Period(12, TimeUnit.Months),
            new Period(36, TimeUnit.Months),
            new Period(60, TimeUnit.Months),
            new Period(84, TimeUnit.Months));
    private static final List<Double> LOSSES_4 = Arrays.asList(0.03, 0.07, 0.12, 0.22);
    private static final double[][] CORRELS_4 = {
            {0.30, 0.32, 0.34, 0.36},
            {0.40, 0.42, 0.44, 0.46},
            {0.55, 0.57, 0.60, 0.63},
            {0.70, 0.72, 0.75, 0.78}
    };

    private static final List<Period> TENORS_2 = Arrays.asList(
            new Period(12, TimeUnit.Months),
            new Period(60, TimeUnit.Months));
    private static final List<Double> LOSSES_2 = Arrays.asList(0.05, 0.20);
    private static final double[][] CORRELS_2 = {
            {0.40, 0.50},
            {0.65, 0.78}
    };

    private static List<List<Handle<Quote>>> handles(final double[][] correls) {
        final List<List<Handle<Quote>>> out = new ArrayList<>();
        for (final double[] row : correls) {
            final List<Handle<Quote>> r = new ArrayList<>();
            for (final double v : row) {
                r.add(new Handle<Quote>(new SimpleQuote(v)));
            }
            out.add(r);
        }
        return out;
    }

    private static void resetEvaluationDate() {
        new Settings().setEvaluationDate(AS_OF);
    }

    // -------------------------------------------------------------------
    // Bilinear 4x4
    // -------------------------------------------------------------------

    @Test
    public void bilinear4x4_gridPoints_matchInputCorrelations() {
        resetEvaluationDate();
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final BaseCorrelationTermStructure ts = new BilinearBaseCorrelationTermStructure(
                SETTLEMENT_DAYS, CAL, BDC, TENORS_4, LOSSES_4, handles(CORRELS_4), DC);

        for (int i = 0; i < LOSSES_4.size(); ++i) {
            for (int j = 0; j < TENORS_4.size(); ++j) {
                final Date d = CAL.advance(AS_OF, TENORS_4.get(j), BDC);
                final double got = ts.correlation(d, LOSSES_4.get(i), true);
                final double expected = ref.getCase("bilinear_grid_i" + i + "_j" + j).expectedDouble();
                assertEquals("bilinear grid i=" + i + " j=" + j, expected, got, TIGHT_GRID);
            }
        }
    }

    @Test
    public void bilinear4x4_interiorHalfwayPoint_matchesCpp() {
        resetEvaluationDate();
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final BaseCorrelationTermStructure ts = new BilinearBaseCorrelationTermStructure(
                SETTLEMENT_DAYS, CAL, BDC, TENORS_4, LOSSES_4, handles(CORRELS_4), DC);

        final int halfMonths = (TENORS_4.get(0).length() + TENORS_4.get(1).length()) / 2;
        final Date dHalf = CAL.advance(AS_OF, new Period(halfMonths, TimeUnit.Months), BDC);
        final double lossHalf = 0.5 * (LOSSES_4.get(0) + LOSSES_4.get(1));
        final double got = ts.correlation(dHalf, lossHalf, true);
        final double expected = ref.getCase("bilinear_interior_half").expectedDouble();
        assertEquals("bilinear interior half", expected, got, TIGHT_INTERIOR);
    }

    @Test
    public void bilinear4x4_metadata() {
        resetEvaluationDate();
        final BaseCorrelationTermStructure ts = new BilinearBaseCorrelationTermStructure(
                SETTLEMENT_DAYS, CAL, BDC, TENORS_4, LOSSES_4, handles(CORRELS_4), DC);

        // C++ scalar specialisation: correlationSize() == 1.
        assertEquals(1, ts.correlationSize());
        assertEquals(SETTLEMENT_DAYS, ts.settlementDays());
        // maxDate is the last tranche-tenor advanced date.
        assertEquals(CAL.advance(AS_OF, TENORS_4.get(TENORS_4.size() - 1), BDC), ts.maxDate());
    }

    // -------------------------------------------------------------------
    // Bicubic 4x4
    // -------------------------------------------------------------------

    @Test
    public void bicubic4x4_gridPoints_matchInputCorrelations() {
        resetEvaluationDate();
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final BaseCorrelationTermStructure ts = new BicubicBaseCorrelationTermStructure(
                SETTLEMENT_DAYS, CAL, BDC, TENORS_4, LOSSES_4, handles(CORRELS_4), DC);

        for (int i = 0; i < LOSSES_4.size(); ++i) {
            for (int j = 0; j < TENORS_4.size(); ++j) {
                final Date d = CAL.advance(AS_OF, TENORS_4.get(j), BDC);
                final double got = ts.correlation(d, LOSSES_4.get(i), true);
                final double expected = ref.getCase("bicubic_grid_i" + i + "_j" + j).expectedDouble();
                assertEquals("bicubic grid i=" + i + " j=" + j, expected, got, TIGHT_GRID);
            }
        }
    }

    @Test
    public void bicubic4x4_interiorHalfwayPoint_matchesCpp() {
        resetEvaluationDate();
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final BaseCorrelationTermStructure ts = new BicubicBaseCorrelationTermStructure(
                SETTLEMENT_DAYS, CAL, BDC, TENORS_4, LOSSES_4, handles(CORRELS_4), DC);

        final int halfMonths = (TENORS_4.get(0).length() + TENORS_4.get(1).length()) / 2;
        final Date dHalf = CAL.advance(AS_OF, new Period(halfMonths, TimeUnit.Months), BDC);
        final double lossHalf = 0.5 * (LOSSES_4.get(0) + LOSSES_4.get(1));
        final double got = ts.correlation(dHalf, lossHalf, true);
        final double expected = ref.getCase("bicubic_interior_half").expectedDouble();
        assertEquals("bicubic interior half", expected, got, TIGHT_INTERIOR);
    }

    // -------------------------------------------------------------------
    // Bilinear 2x2
    // -------------------------------------------------------------------

    @Test
    public void bilinear2x2_gridPoints_matchInputCorrelations() {
        resetEvaluationDate();
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final BaseCorrelationTermStructure ts = new BilinearBaseCorrelationTermStructure(
                SETTLEMENT_DAYS, CAL, BDC, TENORS_2, LOSSES_2, handles(CORRELS_2), DC);

        for (int i = 0; i < LOSSES_2.size(); ++i) {
            for (int j = 0; j < TENORS_2.size(); ++j) {
                final Date d = CAL.advance(AS_OF, TENORS_2.get(j), BDC);
                final double got = ts.correlation(d, LOSSES_2.get(i), true);
                final double expected = ref.getCase("bilinear2x2_grid_i" + i + "_j" + j).expectedDouble();
                assertEquals("bilinear2x2 grid i=" + i + " j=" + j, expected, got, TIGHT_GRID);
            }
        }
    }

    @Test
    public void bilinear2x2_interiorHalfwayPoint_matchesCpp() {
        resetEvaluationDate();
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final BaseCorrelationTermStructure ts = new BilinearBaseCorrelationTermStructure(
                SETTLEMENT_DAYS, CAL, BDC, TENORS_2, LOSSES_2, handles(CORRELS_2), DC);

        final int halfMonths = (TENORS_2.get(0).length() + TENORS_2.get(1).length()) / 2;
        final Date dHalf = CAL.advance(AS_OF, new Period(halfMonths, TimeUnit.Months), BDC);
        final double lossHalf = 0.5 * (LOSSES_2.get(0) + LOSSES_2.get(1));
        final double got = ts.correlation(dHalf, lossHalf, true);
        final double expected = ref.getCase("bilinear2x2_interior_half").expectedDouble();
        assertEquals("bilinear2x2 interior half", expected, got, TIGHT_INTERIOR);
    }

    // -------------------------------------------------------------------
    // Reactive update path: change a quote, verify update() refreshes the matrix
    // -------------------------------------------------------------------

    @Test
    public void quoteUpdate_refreshesCorrelations() {
        resetEvaluationDate();
        // Build the quote handles directly so we can mutate them later.
        final List<List<Handle<Quote>>> hs = new ArrayList<>();
        final SimpleQuote q00 = new SimpleQuote(0.40);
        final List<Handle<Quote>> r0 = new ArrayList<>();
        r0.add(new Handle<Quote>(q00));
        r0.add(new Handle<Quote>(new SimpleQuote(0.50)));
        hs.add(r0);
        final List<Handle<Quote>> r1 = new ArrayList<>();
        r1.add(new Handle<Quote>(new SimpleQuote(0.65)));
        r1.add(new Handle<Quote>(new SimpleQuote(0.78)));
        hs.add(r1);

        final BaseCorrelationTermStructure ts = new BilinearBaseCorrelationTermStructure(
                SETTLEMENT_DAYS, CAL, BDC, TENORS_2, LOSSES_2, hs, DC);

        // Hold strong references to the quote (Phase 2x A.4 weak-ref discipline).
        // Otherwise the WeakReferenceObservable could drop the linkage.
        @SuppressWarnings("unused")
        final SimpleQuote keepAlive = q00;

        final Date d12 = CAL.advance(AS_OF, TENORS_2.get(0), BDC);
        // Initial value matches input.
        assertEquals(0.40, ts.correlation(d12, LOSSES_2.get(0), true), TIGHT_GRID);

        // Bump the quote and re-evaluate; updateMatrix re-snapshots the live values.
        q00.setValue(0.55);
        ts.updateMatrix();
        assertEquals(0.55, ts.correlation(d12, LOSSES_2.get(0), true), TIGHT_GRID);
    }
}
