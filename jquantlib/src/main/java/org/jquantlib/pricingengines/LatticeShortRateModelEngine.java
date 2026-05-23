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
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2005 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.pricingengines;

import org.jquantlib.QL;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.methods.lattices.Lattice;
import org.jquantlib.model.shortrate.ShortRateModel;
import org.jquantlib.time.TimeGrid;

/**
 * Engine for a short-rate model specialised on a lattice.
 * <p>
 * Port of C++ v1.42.1
 * {@code ql/pricingengines/latticeshortratemodelengine.hpp}. Derived engines
 * only need to implement {@code calculate()}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ takes both an {@code ext::shared_ptr<ShortRateModel>} and a
 *     {@code Handle<ShortRateModel>}; Java accepts a {@link ShortRateModel}
 *     instance directly. Handles are not used for short-rate models in the
 *     Java port (none of the existing tree engines passes one), so the second
 *     constructor variant is omitted.</li>
 * <li>The C++ class uses templates {@code <Arguments, Results>}; Java uses
 *     bounded generics {@code <A extends Instrument.Arguments,
 *     R extends Instrument.Results>}. Behaviour matches: the base class holds
 *     the model + time grid + step count, rebuilds the lattice from the model
 *     on {@link #update()} when a grid is supplied, and exposes the lattice to
 *     subclasses via the {@link #lattice()} accessor (the C++ field
 *     {@code lattice_} is protected; Java uses an accessor to avoid exposing a
 *     mutable protected reference).</li>
 * <li>The existing {@link org.jquantlib.pricingengines.swaption.TreeSwaptionEngine}
 *     (Phase 2e) was written before this base class existed; it intentionally
 *     extends {@code Swaption.EngineImpl} directly and replicates the
 *     lattice-build logic inline. Future tree engines should prefer this base.</li>
 * </ul>
 *
 * @param <A> instrument arguments type
 * @param <R> instrument results type
 */
public abstract class LatticeShortRateModelEngine< A extends Instrument.Arguments, R extends Instrument.Results >
        extends GenericModelEngine< ShortRateModel, A, R > {

    //
    // protected fields
    //

    /** Time grid (may be {@code null} when constructed with a step count). */
    protected TimeGrid timeGrid_;

    /** Step count (0 when constructed with an explicit time grid). */
    protected int timeSteps_;

    /**
     * Lattice. Built up-front in the {@code (model, TimeGrid)} ctor and
     * rebuilt in {@link #update()}; null in the {@code (model, timeSteps)}
     * ctor (subclasses build it lazily in {@code calculate()}).
     */
    protected Lattice lattice_;

    //
    // public constructors
    //

    /**
     * Build with a positive step count. The grid + lattice are built lazily
     * by subclasses in {@code calculate()} from the instrument's mandatory
     * times. Mirrors C++ ctor #1.
     */
    protected LatticeShortRateModelEngine(final ShortRateModel model, final int timeSteps, final A arguments,
            final R results) {
        super(model, arguments, results);
        QL.require(timeSteps > 0, "timeSteps must be positive, " + timeSteps + " not allowed");
        this.timeSteps_ = timeSteps;
        this.timeGrid_ = null;
        this.lattice_ = null;
    }

    /**
     * Build with an explicit time grid. The model's tree is built up-front on
     * this grid. Mirrors C++ ctor #3 (the {@code Handle<ShortRateModel>}
     * variant is omitted — see class javadoc).
     */
    protected LatticeShortRateModelEngine(final ShortRateModel model, final TimeGrid timeGrid, final A arguments,
            final R results) {
        super(model, arguments, results);
        this.timeGrid_ = timeGrid;
        this.timeSteps_ = 0;
        this.lattice_ = (model != null) ? model.tree(timeGrid) : null;
    }

    //
    // public methods
    //

    /**
     * Accessor for the lattice. Convenience over {@link #lattice_} for
     * subclasses outside this package.
     */
    public Lattice lattice() {
        return lattice_;
    }

    /**
     * Rebuild the lattice from the model on the cached time grid (if any),
     * then propagate to {@link GenericEngine#update()}.
     * <p>
     * Mirrors C++ {@code LatticeShortRateModelEngine::update()}.
     */
    @Override
    public void update() {
        if ( timeGrid_ != null && !timeGrid_.empty() && model != null ) {
            lattice_ = model.tree(timeGrid_);
        }
        super.update();
    }
}
