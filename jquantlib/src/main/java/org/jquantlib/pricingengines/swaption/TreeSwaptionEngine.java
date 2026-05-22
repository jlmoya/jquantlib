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
package org.jquantlib.pricingengines.swaption;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Settlement;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.methods.lattices.Lattice;
import org.jquantlib.model.shortrate.ShortRateModel;
import org.jquantlib.model.shortrate.onefactormodels.TermStructureConsistentModel;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Numerical lattice engine for swaptions.
 * <p>
 * Port of C++ v1.42.1 {@code ql/pricingengines/swaption/treeswaptionengine.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ uses a templated {@code LatticeShortRateModelEngine<args, results>}
 *     base; Java has no such generic engine, so this class extends
 *     {@link Swaption.EngineImpl} directly and holds the model + step config
 *     as private fields.
 * <li>The C++ ctor that takes a {@code Handle<ShortRateModel>} is omitted —
 *     Java callers can pass a concrete {@link ShortRateModel} instance.
 * </ul>
 *
 * <p><strong>Warning</strong> (mirrors C++): this engine is not guaranteed to
 * work if the underlying swap has a start date in the past.
 */
public class TreeSwaptionEngine extends Swaption.EngineImpl {

    private final ShortRateModel model_;
    private final TimeGrid timeGrid_;
    private final int timeSteps_;
    private final Lattice lattice_;
    private final Handle< YieldTermStructure > termStructure_;

    /**
     * Build with a step count. The grid is constructed lazily from the swaption's mandatory times in
     * {@link #calculate()}.
     */
    public TreeSwaptionEngine(final ShortRateModel model, final int timeSteps,
            final Handle< YieldTermStructure > termStructure) {
        super();
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
    public TreeSwaptionEngine(final ShortRateModel model, final TimeGrid grid,
            final Handle< YieldTermStructure > termStructure) {
        super();
        this.model_ = model;
        this.timeGrid_ = grid;
        this.timeSteps_ = 0;
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
        final Swaption.ArgumentsImpl args = (Swaption.ArgumentsImpl) arguments_;
        final Swaption.ResultsImpl results = (Swaption.ResultsImpl) results_;

        QL.require(args.settlementMethod != Settlement.Method.ParYieldCurve,
                "cash settled (ParYieldCurve) swaptions not priced with TreeSwaptionEngine");
        QL.require(model_ != null, "no model specified");

        final Date referenceDate;
        final DayCounter dayCounter;
        if ( model_ instanceof TermStructureConsistentModel ) {
            final TermStructureConsistentModel tsm = (TermStructureConsistentModel) model_;
            referenceDate = tsm.termStructure().currentLink().referenceDate();
            dayCounter = tsm.termStructure().currentLink().dayCounter();
        } else {
            QL.require(termStructure_ != null && !termStructure_.empty(), "no term structure available");
            referenceDate = termStructure_.currentLink().referenceDate();
            dayCounter = termStructure_.currentLink().dayCounter();
        }

        final DiscretizedSwaption swaption = new DiscretizedSwaption(args, referenceDate, dayCounter);

        final Lattice lattice;
        if ( lattice_ != null ) {
            lattice = lattice_;
        } else {
            final List< Double > mandatory = swaption.mandatoryTimes();
            final TimeGrid grid = new TimeGrid(mandatory, timeSteps_);
            lattice = model_.tree(grid);
        }

        final List< Double > stoppingTimes = new ArrayList<>(args.exercise.dates().size());
        for ( int i = 0; i < args.exercise.dates().size(); i++ ) {
            stoppingTimes.add(dayCounter.yearFraction(referenceDate, args.exercise.date(i)));
        }

        final double last = stoppingTimes.get(stoppingTimes.size() - 1);
        swaption.initialize(lattice, last);

        // First non-negative stopping time — mirrors C++ find_if(t >= 0.0).
        double nextExercise = stoppingTimes.get(0);
        for ( final double t : stoppingTimes ) {
            if ( t >= 0.0 ) {
                nextExercise = t;
                break;
            }
        }
        swaption.rollback(nextExercise);

        results.value = swaption.presentValue();
    }
}
