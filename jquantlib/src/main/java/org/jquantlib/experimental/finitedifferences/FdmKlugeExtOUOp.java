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
package org.jquantlib.experimental.finitedifferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.processes.ExtOUWithJumpsProcess;
import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess;
import org.jquantlib.experimental.processes.KlugeExtOUProcess;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.operators.NinePointLinearOp;
import org.jquantlib.methods.finitedifferences.operators.SecondOrderMixedDerivativeOp;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;

/**
 * Finite-difference operator combining the Kluge power process (a
 * 2D {@link ExtOUWithJumpsProcess}) with a correlated extended
 * Ornstein–Uhlenbeck process for gas (direction 2).
 * <p>
 * Java port of v1.42.1
 * {@code ql/experimental/finitedifferences/fdmklugeextouop.{hpp,cpp}}.
 * <p>
 * Composition mirrors the C++ class verbatim:
 * <ul>
 *   <li>{@code klugeOp_}: {@link FdmExtOUJumpOp} on directions 0 (X = log-power
 *       diffusion) and 1 (Y = exponential jump component);</li>
 *   <li>{@code ouOp_}: {@link FdmExtendedOrnsteinUhlenbeckOp} on direction 2
 *       (U = log-gas), built with a zero-rate {@link FlatForward} so the
 *       discount-rate term {@code -r} is only applied once (through
 *       {@code klugeOp_});</li>
 *   <li>{@code corrMap_}: a nine-point cross-derivative operator
 *       {@link SecondOrderMixedDerivativeOp} scaled by
 *       {@code rho * vol_u * vol_x} that couples directions 0 and 2.</li>
 * </ul>
 * <p>
 * The {@link #toMatrixDecomp()} contract returns four matrices:
 * <ol start="0">
 *   <li>the Kluge OU x-direction matrix;</li>
 *   <li>the Kluge dy (first-derivative) matrix;</li>
 *   <li>the gas OU u-direction matrix;</li>
 *   <li>the correlation matrix + Kluge integro (mixed) matrix.</li>
 * </ol>
 *
 * @author Phase 5e.5b-CFC-d-285 port
 */
public class FdmKlugeExtOUOp implements FdmLinearOpComposite {

    @SuppressWarnings("unused")
    private final FdmMesher mesher_;
    private final ExtOUWithJumpsProcess kluge_;
    private final ExtendedOrnsteinUhlenbeckProcess extOU_;
    @SuppressWarnings("unused")
    private final YieldTermStructure rTS_;
    @SuppressWarnings("unused")
    private final FdmBoundaryConditionSet bcSet_;

    private final FdmExtOUJumpOp klugeOp_;
    private final FdmExtendedOrnsteinUhlenbeckOp ouOp_;

    private final NinePointLinearOp corrMap_;

    public FdmKlugeExtOUOp(final FdmMesher mesher,
                           final KlugeExtOUProcess klugeOUProcess,
                           final YieldTermStructure rTS,
                           final FdmBoundaryConditionSet bcSet,
                           final int integroIntegrationOrder) {
        this.mesher_ = mesher;
        this.kluge_  = klugeOUProcess.getKlugeProcess();
        this.extOU_  = klugeOUProcess.getExtOUProcess();
        this.rTS_    = rTS;
        this.bcSet_  = bcSet;

        this.klugeOp_ = new FdmExtOUJumpOp(
                mesher, kluge_, rTS, bcSet, integroIntegrationOrder);

        // The U-direction ouOp uses a zero-rate flat curve so that the
        // discount-rate term -r is applied exactly once (in klugeOp_).
        final Date refDate = rTS.referenceDate();
        final DayCounter dc = rTS.dayCounter();
        final YieldTermStructure zeroRateCurve = new FlatForward(refDate, 0.0, dc);
        this.ouOp_ = new FdmExtendedOrnsteinUhlenbeckOp(
                mesher, extOU_, zeroRateCurve, bcSet, 2);

        // corrMap = SecondOrderMixedDerivativeOp(0, 2, mesher)
        //              .mult( rho * vol_u * vol_x  *  Array(n, 1) )
        final double rho = klugeOUProcess.rho();
        final double volU = extOU_.volatility();
        final double volX = kluge_.getExtendedOrnsteinUhlenbeckProcess().volatility();
        final double coeff = rho * volU * volX;
        final Array coefficients = new Array(mesher.layout().size()).fill(coeff);
        this.corrMap_ = new SecondOrderMixedDerivativeOp(0, 2, mesher).mult(coefficients);
    }

    @Override
    public int size() {
        return mesher_.layout().dim().length;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        ouOp_.setTime(t1, t2);
        klugeOp_.setTime(t1, t2);
    }

    @Override
    public Array apply(final Array r) {
        return ouOp_.apply(r).add(klugeOp_.apply(r)).add(corrMap_.apply(r));
    }

    @Override
    public Array applyMixed(final Array r) {
        return corrMap_.apply(r).add(klugeOp_.applyMixed(r));
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        return klugeOp_.applyDirection(direction, r)
                .add(ouOp_.applyDirection(direction, r));
    }

    @Override
    public Array solveSplitting(final int direction, final Array r, final double a) {
        if (direction == 0 || direction == 1) {
            return klugeOp_.solveSplitting(direction, r, a);
        } else if (direction == 2) {
            return ouOp_.solveSplitting(direction, r, a);
        }
        return r.clone();
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return klugeOp_.solveSplitting(0, r, dt);
    }

    @Override
    public Matrix toMatrix() {
        // Composite: sum of all decomposition matrices, mirroring C++
        // FdmLinearOpComposite::toMatrix() std::accumulate over decomp.
        final List<Matrix> dcmp = toMatrixDecomp();
        Matrix acc = new Matrix(dcmp.get(0));
        for (int i = 1; i < dcmp.size(); ++i) {
            acc = acc.add(dcmp.get(i));
        }
        return acc;
    }

    @Override
    public List<Matrix> toMatrixDecomp() {
        final List<Matrix> klugeDecomp = klugeOp_.toMatrixDecomp();
        // klugeDecomp = [ouMatX, dyMatY, integroMixed]
        final List<Matrix> ret = new ArrayList<Matrix>(4);
        ret.add(klugeDecomp.get(0));                       // X
        ret.add(klugeDecomp.get(1));                       // Y
        ret.add(ouOp_.toMatrixDecomp().get(0));            // U
        ret.add(corrMap_.toMatrix().add(klugeDecomp.get(2))); // mixed
        return Collections.unmodifiableList(ret);
    }

    /**
     * Native sparse decomposition mirroring the C++
     * {@code FdmKlugeExtOUOp::toMatrixDecomp()} which returns
     * {@code std::vector<SparseMatrix>}. Avoids the {@code O(n^2)}
     * dense intermediates of {@link #toMatrixDecomp()} — critical for
     * 50x20x20 = 20000-cell grids used by
     * {@code VppTest.testKlugeExtOUMatrixDecomposition}.
     *
     * <p>Returns {@code [klugeDecomp[0], klugeDecomp[1],
     * ouOp.toSparseMatrixDecomp()[0], corrMap.toSparseMatrix() + klugeDecomp[2]]}.</p>
     */
    @Override
    public List<SparseMatrix> toSparseMatrixDecomp() {
        final List<SparseMatrix> klugeDecomp = klugeOp_.toSparseMatrixDecomp();
        final List<SparseMatrix> ret = new ArrayList<SparseMatrix>(4);
        ret.add(klugeDecomp.get(0));                              // X
        ret.add(klugeDecomp.get(1));                              // Y
        ret.add(ouOp_.toSparseMatrixDecomp().get(0));             // U
        ret.add(corrMap_.toSparseMatrix().add(klugeDecomp.get(2))); // mixed
        return Collections.unmodifiableList(ret);
    }
}
