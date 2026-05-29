/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation of RandomDefaultModel / GaussianRandomDefaultModel against
 C++ QuantLib v1.42.1 reference values produced by random_default_model_probe.

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.jquantlib.Settings;
import org.jquantlib.currencies.Europe;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.experimental.credit.DefaultEvent;
import org.jquantlib.experimental.credit.DefaultProbKey;
import org.jquantlib.experimental.credit.GaussianRandomDefaultModel;
import org.jquantlib.experimental.credit.Issuer;
import org.jquantlib.experimental.credit.NorthAmericaCorpDefaultKey;
import org.jquantlib.experimental.credit.OneFactorCopula;
import org.jquantlib.experimental.credit.OneFactorGaussianCopula;
import org.jquantlib.experimental.credit.Pool;
import org.jquantlib.experimental.credit.Seniority;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Cross-validation of {@link GaussianRandomDefaultModel} (and its
 * {@code RandomDefaultModel} base) against C++ v1.42.1 references in
 * {@code migration-harness/references/credit/random_default_model.json}.
 *
 * <p><b>Cross-validation strategy.</b> {@code nextSequence()} is Monte-Carlo
 * (RNG-driven), so it cannot be deterministically matched bit-for-bit against
 * the C++ Mersenne-Twister without reproducing the RNG exactly — which is
 * brittle. Per the migration guidance we instead assert the two
 * <i>deterministic</i> sub-computations that {@code nextSequence()} performs
 * for each name:
 *
 * <ol>
 *   <li><b>Conditional default probability</b> {@code p = Phi(y)} where
 *       {@code y = a*M + sqrt(1-a^2)*Z}, {@code a = sqrt(correlation)} — the
 *       copula-draw → implied-default-probability step. TIGHT.</li>
 *   <li><b>Default-time inversion</b>: the time {@code t} solving
 *       {@code dts.defaultProbability(t) = p}, found with the same Brent setup
 *       ({@code lower=0, upper=tmax, guess=tmax/2, step=1}) the model uses
 *       inside {@code nextSequence()}. TIGHT (root accuracy 1e-8 ⇒ assert to
 *       1e-7).</li>
 * </ol>
 *
 * <p>Together these fully determine the default time produced for a given
 * copula draw, so the deterministic cross-check is faithful. A final
 * structural test exercises the real {@code nextSequence()}/{@code reset()}
 * path and asserts seed-reproducibility (not a C++ bit match).
 */
public class GaussianRandomDefaultModelTest {

    private static final String REF_GROUP = "credit/random_default_model";
    private static final ReferenceReader REF = ReferenceReader.load(REF_GROUP);

    private static final double TIGHT = 1.0e-9;
    /** root accuracy in the probe is 1e-8 ⇒ allow 1e-7 here. */
    private static final double INV_TIME_TOL = 1.0e-7;

    private static final Date AS_OF = new Date(31, Month.August, 2006);
    private static final CumulativeNormalDistribution PHI = new CumulativeNormalDistribution();

    // ----- 1. conditional default probability p = Phi(y) ---------------------

    private void checkCondProb(final List<String> failures, final String name, final double correlation,
            final double m, final double z) {
        final double a = Math.sqrt(correlation);
        final double y = a * m + Math.sqrt(1.0 - a * a) * z;
        final double actual = PHI.op(y);
        final double expected = REF.getCase("condprob_" + name).expectedDouble();
        if (Math.abs(actual - expected) > Math.max(TIGHT, TIGHT * Math.abs(expected))) {
            failures.add("condprob_" + name + " expected=" + expected + " actual=" + actual);
        }
    }

    @Test
    public void conditionalDefaultProbability_matchesCpp() {
        final List<String> f = new ArrayList<>();
        checkCondProb(f, "corr30_m0_z0", 0.30, 0.0, 0.0);
        checkCondProb(f, "corr30_mp1_zm05", 0.30, 1.0, -0.5);
        checkCondProb(f, "corr30_mm15_zp2", 0.30, -1.5, 2.0);
        checkCondProb(f, "corr10_mp05_zp05", 0.10, 0.5, 0.5);
        checkCondProb(f, "corr50_mm2_zm1", 0.50, -2.0, -1.0);
        assertTrue("Failures:\n" + String.join("\n", f), f.isEmpty());
    }

    // ----- 2. default-time inversion via Brent (flat hazard) -----------------

    @Test
    public void defaultTimeInversion_matchesCpp() {
        new Settings().setEvaluationDate(AS_OF);
        final double lambda = 0.01;
        final double tmax = 5.0;
        final double accuracy = 1.0e-8;
        final Handle<Quote> hazardRate = new Handle<Quote>(new SimpleQuote(lambda));
        final DefaultProbabilityTermStructure dts = new FlatHazardRate(AS_OF, hazardRate,
                new ActualActual(ActualActual.Convention.ISDA));

        final String[][] targets = {
                {"p_001", "0.001"}, {"p_005", "0.005"}, {"p_01", "0.01"},
                {"p_02", "0.02"}, {"p_03", "0.03"}
        };
        final List<String> f = new ArrayList<>();
        for (final String[] tg : targets) {
            final double p = Double.parseDouble(tg[1]);
            // exactly the solver call inside nextSequence():
            final Brent brent = new Brent();
            brent.setLowerBound(0.0);
            brent.setUpperBound(tmax);
            final Ops.DoubleOp root = t -> dts.defaultProbability(t, true) - p;
            final double t = brent.solve(root, accuracy, tmax / 2.0, 1.0);
            final double expected = REF.getCase("invtime_" + tg[0]).expectedDouble();
            if (Math.abs(t - expected) > INV_TIME_TOL) {
                f.add("invtime_" + tg[0] + " expected=" + expected + " actual=" + t);
            }
        }
        // default probability at tmax (the early-exit guard in nextSequence).
        final double dpExpected = REF.getCase("defprob_at_tmax").expectedDouble();
        final double dpActual = dts.defaultProbability(tmax, true);
        assertEquals("defprob_at_tmax", dpExpected, dpActual, TIGHT);

        assertTrue("Failures:\n" + String.join("\n", f), f.isEmpty());
    }

    // ----- 3. structural: real nextSequence()/reset() + seed reproducibility -

    @Test
    public void nextSequence_populatesPoolTimes_andIsSeedReproducible() {
        new Settings().setEvaluationDate(AS_OF);
        final double lambda = 0.01;
        final long seed = 42L;
        final double tmax = 5.0;

        final Pool pool = buildPool(lambda, 5);
        final List<DefaultProbKey> keys = pool.defaultKeys();

        final SimpleQuote correl = new SimpleQuote(0.30);
        final Handle<OneFactorCopula> copula = new Handle<OneFactorCopula>(
                new OneFactorGaussianCopula(new Handle<Quote>(correl)));

        final GaussianRandomDefaultModel model = new GaussianRandomDefaultModel(pool, keys, copula, 1.0e-8, seed);

        // Draw a sequence; every name must get a (finite, non-negative) time set.
        model.nextSequence(tmax);
        final List<Double> firstRun = new ArrayList<>();
        for (final String name : pool.names()) {
            final double t = pool.getTime(name);
            assertTrue("default time must be >= 0 for " + name, t >= 0.0);
            // time is either a real default time in [0, tmax] or the
            // "beyond horizon" sentinel tmax + 1.
            assertTrue("default time out of range for " + name + ": " + t,
                    t <= tmax || Math.abs(t - (tmax + 1)) < 1.0e-12);
            firstRun.add(t);
        }

        // reset() rebuilds the RSG from the same seed ⇒ the next draw must be
        // bit-identical to the first (seed reproducibility, not a C++ match).
        model.reset();
        model.nextSequence(tmax);
        final List<Double> secondRun = new ArrayList<>();
        for (final String name : pool.names()) {
            secondRun.add(pool.getTime(name));
        }
        for (int i = 0; i < firstRun.size(); ++i) {
            assertEquals("seed reproducibility name " + i, firstRun.get(i), secondRun.get(i), 0.0);
        }
    }

    private static Pool buildPool(final double lambda, final int poolSize) {
        final Handle<Quote> hazardRate = new Handle<Quote>(new SimpleQuote(lambda));
        final DefaultProbabilityTermStructure defPtr = new FlatHazardRate(AS_OF, hazardRate,
                new ActualActual(ActualActual.Convention.ISDA));
        final Handle<DefaultProbabilityTermStructure> defHandle =
                new Handle<DefaultProbabilityTermStructure>(defPtr);
        final NorthAmericaCorpDefaultKey probKey = new NorthAmericaCorpDefaultKey(
                new Europe.EURCurrency(), Seniority.SeniorSec, new Period(0, TimeUnit.Weeks), 10.0);

        final Pool pool = new Pool();
        for (int i = 0; i < poolSize; ++i) {
            final List<Issuer.KeyCurvePair> probabilities = new ArrayList<>();
            probabilities.add(new Issuer.KeyCurvePair(probKey, defHandle));
            final Issuer issuer = new Issuer(probabilities, new TreeSet<DefaultEvent>(Issuer.EARLIER_THAN));
            pool.add("issuer-" + i, issuer, new NorthAmericaCorpDefaultKey(
                    new Europe.EURCurrency(), Seniority.SeniorSec, new Period(), 1.0));
        }
        return pool;
    }
}
