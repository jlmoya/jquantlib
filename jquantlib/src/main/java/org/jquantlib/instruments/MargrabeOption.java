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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;

/**
 * Margrabe option on two assets.
 *
 * <p>This option gives the holder the right to exchange Q2 stocks of the
 * second asset for Q1 stocks of the first at expiration.
 *
 * <p>Phase 5i.5-MGR port of {@code QuantLib::MargrabeOption}
 * (v1.42.1 ql/instruments/margrabeoption.{hpp,cpp}). Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 */
public class MargrabeOption extends MultiAssetOption {

    private final int Q1_;
    private final int Q2_;
    private double delta1_;
    private double delta2_;
    private double gamma1_;
    private double gamma2_;

    public MargrabeOption(final int Q1, final int Q2, final Exercise exercise) {
        super(new NullPayoff(), exercise);
        this.Q1_ = Q1;
        this.Q2_ = Q2;
    }

    public double delta1() {
        calculate();
        QL.require(delta1_ != Constants.NULL_REAL, "delta1 not provided");
        return delta1_;
    }

    public double delta2() {
        calculate();
        QL.require(delta2_ != Constants.NULL_REAL, "delta2 not provided");
        return delta2_;
    }

    public double gamma1() {
        calculate();
        QL.require(gamma1_ != Constants.NULL_REAL, "gamma1 not provided");
        return gamma1_;
    }

    public double gamma2() {
        calculate();
        QL.require(gamma2_ != Constants.NULL_REAL, "gamma2 not provided");
        return gamma2_;
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);
        QL.require(args instanceof MargrabeOption.ArgumentsImpl, ReflectConstants.WRONG_ARGUMENT_TYPE);
        final MargrabeOption.ArgumentsImpl a = (MargrabeOption.ArgumentsImpl) args;
        a.Q1 = Q1_;
        a.Q2 = Q2_;
    }

    @Override
    public void fetchResults(final PricingEngine.Results r) {
        super.fetchResults(r);
        QL.require(r instanceof MargrabeOption.ResultsImpl, "wrong result type");
        final MargrabeOption.ResultsImpl mr = (MargrabeOption.ResultsImpl) r;
        delta1_ = mr.delta1;
        delta2_ = mr.delta2;
        gamma1_ = mr.gamma1;
        gamma2_ = mr.gamma2;
    }

    //
    // public inner interfaces
    //

    public interface Arguments extends MultiAssetOption.Arguments { /* marker */
    }

    public interface Results extends MultiAssetOption.Results { /* marker */
    }

    //
    // public inner classes
    //

    /**
     * Extra arguments for Margrabe option calculation.
     */
    public static class ArgumentsImpl extends MultiAssetOption.ArgumentsImpl implements MargrabeOption.Arguments {

        public int Q1 = -1;   // sentinel for "unspecified"
        public int Q2 = -1;

        @Override
        public void validate() {
            super.validate();
            QL.require(Q1 != -1, "unspecified quantity for asset 1");
            QL.require(Q2 != -1, "unspecified quantity for asset 2");
            QL.require(Q1 > 0, "quantity of asset 1 must be positive");
            QL.require(Q2 > 0, "quantity of asset 2 must be positive");
        }
    }

    /**
     * Extra results for Margrabe option.
     */
    public static class ResultsImpl extends MultiAssetOption.ResultsImpl implements MargrabeOption.Results {

        public double delta1 = Constants.NULL_REAL;
        public double delta2 = Constants.NULL_REAL;
        public double gamma1 = Constants.NULL_REAL;
        public double gamma2 = Constants.NULL_REAL;

        @Override
        public void reset() {
            super.reset();
            delta1 = Constants.NULL_REAL;
            delta2 = Constants.NULL_REAL;
            gamma1 = Constants.NULL_REAL;
            gamma2 = Constants.NULL_REAL;
        }
    }

    /**
     * Margrabe-option engine base class.
     */
    public abstract static class EngineImpl
            extends GenericEngine< MargrabeOption.ArgumentsImpl, MargrabeOption.ResultsImpl > {

        public EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }
    }
}
