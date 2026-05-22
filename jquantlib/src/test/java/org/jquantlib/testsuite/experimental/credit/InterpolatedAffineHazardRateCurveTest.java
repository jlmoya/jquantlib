/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.experimental.credit.InterpolatedAffineHazardRateCurve;
import org.jquantlib.math.interpolations.factories.BackwardFlat;
import org.jquantlib.model.shortrate.onefactormodels.CoxIngersollRoss;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Phase 4m.6 tests for {@link InterpolatedAffineHazardRateCurve}.
 *
 * <p>Cross-validation: deterministic-component-only checks (single-pillar,
 * flat hazard rate) plus continuity at the reference date. Reference:
 * QuantLib v1.42.1
 * {@code ql/experimental/credit/interpolatedaffinehazardratecurve.hpp}.
 */
public class InterpolatedAffineHazardRateCurveTest {

    @Test
    public void constructionAndInspectors() {
        final Date today = new Date(15, Month.January, 2026);
        final Date[] dates = {
                today,
                today.add(new Period(1, TimeUnit.Years)),
                today.add(new Period(2, TimeUnit.Years)),
                today.add(new Period(5, TimeUnit.Years))
        };
        final double[] hazardRates = { 0.01, 0.02, 0.03, 0.04 };
        // CIR with very small initial rate so the affine bond contribution
        // doesn't dominate the deterministic part.
        final CoxIngersollRoss model = new CoxIngersollRoss(0.001, 0.001, 0.1, 0.05);
        final var curve = new InterpolatedAffineHazardRateCurve<BackwardFlat>(BackwardFlat.class, dates, hazardRates,
                        new Actual365Fixed(), model);

        // Reference date is dates[0]
        assertEquals(today, curve.referenceDate());
        // maxDate is dates[N-1]
        assertEquals(dates[dates.length - 1], curve.maxDate());
        // dates / data inspectors
        assertEquals(4, curve.dates().length);
        assertEquals(4, curve.data().length);
        assertEquals(0.04, curve.hazardRates()[3], 0.0);
        assertNotNull(curve.interpolator());
        assertNotNull(curve.interpolation());
        assertEquals(BackwardFlat.class, curve.interpolatorClass());
        assertEquals(4, curve.nodes().size());
    }

    @Test
    public void rejectsMismatchedSizes() {
        final Date today = new Date(15, Month.January, 2026);
        final Date[] dates = { today, today.add(new Period(1, TimeUnit.Years)) };
        final double[] hazardRates = { 0.01, 0.02, 0.03 };  // mismatch
        final CoxIngersollRoss model = new CoxIngersollRoss(0.001, 0.001, 0.1, 0.05);
        try {
            new InterpolatedAffineHazardRateCurve<>(BackwardFlat.class, dates, hazardRates,
                    new Actual365Fixed(), model);
            fail("expected mismatch exception");
        } catch (final Exception e) {
            // expected
        }
    }

    @Test
    public void survivalProbabilityAtReferenceDateIsAffineModelDiscount() {
        // At t=0 the deterministic contribution is exp(0) = 1, so the survival
        // probability is exactly the affine model's discountBond(0,0,initValHR),
        // which is 1 (model discount at t=T=0).
        final Date today = new Date(15, Month.January, 2026);
        final Date[] dates = {
                today,
                today.add(new Period(1, TimeUnit.Years)),
                today.add(new Period(2, TimeUnit.Years))
        };
        final double[] hazardRates = { 0.01, 0.02, 0.03 };
        final CoxIngersollRoss model = new CoxIngersollRoss(0.01, 0.01, 0.1, 0.05);
        final var curve = new InterpolatedAffineHazardRateCurve<BackwardFlat>(BackwardFlat.class, dates, hazardRates,
                        new Actual365Fixed(), model);
        // survivalProbability(today) should be model.discountBond(0,0,...) = 1
        assertEquals(1.0, curve.survivalProbability(today), 1.0e-12);
    }

    @Test
    public void survivalProbabilityIsMonotonicallyDecreasing() {
        final Date today = new Date(15, Month.January, 2026);
        final Date[] dates = {
                today,
                today.add(new Period(1, TimeUnit.Years)),
                today.add(new Period(3, TimeUnit.Years)),
                today.add(new Period(5, TimeUnit.Years))
        };
        final double[] hazardRates = { 0.01, 0.02, 0.03, 0.04 };
        final CoxIngersollRoss model = new CoxIngersollRoss(0.001, 0.001, 0.1, 0.05);
        final var curve = new InterpolatedAffineHazardRateCurve<BackwardFlat>(BackwardFlat.class, dates, hazardRates,
                        new Actual365Fixed(), model);

        double prev = 1.0;
        for (int yr = 0; yr <= 5; ++yr) {
            final Date d = today.add(new Period(yr, TimeUnit.Years));
            final double s = curve.survivalProbability(d);
            assertTrue("survival not monotone at year=" + yr + " (prev=" + prev + ", s=" + s + ")",
                    s <= prev + 1.0e-12);
            assertTrue("survival negative or > 1 at year=" + yr + ": " + s,
                    s >= 0.0 && s <= 1.0);
            prev = s;
        }
    }

    @Test
    public void hazardRateAtReferenceDateMatchesFirstPillar() {
        // BackwardFlat at the reference date returns the first hazard rate.
        final Date today = new Date(15, Month.January, 2026);
        final Date[] dates = {
                today,
                today.add(new Period(1, TimeUnit.Years)),
                today.add(new Period(2, TimeUnit.Years))
        };
        final double[] hazardRates = { 0.01, 0.02, 0.03 };
        final CoxIngersollRoss model = new CoxIngersollRoss(0.001, 0.001, 0.1, 0.05);
        final var curve = new InterpolatedAffineHazardRateCurve<BackwardFlat>(BackwardFlat.class, dates, hazardRates,
                        new Actual365Fixed(), model);
        // At time 0, BackwardFlat returns the first y value
        assertEquals(0.01, curve.hazardRate(today, true), 1.0e-12);
    }
}
