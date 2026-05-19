/*
 Copyright (C) 2026 JQuantLib migration

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

/*
 Copyright (C) 2008 Roland Lichters
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.util.LazyObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for one-factor copula models.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::OneFactorCopula}
 * ({@code ql/experimental/credit/onefactorcopula.{hpp,cpp}}).
 *
 * <p>Reference: John Hull and Alan White, <i>The Perfect Copula</i>, 2006.
 *
 * <p>For each counterparty i, define
 * {@code Y_i = a_i M + sqrt(1 - a_i^2) Z_i} where M and Z_i are independent zero-mean unit-variance random variables
 * and {@code a_i in [-1, +1]}. Concrete subclasses (Gaussian, Student-t) supply the densities of M and Z and tabulate
 * the cumulative distribution of Y for inverse lookup.
 *
 * <p>The conditional default probability is
 * {@code F_Z((F_Y^{-1}(p) - a m) / sqrt(1 - a^2))}; integration over the M-density is performed by simple Euler steps
 * over the cached grid.
 *
 * <p>Phase 4m.5 — base only; {@code OneFactorGaussianCopula} and
 * {@code OneFactorStudentCopula} are subclasses (Gaussian land in this phase; Student deferred to 4m.6).
 */
public abstract class OneFactorCopula extends LazyObject {

    protected final Handle< Quote > correlation;
    /** Tabulated numerical solution of the cumulated distribution of Y. */
    protected final List< Double > y = new ArrayList<>();
    protected final List< Double > cumulativeY = new ArrayList<>();
    protected double max;
    protected int steps;
    protected double min;

    public OneFactorCopula(final Handle< Quote > correlation, final double maximum, final int integrationSteps,
            final double minimum) {
        this.correlation = correlation;
        this.max = maximum;
        this.steps = integrationSteps;
        this.min = minimum;
        QL.require(correlation.currentLink().value() >= -1 && correlation.currentLink().value() <= 1,
                "correlation out of range [-1, +1]");
        correlation.addObserver(this);
    }

    public OneFactorCopula(final Handle< Quote > correlation) {
        this(correlation, 5.0, 50, -5.0);
    }

    /**
     * Density of M. Concrete subclasses must override and ensure zero mean and unit variance.
     */
    public abstract double density(double m);

    /**
     * Cumulative distribution of Z. Concrete subclasses must override and ensure zero mean and unit variance.
     */
    public abstract double cumulativeZ(double z);

    /** Cumulative distribution of Y — interpolation on the tabulated values. */
    public double cumulativeY(final double yval) {
        calculate();
        QL.require(!y.isEmpty(), "cumulative Y not tabulated yet");
        if ( yval < y.get(0) ) {
            return cumulativeY.get(0);
        }
        for ( int i = 0; i < y.size(); i++ ) {
            if ( y.get(i) > yval ) {
                return ((y.get(i) - yval) * cumulativeY.get(i - 1) + (yval - y.get(i - 1)) * cumulativeY.get(i)) / (
                        y.get(i) - y.get(i - 1));
            }
        }
        return cumulativeY.get(cumulativeY.size() - 1);
    }

    /** Inverse cumulative distribution of Y — interpolation on the tabulated values. */
    public double inverseCumulativeY(final double x) {
        calculate();
        QL.require(!y.isEmpty(), "cumulative Y not tabulated yet");
        if ( x < cumulativeY.get(0) ) {
            return y.get(0);
        }
        for ( int i = 0; i < cumulativeY.size(); i++ ) {
            if ( cumulativeY.get(i) > x ) {
                return ((cumulativeY.get(i) - x) * y.get(i - 1) + (x - cumulativeY.get(i - 1)) * y.get(i)) / (
                        cumulativeY.get(i) - cumulativeY.get(i - 1));
            }
        }
        return y.get(y.size() - 1);
    }

    public double correlation() {
        calculate();
        return correlation.currentLink().value();
    }

    /** Conditional probability {@code F_Z((F_Y^{-1}(p) - a m) / sqrt(1 - a^2))}. */
    public double conditionalProbability(final double p, final double m) {
        calculate();
        if ( p < 1e-10 ) {
            return 0.0;
        }
        final double c = correlation.currentLink().value();
        final double res = cumulativeZ((inverseCumulativeY(p) - Math.sqrt(c) * m) / Math.sqrt(1.0 - c));
        QL.require(res >= 0 && res <= 1, "conditional probability " + res + " out of range");
        return res;
    }

    public List< Double > conditionalProbability(final List< Double > probabilities, final double m) {
        calculate();
        final List< Double > p = new ArrayList<>(probabilities.size());
        for ( final double prob : probabilities ) {
            p.add(conditionalProbability(prob, m));
        }
        return p;
    }

    /**
     * Integral over the density of M and the conditional probability related to p:
     * {@code int_-inf^inf rho(m) F_Z((F_Y^{-1}(p) - a m) / sqrt(1 - a^2)) dm}.
     */
    public double integral(final double p) {
        QL.require(p >= 0 && p <= 1, "probability p=" + p + " out of range [0,1]");
        calculate();
        double avg = 0.0;
        for ( int k = 0; k < steps(); k++ ) {
            final double pp = conditionalProbability(p, m(k));
            avg += pp * densitydm(k);
        }
        return avg;
    }

    public double integral(final ScalarF f, final List< Double > probabilities) {
        calculate();
        double avg = 0.0;
        for ( int i = 0; i < steps; i++ ) {
            final List< Double > conditional = conditionalProbability(probabilities, m(i));
            final double prob = f.evaluate(conditional);
            avg += prob * densitydm(i);
        }
        return avg;
    }

    public Distribution integral(final DistF f, final List< Double > nominals, final List< Double > probabilities) {
        calculate();
        final Distribution dist = new Distribution(f.buckets(), 0.0, f.maximum());
        for ( int i = 0; i < steps(); i++ ) {
            final List< Double > conditional = conditionalProbability(probabilities, m(i));
            final Distribution d = f.evaluate(nominals, conditional);
            for ( int j = 0; j < dist.size(); j++ ) {
                dist.addDensity(j, d.density(j) * densitydm(i));
            }
        }
        return dist;
    }

    /** Check moments (unit norm, zero mean, unit variance) of M, Z, Y densities. */
    public int checkMoments(final double tolerance) {
        calculate();
        double norm = 0;
        double mean = 0;
        double var = 0;
        for ( int i = 0; i < steps(); i++ ) {
            norm += densitydm(i);
            mean += m(i) * densitydm(i);
            var += Math.pow(m(i), 2) * densitydm(i);
        }
        QL.require(Math.abs(norm - 1.0) < tolerance, "norm out of tolerance range");
        QL.require(Math.abs(mean) < tolerance, "mean out of tolerance range");
        QL.require(Math.abs(var - 1.0) < tolerance, "variance out of tolerance range");

        final double zMin = -10;
        final double zMax = +10;
        final int zSteps = 200;
        norm = 0;
        mean = 0;
        var = 0;
        for ( int i = 1; i < zSteps; i++ ) {
            final double z1 = zMin + (zMax - zMin) / zSteps * (i - 1);
            final double z2 = zMin + (zMax - zMin) / zSteps * i;
            final double z = (z1 + z2) / 2;
            final double densitydz = cumulativeZ(z2) - cumulativeZ(z1);
            norm += densitydz;
            mean += z * densitydz;
            var += Math.pow(z, 2) * densitydz;
        }
        QL.require(Math.abs(norm - 1.0) < tolerance, "norm out of tolerance range");
        QL.require(Math.abs(mean) < tolerance, "mean out of tolerance range");
        QL.require(Math.abs(var - 1.0) < tolerance, "variance out of tolerance range");

        final double yMin = -10;
        final double yMax = +10;
        final int ySteps = 200;
        norm = 0;
        mean = 0;
        var = 0;
        for ( int i = 1; i < ySteps; i++ ) {
            final double y1 = yMin + (yMax - yMin) / ySteps * (i - 1);
            final double y2 = yMin + (yMax - yMin) / ySteps * i;
            final double yv = (y1 + y2) / 2;
            final double densitydy = cumulativeY(y2) - cumulativeY(y1);
            norm += densitydy;
            mean += yv * densitydy;
            var += yv * yv * densitydy;
        }
        QL.require(Math.abs(norm - 1.0) < tolerance, "norm out of tolerance range");
        QL.require(Math.abs(mean) < tolerance, "mean out of tolerance range");
        QL.require(Math.abs(var - 1.0) < tolerance, "variance out of tolerance range");

        return 0;
    }

    public int steps() {
        return steps;
    }

    public double dm(final int i) {
        return (max - min) / steps;
    }

    public double m(final int i) {
        QL.require(i < steps, "index out of range");
        return min + dm(i) * i + dm(i) / 2.0;
    }

    public double densitydm(final int i) {
        QL.require(i < steps, "index out of range");
        return density(m(i)) * dm(i);
    }

    /** No-op default; subclasses tabulate {@code y_} / {@code cumulativeY_}. */
    @Override
    protected void performCalculations() {
        // intentionally empty in the base — derived classes populate the tables.
    }

    /**
     * Integral over the density of M and a one-dimensional function {@code f} of conditional probabilities of an input
     * vector p. Mirrors the C++ {@code template<class F> integral(F&, vector<Real>&)}.
     */
    public interface ScalarF {
        double evaluate(List< Double > conditional);
    }

    /**
     * Integral over the density of M and a multi-dimensional (vector-valued) function {@code f} of conditional
     * probabilities related to the input vector p. Mirrors C++
     * {@code template<class F> integral(F&, vector<Real>&, vector<Real>&)}.
     */
    public interface DistF {
        Distribution evaluate(List< Double > nominals, List< Double > conditional);

        int buckets();

        double maximum();
    }
}
