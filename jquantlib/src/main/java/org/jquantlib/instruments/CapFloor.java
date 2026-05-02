/*
 Copyright (C) 2007 Ueli Hofstetter

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
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2006 Ferdinando Ametrano
 Copyright (C) 2006 Francois du Vignaud
 Copyright (C) 2006, 2007 StatPro Italia srl

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

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.indexes.InterestRateIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Base class for cap-like instruments
 *
 * <p>Mirrors C++ v1.42.1 ql/instruments/capfloor.{hpp,cpp} except that the
 * Java {@code termStructure} carryover field is preserved on the public
 * constructors so older call-sites continue to compile. v1.42.1 dropped the
 * curve from the {@code CapFloor} constructor; the curve travels through
 * the engine instead. The Java retained field is unused by
 * {@link #setupArguments(PricingEngine.Arguments)} and is purely an
 * observation point preserved from earlier JQuantLib API.
 *
 * <p>Phase 2e WI-2 ports {@link #setupArguments(PricingEngine.Arguments)},
 * {@link #fetchResults(PricingEngine.Results)}, and
 * {@link #performCalculations()} verbatim from caphelper.cpp lines 210-269,
 * plus the inner {@link Arguments}/{@link Results}/{@link Engine} types,
 * so that {@link Instrument#NPV()} dispatches through the engine.
 *
 * @category instruments
 *
 * @author Ueli Hofstetter
 */
public class CapFloor extends Instrument {

    public enum Type { Cap, Floor, Collar };

    private final Type type_;
    private final Leg floatingLeg_;
    private List</*@Rate*/ Double> capRates_;
    private List</*@Rate*/ Double> floorRates_;
    private final Handle<YieldTermStructure> termStructure_;

    public CapFloor(
            final CapFloor.Type type,
            final Leg floatingLeg,
            final List</*@Rate*/ Double> capRates,
            final List</*@Rate*/ Double> floorRates,
            final Handle<YieldTermStructure> termStructure,
            final PricingEngine engine){


        this.type_ = type;
        this.floatingLeg_ = floatingLeg;
        this.capRates_ = capRates;
        this.floorRates_ = floorRates;
        this.termStructure_ = termStructure;

        if (engine != null) {
            setPricingEngine(engine);
        }


        if (type_ == Type.Cap || type_ == Type.Collar) {
            QL.require(capRates_.size()>0 , "no cap rates given"); // TODO: message
            // capRates_.reserve(floatingLeg_.size());
            while (capRates_.size() < floatingLeg_.size()) {
                // this looks kind of suspicious...
                capRates_.add(capRates_.get(capRates_.size() - 1));
            }
        }

        if (type_ == Type.Floor || type_ == Type.Collar) {
            QL.require(floorRates_.size()>0 , "no floor rates given"); // TODO: message
            // floorRates_.reserve(floatingLeg_.size());
            while (floorRates_.size() < floatingLeg_.size()) {
                floorRates_.add(floorRates_.get(floorRates_.size() - 1));
            }
        }

        final Date evaluationDate = new Settings().evaluationDate();
        for (final CashFlow cashFlow : floatingLeg_) {
            cashFlow.addObserver(this);
        }

        // termStructure may be empty when callers pass the curve through
        // the engine instead (post-v1.42.1 style). Only register if non-null
        // to preserve historical observation points.
        if (this.termStructure_ != null) {
            this.termStructure_.addObserver(this);
        }
        evaluationDate.addObserver(this);
    }

    public CapFloor(
            final Type type,
            final Leg floatingLeg,
            final List</*@Rate*/ Double> strikes,
            final Handle<YieldTermStructure> termStructure,
            final PricingEngine engine){

        this.type_ = type;
        this.floatingLeg_ = floatingLeg;
        this.termStructure_ = termStructure;

        if (engine != null) {
            setPricingEngine(engine);
        }

        QL.require(strikes.size()>0 , "no strikes given"); // TODO: message
        if (type_ == Type.Cap) {
            capRates_ = strikes;
            //capRates_.reserve(floatingLeg_.size());
            while (capRates_.size() < floatingLeg_.size()) {
                capRates_.add(capRates_.get(capRates_.size()-1));
            }
        } else if (type_ == Type.Floor) {
            floorRates_ = strikes;
            //floorRates_.reserve(floatingLeg_.size());
            while (floorRates_.size() < floatingLeg_.size()) {
                floorRates_.add(floorRates_.get(floorRates_.size()-1));
            }
        } else
            throw new LibraryException("only Cap/Floor types allowed in this constructor"); // TODO: message

        final Date evaluationDate = new Settings().evaluationDate();
        for (final CashFlow cashFlow : floatingLeg_) {
            cashFlow.addObserver(this);
        }

        if (this.termStructure_ != null) {
            this.termStructure_.addObserver(this);
        }
        evaluationDate.addObserver(this);
    }

    public Type type() {
        return type_;
    }

    public List</*@Rate*/ Double> capRates() {
        return capRates_;
    }

    public List</*@Rate*/ Double> floorRates() {
        return floorRates_;
    }

    public Leg floatingLeg() {
        return floatingLeg_;
    }

    public /*@Rate*/double atmRate(){
        return CashFlows.getInstance().atmRate(floatingLeg_, termStructure_);
    }

    @Override
    public boolean isExpired(){
        Date lastPaymentDate = Date.minDate();
        for (int i=0; i<floatingLeg_.size(); i++) {
            //FIXME: kind of ugly... intention: get the last date of all dates in the floatingdate c++ max syntax.
            lastPaymentDate = lastPaymentDate.le(floatingLeg_.get(i).date())?floatingLeg_.get(i).date():lastPaymentDate;
        }
        // termStructure may be null in v1.42.1-style construction; fall back
        // to the global evaluation date in that case.
        final Date ref = (termStructure_ != null && !termStructure_.empty())
                ? termStructure_.currentLink().referenceDate()
                : new Settings().evaluationDate();
        return lastPaymentDate.le(ref);
    }

    public Date startDate(){
        return CashFlows.getInstance().startDate(floatingLeg_);
    }

    public Date maturityDate() {
        return CashFlows.getInstance().maturityDate(floatingLeg_);
    }

    public Date lastFixingDate() {
        final CashFlow lastCoupon = floatingLeg_.get(floatingLeg_.size() - 1); // no linkedlist :-(
        final FloatingRateCoupon lastFloatingCoupon = (FloatingRateCoupon) lastCoupon;
        return lastFloatingCoupon.fixingDate();
    }

    //
    // overrides Instrument
    //

    /**
     * Mirrors C++ v1.42.1 capfloor.cpp lines 210-269.
     */
    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        final CapFloor.ArgumentsImpl arguments = (CapFloor.ArgumentsImpl) args;
        QL.require(arguments != null, "wrong argument type");

        final int n = floatingLeg_.size();

        arguments.startDates = new Date[n];
        arguments.fixingDates = new Date[n];
        arguments.endDates = new Date[n];
        arguments.accrualTimes = new double[n];
        arguments.forwards = new double[n];
        arguments.nominals = new double[n];
        arguments.gearings = new double[n];
        arguments.capRates = new double[n];
        arguments.floorRates = new double[n];
        arguments.spreads = new double[n];
        arguments.indexes = new InterestRateIndex[n];

        arguments.type = type_;

        final Date today = new Settings().evaluationDate();

        for (int i=0; i<n; ++i) {
            final CashFlow cf = floatingLeg_.get(i);
            QL.require(cf instanceof FloatingRateCoupon, "non-FloatingRateCoupon given");
            final FloatingRateCoupon coupon = (FloatingRateCoupon) cf;
            arguments.startDates[i] = coupon.accrualStartDate();
            arguments.fixingDates[i] = coupon.fixingDate();
            arguments.endDates[i] = coupon.date();

            // this is passed explicitly for precision
            arguments.accrualTimes[i] = coupon.accrualPeriod();

            // this is passed explicitly for precision...
            if (arguments.endDates[i].ge(today)) { // ...but only if needed
                arguments.forwards[i] = coupon.adjustedFixing();
            } else {
                arguments.forwards[i] = Constants.NULL_REAL;
            }

            arguments.nominals[i] = coupon.nominal();
            arguments.indexes[i] = coupon.index();
            final double spread = coupon.spread();
            final double gearing = coupon.gearing();
            arguments.gearings[i] = gearing;
            arguments.spreads[i] = spread;

            if (type_ == Type.Cap || type_ == Type.Collar) {
                arguments.capRates[i] = (capRates_.get(i) - spread) / gearing;
            } else {
                arguments.capRates[i] = Constants.NULL_REAL;
            }

            if (type_ == Type.Floor || type_ == Type.Collar) {
                arguments.floorRates[i] = (floorRates_.get(i) - spread) / gearing;
            } else {
                arguments.floorRates[i] = Constants.NULL_REAL;
            }
        }
    }

    @Override
    protected void fetchResults(final PricingEngine.Results r) {
        super.fetchResults(r);
        // CapFloor only carries the inherited Instrument.value (= NPV).
        // No CapFloor-specific results beyond what Instrument.fetchResults
        // already extracts. Mirrors C++ CapFloor::results being empty.
    }


    //
    // public inner interfaces and classes — mirrors C++ CapFloor::arguments,
    // CapFloor::results, CapFloor::engine.
    //

    /** Marking interface; mirrors C++ CapFloor::arguments base. */
    public interface Arguments extends Instrument.Arguments { /* marker */ }

    /** Marking interface; mirrors C++ CapFloor::results being empty. */
    public interface Results extends Instrument.Results { /* marker */ }

    /**
     * Concrete arguments DTO populated by {@link CapFloor#setupArguments}
     * and consumed by {@link Engine#calculate}. Mirrors C++
     * CapFloor::arguments fields verbatim (capfloor.hpp:138-154); all
     * v1.42.1 fields including {@code indexes} and {@code spreads} are present
     * and populated by setupArguments. Phase 2j WI-2.2 align: {@code indexes}
     * array added (was declared in Javadoc but not present as a field).
     */
    static public class ArgumentsImpl implements CapFloor.Arguments {
        public CapFloor.Type type;
        public Date[] startDates;
        public Date[] fixingDates;
        public Date[] endDates;
        public double[] accrualTimes;
        public double[] capRates;
        public double[] floorRates;
        public double[] forwards;
        public double[] gearings;
        public double[] spreads;
        public double[] nominals;
        /** Mirrors C++ {@code CapFloor::arguments::indexes}. Each element is the
         *  {@code InterestRateIndex} (typically {@code IborIndex}) of the
         *  corresponding floating-rate coupon; populated by
         *  {@link CapFloor#setupArguments}. May be {@code null} if the coupon
         *  carries no index. */
        public InterestRateIndex[] indexes;

        @Override
        public void validate() {
            QL.require(endDates.length == startDates.length,
                    "number of start dates different from that of end dates");
            QL.require(accrualTimes.length == startDates.length,
                    "number of start dates different from that of accrual times");
            QL.require(type == CapFloor.Type.Floor
                    || capRates.length == startDates.length,
                    "number of start dates different from that of cap rates");
            QL.require(type == CapFloor.Type.Cap
                    || floorRates.length == startDates.length,
                    "number of start dates different from that of floor rates");
            QL.require(gearings.length == startDates.length,
                    "number of start dates different from that of gearings");
            QL.require(spreads.length == startDates.length,
                    "number of start dates different from that of spreads");
            QL.require(nominals.length == startDates.length,
                    "number of start dates different from that of nominals");
            QL.require(forwards.length == startDates.length,
                    "number of start dates different from that of forwards");
        }
    }

    /**
     * Concrete results DTO. CapFloor inherits Instrument's value/error
     * fields; this subclass exists only to give the GenericEngine a
     * concrete CapFloor.Results to instantiate.
     */
    static public class ResultsImpl extends Instrument.ResultsImpl
            implements CapFloor.Results {
    }

    /**
     * Base class for cap/floor pricing engines.
     * Mirrors C++ CapFloor::engine
     *   = GenericEngine<CapFloor::arguments, CapFloor::results>.
     */
    static public abstract class Engine
            extends GenericEngine<CapFloor.Arguments, CapFloor.Results> {
        protected Engine() {
            super(new CapFloor.ArgumentsImpl(), new CapFloor.ResultsImpl());
        }
    }
}
