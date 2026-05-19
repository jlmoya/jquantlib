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
 Copyright (C) 2010 Klaus Spanderen
*/
package org.jquantlib.methods.finitedifferences.operators;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussHermiteIntegration;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.processes.BatesProcess;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.ZeroSpreadedTermStructure;
import org.jquantlib.time.Frequency;

import java.util.ArrayList;
import java.util.List;

/**
 * Bates linear FD operator: Heston FD operator + jump-integro term.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/methods/finitedifferences/operators/fdmbatesop.{hpp,cpp}} (Phase 5h.5-Bates-b).
 *
 * <p>Decomposes into:
 * <ul>
 *   <li>The Heston PDE operator on (log-spot, variance), built with the
 *       jump-compensated risk-free spread {@code lambda * m} added to the
 *       dividend yield (so the equity drift retains {@code r - q}
 *       overall — the explicit jump compensator in
 *       {@link BatesProcess#drift} cancels out the additional yield);</li>
 *   <li>The jump-integro term
 *       <pre>
 *         lambda * (∫ omega(J) V(x+J, v, t) dJ - V(x, v, t))
 *       </pre>
 *       where {@code omega(J) ~ N(nu, delta^2)}; computed via Gauss-Hermite
 *       quadrature on the substitution {@code y = (J - nu) / (sqrt(2)*delta)}.</li>
 * </ul>
 *
 * <p>Limitations vs. C++ v1.42.1:
 * <ul>
 *   <li>{@code FdmQuantoHelper} not yet ported — quanto-adjustment path
 *       deferred (matches {@link FdmHestonOp}).</li>
 *   <li>The Dirichlet-boundary adjustment in the integrand is currently a
 *       no-op for empty boundary sets (the only path exercised by the
 *       European {@link org.jquantlib.pricingengines.vanilla.FdBatesVanillaEngine}
 *       constructor); calling code passing a non-empty boundary set will
 *       hit a {@code QL_FAIL} matching C++ until the
 *       {@code FdmDirichletBoundary} type is ported.</li>
 *   <li>{@code toMatrixDecomp} is left unimplemented (matches C++).</li>
 * </ul>
 *
 * @author JQuantLib
 * @see FdmHestonOp
 * @see BatesProcess
 */
public class FdmBatesOp implements FdmLinearOpComposite {

    private static final double M_SQRT2 = Math.sqrt(2.0);
    private static final double M_1_SQRTPI = 1.0 / Math.sqrt(Math.PI);
    /** Workaround: java's Constants holds QL_EPSILON; we pull this in for completeness. */
    @SuppressWarnings( "unused" )
    private static final double QL_EPSILON = Constants.QL_EPSILON;
    private final double lambda_, delta_, nu_, m_;
    private final GaussHermiteIntegration gaussHermiteIntegration_;
    private final FdmMesher mesher_;
    private final FdmBoundaryConditionSet bcSet_;
    private final FdmHestonOp hestonOp_;

    /**
     * Convenience constructor: empty quanto helper (no quanto), default Gauss-Hermite quadrature order = 16. Mirrors
     * C++ {@code FdmBatesOp(mesher, batesProcess, bcSet, integroIntegrationOrder=16)}.
     */
    public FdmBatesOp(final FdmMesher mesher, final BatesProcess batesProcess, final FdmBoundaryConditionSet bcSet) {
        this(mesher, batesProcess, bcSet, 16);
    }

    /** Standard constructor (no quanto helper). */
    public FdmBatesOp(final FdmMesher mesher, final BatesProcess batesProcess, final FdmBoundaryConditionSet bcSet,
            final int integroIntegrationOrder) {
        QL.require(mesher != null, "null mesher");
        QL.require(batesProcess != null, "null Bates process");
        QL.require(bcSet != null, "null boundary set");

        this.lambda_ = batesProcess.lambda();
        this.delta_ = batesProcess.delta();
        this.nu_ = batesProcess.nu();
        this.m_ = Math.exp(nu_ + 0.5 * delta_ * delta_) - 1.0;
        this.gaussHermiteIntegration_ = new GaussHermiteIntegration(integroIntegrationOrder);
        this.mesher_ = mesher;
        this.bcSet_ = bcSet;

        // The Java HestonProcess constructor (matching the C++) takes
        // (rTS, qTS, s0, v0, kappa, theta, sigma, rho). We feed it the
        // Bates risk-free curve and a "spread-bumped" dividend curve — the
        // additional spread = lambda * m enters as a continuous yield shift
        // on the dividend leg, cancelling the explicit lambda*m subtraction
        // in BatesProcess.drift so the Heston PDE operator sees the net
        // (r - q - lambda*m) - (-lambda*m) = r - q drift it expects.
        // Mirrors C++ ZeroSpreadedTermStructure(qYield, lambda*m, Continuous).
        final ZeroSpreadedTermStructure spreadedQ = new ZeroSpreadedTermStructure(batesProcess.dividendYield(),
                new Handle< Quote >(new SimpleQuote(lambda_ * m_)), Compounding.Continuous, Frequency.NoFrequency,
                defaultDayCounter(batesProcess));

        final HestonProcess equivHeston = new HestonProcess(batesProcess.riskFreeRate(),
                new Handle< YieldTermStructure >(spreadedQ), batesProcess.s0(), batesProcess.v0().currentLink().value(),
                batesProcess.kappa().currentLink().value(), batesProcess.theta().currentLink().value(),
                batesProcess.sigma().currentLink().value(), batesProcess.rho().currentLink().value());
        equivHeston.update();

        this.hestonOp_ = new FdmHestonOp(mesher, equivHeston);
    }

    //
    // FdmLinearOpComposite
    //

    /**
     * Pick a sensible day counter for the spread-bumped dividend curve: mirror the original dividend curve's day
     * counter when available; fall back to {@link Actual365Fixed} otherwise.
     */
    private static DayCounter defaultDayCounter(final BatesProcess proc) {
        try {
            return proc.dividendYield().currentLink().dayCounter();
        } catch ( final Exception e ) {
            return new Actual365Fixed();
        }
    }

    @Override
    public int size() {
        return hestonOp_.size();
    }

    @Override
    public void setTime(final double t1, final double t2) {
        hestonOp_.setTime(t1, t2);
    }

    @Override
    public Array apply(final Array r) {
        return hestonOp_.apply(r).add(integro(r));
    }

    @Override
    public Array applyMixed(final Array r) {
        return hestonOp_.applyMixed(r).add(integro(r));
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        return hestonOp_.applyDirection(direction, r);
    }

    @Override
    public Array solveSplitting(final int direction, final Array r, final double s) {
        return hestonOp_.solveSplitting(direction, r, s);
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return hestonOp_.preconditioner(r, dt);
    }

    @Override
    public Matrix toMatrix() {
        throw new UnsupportedOperationException("FdmBatesOp.toMatrix() not implemented; use toMatrixDecomp()");
    }

    //
    // jump-integro term
    //

    @Override
    public List< Matrix > toMatrixDecomp() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * The full jump-integro contribution
     * <pre>
     *   lambda * (∫ omega(J) V(x+J, v, t) dJ - V(x, v, t))
     * </pre>
     * computed by Gauss-Hermite quadrature per (x, v) grid node.
     */
    private Array integro(final Array r) {
        final int[] dim = mesher_.layout().dim();
        QL.require(dim.length == 2, "invalid layout dimension (need 2)");
        final int nx = dim[0];
        final int nv = dim[1];

        // Build a per-v interpolant V(x ; v) over the x-mesh.
        final Array xLoc = new Array(nx);
        final Matrix f = new Matrix(nv, nx);
        for ( final FdmLinearOpIterator iter : mesher_.layout() ) {
            final int i = iter.coordinates()[0];
            final int j = iter.coordinates()[1];
            xLoc.set(i, mesher_.location(iter, 0));
            f.set(j, i, r.get(iter.index()));
        }

        final List< LinearInterpolation > interpl = new ArrayList< LinearInterpolation >(nv);
        for ( int j = 0; j < nv; ++j ) {
            final double[] yRow = new double[nx];
            for ( int i = 0; i < nx; ++i ) {
                yRow[i] = f.get(j, i);
            }
            final LinearInterpolation li = new LinearInterpolation(xLoc, new Array(yRow));
            li.enableExtrapolation();
            li.update();
            interpl.add(li);
        }

        final Array integral = new Array(r.size());
        for ( final FdmLinearOpIterator iter : mesher_.layout() ) {
            final int i = iter.coordinates()[0];
            final int j = iter.coordinates()[1];
            final double quad = gaussHermiteIntegration_.op(
                    new IntegroIntegrand(interpl.get(j), bcSet_, xLoc.get(i), delta_, nu_));
            integral.set(iter.index(), M_1_SQRTPI * quad);
        }

        // lambda * (integral - r)
        return integral.sub(r).mul(lambda_);
    }

    /**
     * Integrand for the jump-integral, mirrors C++ {@code IntegroIntegrand}.
     *
     * <p>{@code y = (J - nu) / (sqrt(2)*delta)} substitution maps the
     * Gaussian {@code omega(J)} integral to a Gauss-Hermite quadrature sample
     * {@code exp(-y^2) * V(x + sqrt(2)*delta*y + nu)}.
     */
    private static final class IntegroIntegrand implements Ops.DoubleOp {
        private final double x_, delta_, nu_;
        private final FdmBoundaryConditionSet bcSet_;
        private final LinearInterpolation interpl_;

        IntegroIntegrand(final LinearInterpolation interpl, final FdmBoundaryConditionSet bcSet, final double x,
                final double delta, final double nu) {
            this.x_ = x;
            this.delta_ = delta;
            this.nu_ = nu;
            this.bcSet_ = bcSet;
            this.interpl_ = interpl;
        }

        @Override
        public double op(final double y) {
            final double xJump = x_ + M_SQRT2 * delta_ * y + nu_;
            double valueOfDerivative = interpl_.op(xJump, /* extrapolate */ true);

            // Mirror C++: iterate Dirichlet-only boundaries. With the
            // empty-set fast-path (the European Bates engine constructor
            // passes new FdmBoundaryConditionSet()), this loop is a no-op.
            QL.require(bcSet_.isEmpty(), "FdmBatesOp can only deal with Dirichlet boundary conditions; "
                    + "FdmDirichletBoundary not yet ported (Phase 5h.5-Bates-c carry).");
            return Math.exp(-y * y) * valueOfDerivative;
        }
    }
}
