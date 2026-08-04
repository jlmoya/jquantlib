/*
 Copyright (C) 2026 Jose Moya

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

package org.jquantlib.math.interpolations;

import org.jquantlib.QL;

/**
 * Decorator that turns any 1-D {@link Interpolation} into one that extrapolates flat — clamping to the boundary values
 * — outside the original data range.
 * <p>
 * Ported from C++ QuantLib v1.43 {@code ql/math/interpolations/flatextrapolation.hpp} — new in that release.
 * <p>
 * Three details of the C++ semantics are easy to get wrong and are reproduced deliberately:
 * <ul>
 * <li>The decorator keeps <em>its own</em> extrapolation flag. Enabling extrapolation on the decorated interpolation
 * does not propagate: an out-of-range call still throws until {@link #enableExtrapolation()} is called here.
 * Internally every delegated call passes {@code allowExtrapolation = true}, so the decorated object's own flag never
 * matters.</li>
 * <li>The out-of-range test in {@link #derivative(double)} and {@link #secondDerivative(double)} is
 * <em>strict</em> ({@code x < xMin() || x > xMax()}). Exactly at a boundary the derivative comes from the decorated
 * interpolation, not from the flat branch.</li>
 * <li>{@link #primitive(double)} extends <em>linearly</em> outside the range — the integral of a constant — rather
 * than flat.</li>
 * </ul>
 *
 * @author Jose Moya
 */
public class FlatExtrapolator implements Interpolation {

    private final Interpolation decoratedInterp;
    private final DefaultExtrapolator delegatedExtrapolator = new DefaultExtrapolator();

    public FlatExtrapolator(final Interpolation decoratedInterpolation) {
        QL.require(decoratedInterpolation != null, "no interpolation to decorate");
        this.decoratedInterp = decoratedInterpolation;
        // Mirrors FlatExtrapolatorImpl's constructor, which calls calculate() -> decoratedInterp_->update().
        this.decoratedInterp.update();
    }

    /**
     * The interpolation this decorator wraps.
     */
    public Interpolation decoratedInterpolation() {
        return decoratedInterp;
    }

    //
    // implements Interpolation
    //

    @Override
    public boolean empty() /* @ReadOnly */ {
        return decoratedInterp.empty();
    }

    @Override
    public /*@Real*/ double op(final /*@Real*/ double x) /* @ReadOnly */ {
        return op(x, false);
    }

    @Override
    public /*@Real*/ double op(final /*@Real*/ double x, final boolean allowExtrapolation) /* @ReadOnly */ {
        checkRange(x, allowExtrapolation);
        return decoratedInterp.op(bind(x), true);
    }

    @Override
    public /*@Real*/ double primitive(final /*@Real*/ double x) /* @ReadOnly */ {
        return primitive(x, false);
    }

    @Override
    public /*@Real*/ double primitive(final /*@Real*/ double x, final boolean allowExtrapolation) /* @ReadOnly */ {
        checkRange(x, allowExtrapolation);
        if ( x < xMin() ) {
            return decoratedInterp.primitive(xMin(), true) + decoratedInterp.op(xMin(), true) * (x - xMin());
        }
        if ( x > xMax() ) {
            return decoratedInterp.primitive(xMax(), true) + decoratedInterp.op(xMax(), true) * (x - xMax());
        }
        return decoratedInterp.primitive(x, true);
    }

    @Override
    public /*@Real*/ double derivative(final /*@Real*/ double x) /* @ReadOnly */ {
        return derivative(x, false);
    }

    @Override
    public /*@Real*/ double derivative(final /*@Real*/ double x, final boolean allowExtrapolation) /* @ReadOnly */ {
        checkRange(x, allowExtrapolation);
        if ( x < xMin() || x > xMax() ) {
            return 0.0;
        }
        return decoratedInterp.derivative(x, true);
    }

    @Override
    public /*@Real*/ double secondDerivative(final /*@Real*/ double x) /* @ReadOnly */ {
        return secondDerivative(x, false);
    }

    @Override
    public /*@Real*/ double secondDerivative(final /*@Real*/ double x,
            final boolean allowExtrapolation) /* @ReadOnly */ {
        checkRange(x, allowExtrapolation);
        if ( x < xMin() || x > xMax() ) {
            return 0.0;
        }
        return decoratedInterp.secondDerivative(x, true);
    }

    @Override
    public /*@Real*/ double xMin() /* @ReadOnly */ {
        return decoratedInterp.xMin();
    }

    @Override
    public /*@Real*/ double xMax() /* @ReadOnly */ {
        return decoratedInterp.xMax();
    }

    @Override
    public boolean isInRange(final /*@Real*/ double x) /* @ReadOnly */ {
        return decoratedInterp.isInRange(x);
    }

    @Override
    public void update() {
        decoratedInterp.update();
    }

    //
    // implements Extrapolator
    //

    @Override
    public boolean allowsExtrapolation() {
        return delegatedExtrapolator.allowsExtrapolation();
    }

    @Override
    public void disableExtrapolation() {
        delegatedExtrapolator.disableExtrapolation();
    }

    @Override
    public void enableExtrapolation() {
        delegatedExtrapolator.enableExtrapolation();
    }

    //
    // private methods
    //

    /**
     * Clamps {@code x} into the decorated range — the flat part of "flat extrapolation".
     */
    private double bind(final double x) {
        return Math.min(Math.max(x, xMin()), xMax());
    }

    /**
     * Mirrors C++ {@code Interpolation::checkRange}. Note it consults <em>this</em> decorator's extrapolation flag,
     * while the range itself comes from the decorated interpolation.
     */
    private void checkRange(final double x, final boolean extrapolate) /* @ReadOnly */ {
        if ( !(extrapolate || allowsExtrapolation() || isInRange(x)) ) {
            throw new IllegalArgumentException("interpolation range is [" + xMin() + ", " + xMax()
                    + "]: extrapolation at " + x + " not allowed");
        }
    }
}
