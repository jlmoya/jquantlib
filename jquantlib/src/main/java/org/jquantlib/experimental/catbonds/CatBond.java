/*
 Copyright (C) 2012, 2013 Grzegorz Andruszkiewicz
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

package org.jquantlib.experimental.catbonds;

import org.jquantlib.QL;
import org.jquantlib.instruments.Bond;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * Catastrophe bond base class.
 *
 * <p>Port of {@code ql/experimental/catbonds/catbond.hpp/.cpp} {@code CatBond}.
 *
 * @category instruments
 */
public class CatBond extends Bond {

    protected final NotionalRisk notionalRisk_;

    protected /* mutable */ double lossProbability_;
    protected /* mutable */ double exhaustionProbability_;
    protected /* mutable */ double expectedLoss_;

    public CatBond(final int settlementDays, final Calendar calendar, final Date issueDate,
            final NotionalRisk notionalRisk) {

        super(settlementDays, calendar, issueDate);
        this.notionalRisk_ = notionalRisk;
    }

    public double lossProbability() {
        return lossProbability_;
    }

    public double expectedLoss() {
        return expectedLoss_;
    }

    public double exhaustionProbability() {
        return exhaustionProbability_;
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        QL.require(args instanceof CatBond.ArgumentsImpl, "wrong arguments type for CatBond");
        final CatBond.ArgumentsImpl arguments = (CatBond.ArgumentsImpl) args;

        super.setupArguments(args);

        arguments.notionalRisk = notionalRisk_;
        arguments.startDate = issueDate();
    }

    @Override
    protected void fetchResults(final PricingEngine.Results r) {
        QL.require(r instanceof CatBond.ResultsImpl, "wrong result type for CatBond");
        final CatBond.ResultsImpl results = (CatBond.ResultsImpl) r;

        super.fetchResults(r);

        lossProbability_ = results.lossProbability;
        expectedLoss_ = results.expectedLoss;
        exhaustionProbability_ = results.exhaustionProbability;
    }

    // -----------------------------------------------------------------------
    // Inner types
    // -----------------------------------------------------------------------

    public static class ArgumentsImpl extends Bond.ArgumentsImpl {

        public Date startDate;
        public NotionalRisk notionalRisk;

        @Override
        public void validate() {
            super.validate();
            QL.require(notionalRisk != null, "null notionalRisk");
        }
    }

    public static class ResultsImpl extends Bond.ResultsImpl {

        public double lossProbability;
        public double exhaustionProbability;
        public double expectedLoss;

        @Override
        public void reset() {
            lossProbability = Constants.NULL_REAL;
            exhaustionProbability = Constants.NULL_REAL;
            expectedLoss = Constants.NULL_REAL;
            super.reset();
        }
    }

    /** Base class for cat-bond pricing engines. */
    public static abstract class EngineImpl extends GenericEngine< CatBond.ArgumentsImpl, CatBond.ResultsImpl >
            implements Bond.Engine {

        protected EngineImpl() {
            super(new CatBond.ArgumentsImpl(), new CatBond.ResultsImpl());
        }
    }
}
