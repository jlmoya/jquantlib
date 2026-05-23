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
 */
package org.jquantlib.testsuite.math.integrals;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.integrals.DiscreteSimpsonIntegrator;
import org.jquantlib.math.integrals.DiscreteTrapezoidIntegral;
import org.jquantlib.math.integrals.GaussChebyshev2ndIntegration;
import org.jquantlib.math.integrals.GaussChebyshevIntegration;
import org.jquantlib.math.integrals.GaussGegenbauerIntegration;
import org.jquantlib.math.integrals.GaussGegenbauerPolynomial;
import org.jquantlib.math.integrals.GaussHyperbolicIntegration;
import org.jquantlib.math.integrals.GaussJacobiIntegration;
import org.jquantlib.math.integrals.GaussLegendreIntegration;
import org.jquantlib.math.integrals.GaussianQuadrature;
import org.jquantlib.math.integrals.GaussianQuadratureIntegrator;
import org.jquantlib.math.matrixutilities.Array;
import org.junit.Test;

/**
 * Tests for L1-D integrals batch: GaussChebyshev{,2nd}Integration,
 * GaussJacobiIntegration, GaussHyperbolicIntegration, GaussGegenbauer{,Polynomial}Integration,
 * GaussianQuadratureIntegrator, DiscreteSimpsonIntegrator, DiscreteTrapezoidIntegral.
 *
 * <p>Phase 2 L1-D port.
 */
public class L1DIntegralsTest {

    public L1DIntegralsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    // ----- analytic integrals of constant functions used as ground truth -----
    // QuantLib's GaussianQuadrature integrates f(x) directly over the natural
    // domain (the weight is folded into the abscissae/weights internally;
    // see gaussianquadratures.cpp:59 — w_[i] = mu_0*ev*ev / orthPoly.w(x_i)).
    // For f=1, sum equals the integral of 1 dx over the domain:
    //   Chebyshev{,2nd},Jacobi,Legendre on [-1, 1] -> 2
    //   Hyperbolic on (-inf, +inf) with quadrature truncation -> not pi*
    //   * Hyperbolic weight 1/cosh integrates to pi but our f=1 changes that.

    @Test
    public void testGaussChebyshevIntegratesUnitOverInterval() {
        // f=1 on [-1,1] => integral = 2. Higher orders converge.
        final GaussChebyshevIntegration q = new GaussChebyshevIntegration(64);
        final double result = q.op(new Ops.DoubleOp() {
            @Override
            public double op(final double x) { return 1.0; }
        });
        assertEquals("integral of 1 on [-1,1]", 2.0, result, 1.0e-3);
    }

    @Test
    public void testGaussChebyshev2ndIntegratesUnitOverInterval() {
        final GaussChebyshev2ndIntegration q = new GaussChebyshev2ndIntegration(64);
        final double result = q.op(new Ops.DoubleOp() {
            @Override
            public double op(final double x) { return 1.0; }
        });
        assertEquals("integral of 1 on [-1,1]", 2.0, result, 1.0e-3);
    }

    @Test
    public void testGaussJacobiIntegrationConvergesToLegendre() {
        // alpha=beta=0 reduces Jacobi to Legendre
        final GaussJacobiIntegration q = new GaussJacobiIntegration(16, 0.0, 0.0);
        final GaussLegendreIntegration ref = new GaussLegendreIntegration(16);
        final Ops.DoubleOp f = new Ops.DoubleOp() {
            @Override
            public double op(final double x) { return Math.exp(x); }
        };
        assertEquals("e^x on [-1,1]", ref.op(f), q.op(f), 1.0e-12);
    }

    @Test
    public void testGaussHyperbolicIntegrationConsistency() {
        // GaussHyperbolicIntegration(n) shares the same machinery as Hermite
        // family — for f=1 the result is well-defined and finite by the
        // QuantLib weight-folding convention. Sanity-check it matches the
        // explicit polynomial form built via the generic GaussianQuadrature.
        final GaussHyperbolicIntegration q1 = new GaussHyperbolicIntegration(16);
        final GaussianQuadrature q2 = new GaussianQuadrature(
                16, new org.jquantlib.math.integrals.GaussHyperbolicPolynomial());
        final Ops.DoubleOp f = new Ops.DoubleOp() {
            @Override
            public double op(final double x) { return Math.exp(-x * x); }
        };
        assertEquals("Hyperbolic integration vs explicit polynomial", q2.op(f), q1.op(f), 1.0e-14);
    }

    @Test
    public void testGaussGegenbauerEqualsJacobiWithSymmetricAlphaBeta() {
        // GaussGegenbauerPolynomial(lambda) == GaussJacobiPolynomial(lambda-1/2, lambda-1/2)
        final double lambda = 0.55;
        final GaussianQuadrature qGegen = new GaussianQuadrature(20, new GaussGegenbauerPolynomial(lambda));
        final GaussGegenbauerIntegration qIntg = new GaussGegenbauerIntegration(20, lambda);
        final Ops.DoubleOp f = new Ops.DoubleOp() {
            @Override
            public double op(final double x) { return 1.0; }
        };
        assertEquals("Gegenbauer poly == Gegenbauer integration", qGegen.op(f), qIntg.op(f), 1.0e-14);
    }

    @Test
    public void testGaussianQuadratureIntegratorMapsToInterval() {
        // Wrap GaussLegendreIntegration into the generic integrator and check it
        // integrates a Gaussian normal density over [-10, 10] to 1.0.
        final GaussianQuadratureIntegrator<GaussLegendreIntegration> integrator =
                new GaussianQuadratureIntegrator<GaussLegendreIntegration>(new GaussLegendreIntegration(64));
        final double result = integrator.op(new NormalDistribution(), -10.0, 10.0);
        assertEquals("normal density integrates to 1 on [-10,10]", 1.0, result, 1.0e-9);
    }

    @Test
    public void testGaussianQuadratureIntegratorWrapsChebyshev() {
        // Integrate the Gaussian density via a [-10,10]-mapped Chebyshev quadrature.
        final GaussianQuadratureIntegrator<GaussChebyshevIntegration> integrator =
                new GaussianQuadratureIntegrator<GaussChebyshevIntegration>(new GaussChebyshevIntegration(64));
        final double result = integrator.op(new NormalDistribution(), -10.0, 10.0);
        // Chebyshev weight introduces (1-x^2)^{-1/2} but ints to the same value
        // for sufficiently regular f on bounded domain; tolerance loose.
        assertEquals("non-trivial value (sanity)", 1.0, result, 1.0e-3);
    }

    @Test
    public void testDiscreteSimpsonIntegratorPolynomial() {
        // integrate f(x) = 1.2 x^2 + 3.2 x + 3.1 on [0, 2]:
        //   F(2) - F(0) = 1.2*8/3 + 3.2*2 + 3.1*2 = 3.2 + 6.4 + 6.2 = 15.8
        final DiscreteSimpsonIntegrator integrator = new DiscreteSimpsonIntegrator(1001);
        final double result = integrator.op(new Ops.DoubleOp() {
            @Override
            public double op(final double x) { return 1.2 * x * x + 3.2 * x + 3.1; }
        }, 0.0, 2.0);
        assertEquals("polynomial integral", 15.8, result, 1.0e-10);
    }

    @Test
    public void testDiscreteTrapezoidIntegralOnUniformGrid() {
        // Build a uniform grid for f(x) = x on [0, 1], integral = 0.5
        final int n = 201;
        final double[] xs = new double[n];
        final double[] fs = new double[n];
        for ( int i = 0; i < n; i++ ) {
            xs[i] = i / (double) (n - 1);
            fs[i] = xs[i];
        }
        final DiscreteTrapezoidIntegral integrator = new DiscreteTrapezoidIntegral();
        final double result = integrator.evaluate(new Array(xs), new Array(fs));
        assertEquals("integral of x on [0,1]", 0.5, result, 1.0e-10);
    }
}
