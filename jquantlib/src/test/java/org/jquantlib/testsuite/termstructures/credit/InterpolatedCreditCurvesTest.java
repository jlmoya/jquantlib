/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Smoke + tier-stratified tests for InterpolatedHazardRateCurve,
 InterpolatedSurvivalProbabilityCurve, and InterpolatedDefaultDensityCurve.
 Phase 3a L1.
*/
package org.jquantlib.testsuite.termstructures.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.interpolations.factories.BackwardFlat;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.interpolations.factories.LogLinear;
import org.jquantlib.termstructures.credit.InterpolatedDefaultDensityCurve;
import org.jquantlib.termstructures.credit.InterpolatedHazardRateCurve;
import org.jquantlib.termstructures.credit.InterpolatedSurvivalProbabilityCurve;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

/**
 * L1 cross-validation against closed-form / structural identities.
 *
 * <p>For each interpolated credit curve, we exercise:
 * <ul>
 *   <li>{@code maxDate()} reflects the last input pillar.
 *   <li>node-value reproduction (interpolation passes through pillars).
 *   <li>structural identities the C++ source guarantees (e.g. for a
 *       constant-hazard BackwardFlat curve, {@code S(t) = exp(-h t)}).
 * </ul>
 *
 * <p>Tolerance tier: TIGHT (1e-12) for closed-form identities, LOOSE (1e-6)
 * for derivative-based density (numerical-differentiation noise is bounded
 * by the interpolation slope continuity).
 */
public class InterpolatedCreditCurvesTest {

    private static final double TIGHT = 1.0e-12;
    private static final double LOOSE = 1.0e-6;

    private static final DayCounter DC = new Actual360();
    private static final Date REF = new Date(15, Month.July, 2023);

    @Test
    public void interpolatedHazardRate_constantBackwardFlat_matchesClosedForm() {
        // Constant hazard rate h = 0.02 with BackwardFlat: every step
        // returns h. S(t) = exp(-h t) for any t in [0, last node].
        final double h = 0.02;
        final Date[] dates = {
                REF,
                REF.add(360),    // ~1y
                REF.add(720),    // ~2y
                REF.add(1080)    // ~3y
        };
        final double[] hr = { h, h, h, h };

        final var curve = new InterpolatedHazardRateCurve<BackwardFlat>(BackwardFlat.class, dates, hr, DC);

        // node values
        for (int i = 0; i < dates.length; ++i) {
            assertEquals("h(node " + i + ")",
                    h, curve.hazardRate(DC.yearFraction(REF, dates[i])), TIGHT);
        }

        // mid-interval: BackwardFlat returns the right-end value (which is h).
        assertEquals("h(mid)", h, curve.hazardRate(0.5), TIGHT);

        // S(t) = exp(-h t) — closed form
        for (final double t : new double[] { 0.25, 0.5, 1.0, 1.5, 2.0, 2.5 }) {
            assertEquals("S(" + t + ")",
                    Math.exp(-h * t),
                    curve.survivalProbability(t),
                    TIGHT);
        }
    }

    @Test
    public void interpolatedHazardRate_flatExtrapolation() {
        final double h = 0.03;
        final Date[] dates = { REF, REF.add(360) };
        final double[] hr = { h, h };

        final var curve = new InterpolatedHazardRateCurve<BackwardFlat>(BackwardFlat.class, dates, hr, DC);
        curve.enableExtrapolation();

        // Past last node, hazard rate is flat at h. So S(t) = exp(-h t).
        assertEquals("S(2.0) flat extrapolation",
                Math.exp(-h * 2.0),
                curve.survivalProbability(2.0, true),
                TIGHT);
    }

    @Test
    public void interpolatedHazardRate_maxDateMatchesLastPillar() {
        final Date[] dates = { REF, REF.add(360), REF.add(720) };
        final double[] hr = { 0.01, 0.02, 0.03 };
        final var curve = new InterpolatedHazardRateCurve<BackwardFlat>(BackwardFlat.class, dates, hr, DC);

        assertTrue("maxDate is last pillar",
                curve.maxDate().eq(dates[dates.length - 1]));
        assertEquals("3 hazard rates inspected", 3, curve.hazardRates().length);
    }

    @Test
    public void interpolatedSurvivalProbability_decreasingNodes() {
        final Date[] dates = {
                REF,
                REF.add(360),
                REF.add(720),
                REF.add(1080)
        };
        // Strictly decreasing: S(0)=1, S(1y)=0.99, S(2y)=0.97, S(3y)=0.94.
        final double[] s = { 1.0, 0.99, 0.97, 0.94 };

        final var curve = new InterpolatedSurvivalProbabilityCurve<LogLinear>(LogLinear.class, dates, s, DC);

        // Node values via date accessor.
        for (int i = 0; i < dates.length; ++i) {
            assertEquals("S(node " + i + ")",
                    s[i],
                    curve.survivalProbability(dates[i]),
                    TIGHT);
        }
    }

    @Test
    public void interpolatedSurvivalProbability_validatesMonotonicity() {
        final Date[] dates = { REF, REF.add(360), REF.add(720) };
        final double[] bad = { 1.0, 0.95, 0.97 };  // increasing — violates ctor check
        try {
            new InterpolatedSurvivalProbabilityCurve<>(LogLinear.class, dates, bad, DC);
            fail("expected exception for non-monotone survival probabilities");
        } catch (final RuntimeException expected) {
            // expected — the constructor must reject increasing series.
        }
    }

    @Test
    public void interpolatedSurvivalProbability_firstNodeMustBeOne() {
        final Date[] dates = { REF, REF.add(360) };
        final double[] notOne = { 0.99, 0.95 };
        try {
            new InterpolatedSurvivalProbabilityCurve<>(LogLinear.class, dates, notOne, DC);
            fail("expected exception for first survival != 1.0");
        } catch (final RuntimeException expected) {
            // expected
        }
    }

    @Test
    public void interpolatedDefaultDensity_atNodes_matchesData() {
        final Date[] dates = {
                REF,
                REF.add(360),
                REF.add(720),
                REF.add(1080)
        };
        final double[] p = { 0.01, 0.012, 0.014, 0.016 };

        final var curve = new InterpolatedDefaultDensityCurve<Linear>(Linear.class, dates, p, DC);

        for (int i = 0; i < dates.length; ++i) {
            assertEquals("p(node " + i + ")",
                    p[i],
                    curve.defaultDensity(dates[i]),
                    TIGHT);
        }
    }

    @Test
    public void interpolatedDefaultDensity_survivalIsNonIncreasing() {
        final Date[] dates = { REF, REF.add(360), REF.add(720) };
        final double[] p = { 0.01, 0.012, 0.014 };

        final var curve = new InterpolatedDefaultDensityCurve<Linear>(Linear.class, dates, p, DC);

        // S(t) = max(1 - integral, 0) — must be monotone non-increasing in t.
        double prev = curve.survivalProbability(0.0);
        for (final double t : new double[] { 0.1, 0.25, 0.5, 0.75, 1.0, 1.5 }) {
            final double s = curve.survivalProbability(t);
            assertTrue("S monotone non-increasing at t=" + t, s <= prev + LOOSE);
            assertTrue("S non-negative at t=" + t, s >= 0.0);
            prev = s;
        }
    }

    @Test
    public void interpolatedDefaultDensity_rejectsNegativeDensity() {
        final Date[] dates = { REF, REF.add(360), REF.add(720) };
        final double[] bad = { 0.01, -0.01, 0.014 };
        try {
            new InterpolatedDefaultDensityCurve<>(Linear.class, dates, bad, DC);
            fail("expected exception for negative density");
        } catch (final RuntimeException expected) {
            // expected
        }
    }
}
