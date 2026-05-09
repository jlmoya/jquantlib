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

/*
 Copyright (C) 2008 Andrea Odetti

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.mcbasket;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

/**
 * Base class for path-dependent options on multiple assets.
 *
 * <p>Phase 4i port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/pathmultiassetoption.{hpp,cpp}}.
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Subclasses provide {@link #pathPayoff()} and {@link #fixingDates()}; the
 * engine consumes both via the arguments object.
 */
public abstract class PathMultiAssetOption extends Instrument {

    /**
     * Default constructor (matches C++ explicit ctor with optional engine).
     * Pass {@code null} for no engine.
     */
    protected PathMultiAssetOption(final PricingEngine engine) {
        if (engine != null) {
            setPricingEngine(engine);
        }
    }

    protected PathMultiAssetOption() {
        this(null);
    }

    //
    // public abstract methods
    //

    public abstract PathPayoff pathPayoff();

    public abstract List<Date> fixingDates();

    //
    // overrides Instrument
    //

    @Override
    public boolean isExpired() /* @ReadOnly */ {
        final List<Date> dates = fixingDates();
        QL.require(!dates.isEmpty(), "no fixing dates given");
        final Date last = dates.get(dates.size() - 1);
        // Mirrors C++ detail::simple_event(...).hasOccurred(): event is in
        // the past or today (with includeRefDate semantics handled by
        // Settings).
        final Date today = new Settings().evaluationDate();
        return last.lt(today);
    }

    @Override
    protected void setupExpired() /* @ReadOnly */ {
        super.setupExpired();
        NPV = 0.0;
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        QL.require(args instanceof PathMultiAssetOption.ArgumentsImpl,
                "wrong argument type");
        final PathMultiAssetOption.ArgumentsImpl arguments =
                (PathMultiAssetOption.ArgumentsImpl) args;
        arguments.payoff = pathPayoff();
        arguments.fixingDates = fixingDates();
    }

    //
    // public inner interfaces
    //

    public interface Arguments extends Instrument.Arguments { /* marker */ }

    public interface Results extends Instrument.Results { /* marker */ }

    //
    // public inner classes
    //

    public static class ArgumentsImpl implements PathMultiAssetOption.Arguments {

        public PathPayoff payoff;
        public List<Date> fixingDates;

        @Override
        public void validate() /* @ReadOnly */ {
            QL.require(payoff != null, "no payoff given");
            QL.require(fixingDates != null && !fixingDates.isEmpty(), "no dates given");
        }
    }

    public static class ResultsImpl extends Instrument.ResultsImpl
            implements PathMultiAssetOption.Results {
        @Override
        public void reset() {
            super.reset();
        }
    }

    public abstract static class EngineImpl
            extends GenericEngine<PathMultiAssetOption.Arguments,
                                  PathMultiAssetOption.Results> {

        protected EngineImpl() {
            super(new PathMultiAssetOption.ArgumentsImpl(),
                  new PathMultiAssetOption.ResultsImpl());
        }
    }
}
