/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation of the CDO instrument (Hull-White probability bucketing)
 against C++ QuantLib v1.42.1 reference values produced by cdo_probe.

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

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.credit.CDO;
import org.jquantlib.experimental.credit.OneFactorCopula;
import org.jquantlib.experimental.credit.OneFactorGaussianCopula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Cross-validation of {@link CDO} (the Hull-White probability-bucketing CDO
 * instrument, distinct from {@code SyntheticCdo}) against C++ v1.42.1
 * references in
 * {@code migration-harness/references/credit/cdo.json}.
 *
 * <p>Tolerance tier: TIGHT. The CDO premium/protection/NPV are fully
 * deterministic (no Monte-Carlo) — they are produced by the same
 * probability-bucketing convolution over a one-factor Gaussian copula in both
 * C++ and Java. The numeric chain (FlatForward discounts, FlatHazardRate
 * default probabilities, OneFactorGaussianCopula Euler integration over the
 * systemic factor, LossDistBucketing) is shared in structure, so agreement is
 * expected to ~1e-9 relative. A handful of bivariate/normal evaluations carry
 * a few ULPs, hence rel 1e-9 rather than 1e-12.
 */
public class CDOTest {

    private static final String REF_GROUP = "credit/cdo";
    private static final ReferenceReader REF = ReferenceReader.load(REF_GROUP);

    /** TIGHT: abs floor 1e-9, rel 1e-9 (deterministic bucketing convolution). */
    private static final double TIGHT_ABS = 1.0e-9;
    private static final double TIGHT_REL = 1.0e-9;

    private static final Date AS_OF = new Date(31, Month.August, 2006);

    private CDO buildCdo(final double attachment, final double detachment, final double correlation,
            final boolean protectionSeller, final int poolSize, final double lambda, final double rate,
            final double recovery, final double premium, final double upfront, final int nBuckets) {
        new Settings().setEvaluationDate(AS_OF);
        final DayCounter daycount = new Actual360();

        final YieldTermStructure yieldPtr = new FlatForward(AS_OF, rate, daycount, Compounding.Continuous,
                Frequency.Annual);
        final Handle<YieldTermStructure> yieldHandle = new Handle<YieldTermStructure>(yieldPtr);

        final Handle<Quote> hazardRate = new Handle<Quote>(new SimpleQuote(lambda));
        final DefaultProbabilityTermStructure defPtr = new FlatHazardRate(AS_OF, hazardRate,
                new ActualActual(ActualActual.Convention.ISDA));

        final List<Handle<DefaultProbabilityTermStructure>> basket = new ArrayList<>();
        final List<Double> nominals = new ArrayList<>();
        for (int i = 0; i < poolSize; ++i) {
            basket.add(new Handle<DefaultProbabilityTermStructure>(defPtr));
            nominals.add(100.0);
        }

        final SimpleQuote correl = new SimpleQuote(correlation);
        final Handle<OneFactorCopula> copula = new Handle<OneFactorCopula>(
                new OneFactorGaussianCopula(new Handle<Quote>(correl)));

        final Schedule schedule = new MakeSchedule()
                .from(new Date(1, Month.September, 2006))
                .to(new Date(1, Month.September, 2011))
                .withTenor(new Period(3, TimeUnit.Months))
                .withCalendar(new Target())
                .schedule();

        return new CDO(attachment, detachment, nominals, basket, copula, protectionSeller, schedule, premium,
                daycount, recovery, upfront, yieldHandle, nBuckets, new Period(1, TimeUnit.Years));
    }

    private void assertTight(final List<String> failures, final String caseName, final double actual) {
        final double expected = REF.getCase(caseName).expectedDouble();
        final double tol = Math.max(TIGHT_ABS, TIGHT_REL * Math.abs(expected));
        if (Math.abs(actual - expected) > tol) {
            failures.add(caseName + " expected=" + expected + " actual=" + actual
                    + " diff=" + Math.abs(actual - expected) + " tol=" + tol);
        }
    }

    private void checkConfig(final List<String> failures, final String prefix, final double attachment,
            final double detachment, final double correlation, final boolean protectionSeller) {
        final CDO cdo = buildCdo(attachment, detachment, correlation, protectionSeller, 10, 0.01, 0.05, 0.4,
                0.02, 0.0, 100);
        assertTight(failures, prefix + "_premiumValue", cdo.premiumValue());
        assertTight(failures, prefix + "_protectionValue", cdo.protectionValue());
        assertTight(failures, prefix + "_fairPremium", cdo.fairPremium());
        assertTight(failures, prefix + "_NPV", cdo.NPV());
        // error count and structural quantities are EXACT.
        assertEquals(prefix + "_error", (long) REF.getCase(prefix + "_error").expectedDouble(), cdo.error());
        assertTight(failures, prefix + "_nominal", cdo.nominal());
        // NOTE: cdo.lgd() is deliberately NOT cross-validated against the C++ probe.
        // The C++ CDO (v1.42.1 cdo.hpp:173 / cdo.cpp:77) declares `Real lgd_;` with
        // no initializer and accumulates `lgd_ += lgds_[i]` without first zeroing it,
        // so lgd() reads an uninitialised member: across sequentially-constructed CDO
        // objects in one process the reference values come out as 600, 1200, 1800,
        // 2400, 3000 (i.e. 600 * cumulative-object-count) rather than the correct
        // 600 = nominal * (1 - recovery). lgd_ is never used in pricing, so this does
        // not affect any priced value. JQuantLib initialises lgd_ = 0 per instance and
        // therefore always returns the correct 600; we assert that analytic value
        // directly instead of the C++ garbage.
        final double expectedLgd = 10 * 100.0 * (1.0 - 0.4); // 600
        assertEquals(prefix + "_lgd (analytic; C++ value is an uninit-member bug)",
                expectedLgd, cdo.lgd(), 1.0e-9);
    }

    @Test
    public void cdo_equity_0_3_corr10() {
        final List<String> f = new ArrayList<>();
        checkConfig(f, "equity_0_3_corr10", 0.00, 0.03, 0.1, true);
        assertTrue("Failures:\n" + String.join("\n", f), f.isEmpty());
    }

    @Test
    public void cdo_equity_0_3_corr30() {
        final List<String> f = new ArrayList<>();
        checkConfig(f, "equity_0_3_corr30", 0.00, 0.03, 0.3, true);
        assertTrue("Failures:\n" + String.join("\n", f), f.isEmpty());
    }

    @Test
    public void cdo_mezz_3_6_corr30() {
        final List<String> f = new ArrayList<>();
        checkConfig(f, "mezz_3_6_corr30", 0.03, 0.06, 0.3, true);
        assertTrue("Failures:\n" + String.join("\n", f), f.isEmpty());
    }

    @Test
    public void cdo_senior_10_100_corr30() {
        final List<String> f = new ArrayList<>();
        checkConfig(f, "senior_10_100_corr30", 0.10, 1.00, 0.3, true);
        assertTrue("Failures:\n" + String.join("\n", f), f.isEmpty());
    }

    @Test
    public void cdo_equity_0_3_corr30_buyer() {
        final List<String> f = new ArrayList<>();
        checkConfig(f, "equity_0_3_corr30_buyer", 0.00, 0.03, 0.3, false);
        assertTrue("Failures:\n" + String.join("\n", f), f.isEmpty());
    }
}
