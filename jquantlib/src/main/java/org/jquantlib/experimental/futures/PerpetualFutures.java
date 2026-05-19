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
 Copyright (C) 2025 Hiroto Ogawa

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.futures;

import org.jquantlib.QL;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Perpetual Futures.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code PerpetualFutures} in {@code ql/instruments/perpetualfutures.{hpp,cpp}}.
 * <p>
 * Futures with no termination date, mainly used for cryptocurrencies. The funding-cashflow convention varies by
 * exchange.
 * <p>
 * {@code PayoffType}:
 * <ul>
 *   <li>{@code Linear}: underlying is FOR/DOM pair, margin and settlement in DOM;</li>
 *   <li>{@code Inverse}: underlying is FOR/DOM pair, margin and settlement in FOR;</li>
 *   <li>{@code Quanto}: underlying is FOR/DOM pair, margin and settlement in Quanto.</li>
 * </ul>
 * {@code FundingType}:
 * <ul>
 *   <li>{@code FundingWithPreviousSpot}:
 *     <code>cf(t+1) = f_{t+1} - f_t - fr_t (f_t - x_t) - iDiff_t x_t</code></li>
 *   <li>{@code FundingWithCurrentSpot}:
 *     <code>cf(t+1) = f_{t+1} - f_t - fr_t x_{t+1} (f_t - x_t)/x_t - iDiff_t x_{t+1}</code></li>
 * </ul>
 * {@code fundingFrequency}: zero-length period means continuous-time funding,
 * otherwise discrete.
 * <p>
 * Reference: Ackerer, Hugonnier, Jermann, "Perpetual Futures Pricing" (2024).
 *
 * <p><b>Note on time units</b>: the C++ implementation accepts
 * {@code Hours/Minutes/Seconds/Milliseconds/Microseconds} for
 * {@code fundingFrequency}; the JQuantLib {@link TimeUnit} enum currently
 * exposes only {@code Days/Weeks/Months/Years} so this port restricts
 * the allowed units accordingly. The Java default {@code fundingFrequency}
 * is therefore {@code Period(2, Months)} rather than the C++ default
 * {@code Period(8, Hours)}.
 *
 * @author Jose Moya
 */
public class PerpetualFutures extends Instrument {

    //
    // public enums
    //

    private final PayoffType payoffType;
    private final FundingType fundingType;

    //
    // private fields
    //
    private final Period fundingFrequency;
    private final Calendar cal;
    private final DayCounter dc;
    /**
     * Constructs a perpetual futures with default Java-supported parameters (FundingWithCurrentSpot, Period(2, Months),
     * NullCalendar, ActualActual(ISDA)).
     */
    public PerpetualFutures(final PayoffType payoffType) {
        this(payoffType, FundingType.FundingWithCurrentSpot, new Period(2, TimeUnit.Months), new NullCalendar(),
                new ActualActual(ActualActual.Convention.ISDA));
    }
    public PerpetualFutures(final PayoffType payoffType, final FundingType fundingType, final Period fundingFrequency,
            final Calendar cal, final DayCounter dc) {
        this.payoffType = payoffType;
        this.fundingType = fundingType;
        this.fundingFrequency = fundingFrequency;
        this.cal = cal;
        this.dc = dc;
    }

    //
    // public constructors
    //

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        QL.require(PerpetualFutures.ArgumentsImpl.class.isAssignableFrom(args.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final PerpetualFutures.ArgumentsImpl a = (PerpetualFutures.ArgumentsImpl) args;
        a.payoffType = payoffType;
        a.fundingType = fundingType;
        a.fundingFrequency = fundingFrequency;
        a.cal = cal;
        a.dc = dc;
    }

    //
    // overrides Instrument
    //

    public enum PayoffType {Linear, Inverse, Quanto}

    public enum FundingType {FundingWithPreviousSpot, FundingWithCurrentSpot}

    //
    // public inner classes
    //

    /**
     * Arguments for perpetual futures calculation.
     */
    public static class ArgumentsImpl implements Instrument.Arguments {

        public PayoffType payoffType;
        public FundingType fundingType;
        public Period fundingFrequency;
        public Calendar cal;
        public DayCounter dc;

        public ArgumentsImpl() {
            this.payoffType = null;
            this.fundingType = null;
            this.fundingFrequency = new Period(2, TimeUnit.Months);
            this.cal = new NullCalendar();
            this.dc = new ActualActual(ActualActual.Convention.ISDA);
        }

        @Override
        public void validate() {
            QL.require(payoffType != null, "unknown payoff type");
            switch ( payoffType ) {
            case Linear:
            case Inverse:
            case Quanto:
                break;
            default:
                QL.error("unknown payoff type");
            }
            QL.require(fundingType != null, "unknown funding type");
            switch ( fundingType ) {
            case FundingWithPreviousSpot:
            case FundingWithCurrentSpot:
                break;
            default:
                QL.error("unknown funding type");
            }
        }
    }

    /**
     * Base class for perpetual futures pricing engines.
     */
    public abstract static class EngineImpl
            extends GenericEngine< PerpetualFutures.ArgumentsImpl, Instrument.ResultsImpl > {

        protected EngineImpl() {
            super(new ArgumentsImpl(), new Instrument.ResultsImpl());
        }
    }
}
