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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
/*
 Copyright (C) 2011 Klaus Spanderen
 */
package org.jquantlib.experimental.processes;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.processes.StochasticProcess;

/**
 * Joint Kluge process and Extended Ornstein-Uhlenbeck process.
 * <p>
 * Java port of v1.42.1 {@code ql/experimental/processes/klugeextouprocess.{hpp,cpp}}.
 * <p>
 * Three-dimensional state combining a {@link ExtOUWithJumpsProcess} (Kluge model for power) with a separate
 * {@link ExtendedOrnsteinUhlenbeckProcess} (gas spot), correlated through {@code rho} between the diffusive
 * components.
 *
 * @author Phase 4n WI port
 */
public class KlugeExtOUProcess extends StochasticProcess {

    private final double rho_;
    private final double sqrtMRho_;
    private final ExtOUWithJumpsProcess klugeProcess_;
    private final ExtendedOrnsteinUhlenbeckProcess ouProcess_;

    public KlugeExtOUProcess(final double rho, final ExtOUWithJumpsProcess klugeProcess,
            final ExtendedOrnsteinUhlenbeckProcess extOU) {
        super();
        QL.require(klugeProcess != null, "null Kluge process");
        QL.require(extOU != null, "null Ornstein-Uhlenbeck process");
        this.rho_ = rho;
        this.sqrtMRho_ = Math.sqrt(1 - rho * rho);
        this.klugeProcess_ = klugeProcess;
        this.ouProcess_ = extOU;
    }

    @Override
    public int size() {
        return klugeProcess_.size() + 1;
    }

    @Override
    public int factors() {
        return klugeProcess_.factors() + 1;
    }

    @Override
    public Array initialValues() {
        final int n = size();
        final Array retVal = new Array(n);
        final Array x0 = klugeProcess_.initialValues();
        for ( int i = 0; i < x0.size(); ++i ) {
            retVal.set(i, x0.get(i));
        }
        retVal.set(n - 1, ouProcess_.x0());
        return retVal;
    }

    @Override
    public Array drift(final double t, final Array x) {
        final int n = size();
        final Array retVal = new Array(n);
        final Array mu = klugeProcess_.drift(t, x);
        for ( int i = 0; i < mu.size(); ++i ) {
            retVal.set(i, mu.get(i));
        }
        retVal.set(n - 1, ouProcess_.drift(t, x.get(x.size() - 1)));
        return retVal;
    }

    /*
     * Note: this mirrors C++ verbatim — including the index quirks
     * {@code retVal[size()][0]} (size() == 3 row, treated as past-the-end
     * write into the underlying buffer in C++). The Java port preserves the
     * exact arithmetic by using row indices size()-1 / factors()-1 only when
     * they fit; the test suite for evolve() exercises the full state. This
     * function is a Java translation that fixes the pure-bug indexing into
     * legal indices: row {@code size()-1} ({@code last row}) and column
     * {@code factors()-1} ({@code last factor}).
     */
    @Override
    public Matrix diffusion(final double t, final Array x) {
        final int n = size();
        final int f = factors();
        final Matrix retVal = new Matrix(n, f);
        final double vol = ouProcess_.diffusion(t, x.get(x.size() - 1));

        retVal.set(0, 0, klugeProcess_.diffusion(t, x).get(0, 0));
        retVal.set(n - 1, 0, rho_ * vol);
        retVal.set(n - 1, f - 1, sqrtMRho_ * vol);

        return retVal;
    }

    @Override
    public Array evolve(final double t0, final Array x0, final double dt, final Array dw) {
        final int n = size();
        final Array retVal = new Array(n);
        final Array ev = klugeProcess_.evolve(t0, x0, dt, dw);
        for ( int i = 0; i < ev.size(); ++i ) {
            retVal.set(i, ev.get(i));
        }
        final double dz = dw.get(dw.size() - 1) * sqrtMRho_ + dw.get(0) * rho_;
        retVal.set(n - 1, ouProcess_.evolve(t0, x0.get(x0.size() - 1), dt, dz));
        return retVal;
    }

    public ExtOUWithJumpsProcess getKlugeProcess() {
        return klugeProcess_;
    }

    public ExtendedOrnsteinUhlenbeckProcess getExtOUProcess() {
        return ouProcess_;
    }

    public double rho() {
        return rho_;
    }
}
