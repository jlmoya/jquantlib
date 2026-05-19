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
 Copyright (C) 2010, 2011 Chris Kenyon
 Copyright (C) 2021 Ralf Konrad Eckel

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

/**
 * CPI cap or floor — zero-inflation-indexed-ratio-with-base option.
 *
 * <p>Quoted as a fixed strike rate {@code K}. Payoff:
 * <pre>
 *   P_n(0,T) max( y * (N * [(1+K)^T - 1]
 *                    - N * [I(T)/I(0) - 1] ), 0 )
 * </pre>
 * where {@code T} is the maturity time, {@code P_n(0,t)} is the nominal discount factor at time {@code t}, {@code N} is
 * the notional, and {@code I(t)} is the inflation index value at time {@code t}.
 *
 * <p>Inflation is generally available on every day, including holidays and
 * weekends. Hence there is a variable to state whether the observe/fix dates for inflation are adjusted or not. The
 * default is not to adjust.
 *
 * <p>N.B. a CPI cap or floor is an option, not a cap or floor on a coupon.
 * Thus this is very similar to a ZCIIS and has a single flow; this is as usual for CPI because it is cumulative up to
 * option maturity from base date.
 *
 * <p>We do not inherit from {@link Option}, although that would be reasonable,
 * because we do not have that degree of generality.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::CPICapFloor}
 * ({@code ql/instruments/cpicapfloor.{hpp,cpp}}).
 *
 * @author JQuantLib migration team (Phase 2r C.1)
 */
public class CPICapFloor extends Instrument {

    //
    // protected fields
    //

    protected Option.Type type_;
    protected double nominal_;
    protected Date startDate_, fixDate_, payDate_;
    protected double baseCPI_;
    protected Date maturity_;
    protected Calendar fixCalendar_;
    protected BusinessDayConvention fixConvention_;
    protected Calendar payCalendar_;
    protected BusinessDayConvention payConvention_;
    protected double strike_;
    protected ZeroInflationIndex index_;
    protected Period observationLag_;
    protected CPI.InterpolationType observationInterpolation_;

    //
    // public constructors
    //

    public CPICapFloor(final Option.Type type, final double nominal, final Date startDate, final double baseCPI,
            final Date maturity, final Calendar fixCalendar, final BusinessDayConvention fixConvention,
            final Calendar payCalendar, final BusinessDayConvention payConvention, final double strike,
            final ZeroInflationIndex inflationIndex, final Period observationLag) {
        this(type, nominal, startDate, baseCPI, maturity, fixCalendar, fixConvention, payCalendar, payConvention,
                strike, inflationIndex, observationLag, CPI.InterpolationType.AsIndex);
    }

    public CPICapFloor(final Option.Type type, final double nominal, final Date startDate, final double baseCPI,
            final Date maturity, final Calendar fixCalendar, final BusinessDayConvention fixConvention,
            final Calendar payCalendar, final BusinessDayConvention payConvention, final double strike,
            final ZeroInflationIndex inflationIndex, final Period observationLag,
            final CPI.InterpolationType observationInterpolation) {
        this.type_ = type;
        this.nominal_ = nominal;
        this.startDate_ = startDate;
        this.baseCPI_ = baseCPI;
        this.maturity_ = maturity;
        this.fixCalendar_ = fixCalendar;
        this.fixConvention_ = fixConvention;
        this.payCalendar_ = payCalendar;
        this.payConvention_ = payConvention;
        this.strike_ = strike;
        this.index_ = inflationIndex;
        this.observationLag_ = observationLag;
        this.observationInterpolation_ = observationInterpolation;

        QL.require(index_ != null, "no inflation index passed");
        QL.require(fixCalendar_ != null, "no fixing calendar passed");
        QL.require(payCalendar_ != null, "no payment calendar passed");

        if ( !isInterpolated(observationInterpolation_) ) {
            QL.require(observationLag_.ge(index_.availabilityLag()),
                    "CPI capfloor's observationLag must be at least availabilityLag of inflation index "
                            + "when the observation is effectively flat");
        } else {
            QL.require(observationLag_.gt(index_.availabilityLag()),
                    "CPI capfloor's observationLag must be greater than availabilityLag of inflation index "
                            + "when the observation is effectively linear");
        }
    }

    /**
     * Mirror of C++ {@code detail::CPI::isInterpolated} — Linear is interpolated; AsIndex/Flat are not.
     */
    private static boolean isInterpolated(final CPI.InterpolationType t) {
        return t == CPI.InterpolationType.Linear;
    }

    //
    // inspectors
    //

    public Option.Type type() {
        return type_;
    }

    public double nominal() {
        return nominal_;
    }

    /** Strike {@code K} in the payoff formula. */
    public double strike() {
        return strike_;
    }

    public Date fixingDate() {
        return fixCalendar_.adjust(maturity_.sub(observationLag_), fixConvention_);
    }

    public Date payDate() {
        return payCalendar_.adjust(maturity_, payConvention_);
    }

    public ZeroInflationIndex index() {
        return index_;
    }

    public Period observationLag() {
        return observationLag_;
    }

    //
    // overrides Instrument
    //

    @Override
    public boolean isExpired() {
        return new Settings().evaluationDate().gt(maturity_);
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        final CPICapFloor.ArgumentsImpl arguments = (CPICapFloor.ArgumentsImpl) args;
        QL.require(arguments != null, "wrong argument type, not CPICapFloor::arguments*");

        arguments.type = type_;
        arguments.nominal = nominal_;
        arguments.startDate = startDate_;
        arguments.baseCPI = baseCPI_;
        arguments.maturity = maturity_;
        arguments.fixCalendar = fixCalendar_;
        arguments.fixConvention = fixConvention_;
        arguments.payCalendar = payCalendar_;
        arguments.payConvention = payConvention_;
        arguments.fixDate = fixingDate();
        arguments.payDate = payDate();
        arguments.strike = strike_;
        arguments.index = index_;
        arguments.observationLag = observationLag_;
        arguments.observationInterpolation = observationInterpolation_;
    }

    //
    // public inner classes — mirror C++ inner types
    //

    /** Marking interface; mirrors C++ {@code CPICapFloor::arguments}. */
    public interface Arguments extends Instrument.Arguments { /* marker */
    }

    /** Marking interface; mirrors C++ {@code CPICapFloor::results}. */
    public interface Results extends Instrument.Results { /* marker */
    }

    /** Concrete arguments DTO. */
    public static class ArgumentsImpl implements CPICapFloor.Arguments {
        public Option.Type type;
        public double nominal;
        public Date startDate, fixDate, payDate;
        public double baseCPI;
        public Date maturity;
        public Calendar fixCalendar, payCalendar;
        public BusinessDayConvention fixConvention, payConvention;
        public double strike;
        public ZeroInflationIndex index;
        public Period observationLag;
        public CPI.InterpolationType observationInterpolation;

        @Override
        public void validate() {
            // C++ has empty validate body — match it.
        }
    }

    /** Concrete results DTO. */
    public static class ResultsImpl extends Instrument.ResultsImpl implements CPICapFloor.Results {
    }

    /**
     * Base class for CPI cap/floor pricing engines. Mirrors C++
     * {@code CPICapFloor::engine = GenericEngine<arguments, results>}.
     */
    public abstract static class Engine extends GenericEngine< CPICapFloor.Arguments, CPICapFloor.Results > {
        protected Engine() {
            super(new CPICapFloor.ArgumentsImpl(), new CPICapFloor.ResultsImpl());
        }
    }
}
