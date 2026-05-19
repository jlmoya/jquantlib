/*
 Copyright (C) 2019 Klaus Spanderen
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
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Discounted value Dirichlet boundary condition.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/utilities/fdmdiscountdirichletboundary.{hpp,cpp}}.
 *
 * <p>Delegates to {@link FdmTimeDepDirichletBoundary} with the time-dependent
 * function {@code t -> valueOnBoundary * discount(maturityTime) / discount(t)}.
 *
 * @author Phase 2m Track C port
 */
public class FdmDiscountDirichletBoundary implements BoundaryCondition< FdmLinearOp > {

    private final FdmTimeDepDirichletBoundary bc_;

    public FdmDiscountDirichletBoundary(final FdmMesher mesher, final YieldTermStructure rTS, final double maturityTime,
            final double valueOnBoundary, final int direction, final Side side) {

        final double discountAtMaturity = rTS.discount(maturityTime);

        this.bc_ = new FdmTimeDepDirichletBoundary(mesher, t -> valueOnBoundary * discountAtMaturity / rTS.discount(t),
                direction, side);
    }

    @Override
    public void setTime(final double t) {
        bc_.setTime(t);
    }

    @Override
    public void applyBeforeApplying(final FdmLinearOp op) {
        bc_.applyBeforeApplying(op);
    }

    @Override
    public void applyBeforeSolving(final FdmLinearOp op, final Array rhs) {
        bc_.applyBeforeSolving(op, rhs);
    }

    @Override
    public void applyAfterApplying(final Array a) {
        bc_.applyAfterApplying(a);
    }

    @Override
    public void applyAfterSolving(final Array a) {
        bc_.applyAfterSolving(a);
    }
}
