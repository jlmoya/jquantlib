/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation of BaseCorrelationLossModel (vanilla GaussianLHPFlatBCLM)
 against C++ QuantLib v1.42.1 reference values produced by
 base_correlation_loss_model_probe.

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
import java.util.List;
import java.util.TreeSet;

import org.jquantlib.Settings;
import org.jquantlib.currencies.Europe;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.credit.BaseCorrelationLossModel;
import org.jquantlib.experimental.credit.BaseCorrelationTermStructure;
import org.jquantlib.experimental.credit.BilinearBaseCorrelationTermStructure;
import org.jquantlib.experimental.credit.Basket;
import org.jquantlib.experimental.credit.DefaultEvent;
import org.jquantlib.experimental.credit.Issuer;
import org.jquantlib.experimental.credit.NorthAmericaCorpDefaultKey;
import org.jquantlib.experimental.credit.Pool;
import org.jquantlib.experimental.credit.Seniority;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.credit.FlatHazardRate;
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
 * Cross-validation of {@link BaseCorrelationLossModel} (the vanilla
 * {@code GaussianLHPFlatBCLM = BaseCorrelationLossModel<GaussianLHPLossModel,
 * BilinearInterpolation>}) against C++ v1.42.1 references in
 * {@code migration-harness/references/credit/base_correlation_loss_model.json}.
 *
 * <p>Tolerance tier: TIGHT. The expected tranche loss is a deterministic
 * composition of (a) bilinear interpolation off the base-correlation surface
 * and (b) the analytic GaussianLHP expected-tranche-loss kernel on two equity
 * sub-baskets. Both are shared in structure with C++; agreement is expected to
 * ~1e-9 relative (a few ULPs from the bivariate normal / inverse normal in the
 * LHP kernel).
 */
public class BaseCorrelationLossModelTest {

    private static final String REF_GROUP = "credit/base_correlation_loss_model";
    private static final ReferenceReader REF = ReferenceReader.load(REF_GROUP);

    private static final double TIGHT_ABS = 1.0e-9;
    private static final double TIGHT_REL = 1.0e-9;

    private static final Date AS_OF = new Date(15, Month.June, 2010);
    private static final Calendar CAL = new NullCalendar();
    private static final BusinessDayConvention BDC = BusinessDayConvention.ModifiedFollowing;
    private static final DayCounter DC = new Actual360();
    private static final int SETTLEMENT_DAYS = 0;
    private static final int POOL_SIZE = 20;
    private static final double LAMBDA = 0.01;
    private static final double RECOVERY = 0.4;

    // Surface grid (matches the C++ probe).
    private static final List<Period> TENORS = List.of(
            new Period(12, TimeUnit.Months),
            new Period(36, TimeUnit.Months),
            new Period(60, TimeUnit.Months),
            new Period(84, TimeUnit.Months));
    private static final List<Double> LOSSES = List.of(0.03, 0.10, 0.20);
    private static final double[][] CORRELS = {
            {0.20, 0.22, 0.25, 0.27},
            {0.35, 0.37, 0.40, 0.42},
            {0.55, 0.57, 0.60, 0.63}
    };

    private static List<List<Handle<Quote>>> handles() {
        final List<List<Handle<Quote>>> out = new ArrayList<>();
        for (final double[] row : CORRELS) {
            final List<Handle<Quote>> r = new ArrayList<>();
            for (final double v : row) {
                r.add(new Handle<Quote>(new SimpleQuote(v)));
            }
            out.add(r);
        }
        return out;
    }

    private static Pool buildPool(final List<String> names, final List<Double> nominals) {
        new Settings().setEvaluationDate(AS_OF);
        final Handle<Quote> hazardRate = new Handle<Quote>(new SimpleQuote(LAMBDA));
        final DefaultProbabilityTermStructure defPtr = new FlatHazardRate(AS_OF, hazardRate,
                new ActualActual(ActualActual.Convention.ISDA));
        final Handle<DefaultProbabilityTermStructure> defHandle =
                new Handle<DefaultProbabilityTermStructure>(defPtr);

        // probability key (matches the probe): NA-corp EUR/SeniorSec, grace 0 weeks, amount 10.
        final NorthAmericaCorpDefaultKey probKey = new NorthAmericaCorpDefaultKey(
                new Europe.EURCurrency(), Seniority.SeniorSec, new Period(0, TimeUnit.Weeks), 10.0);

        final Pool pool = new Pool();
        for (int i = 0; i < POOL_SIZE; ++i) {
            names.add("issuer-" + i);
            nominals.add(100.0);
            final List<Issuer.KeyCurvePair> probabilities = new ArrayList<>();
            probabilities.add(new Issuer.KeyCurvePair(probKey, defHandle));
            final Issuer issuer = new Issuer(probabilities, new TreeSet<DefaultEvent>(Issuer.EARLIER_THAN));
            // contract trigger (matches the probe): NA-corp EUR/SeniorSec, default grace, amount 1.
            pool.add(names.get(i), issuer, new NorthAmericaCorpDefaultKey(
                    new Europe.EURCurrency(), Seniority.SeniorSec, new Period(), 1.0));
        }
        return pool;
    }

    private void checkTranche(final List<String> failures, final String prefix, final double attach,
            final double detach) {
        new Settings().setEvaluationDate(AS_OF);

        final BaseCorrelationTermStructure surface = new BilinearBaseCorrelationTermStructure(
                SETTLEMENT_DAYS, CAL, BDC, TENORS, LOSSES, handles(), DC);
        final Handle<BaseCorrelationTermStructure> surfaceHandle =
                new Handle<BaseCorrelationTermStructure>(surface);

        final List<String> names = new ArrayList<>();
        final List<Double> nominals = new ArrayList<>();
        final Pool pool = buildPool(names, nominals);

        final Basket basket = new Basket(AS_OF, names, nominals, pool, attach, detach);

        final List<Double> recoveries = new ArrayList<>();
        for (int i = 0; i < POOL_SIZE; ++i) {
            recoveries.add(RECOVERY);
        }
        final BaseCorrelationLossModel model = new BaseCorrelationLossModel(surfaceHandle, recoveries);
        basket.setLossModel(model);

        final String[][] dateTags = {
                {"1y", "12"}, {"3y", "36"}, {"5y", "60"}
        };
        for (final String[] tag : dateTags) {
            final int months = Integer.parseInt(tag[1]);
            final Date d = AS_OF.add(new Period(months, TimeUnit.Months));
            final double actual = basket.expectedTrancheLoss(d);
            final String caseName = prefix + "_etl_" + tag[0];
            final double expected = REF.getCase(caseName).expectedDouble();
            final double tol = Math.max(TIGHT_ABS, TIGHT_REL * Math.abs(expected));
            if (Math.abs(actual - expected) > tol) {
                failures.add(caseName + " expected=" + expected + " actual=" + actual
                        + " diff=" + Math.abs(actual - expected) + " tol=" + tol);
            }
        }
    }

    @Test
    public void bclm_equity_0_3() {
        final List<String> f = new ArrayList<>();
        checkTranche(f, "equity_0_3", 0.00, 0.03);
        assertTrue("Failures:\n" + String.join("\n", f), f.isEmpty());
    }

    @Test
    public void bclm_mezz_3_10() {
        final List<String> f = new ArrayList<>();
        checkTranche(f, "mezz_3_10", 0.03, 0.10);
        assertTrue("Failures:\n" + String.join("\n", f), f.isEmpty());
    }

    @Test
    public void bclm_senior_10_20() {
        final List<String> f = new ArrayList<>();
        checkTranche(f, "senior_10_20", 0.10, 0.20);
        assertTrue("Failures:\n" + String.join("\n", f), f.isEmpty());
    }
}
