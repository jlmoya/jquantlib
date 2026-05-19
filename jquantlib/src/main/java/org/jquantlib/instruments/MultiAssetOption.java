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

/*
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2004 Neil Firth
 Copyright (C) 2007 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.util.Observer;

/**
 * Base class for options on multiple assets.
 *
 * <p>Ported from C++ QuantLib v1.42.1 ql/instruments/multiassetoption.{hpp,cpp}.</p>
 *
 * @author Jose Moya
 */
public class MultiAssetOption extends Option {

    //
    // Greeks (mutable result fields)
    //

    protected double delta;
    protected double gamma;
    protected double theta;
    protected double vega;
    protected double rho;
    protected double dividendRho;

    //
    // public constructors
    //

    public MultiAssetOption(final Payoff payoff, final Exercise exercise) {
        super(payoff, exercise);
    }

    //
    // Instrument interface
    //

    @Override
    public boolean isExpired() /* @ReadOnly */ {
        return exercise.lastDate().lt(new Settings().evaluationDate());
    }

    //
    // greeks
    //

    public double delta() {
        calculate();
        QL.require(delta != Constants.NULL_REAL, "delta not provided");
        return delta;
    }

    public double gamma() {
        calculate();
        QL.require(gamma != Constants.NULL_REAL, "gamma not provided");
        return gamma;
    }

    public double theta() {
        calculate();
        QL.require(theta != Constants.NULL_REAL, "theta not provided");
        return theta;
    }

    public double vega() {
        calculate();
        QL.require(vega != Constants.NULL_REAL, "vega not provided");
        return vega;
    }

    public double rho() {
        calculate();
        QL.require(rho != Constants.NULL_REAL, "rho not provided");
        return rho;
    }

    public double dividendRho() {
        calculate();
        QL.require(dividendRho != Constants.NULL_REAL, "dividend rho not provided");
        return dividendRho;
    }

    @Override
    protected void setupExpired() /* @ReadOnly */ {
        super.setupExpired();
        delta = gamma = theta = vega = rho = dividendRho = 0.0;
    }

    @Override
    public void fetchResults(final PricingEngine.Results r) /* @ReadOnly */ {
        super.fetchResults(r);

        QL.require(MultiAssetOption.Results.class.isAssignableFrom(r.getClass()), ReflectConstants.WRONG_ARGUMENT_TYPE);

        final MultiAssetOption.ResultsImpl ri = (MultiAssetOption.ResultsImpl) r;
        final Option.GreeksImpl results = ri.greeks();

        QL.ensure(results != null, "no greeks returned from pricing engine");

        delta = results.delta;
        gamma = results.gamma;
        theta = results.theta;
        vega = results.vega;
        rho = results.rho;
        dividendRho = results.dividendRho;
    }

    //
    // public inner interfaces
    //

    /**
     * basic multi-asset option arguments (inherits from Option.Arguments)
     */
    public interface Arguments extends Option.Arguments { /* marking interface */
    }

    /**
     * Results from multi-asset option calculation
     */
    public interface Results extends Instrument.Results, Option.Greeks { /* marking interface */
    }

    public interface Engine extends PricingEngine, Observer { /* marking interface */
    }

    //
    // public static inner classes
    //

    public static class ArgumentsImpl extends Option.ArgumentsImpl implements MultiAssetOption.Arguments { /* marking */
    }

    public static class ResultsImpl extends Instrument.ResultsImpl implements MultiAssetOption.Results {

        private final Option.GreeksImpl greeks;

        public ResultsImpl() {
            greeks = new Option.GreeksImpl();
        }

        public final Option.GreeksImpl greeks() {
            return greeks;
        }

        @Override
        public void reset() /* @ReadOnly */ {
            super.reset();
            greeks.reset();
        }
    }

    /**
     * The pricing engine for multi-asset options
     */
    public static abstract class EngineImpl
            extends GenericEngine< MultiAssetOption.ArgumentsImpl, MultiAssetOption.ResultsImpl >
            implements MultiAssetOption.Engine {

        public EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }

        public EngineImpl(final MultiAssetOption.ArgumentsImpl arguments, final MultiAssetOption.ResultsImpl results) {
            super(arguments, results);
        }
    }
}
