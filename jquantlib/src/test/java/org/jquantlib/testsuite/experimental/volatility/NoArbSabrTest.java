/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.experimental.volatility;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.experimental.volatility.NoArbSabrModel;
import org.jquantlib.experimental.volatility.NoArbSabrSmileSection;
import org.jquantlib.instruments.Option;
import org.jquantlib.termstructures.volatilities.SabrSmileSection;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/noarbsabr.cpp (Phase 5e.5b-CFC-d-200).
 *
 * <p>Direct-named equivalent of the C++ suite. Both cases are now active:
 * <ol>
 *   <li>{@code testAbsorptionMatrix} — exercises the package-visible
 *       {@code D0Interpolator} which interpolates the 1.2M-entry
 *       absorption-count table ({@code NoArbSabrAbsorptions}). Verifies the
 *       16 corner/interior reference cells from C++.</li>
 *   <li>{@code testConsistencyWithHagan} — compares {@code NoArbSabrSmileSection}
 *       to {@code SabrSmileSection} across vanilla price, digital price, and
 *       density, mirroring the C++ tolerances (1e-5 / 1e-3 / 1.0).</li>
 * </ol>
 */
public class NoArbSabrTest {

    public NoArbSabrTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Helper mirroring {@code checkD0} from C++ noarbsabr.cpp lines 32-46.
     * Builds a {@link NoArbSabrModel} (which constructs and invokes the
     * package-private {@code D0Interpolator}) and verifies the resulting
     * absorption probability times {@code NSIM} matches the expected count
     * to within 0.1 absolute (C++ test tolerance verbatim).
     */
    private static void checkD0(final double sigmaI, final double beta,
            final double rho, final double nu, final double tau,
            final long expectedAbsorptions) {
        final double forward = 0.03; // does not matter in the end
        final double alpha = sigmaI / Math.pow(forward, beta - 1.0);

        // We exercise D0Interpolator via NoArbSabrModel.absorptionProbability().
        // Constructing the full model is heavier (it also runs the Brent
        // forward-error fit) but matches the C++ test exactly.
        final NoArbSabrModel m = new NoArbSabrModel(tau, forward, alpha, beta, nu, rho);
        final double d = m.absorptionProbability();
        final double actual = d * NoArbSabrModel.Constants.NSIM;
        if (Math.abs(actual - expectedAbsorptions) > 0.1) {
            fail("failed to reproduce number of absorptions at sigmaI=" + sigmaI
                    + ", beta=" + beta + ", rho=" + rho + ", nu=" + nu
                    + " tau=" + tau + ": D0Interpolator says " + actual
                    + " while the reference value is " + expectedAbsorptions);
        }
    }

    @Test
    public void testAbsorptionMatrix() {
        QL.info("Testing no-arbitrage Sabr absorption matrix...");

        // (sigmaI, beta, rho, nu, tau, absorptions) — C++ noarbsabr.cpp lines 56-71
        checkD0(1.0,  0.01,  0.75, 0.1,  0.25, 60342L);  // upper-left corner
        checkD0(0.8,  0.01,  0.75, 0.1,  0.25, 12148L);
        checkD0(0.05, 0.01,  0.75, 0.1,  0.25, 0L);
        checkD0(1.0,  0.01,  0.75, 0.1, 10.0,  1890509L);
        checkD0(0.8,  0.01,  0.75, 0.1, 10.0,  1740233L);
        checkD0(0.05, 0.01,  0.75, 0.1, 10.0,  0L);
        checkD0(1.0,  0.01,  0.75, 0.1, 30.0,  2174176L);
        checkD0(0.8,  0.01,  0.75, 0.1, 30.0,  2090672L);
        checkD0(0.05, 0.01,  0.75, 0.1, 30.0,  31L);
        checkD0(0.35, 0.10, -0.75, 0.1,  0.25, 0L);
        checkD0(0.35, 0.10, -0.75, 0.1, 14.75, 1087841L);
        checkD0(0.35, 0.10, -0.75, 0.1, 30.0,  1406569L);
        checkD0(0.24, 0.90,  0.50, 0.8,  1.25, 27L);
        checkD0(0.24, 0.90,  0.50, 0.8, 25.75, 167541L);
        checkD0(0.05, 0.90, -0.75, 0.8,  2.0,  17L);
        checkD0(0.05, 0.90, -0.75, 0.8, 30.0,  42100L);  // lower-right corner
    }

    @Test
    public void testConsistencyWithHagan() {
        QL.info("Testing consistency of noarb-sabr with Hagan et al (2002)...");

        // parameters taken from Doust's paper, figure 3 — C++ noarbsabr.cpp 81-86
        final double tau = 1.0;
        final double beta = 0.5;
        final double alpha = 0.026;
        final double rho = -0.1;
        final double nu = 0.4;
        final double f = 0.0488;

        final double[] params = { alpha, beta, nu, rho };
        final SabrSmileSection sabr = new SabrSmileSection(tau, f, params);
        final NoArbSabrSmileSection noarbsabr = new NoArbSabrSmileSection(tau, f, params);

        final double absProb = noarbsabr.model().absorptionProbability();
        assertTrue("absorption probability should be close to zero, but is "
                + absProb, absProb >= 0.0 && absProb <= 1.0e-10);

        double strike = 0.0001;
        while (strike < 0.15) {
            // vanilla call prices
            final double sabrPrice = sabr.optionPrice(strike, Option.Type.Call, 1.0);
            final double noarbsabrPrice = noarbsabr.optionPrice(strike, Option.Type.Call, 1.0);
            if (Math.abs(sabrPrice - noarbsabrPrice) > 1.0e-5) {
                fail("inconsistent Hagan price (" + sabrPrice
                        + ") and noarb-sabr price (" + noarbsabrPrice
                        + ") at strike " + strike);
            }
            // digitals
            final double sabrDigital = sabr.digitalOptionPrice(strike, Option.Type.Call, 1.0, 1.0e-5);
            final double noarbsabrDigital = noarbsabr.digitalOptionPrice(strike, Option.Type.Call, 1.0, 1.0e-5);
            if (Math.abs(sabrDigital - noarbsabrDigital) > 1.0e-3) {
                fail("inconsistent Hagan digital (" + sabrDigital
                        + ") and noarb-sabr digital (" + noarbsabrDigital
                        + ") at strike " + strike);
            }
            // density — SmileSection::density via centred FD on digital
            // (matches C++ smilesection.cpp lines 99-105). NoArbSabrSmileSection
            // overrides density() to read from the model directly.
            final double gap = 1.0e-4;
            final double m = -sabr.shift();
            final double kl = Math.max(strike - 0.5 * gap, m);
            final double kr = kl + gap;
            final double sabrDensity =
                    (sabr.digitalOptionPrice(kl, Option.Type.Call, 1.0, gap)
                            - sabr.digitalOptionPrice(kr, Option.Type.Call, 1.0, gap)) / gap;
            final double noarbsabrDensity = noarbsabr.density(strike, 1.0, gap);
            if (Math.abs(sabrDensity - noarbsabrDensity) > 1.0) {
                fail("inconsistent Hagan density (" + sabrDensity
                        + ") and noarb-sabr density (" + noarbsabrDensity
                        + ") at strike " + strike);
            }
            strike += 0.0001;
        }
    }
}
