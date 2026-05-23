/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
*/

package org.jquantlib.pricingengines.capfloor;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.methods.lattices.Lattice;
import org.jquantlib.model.shortrate.ShortRateModel;
import org.jquantlib.model.shortrate.onefactormodels.TermStructureConsistentModel;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

import java.util.List;

/**
 * Numerical lattice engine for cap/floors.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/capfloor/treecapfloorengine.{hpp,cpp}} (Phase 2 L3-D). Mirrors the same pattern as
 * {@link org.jquantlib.pricingengines.swaption.TreeSwaptionEngine}: build a {@link DiscretizedCapFloor}, lay it on the
 * model's tree (built either from explicit time grid or a step count + mandatory times), roll back to the cap's first
 * start date, and read off the present value.
 *
 * <p>C++ inherits from {@code LatticeShortRateModelEngine}; Java has no
 * such generic engine, so this class extends {@link CapFloor.Engine} directly and carries the model + step config as
 * private fields.
 */
public class TreeCapFloorEngine extends CapFloor.Engine {

    private final ShortRateModel model_;
    private final TimeGrid timeGrid_;
    private final int timeSteps_;
    private final Lattice lattice_;
    private final Handle< YieldTermStructure > termStructure_;

    /** Build with a step count; the grid is constructed lazily from the cap's mandatory times in {@link #calculate()}. */
    public TreeCapFloorEngine(final ShortRateModel model, final int timeSteps,
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

    /** Step-count overload without a term structure (model must be {@link TermStructureConsistentModel}). */
    public TreeCapFloorEngine(final ShortRateModel model, final int timeSteps) {
        this(model, timeSteps, new Handle< YieldTermStructure >());
    }

    /** Build with an explicit time grid; the model's tree is built on this grid up-front. */
    public TreeCapFloorEngine(final ShortRateModel model, final TimeGrid grid,
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
        QL.require(model_ != null, "no model specified");

        final CapFloor.ArgumentsImpl args = (CapFloor.ArgumentsImpl) arguments_;
        final CapFloor.ResultsImpl results = (CapFloor.ResultsImpl) results_;

        final Date referenceDate;
        final DayCounter dayCounter;
        if ( model_ instanceof TermStructureConsistentModel tsm ) {
            referenceDate = tsm.termStructure().currentLink().referenceDate();
            dayCounter = tsm.termStructure().currentLink().dayCounter();
        } else {
            QL.require(termStructure_ != null && !termStructure_.empty(), "no term structure available");
            referenceDate = termStructure_.currentLink().referenceDate();
            dayCounter = termStructure_.currentLink().dayCounter();
        }

        final DiscretizedCapFloor capfloor = new DiscretizedCapFloor(args, referenceDate, dayCounter);

        final Lattice lattice;
        if ( lattice_ != null ) {
            lattice = lattice_;
        } else {
            final List< Double > mandatory = capfloor.mandatoryTimes();
            final TimeGrid grid = new TimeGrid(mandatory, timeSteps_);
            lattice = model_.tree(grid);
        }

        final double firstTime = dayCounter.yearFraction(referenceDate, args.startDates[0]);
        final double lastTime = dayCounter.yearFraction(referenceDate, args.endDates[args.endDates.length - 1]);

        capfloor.initialize(lattice, lastTime);
        capfloor.rollback(firstTime);

        results.value = capfloor.presentValue();
    }
}
