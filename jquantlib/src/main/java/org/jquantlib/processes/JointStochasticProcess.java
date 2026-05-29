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
 Copyright (C) 2007, 2008 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.processes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm;
import org.jquantlib.math.matrixutilities.SVD;
import org.jquantlib.time.Date;

/**
 * Multi-model process for hybrid products.
 * <p>
 * Aggregates a list of sub-{@link StochasticProcess}es into a single process
 * whose state vector is the concatenation of the constituents' states. The
 * cross-model dependence is supplied by the abstract
 * {@link #crossModelCorrelation(double, Array)} hook, which is combined with
 * the block-diagonal intrinsic covariances in {@link #covariance}. Mirrors
 * C++ v1.42.1 {@code ql/processes/jointstochasticprocess.cpp}.
 *
 * <p>This class is abstract: concrete hybrids must supply {@link #preEvolve},
 * {@link #postEvolve}, {@link #numeraire}, {@link #correlationIsStateDependent}
 * and {@link #crossModelCorrelation}.
 *
 * @author Klaus Spanderen (C++); JQuantLib migration contributors (Java)
 * @category processes
 */
public abstract class JointStochasticProcess extends StochasticProcess {

    protected final List< StochasticProcess > l_;

    private int size_ = 0;
    private int factors_;
    private int modelFactors_ = 0;
    private final List< Integer > vsize_ = new ArrayList<>();
    private final List< Integer > vfactors_ = new ArrayList<>();

    /**
     * Caching key for the (state-independent) cross-model correlation matrix,
     * keyed on {@code (t0, dt)}. Mirrors the private C++ inner struct
     * {@code CachingKey} at {@code jointstochasticprocess.hpp:84}.
     */
    private static final class CachingKey {
        private final /* @Time */ double t0_;
        private final /* @Time */ double dt_;

        CachingKey(final /* @Time */ double t0, final /* @Time */ double dt) {
            this.t0_ = t0;
            this.dt_ = dt;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CachingKey)) {
                return false;
            }
            final CachingKey k = (CachingKey) o;
            return Double.compare(t0_, k.t0_) == 0 && Double.compare(dt_, k.dt_) == 0;
        }

        @Override
        public int hashCode() {
            return Double.hashCode(t0_) * 31 + Double.hashCode(dt_);
        }
    }

    private final transient Map< CachingKey, Matrix > correlationCache_ = new HashMap<>();

    /**
     * @param l       the constituent processes
     * @param factors number of factors of the joint process, or
     *                {@code Integer.MIN_VALUE} (the JQuantLib null-int marker)
     *                to default to the sum of the constituents' factors. See
     *                {@link #JointStochasticProcess(List)}.
     */
    protected JointStochasticProcess(final List< StochasticProcess > l, final int factors) {
        super();
        this.l_ = l;
        this.factors_ = factors;

        for (final StochasticProcess p : l_) {
            p.addObserver(this);
        }

        for (final StochasticProcess p : l_) {
            vsize_.add(size_);
            size_ += p.size();

            vfactors_.add(modelFactors_);
            modelFactors_ += p.factors();
        }

        vsize_.add(size_);
        vfactors_.add(modelFactors_);

        if (factors_ == nullFactors()) {
            factors_ = modelFactors_;
        } else {
            QL.require(factors_ <= size_, "too many factors given");
        }
    }

    /** Convenience ctor: factors default to the sum of constituents' factors. */
    protected JointStochasticProcess(final List< StochasticProcess > l) {
        this(l, nullFactors());
    }

    /**
     * Sentinel matching C++ {@code Null<Size>()} — "no explicit factor count".
     */
    private static int nullFactors() {
        return Integer.MIN_VALUE;
    }

    //
    // abstract hooks (C++ pure virtuals)
    //

    public abstract void preEvolve(final /* @Time */ double t0, final Array x0,
            final /* @Time */ double dt, final Array dw);

    public abstract Array postEvolve(final /* @Time */ double t0, final Array x0,
            final /* @Time */ double dt, final Array dw, final Array y0);

    /** Discount factor used as the numeraire. */
    public abstract /* @DiscountFactor */ double numeraire(final /* @Time */ double t, final Array x);

    public abstract boolean correlationIsStateDependent();

    public abstract Matrix crossModelCorrelation(final /* @Time */ double t0, final Array x0);

    //
    // implements StochasticProcess
    //

    @Override
    public int size() {
        return size_;
    }

    @Override
    public int factors() {
        return factors_;
    }

    /** Cut out the {@code i}-th constituent's slice of the state vector. */
    protected Array slice(final Array x, final int i) {
        final int n = vsize_.get(i + 1) - vsize_.get(i);
        final Array y = new Array(n);
        final int base = vsize_.get(i);
        for (int k = 0; k < n; k++) {
            y.set(k, x.get(base + k));
        }
        return y;
    }

    @Override
    public Array initialValues() {
        final Array retVal = new Array(size());
        for (int i = 0; i < l_.size(); i++) {
            final Array p = l_.get(i).initialValues();
            final int base = vsize_.get(i);
            for (int k = 0; k < p.size(); k++) {
                retVal.set(base + k, p.get(k));
            }
        }
        return retVal;
    }

    @Override
    public Array drift(final /* @Time */ double t, final Array x) {
        final Array retVal = new Array(size());
        for (int i = 0; i < l_.size(); i++) {
            final Array pDrift = l_.get(i).drift(t, slice(x, i));
            final int base = vsize_.get(i);
            for (int k = 0; k < pDrift.size(); k++) {
                retVal.set(base + k, pDrift.get(k));
            }
        }
        return retVal;
    }

    @Override
    public Array expectation(final /* @Time */ double t0, final Array x0, final /* @Time */ double dt) {
        final Array retVal = new Array(size());
        for (int i = 0; i < l_.size(); i++) {
            final Array pExp = l_.get(i).expectation(t0, slice(x0, i), dt);
            final int base = vsize_.get(i);
            for (int k = 0; k < pExp.size(); k++) {
                retVal.set(base + k, pExp.get(k));
            }
        }
        return retVal;
    }

    @Override
    public Matrix diffusion(final /* @Time */ double t, final Array x) {
        // might need some improvement in the future
        final /* @Time */ double dt = 0.001;
        // C++ jointstochasticprocess.cpp:126 calls pseudoSqrt(covariance/dt) with the
        // DEFAULT salvaging algorithm == SalvagingAlgorithm::None (Cholesky); see
        // pseudosqrt.hpp:65. None and Spectral both satisfy A*A^T=M but return
        // different factors A for non-diagonal covariance, so we must match None here.
        return PseudoSqrt.pseudoSqrt(covariance(t, x, dt).mul(1.0 / dt), SalvagingAlgorithm.None);
    }

    @Override
    public Matrix covariance(final /* @Time */ double t0, final Array x0, final /* @Time */ double dt) {
        // get the model intrinsic covariance matrix (block-diagonal)
        final Matrix retVal = new Matrix(size(), size()); // zero-initialized

        for (int j = 0; j < l_.size(); j++) {
            final int vs = vsize_.get(j);
            final Matrix pCov = l_.get(j).covariance(t0, slice(x0, j), dt);
            for (int i = 0; i < pCov.rows(); i++) {
                for (int c = 0; c < pCov.columns(); c++) {
                    retVal.set(vs + i, vs + c, pCov.get(i, c));
                }
            }
        }

        // add the cross-model covariance matrix
        final Array volatility = retVal.diagonal().sqrt();
        final Matrix crossModelCovar = this.crossModelCorrelation(t0, x0);

        for (int i = 0; i < size(); i++) {
            for (int j = 0; j < size(); j++) {
                final double v = crossModelCovar.get(i, j) * volatility.get(i) * volatility.get(j);
                crossModelCovar.set(i, j, v);
            }
        }

        return retVal.add(crossModelCovar);
    }

    @Override
    public Matrix stdDeviation(final /* @Time */ double t0, final Array x0, final /* @Time */ double dt) {
        // C++ jointstochasticprocess.cpp:167 calls pseudoSqrt(covariance) with the
        // DEFAULT salvaging algorithm == SalvagingAlgorithm::None (Cholesky); see
        // pseudosqrt.hpp:65. Match None (not Spectral) so the returned factor agrees
        // with C++ for correlated (non-diagonal) covariance.
        return PseudoSqrt.pseudoSqrt(covariance(t0, x0, dt), SalvagingAlgorithm.None);
    }

    @Override
    public Array apply(final Array x0, final Array dx) {
        final Array retVal = new Array(size());
        for (int i = 0; i < l_.size(); i++) {
            final Array pApply = l_.get(i).apply(slice(x0, i), slice(dx, i));
            final int base = vsize_.get(i);
            for (int k = 0; k < pApply.size(); k++) {
                retVal.set(base + k, pApply.get(k));
            }
        }
        return retVal;
    }

    @Override
    public Array evolve(final /* @Time */ double t0, final Array x0, final /* @Time */ double dt, final Array dw) {
        Array dv = new Array(modelFactors_);

        if (correlationIsStateDependent() || !correlationCache_.containsKey(new CachingKey(t0, dt))) {
            final Matrix cov = covariance(t0, x0, dt);

            final Array sqrtDiag = cov.diagonal().sqrt();
            for (int i = 0; i < cov.rows(); i++) {
                for (int j = i; j < cov.columns(); j++) {
                    final double div = sqrtDiag.get(i) * sqrtDiag.get(j);
                    final double val = (div > 0) ? cov.get(i, j) / div : 0.0;
                    cov.set(i, j, val);
                    cov.set(j, i, val);
                }
            }

            final Matrix diff = new Matrix(size(), modelFactors_); // zero-initialized

            for (int j = 0; j < l_.size(); j++) {
                final int vs = vsize_.get(j);
                final int vf = vfactors_.get(j);

                final Matrix stdDev = l_.get(j).stdDeviation(t0, slice(x0, j), dt);

                for (int i = 0; i < stdDev.rows(); i++) {
                    double sumSq = 0.0;
                    for (int c = 0; c < stdDev.columns(); c++) {
                        final double e = stdDev.get(i, c);
                        sumSq += e * e;
                    }
                    final /* @Volatility */ double vol = Math.sqrt(sumSq);
                    if (vol > 0.0) {
                        for (int c = 0; c < stdDev.columns(); c++) {
                            stdDev.set(i, c, stdDev.get(i, c) / vol);
                        }
                    } else {
                        // keep the svd happy
                        for (int c = 0; c < stdDev.columns(); c++) {
                            stdDev.set(i, c, 100 * i * Constants.QL_EPSILON);
                        }
                    }
                }

                final SVD svd = new SVD(stdDev);
                final Array s = svd.singularValues();
                final Matrix w = new Matrix(s.size(), s.size()); // zero-initialized
                for (int i = 0; i < s.size(); i++) {
                    if (Math.abs(s.get(i)) > Math.sqrt(Constants.QL_EPSILON)) {
                        w.set(i, i, 1.0 / s.get(i));
                    }
                }

                final Matrix inv = svd.U().mul(w).mul(svd.V().transpose());

                for (int i = 0; i < stdDev.rows(); i++) {
                    for (int c = 0; c < inv.columns(); c++) {
                        diff.set(i + vs, vf + c, inv.get(i, c));
                    }
                }
            }

            Matrix rs = PseudoSqrt.rankReducedSqrt(cov, factors_, 1, SalvagingAlgorithm.Spectral);

            if (rs.columns() < factors_) {
                // fewer eigenvalues than expected factors; pad with zeros.
                final Matrix tmp = new Matrix(cov.rows(), factors_); // zero-initialized
                for (int i = 0; i < cov.rows(); i++) {
                    for (int c = 0; c < rs.columns(); c++) {
                        tmp.set(i, c, rs.get(i, c));
                    }
                }
                rs = tmp;
            }

            final Matrix m = diff.transpose().mul(rs);

            if (!correlationIsStateDependent()) {
                correlationCache_.put(new CachingKey(t0, dt), m);
            }
            dv = m.mul(dw);
        } else {
            if (!correlationIsStateDependent()) {
                dv = correlationCache_.get(new CachingKey(t0, dt)).mul(dw);
            }
        }

        this.preEvolve(t0, x0, dt, dv);

        final Array retVal = new Array(size());
        for (int i = 0; i < l_.size(); i++) {
            final StochasticProcess p = l_.get(i);

            final Array dz = new Array(p.factors());
            final int vf = vfactors_.get(i);
            for (int k = 0; k < p.factors(); k++) {
                dz.set(k, dv.get(vf + k));
            }

            final Array x = new Array(p.size());
            final int vs = vsize_.get(i);
            for (int k = 0; k < p.size(); k++) {
                x.set(k, x0.get(vs + k));
            }

            final Array r = p.evolve(t0, x, dt, dz);
            for (int k = 0; k < r.size(); k++) {
                retVal.set(vs + k, r.get(k));
            }
        }

        return this.postEvolve(t0, x0, dt, dv, retVal);
    }

    public List< StochasticProcess > constituents() {
        return l_;
    }

    @Override
    public /* @Time */ double time(final Date date) {
        QL.require(!l_.isEmpty(), "process list is empty");
        return l_.get(0).time(date);
    }

    @Override
    public void update() {
        // clear all caches
        correlationCache_.clear();
        super.update();
    }

}
