/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.model.shortrate;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.model.shortrate.onefactormodels.Vasicek;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Java equivalents of the two short-rate-model tests added to C++ QuantLib's test suite in v1.43
 * ({@code test-suite/shortratemodels.cpp}).
 * <p>
 * Neither tests new v1.43 behaviour — both pin behaviour C++ has always had — and both caught a real divergence in
 * this port, which is why they are worth having rather than assuming.
 *
 * @author Jose Moya
 */
public class ShortRateModelsV143Test {

    private static final double TOLERANCE = 1.0e-12;

    private Date savedEvaluationDate;

    @Before
    public void setUp() {
        savedEvaluationDate = new Settings().evaluationDate();
    }

    @After
    public void tearDown() {
        new Settings().setEvaluationDate(savedEvaluationDate);
    }

    /**
     * The model observes its term structure, so relinking the handle must move the initial short rate with it. The
     * Java port previously left {@code r0} at whatever the curve said when the model was constructed, and documented
     * that as a caveat — meaning every quantity derived from {@code r0} silently used a stale rate after a relink.
     * <p>
     * Mirrors C++ {@code testHullWhiteUpdatesR0WhenTermStructureRelinks}.
     */
    @Test
    public void testHullWhiteUpdatesR0WhenTermStructureRelinks() {
        QL.info("Testing Hull-White r0 update when the term structure is relinked...");

        final Date today = new Date(19, Month.May, 2026);
        new Settings().setEvaluationDate(today);

        final RelinkableHandle< YieldTermStructure > termStructure = new RelinkableHandle< YieldTermStructure >();
        termStructure.linkTo(new FlatForward(today, 0.02, new Actual365Fixed()));

        final HullWhite model = new HullWhite(termStructure);

        termStructure.linkTo(new FlatForward(today, 0.05, new Actual365Fixed()));

        final double expected = termStructure.currentLink()
                .forwardRate(0.0, 0.0, Compounding.Continuous, Frequency.NoFrequency).rate();

        assertEquals("r0 must follow the relinked term structure", expected, model.r0(), TOLERANCE);
    }

    /**
     * As the mean reversion goes to zero the general Vasicek bond-price formula degenerates, so the model takes a
     * limit branch. The Java port returned {@code A = 0} there, pricing every zero bond at exactly zero — a wrong
     * answer that looks like a number rather than an error.
     * <p>
     * Mirrors C++ {@code testVasicekDiscountFactorForSmallMeanReversion}. The expectation is the closed-form limit,
     * so no probe reference is needed.
     */
    @Test
    public void testVasicekDiscountFactorForSmallMeanReversion() {
        QL.info("Testing zero-bond pricing for the Vasicek model with small mean reversion...");

        final double r0 = 0.05;
        final double a = 1.0e-12;
        final double b = 0.05;
        final double sigma = 0.01;
        final double lambda = 0.0;
        final double now = 0.0;
        final double maturity = 1.0;

        final Vasicek model = new Vasicek(r0, a, b, sigma, lambda);

        final double expected = Math.exp(-r0 * maturity + sigma * sigma * maturity * maturity * maturity / 6.0);
        final double calculated = model.discountBond(now, maturity, r0);

        assertEquals("small-mean-reversion zero-bond price", expected, calculated, TOLERANCE);
    }

    /**
     * The same model away from the degenerate branch must be unaffected — otherwise a "fix" to the limit could have
     * quietly moved the ordinary case too.
     */
    @Test
    public void testVasicekDiscountFactorAwayFromTheLimit() {
        QL.info("Testing that the ordinary Vasicek branch is unchanged...");

        final Vasicek model = new Vasicek(0.05, 0.1, 0.05, 0.01, 0.0);
        final double p = model.discountBond(0.0, 1.0, 0.05);

        // A one-year zero on a 5% short rate sits just under exp(-0.05); the exact value is pinned by the existing
        // Vasicek tests, so this only asserts the branch still produces a sane, non-degenerate number.
        assertEquals("ordinary-branch zero bond should be near exp(-0.05)", Math.exp(-0.05), p, 1.0e-3);
    }
}
