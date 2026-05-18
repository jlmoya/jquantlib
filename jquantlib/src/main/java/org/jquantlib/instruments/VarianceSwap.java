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
 Copyright (C) 2006 Warren Chou
 Copyright (C) 2007, 2008 StatPro Italia srl
*/
package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

/**
 * Variance swap instrument.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/instruments/varianceswap.{hpp,cpp}} (Phase 5e.5b-CFC-d-180).
 *
 * <p>A variance swap pays {@code position * notional * (realized_variance
 * - strike)} at maturity, where realized variance is the annualised
 * average squared return over the contract's life.
 *
 * <p>This class does not manage seasoned variance swaps (start date is
 * recorded but currently used only by replicating engines that mark it
 * as the inception date of the contract).
 */
public class VarianceSwap extends Instrument {

    //
    // data members
    //

    protected final Position position_;
    protected final double strike_;
    protected final double notional_;
    protected final Date startDate_;
    protected final Date maturityDate_;

    // results
    protected double variance_ = Double.NaN;


    //
    // constructor
    //

    public VarianceSwap(final Position position,
                        final double strike,
                        final double notional,
                        final Date startDate,
                        final Date maturityDate) {
        super();
        this.position_ = position;
        this.strike_ = strike;
        this.notional_ = notional;
        this.startDate_ = startDate;
        this.maturityDate_ = maturityDate;
    }


    //
    // Instrument interface
    //

    @Override
    public boolean isExpired() {
        return maturityDate_.lt(new Settings().evaluationDate());
    }


    //
    // Additional interface — inspectors
    //

    public double strike()         { return strike_; }
    public Position position()     { return position_; }
    public Date startDate()        { return startDate_; }
    public Date maturityDate()     { return maturityDate_; }
    public double notional()       { return notional_; }


    //
    // Additional interface — results
    //

    /** Mirrors C++ {@code VarianceSwap::variance()}. */
    public double variance() {
        calculate();
        QL.require(!Double.isNaN(variance_), "result not available");
        return variance_;
    }


    //
    // overrides Instrument
    //

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        QL.require(args instanceof VarianceSwap.ArgumentsImpl, "wrong argument type");
        final VarianceSwap.ArgumentsImpl a = (VarianceSwap.ArgumentsImpl) args;
        a.position     = position_;
        a.strike       = strike_;
        a.notional     = notional_;
        a.startDate    = startDate_;
        a.maturityDate = maturityDate_;
    }

    @Override
    public void fetchResults(final PricingEngine.Results r) {
        super.fetchResults(r);
        QL.require(r instanceof VarianceSwap.ResultsImpl, "wrong result type");
        final VarianceSwap.ResultsImpl results = (VarianceSwap.ResultsImpl) r;
        variance_ = results.variance;
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
        variance_ = Double.NaN;
    }


    //
    // public inner classes
    //

    /** Marking interface — extra fields in {@link ArgumentsImpl}. */
    public interface Arguments extends Instrument.Arguments { /* marker */ }

    /** Marking interface — extra field {@code variance} in {@link ResultsImpl}. */
    public interface Results extends Instrument.Results { /* marker */ }

    /**
     * Arguments for forward fair-variance calculation. Mirrors C++
     * {@code VarianceSwap::arguments}.
     */
    public static class ArgumentsImpl implements VarianceSwap.Arguments {
        public Position position;
        public double strike   = Double.NaN;
        public double notional = Double.NaN;
        public Date startDate;
        public Date maturityDate;

        @Override
        public void validate() {
            QL.require(!Double.isNaN(strike), "no strike given");
            QL.require(strike > 0.0, "negative or null strike given");
            QL.require(!Double.isNaN(notional), "no notional given");
            QL.require(notional > 0.0, "negative or null notional given");
            QL.require(startDate != null, "null start date given");
            QL.require(maturityDate != null, "null maturity date given");
        }
    }

    /**
     * Results from variance-swap calculation. Mirrors C++
     * {@code VarianceSwap::results}.
     */
    public static class ResultsImpl extends Instrument.ResultsImpl
            implements VarianceSwap.Results {

        public double variance = Double.NaN;

        @Override
        public void reset() {
            super.reset();
            variance = Double.NaN;
        }
    }

    /**
     * Pricing-engine base for variance swaps. Mirrors C++
     * {@code GenericEngine<VarianceSwap::arguments, VarianceSwap::results>}.
     */
    public abstract static class EngineImpl
            extends GenericEngine<VarianceSwap.Arguments, VarianceSwap.Results> {
        public EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }
    }
}
