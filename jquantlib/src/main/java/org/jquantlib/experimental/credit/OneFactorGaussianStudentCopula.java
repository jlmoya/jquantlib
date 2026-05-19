/*
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.CumulativeStudentDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.distributions.StudentDistribution;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;

/**
 * One-factor Gaussian-Student t-copula.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code QuantLib::OneFactorGaussianStudentCopula} ({@code ql/experimental/credit/onefactorstudentcopula.{hpp,cpp}}).
 *
 * <p>{@code Z} follows a Student-T with {@code nz} dof; {@code M} is
 * Gaussian. {@code Z} is rescaled to unit variance.
 *
 * <p>Phase 4m.6.
 */
public class OneFactorGaussianStudentCopula extends OneFactorCopula {

    private final NormalDistribution density_;            // density of M
    private final CumulativeStudentDistribution cumulative_;  // cumulated density of Z
    private final int nz_;
    private final double scaleZ_;

    public OneFactorGaussianStudentCopula(final Handle< Quote > correlation, final int nz, final double maximum,
            final int integrationSteps) {
        super(correlation, maximum, integrationSteps, -maximum);
        QL.require(nz > 2, "degrees of freedom must be > 2");
        this.density_ = new NormalDistribution();
        this.cumulative_ = new CumulativeStudentDistribution(nz);
        this.nz_ = nz;
        this.scaleZ_ = Math.sqrt((double) (nz - 2) / nz);
        calculate();
    }

    public OneFactorGaussianStudentCopula(final Handle< Quote > correlation, final int nz) {
        this(correlation, nz, 10.0, 200);
    }

    @Override
    public double density(final double m) {
        return density_.op(m);
    }

    @Override
    public double cumulativeZ(final double z) {
        return cumulative_.op(z / scaleZ_);
    }

    @Override
    protected void performCalculations() {
        y.clear();
        cumulativeY.clear();

        final double ymin = -10.0;
        final double ymax = 10.0;
        final int steps = 200;
        for ( int i = 0; i <= steps; ++i ) {
            final double yv = ymin + (ymax - ymin) * i / steps;
            final double c = cumulativeYintegral(yv);
            y.add(yv);
            cumulativeY.add(c);
        }
    }

    private double cumulativeYintegral(final double yv) {
        final double c = correlation.currentLink().value();

        if ( c == 0.0 ) {
            return new CumulativeStudentDistribution(nz_).op(yv / scaleZ_);
        }
        if ( c == 1.0 ) {
            return new CumulativeNormalDistribution().op(yv);
        }

        final StudentDistribution dz = new StudentDistribution(nz_);
        final NormalDistribution dm = new NormalDistribution();

        final double minimum = -10.0;
        final double maximum = +10.0;
        final int steps = 400;
        final double delta = (maximum - minimum) / steps;
        double cumulated = 0.0;

        if ( c < 0.5 ) {
            for ( double m = minimum + delta / 2; m < maximum; m += delta ) {
                final double zMax = (yv - Math.sqrt(c) * m) / Math.sqrt(1.0 - c);
                for ( double z = minimum + delta / 2; z < zMax; z += delta ) {
                    cumulated += dm.op(m) * dz.op(z / scaleZ_) / scaleZ_;
                }
            }
        } else {
            for ( double z = minimum + delta / 2; z < maximum; z += delta ) {
                final double mMax = (yv - Math.sqrt(1.0 - c) * z) / Math.sqrt(c);
                for ( double m = minimum + delta / 2; m < mMax; m += delta ) {
                    cumulated += dm.op(m) * dz.op(z / scaleZ_) / scaleZ_;
                }
            }
        }
        return cumulated * delta * delta;
    }
}
