/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation of GaussianLHPLossModel analytic kernels against
 C++ QuantLib v1.42.1 reference values produced by gaussian_lhp_loss_probe.

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

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.experimental.credit.GaussianLHPLossModel;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Cross-validation of {@link GaussianLHPLossModel} static analytic kernels
 * against C++ v1.42.1 references in
 * {@code migration-harness/references/credit-loss-models/gaussian_lhp_loss.json}.
 *
 * <p>Tolerance tier: TIGHT (1e-9 abs / 1e-12 rel near zero) — analytic
 * formulas with bivariate normal {@code BivariateCumulativeNormalDistribution}
 * (Drezner-style West 2004 algorithm); the bivariate normal in JQuantLib
 * uses a TabulatedGaussLegendre 20-pt rule and matches QuantLib to about
 * machine precision on these inputs. Bivariate-normal tail evaluations
 * may carry a few ULPs of accumulated error.
 */
public class GaussianLHPLossModelTest {

    private static final String TEST_GROUP = "credit-loss-models/gaussian_lhp_loss";
    private static final ReferenceReader REF = ReferenceReader.load(TEST_GROUP);

    private static final double TIGHT = 1.0e-9;

    @Test
    public void expectedTrancheLossKernel_allCases() {
        final String[] cases = {
                "etl_equity_5pct_prob_20pct_corr",
                "etl_mezz_5pct_prob_20pct_corr",
                "etl_senior_5pct_prob_20pct_corr",
                "etl_mezz_10pct_prob_50pct_corr",
                "etl_attach_ge_detach_zero",
                "etl_prob_zero",
                "etl_low_correl"
        };
        final List<String> failures = new ArrayList<>();
        for (final String name : cases) {
            final Case c = REF.getCase(name);
            final JSONObject in = c.inputs();
            final double remNot = in.getDouble("remNot");
            final double prob = in.getDouble("prob");
            final double rr = in.getDouble("recovery");
            final double attach = in.getDouble("attach");
            final double detach = in.getDouble("detach");
            final double rho = in.getDouble("correl");
            final double expected = c.expectedDouble();
            final double actual = GaussianLHPLossModel.expectedTrancheLossKernel(
                    remNot, prob, rr, attach, detach, rho);
            if (Math.abs(actual - expected) > Math.max(TIGHT, TIGHT * Math.abs(expected))) {
                failures.add(name + " expected=" + expected + " actual=" + actual
                        + " diff=" + Math.abs(actual - expected));
            }
        }
        assertTrue("Failures:\n" + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void percentilePortfolioLossFractionKernel_allCases() {
        final String[] cases = {
                "pctl_5pct_prob_95th_q_20pct_corr",
                "pctl_5pct_prob_99th_q_20pct_corr",
                "pctl_10pct_prob_50th_q_50pct_corr",
                "pctl_perctl_zero"
        };
        final List<String> failures = new ArrayList<>();
        for (final String name : cases) {
            final Case c = REF.getCase(name);
            final JSONObject in = c.inputs();
            final double rr = in.getDouble("recovery");
            final double avgProb = in.getDouble("avgProb");
            final double q = in.getDouble("perctl");
            final double rho = in.getDouble("correl");
            final double expected = c.expectedDouble();
            final double actual = GaussianLHPLossModel.percentilePortfolioLossFractionKernel(
                    rr, avgProb, q, rho);
            if (Math.abs(actual - expected) > Math.max(TIGHT, TIGHT * Math.abs(expected))) {
                failures.add(name + " expected=" + expected + " actual=" + actual
                        + " diff=" + Math.abs(actual - expected));
            }
        }
        assertTrue("Failures:\n" + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void probOverLossKernel_allCases() {
        final String[] cases = {
                "pol_avg5pct_pf5pct_20pct_corr",
                "pol_avg5pct_pf10pct_20pct_corr",
                "pol_avg10pct_pf20pct_50pct_corr"
        };
        final List<String> failures = new ArrayList<>();
        for (final String name : cases) {
            final Case c = REF.getCase(name);
            final JSONObject in = c.inputs();
            final double rr = in.getDouble("recovery");
            final double avgProb = in.getDouble("avgProb");
            final double pf = in.getDouble("portfFract");
            final double rho = in.getDouble("correl");
            final double expected = c.expectedDouble();
            final double actual = GaussianLHPLossModel.probOverLossKernel(
                    rr, avgProb, pf, rho);
            if (Math.abs(actual - expected) > Math.max(TIGHT, TIGHT * Math.abs(expected))) {
                failures.add(name + " expected=" + expected + " actual=" + actual
                        + " diff=" + Math.abs(actual - expected));
            }
        }
        assertTrue("Failures:\n" + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void instanceConstruction_correlationCached() {
        final List<Double> recoveries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            recoveries.add(0.40);
        }
        final GaussianLHPLossModel m = new GaussianLHPLossModel(0.20, recoveries);
        // No basket attached → expectedTrancheLossInstance still works as a
        // plain analytic kernel:
        final double etl = m.expectedTrancheLossInstance(100.0, 0.05, 0.40, 0.0, 0.10);
        // Compare against the reference for etl_equity_5pct_prob_20pct_corr
        final Case c = REF.getCase("etl_equity_5pct_prob_20pct_corr");
        final double expected = c.expectedDouble();
        assertTrue("instance ETL: " + etl + " vs ref " + expected,
                Math.abs(etl - expected) < TIGHT);
    }
}
