/*
 Copyright (C) 2011 Fabien Le Floc'h
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
package org.jquantlib.methods.finitedifferences;

import java.util.List;

import org.jquantlib.math.matrixutilities.Array;

/**
 * TR-BDF2 time-stepping scheme for the legacy (TridiagonalOperator) finite-difference framework.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/trbdf2.hpp} (the legacy
 * {@code template<class Operator> class TRBDF2}). See <a href="http://ssrn.com/abstract=1648878">SSRN 1648878</a> for
 * the derivation.
 *
 * <p>This is the <em>legacy</em> template-scheme idiom, a sibling of {@link MixedScheme} / {@link CrankNicolson} /
 * {@link ExplicitEuler}; it is distinct from the modern
 * {@code org.jquantlib.methods.finitedifferences.schemes.TrBDF2Scheme} (which targets the {@code FdmLinearOpComposite}
 * framework). The passed operator must be linear and derived from the {@link Operator} contract.
 *
 * <p><strong>Note:</strong> this scheme has zero instantiations in C++ v1.42.1 (dead upstream); it is ported here for
 * legacy-family completeness and cross-validated structurally against the C++ {@code TRBDF2<Operator>} on a trivial
 * linear operator.
 *
 * @param <T> the operator type
 * @author JQuantLib gap-fdm port
 */
public class TRBDF2< T extends Operator > {

    private final double alpha_;
    private final T L_;
    private final T I_;
    private T explicitTrapezoidalPart_;
    private T explicitBDF2PartFull_;
    private T explicitBDF2PartMid_;
    private T implicitPart_;
    private double dt_;
    private final List< BoundaryCondition< T > > bcs_;

    @SuppressWarnings("unchecked")
    public TRBDF2(final T L, final List< BoundaryCondition< T > > bcs) {
        this.L_ = L;
        this.I_ = (T) L.identity(L.size());
        this.dt_ = 0.0;
        this.bcs_ = bcs;
        this.alpha_ = 2.0 - Math.sqrt(2.0);
    }

    @SuppressWarnings("unchecked")
    public void setStep(final double dt) {
        this.dt_ = dt;

        // implicitPart_           = I + 0.5*alpha*dt*L
        implicitPart_ = (T) I_.add(L_.multiply(0.5 * alpha_ * dt_));
        // explicitTrapezoidalPart_ = I - 0.5*alpha*dt*L
        explicitTrapezoidalPart_ = (T) I_.subtract(L_.multiply(0.5 * alpha_ * dt_));
        // explicitBDF2PartFull_   = -(1-alpha)^2 / (alpha*(2-alpha)) * I
        explicitBDF2PartFull_ =
                (T) I_.multiply(-(1.0 - alpha_) * (1.0 - alpha_) / (alpha_ * (2.0 - alpha_)));
        // explicitBDF2PartMid_    = 1 / (alpha*(2-alpha)) * I
        explicitBDF2PartMid_ = (T) I_.multiply(1.0 / (alpha_ * (2.0 - alpha_)));
    }

    @SuppressWarnings("unchecked")
    public Array step(Array a, final double t) {
        int i;
        final Array aInit = a.clone();

        for ( i = 0; i < bcs_.size(); i++ ) {
            bcs_.get(i).setTime(t);
        }

        // trapezoidal explicit part
        if ( L_.isTimeDependent() ) {
            L_.setTime(t);
            explicitTrapezoidalPart_ = (T) I_.subtract(L_.multiply(0.5 * alpha_ * dt_));
        }
        for ( i = 0; i < bcs_.size(); i++ ) {
            bcs_.get(i).applyBeforeApplying(explicitTrapezoidalPart_);
        }
        a = explicitTrapezoidalPart_.applyTo(a);
        for ( i = 0; i < bcs_.size(); i++ ) {
            bcs_.get(i).applyAfterApplying(a);
        }

        // trapezoidal implicit part
        if ( L_.isTimeDependent() ) {
            L_.setTime(t - dt_);
            implicitPart_ = (T) I_.add(L_.multiply(0.5 * alpha_ * dt_));
        }
        for ( i = 0; i < bcs_.size(); i++ ) {
            bcs_.get(i).applyBeforeSolving(implicitPart_, a);
        }
        a = implicitPart_.solveFor(a);
        for ( i = 0; i < bcs_.size(); i++ ) {
            bcs_.get(i).applyAfterSolving(a);
        }

        // BDF2 explicit part
        if ( L_.isTimeDependent() ) {
            L_.setTime(t);
        }
        for ( i = 0; i < bcs_.size(); i++ ) {
            bcs_.get(i).applyBeforeApplying(explicitBDF2PartFull_);
        }
        final Array b0 = explicitBDF2PartFull_.applyTo(aInit);
        for ( i = 0; i < bcs_.size(); i++ ) {
            bcs_.get(i).applyAfterApplying(b0);
        }

        for ( i = 0; i < bcs_.size(); i++ ) {
            bcs_.get(i).applyBeforeApplying(explicitBDF2PartMid_);
        }
        final Array b1 = explicitBDF2PartMid_.applyTo(a);
        for ( i = 0; i < bcs_.size(); i++ ) {
            bcs_.get(i).applyAfterApplying(b1);
        }
        a = b0.add(b1);

        // reuse implicit part - works only for alpha = 2 - sqrt(2)
        for ( i = 0; i < bcs_.size(); i++ ) {
            bcs_.get(i).applyBeforeSolving(implicitPart_, a);
        }
        a = implicitPart_.solveFor(a);
        for ( i = 0; i < bcs_.size(); i++ ) {
            bcs_.get(i).applyAfterSolving(a);
        }

        return a;
    }
}
