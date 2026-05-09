/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2008 Allen Kuo
 Copyright (C) 2021 Ralf Konrad Eckel

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.experimental.callablebonds;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.callablebonds.CallableBond.CallableBondArgumentsImpl;
import org.jquantlib.experimental.callablebonds.CallableBond.CallableBondEngineImpl;
import org.jquantlib.experimental.callablebonds.CallableBond.CallableBondResultsImpl;
import org.jquantlib.methods.lattices.Lattice;
import org.jquantlib.model.shortrate.ShortRateModel;
import org.jquantlib.model.shortrate.onefactormodels.TermStructureConsistentModel;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

/**
 * Numerical lattice engine for callable fixed-rate bonds.
 * <p>
 * Port of C++ v1.42.1
 * {@code ql/experimental/callablebonds/treecallablebondengine.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ uses a templated {@code LatticeShortRateModelEngine<args, results>}
 *     base; Java has no such generic engine, so this class extends
 *     {@link CallableBondEngineImpl} directly and holds the model + step
 *     configuration as private fields (mirrors {@code TreeSwaptionEngine}).
 * <li>{@code calculateWithSpread(Spread s)} branch that calls
 *     {@code OneFactorModel.ShortRateTree.setSpread(s)} cannot be ported until
 *     {@code ShortRateTree.setSpread} is added to JQuantLib. The engine
 *     therefore ignores any non-zero spread on
 *     {@link CallableBondArgumentsImpl#spread} and prices at the unspread
 *     short-rate model. {@link CallableBond}'s OAS / cleanPriceOAS methods are
 *     unsupported until that infrastructure lands (see CallableBond class
 *     comment).
 * </ul>
 */
public class TreeCallableFixedRateBondEngine extends CallableBondEngineImpl {

    private final ShortRateModel model_;
    private final TimeGrid timeGrid_;
    private final int timeSteps_;
    private final Lattice lattice_;
    private final Handle<YieldTermStructure> termStructure_;

    public TreeCallableFixedRateBondEngine(final ShortRateModel model, final int timeSteps,
            final Handle<YieldTermStructure> termStructure) {
        super();
        this.model_ = model;
        this.timeSteps_ = timeSteps;
        this.timeGrid_ = null;
        this.lattice_ = null;
        this.termStructure_ = termStructure;
        if (this.model_ != null) {
            this.model_.addObserver(this);
        }
        if (this.termStructure_ != null) {
            this.termStructure_.addObserver(this);
        }
    }

    public TreeCallableFixedRateBondEngine(final ShortRateModel model, final TimeGrid grid,
            final Handle<YieldTermStructure> termStructure) {
        super();
        this.model_ = model;
        this.timeGrid_ = grid;
        this.timeSteps_ = 0;
        this.lattice_ = (model != null) ? model.tree(grid) : null;
        this.termStructure_ = termStructure;
        if (this.model_ != null) {
            this.model_.addObserver(this);
        }
        if (this.termStructure_ != null) {
            this.termStructure_.addObserver(this);
        }
    }

    @Override
    public void calculate() {
        calculateWithSpread(((CallableBondArgumentsImpl) arguments_).spread);
    }

    private void calculateWithSpread(final double s) {
        QL.require(model_ != null, "no model specified");

        final Handle<YieldTermStructure> discountCurve;
        if (model_ instanceof TermStructureConsistentModel) {
            discountCurve = ((TermStructureConsistentModel) model_).termStructure();
        } else {
            discountCurve = termStructure_;
        }
        QL.require(discountCurve != null && !discountCurve.empty(),
                "no term structure available");

        final CallableBondArgumentsImpl args = (CallableBondArgumentsImpl) arguments_;
        final CallableBondResultsImpl results = (CallableBondResultsImpl) results_;

        final DiscretizedCallableFixedRateBond callableBond = new DiscretizedCallableFixedRateBond(
                args, discountCurve);

        final Lattice lattice;
        if (lattice_ != null) {
            lattice = lattice_;
        } else {
            final List<Double> times = callableBond.mandatoryTimes();
            final TimeGrid grid = new TimeGrid(times, timeSteps_);
            lattice = model_.tree(grid);
        }

        if (s != 0.0) {
            // Java port: ShortRateTree.setSpread is not yet ported. The C++
            // engine errors out for non-OneFactor lattices; here we error
            // out for ANY non-zero spread until setSpread is wired up.
            QL.require(false,
                    "Spread != 0 is not supported in JQuantLib until ShortRateTree.setSpread is ported");
        }

        final Date referenceDate = discountCurve.currentLink().referenceDate();
        final DayCounter dayCounter = discountCurve.currentLink().dayCounter();
        final double redemptionTime = dayCounter.yearFraction(referenceDate, args.redemptionDate);

        callableBond.initialize(lattice, redemptionTime);
        callableBond.rollback(0.0);

        results.value = callableBond.presentValue();

        final double d = discountCurve.currentLink().discount(args.settlementDate);
        results.settlementValue = results.value / d;
    }
}
