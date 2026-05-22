/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Java port of QuantLib v1.42.1 test-suite/nthtodefault.cpp::testGauss and
 testStudent. Pinned commit 099987f0ca2c11c505dc4348cdb9ce01a598e1e5.

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
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import org.jquantlib.Settings;
import org.jquantlib.currencies.Europe;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.credit.Basket;
import org.jquantlib.experimental.credit.ConstantLossModel;
import org.jquantlib.experimental.credit.DefaultEvent;
import org.jquantlib.experimental.credit.DefaultProbKey;
import org.jquantlib.experimental.credit.IntegralNtdEngine;
import org.jquantlib.experimental.credit.Issuer;
import org.jquantlib.experimental.credit.Issuer.KeyCurvePair;
import org.jquantlib.experimental.credit.LatentModel;
import org.jquantlib.experimental.credit.NorthAmericaCorpDefaultKey;
import org.jquantlib.experimental.credit.NthToDefault;
import org.jquantlib.experimental.credit.Pool;
import org.jquantlib.experimental.credit.Seniority;
import org.jquantlib.experimental.math.GaussianCopulaPolicy;
import org.jquantlib.experimental.math.TCopulaPolicy;
import org.jquantlib.instruments.Protection;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Cross-validation of {@link NthToDefault} pricing under a Gaussian / Student-t
 * copula against the Hull–White reference table (J. Hull & A. White, "Valuation
 * of a CDO and an nth to Default CDS Without Monte Carlo Simulation", Journal
 * of Derivatives 12, 2, Winter 2004, pp 8–23 — reproduced verbatim in
 * QuantLib v1.42.1's {@code test-suite/nthtodefault.cpp}).
 *
 * <p>Java port of QuantLib v1.42.1 test-suite cases:
 * <ul>
 *   <li>{@code BOOST_AUTO_TEST_CASE(testGauss)}</li>
 *   <li>{@code BOOST_AUTO_TEST_CASE(testStudent)}</li>
 * </ul>
 *
 * <p>Both tests are tagged in C++ with {@code precondition(if_speed(Slow))}.
 * The Java equivalents pay the same wall-time cost — they integrate a
 * 1-week step over a 5Y schedule × 10 NTD ranks × (3 correlations for Gauss /
 * 1 correlation for Student) using the Gauss-Hermite quadrature backend of
 * the {@link LatentModel}. Expect a few seconds per test.
 *
 * <p>Tolerance tiers mirror C++ verbatim: relative 1.5% (Gauss) / 1.7%
 * (Student), with an absolute fallback of 1bp.
 */
public class NthToDefaultTest {

    private Date savedEvalDate;

    @Before
    public void setUp() {
        savedEvalDate = new Settings().evaluationDate();
    }

    @After
    public void tearDown() {
        new Settings().setEvaluationDate(savedEvalDate);
    }

    /** Hull–White nth-to-default reference table (rank, spread@rho=0/0.3/0.6). */
    private static final int[][] HW_DATA = {
            { 1, 603, 440, 293 },
            { 2,  98, 139, 137 },
            { 3,  12,  53,  79 },
            { 4,   1,  21,  49 },
            { 5,   0,   8,  31 },
            { 6,   0,   3,  19 },
            { 7,   0,   1,  12 },
            { 8,   0,   0,   7 },
            { 9,   0,   0,   3 },
            {10,   0,   0,   1 }
    };

    private static final double[] HW_CORRELATION = { 0.0, 0.3, 0.6 };

    /** HW Table 3 with corr=0.3, t-copula (5/5 column). */
    private static final int[][] HW_DATA_DIST_5_5 = {
            { 1, 455 },
            { 2, 116 },
            { 3,  44 },
            { 4,  22 },
            { 5,  13 },
            { 6,   8 },
            { 7,   5 },
            { 8,   4 },
            { 9,   2 },
            {10,   1 }
    };

    @Test
    public void testGauss() {
        final double relTolerance = 0.015;
        final double absTolerance = 1.0;
        final Period timeUnit = new Period(1, TimeUnit.Weeks);
        runHwTable(relTolerance, absTolerance, timeUnit, /*useStudent*/ false);
    }

    @Test
    public void testStudent() {
        final double relTolerance = 0.017;
        final double absTolerance = 1.0;
        final Period timeUnit = new Period(1, TimeUnit.Weeks);
        runHwTable(relTolerance, absTolerance, timeUnit, /*useStudent*/ true);
    }

    private void runHwTable(final double relTol, final double absTol, final Period timeUnit, final boolean useStudent) {
        final int names = 10;
        final double rate = 0.05;
        final DayCounter dc = new Actual365Fixed();
        final Compounding cmp = Compounding.Continuous;
        final double recovery = 0.4;
        final double namesNotional = 100.0;
        final Date asof = new Date(31, Month.August, 2006);
        new Settings().setEvaluationDate(asof);

        final Target target = new Target();
        final Schedule schedule = new MakeSchedule()
                .from(new Date(1, Month.September, 2006))
                .to(new Date(1, Month.September, 2011))
                .withTenor(new Period(3, TimeUnit.Months))
                .withCalendar(target)
                .schedule();

        final YieldTermStructure yieldPtr = new FlatForward(asof, rate, dc, cmp);
        final Handle<YieldTermStructure> yieldHandle = new Handle<YieldTermStructure>(yieldPtr);

        // Per-name probability curves (all identical at lambda = 0.01).
        final List<Handle<DefaultProbabilityTermStructure>> probabilities = new ArrayList<>(names);
        for (int i = 0; i < names; ++i) {
            final FlatHazardRate fhr = new FlatHazardRate(asof, 0.01, dc);
            probabilities.add(new Handle<DefaultProbabilityTermStructure>(fhr));
        }

        // Driving correlation quote.
        final SimpleQuote correlQuote = new SimpleQuote(0.0);
        final Handle<Quote> correlHandle = new Handle<Quote>(correlQuote);

        // ConstantLossModel — Gaussian or Student-t with t-order = 5 / 5.
        final ConstantLossModel<?> copula = useStudent
                ? buildStudentLossModel(correlHandle, recovery, names)
                : buildGaussLossModel(correlHandle, recovery, names);

        // Pool + Issuer wiring.
        final Europe.EURCurrency eur = new Europe.EURCurrency();
        final List<String> namesIds = new ArrayList<>(names);
        for (int i = 0; i < names; ++i) {
            namesIds.add("Name" + i);
        }
        final Pool thePool = new Pool();
        final NorthAmericaCorpDefaultKey poolKey =
                new NorthAmericaCorpDefaultKey(eur, Seniority.SeniorSec, new Period(), 1.0);
        for (int i = 0; i < names; ++i) {
            final List<KeyCurvePair> curves = new ArrayList<>(1);
            curves.add(new KeyCurvePair(poolKey, probabilities.get(i)));
            final Issuer issuer = new Issuer(curves, new TreeSet<>(Issuer.EARLIER_THAN));
            thePool.add(namesIds.get(i), issuer, poolKey);
        }
        @SuppressWarnings("unused")
        final List<DefaultProbKey> defaultKeys = new ArrayList<>(probabilities.size());
        for (int i = 0; i < probabilities.size(); ++i) {
            defaultKeys.add(new NorthAmericaCorpDefaultKey(eur, Seniority.SeniorSec, new Period(), 1.0));
        }

        final List<Double> notionals = new ArrayList<>(names);
        for (int i = 0; i < names; ++i) {
            notionals.add(namesNotional / names);
        }
        final Basket basket = new Basket(asof, namesIds, notionals, thePool, 0.0, 1.0);
        final IntegralNtdEngine engine = new IntegralNtdEngine(timeUnit, yieldHandle);

        final List<NthToDefault> ntds = new ArrayList<>(probabilities.size());
        for (int i = 1; i <= probabilities.size(); ++i) {
            final NthToDefault ntd = new NthToDefault(basket, i, Protection.Side.Seller, schedule,
                    0.0, 0.02, new Actual360(), namesNotional * names, true);
            ntd.setPricingEngine(engine);
            ntds.add(ntd);
        }

        basket.setLossModel(copula);

        if (useStudent) {
            // T-copula leg: single correlation = 0.3 against the (5/5) reference column.
            correlQuote.setValue(0.3);
            final List<String> failures = new ArrayList<>();
            for (int i = 0; i < ntds.size(); ++i) {
                final double fair = 1e4 * ntds.get(i).fairPremium();
                final double ref = HW_DATA_DIST_5_5[i][1];
                final double diff = fair - ref;
                final boolean ok = Math.abs(diff / ref) < relTol || Math.abs(diff) < absTol;
                if (!ok) {
                    failures.add("rank=" + HW_DATA_DIST_5_5[i][0] + " ref=" + ref
                            + " actual=" + fair + " diff=" + diff);
                }
            }
            assertTrue("Student tolerance " + relTol + "|" + absTol
                    + " exceeded:\n" + String.join("\n", failures), failures.isEmpty());
        } else {
            // Gaussian: iterate all three correlations vs HW reference table.
            final List<String> failures = new ArrayList<>();
            for (int j = 0; j < HW_CORRELATION.length; ++j) {
                correlQuote.setValue(HW_CORRELATION[j]);
                for (int i = 0; i < ntds.size(); ++i) {
                    final double fair = 1e4 * ntds.get(i).fairPremium();
                    final double ref = HW_DATA[i][1 + j];
                    final double diff = fair - ref;
                    final boolean ok = Math.abs(diff / ref) < relTol || Math.abs(diff) < absTol;
                    if (!ok) {
                        failures.add("corr=" + HW_CORRELATION[j]
                                + " rank=" + HW_DATA[i][0]
                                + " ref=" + ref + " actual=" + fair + " diff=" + diff);
                    }
                }
            }
            assertTrue("Gaussian tolerance " + relTol + "|" + absTol
                    + " exceeded:\n" + String.join("\n", failures), failures.isEmpty());
        }
    }

    private static ConstantLossModel<GaussianCopulaPolicy> buildGaussLossModel(
            final Handle<Quote> correlHandle, final double recovery, final int names) {
        final List<Double> recoveries = new ArrayList<>(names);
        for (int i = 0; i < names; ++i) {
            recoveries.add(recovery);
        }
        final double rho = correlHandle.currentLink().value();
        final List<List<Double>> initialFactorWeights = new ArrayList<>(names);
        for (int i = 0; i < names; ++i) {
            initialFactorWeights.add(new ArrayList<>(Arrays.asList(Math.sqrt(rho))));
        }
        final GaussianCopulaPolicy initialCopula = new GaussianCopulaPolicy(initialFactorWeights);
        return new ConstantLossModel<>(correlHandle, recoveries, names, initialCopula,
                LatentModel.IntegrationType.GaussianQuadrature, fw -> new GaussianCopulaPolicy(fw));
    }

    private static ConstantLossModel<TCopulaPolicy> buildStudentLossModel(
            final Handle<Quote> correlHandle, final double recovery, final int names) {
        final List<Double> recoveries = new ArrayList<>(names);
        for (int i = 0; i < names; ++i) {
            recoveries.add(recovery);
        }
        final double rho = correlHandle.currentLink().value();
        final List<List<Double>> initialFactorWeights = new ArrayList<>(names);
        for (int i = 0; i < names; ++i) {
            initialFactorWeights.add(new ArrayList<>(Arrays.asList(Math.sqrt(rho))));
        }
        // C++: TCopulaPolicy::initTraits iniT; iniT.tOrders = std::vector<Integer>(2, 5);
        final TCopulaPolicy.InitTraits ini = new TCopulaPolicy.InitTraits(5, 5);
        final TCopulaPolicy initialCopula = new TCopulaPolicy(initialFactorWeights, ini);
        return new ConstantLossModel<>(correlHandle, recoveries, names, initialCopula,
                LatentModel.IntegrationType.GaussianQuadrature, fw -> new TCopulaPolicy(fw, ini));
    }
}
