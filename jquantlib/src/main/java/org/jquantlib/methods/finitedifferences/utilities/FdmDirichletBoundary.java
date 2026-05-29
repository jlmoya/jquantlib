/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008 Klaus Spanderen
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

package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOp;

/**
 * Constant-value Dirichlet boundary condition for the modern Fdm framework.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/utilities/fdmdirichletboundary.{hpp,cpp}}.
 *
 * <p>Sets every cell on the chosen boundary hyperplane (lower or upper, in a given direction) to a fixed scalar
 * {@code valueOnBoundary} after the operator is applied / the system is solved. The boundary indices are computed via
 * {@link FdmIndicesOnBoundary}.
 *
 * <p>The scalar overload {@link #applyAfterApplying(double, double)} is used by callers (e.g. inner-value snapping) to
 * clamp values that fall strictly beyond the boundary extreme to {@code valueOnBoundary}.
 *
 * @author JQuantLib gap-fdm port
 */
public class FdmDirichletBoundary implements BoundaryCondition< FdmLinearOp > {

    private final Side side_;
    private final double valueOnBoundary_;
    private final int[] indices_;
    private final double xExtreme_;

    public FdmDirichletBoundary(final FdmMesher mesher, final double valueOnBoundary, final int direction,
            final Side side) {
        this.side_ = side;
        this.valueOnBoundary_ = valueOnBoundary;
        this.indices_ = new FdmIndicesOnBoundary(mesher.layout(), direction, side).getIndices();

        // xExtreme_ is set for every valid side; C++ QL_FAILs on Side.None.
        // (The C++ header leaves xExtreme_ default-initialised, but the ctor
        // always assigns it for Lower/Upper before any read, so there is no
        // undefined behaviour to replicate — we initialise it explicitly.)
        if ( side == Side.Lower ) {
            this.xExtreme_ = mesher.locations(direction).get(0);
        } else if ( side == Side.Upper ) {
            this.xExtreme_ = mesher.locations(direction).get(mesher.layout().dim()[direction] - 1);
        } else {
            throw new IllegalStateException("internal error: Dirichlet boundary side must be Lower or Upper");
        }
    }

    @Override
    public void applyBeforeApplying(final FdmLinearOp op) {
        // no-op (matches C++)
    }

    @Override
    public void applyBeforeSolving(final FdmLinearOp op, final Array rhs) {
        // no-op (matches C++)
    }

    @Override
    public void applyAfterApplying(final Array x) {
        for ( final int indice : indices_ ) {
            x.set(indice, valueOnBoundary_);
        }
    }

    @Override
    public void applyAfterSolving(final Array rhs) {
        applyAfterApplying(rhs);
    }

    @Override
    public void setTime(final double t) {
        // no-op (matches C++ empty setTime)
    }

    /**
     * Scalar variant: returns {@code valueOnBoundary} when {@code x} lies strictly beyond the boundary extreme on the
     * relevant side, otherwise returns {@code value} unchanged. Java port of
     * {@code Real FdmDirichletBoundary::applyAfterApplying(Real x, Real value) const}.
     */
    public double applyAfterApplying(final double x, final double value) {
        return ((side_ == Side.Lower && x < xExtreme_) || (side_ == Side.Upper && x > xExtreme_))
                ? valueOnBoundary_
                : value;
    }
}
