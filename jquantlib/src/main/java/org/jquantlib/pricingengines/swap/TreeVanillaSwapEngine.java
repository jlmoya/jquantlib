/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2005, 2007 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.pricingengines.swap;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Swap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.methods.lattices.Lattice;
import org.jquantlib.model.shortrate.ShortRateModel;
import org.jquantlib.model.shortrate.onefactormodels.TermStructureConsistentModel;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

import java.util.List;

/**
 * Numerical lattice engine for simple swaps.
 * <p>
 * Port of C++ v1.42.1 {@code ql/pricingengines/swap/treeswapengine.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ extends a templated {@code LatticeShortRateModelEngine<args, results>}
 *     base class; Java has no such generic engine, so this class extends
 *     {@link Swap.EngineImpl} directly and holds the model + step config as
 *     private fields. Matches the precedent set by
 *     {@link org.jquantlib.pricingengines.swaption.TreeSwaptionEngine}.</li>
 * <li>C++ {@code DiscretizedSwap} is constructed from
 *     {@code VanillaSwap::arguments}; the Java port's {@link DiscretizedSwap}
 *     reads leg structure directly from a {@link VanillaSwap} reference (see
 *     {@code DiscretizedSwap}'s class-level note), so this engine takes the
 *     {@link VanillaSwap} as a constructor argument and forwards it.
 *     Functionally equivalent; the engine still consumes the swap via the
 *     standard {@code setPricingEngine} → {@code calculate} flow.</li>
 * </ul>
 *
 * <p>Pricing logic: builds a {@link DiscretizedSwap}, asks the model for a
 * {@link Lattice} on the swap's mandatory times (or on an explicit
 * {@link TimeGrid} when supplied), initializes the asset at the maximum
 * mandatory time, rolls back to 0.0, and reads the present value.
 *
 * <p>Source: C++ v1.42.1 {@code ql/pricingengines/swap/treeswapengine.cpp}
 *
 * @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 */
public class TreeVanillaSwapEngine extends Swap.EngineImpl {

    private final VanillaSwap swap_;
    private final ShortRateModel model_;
    private final int timeSteps_;
    private final TimeGrid timeGrid_;
    private final Lattice lattice_;
    private final Handle< YieldTermStructure > termStructure_;

    /**
     * Build with a step count. The grid is constructed lazily from the swap's mandatory times in {@link #calculate()}.
     *
     * @param swap          the underlying {@link VanillaSwap} (Java deviation — C++ extracts everything from
     *                      {@code VanillaSwap::arguments}).
     * @param model         short-rate model providing the lattice.
     * @param timeSteps     time-step count passed to the lazy {@link TimeGrid}.
     * @param termStructure optional discount curve; only needed when {@code model} is not a
     *                      {@link TermStructureConsistentModel}.
     */
    public TreeVanillaSwapEngine(final VanillaSwap swap, final ShortRateModel model, final int timeSteps,
            final Handle< YieldTermStructure > termStructure) {
        super();
        this.swap_ = swap;
        this.model_ = model;
        this.timeSteps_ = timeSteps;
        this.timeGrid_ = null;
        this.lattice_ = null;
        this.termStructure_ = termStructure;
        if ( this.model_ != null ) {
            this.model_.addObserver(this);
        }
        if ( this.termStructure_ != null ) {
            this.termStructure_.addObserver(this);
        }
    }

    /**
     * Build with an explicit time grid; the model's tree is built on this grid up-front.
     */
    public TreeVanillaSwapEngine(final VanillaSwap swap, final ShortRateModel model, final TimeGrid grid,
            final Handle< YieldTermStructure > termStructure) {
        super();
        this.swap_ = swap;
        this.model_ = model;
        this.timeSteps_ = 0;
        this.timeGrid_ = grid;
        this.lattice_ = (model != null) ? model.tree(grid) : null;
        this.termStructure_ = termStructure;
        if ( this.model_ != null ) {
            this.model_.addObserver(this);
        }
        if ( this.termStructure_ != null ) {
            this.termStructure_.addObserver(this);
        }
    }

    @Override
    public void calculate() /* @ReadOnly */ {
        QL.require(model_ != null, "no model specified");
        QL.require(swap_ != null, "no underlying swap supplied");

        final Date referenceDate;
        final DayCounter dayCounter;
        if (model_ instanceof TermStructureConsistentModel tsm) {
            referenceDate = tsm.termStructure().currentLink().referenceDate();
            dayCounter = tsm.termStructure().currentLink().dayCounter();
        } else {
            QL.require(termStructure_ != null && !termStructure_.empty(), "no term structure available");
            referenceDate = termStructure_.currentLink().referenceDate();
            dayCounter = termStructure_.currentLink().dayCounter();
        }

        final DiscretizedSwap swap = new DiscretizedSwap(swap_, referenceDate, dayCounter);
        final List< Double > times = swap.mandatoryTimes();

        final Lattice lattice;
        if ( lattice_ != null ) {
            lattice = lattice_;
        } else {
            final TimeGrid grid = new TimeGrid(times, timeSteps_);
            lattice = model_.tree(grid);
        }

        double maxTime = Double.NEGATIVE_INFINITY;
        for ( final double t : times ) {
            if ( t > maxTime ) {
                maxTime = t;
            }
        }
        swap.initialize(lattice, maxTime);
        swap.rollback(0.0);

        // Swap.EngineImpl exposes results_ typed as Swap.Results (interface);
        // the concrete object is Swap.ResultsImpl, populated by Swap's own
        // fetchResults path. We only set the NPV; leg-level results (NPV/BPS/
        // discounts) are not produced by the tree engine — matches C++ which
        // only writes results_.value.
        final org.jquantlib.instruments.Swap.ResultsImpl r = (org.jquantlib.instruments.Swap.ResultsImpl) results_;
        r.value = swap.presentValue();
        // Set leg arrays to empty so Swap.fetchResults' length-check branches
        // take the "fill NULL_REAL" fallback rather than NPE on null arrays.
        r.legNPV = new double[0];
        r.legBPS = new double[0];
        r.startDiscounts = new double[0];
        r.endDiscounts = new double[0];
    }
}
