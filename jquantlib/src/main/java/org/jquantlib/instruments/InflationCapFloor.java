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
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.YoYInflationCoupon;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

import java.util.ArrayList;
import java.util.List;

/**
 * Year-on-year inflation cap/floor instrument (the only inflation cap/floor variety; ZCII swaps don't generate caplet
 * flow).
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::YoYInflationCapFloor}
 * ({@code ql/instruments/inflationcapfloor.{hpp,cpp}}).
 *
 * <p>Note that the standard YoY inflation cap/floor defined here is different
 * from nominal: standard nominal cap/floors do not have the first optionlet because they set in advance. YoY inflation
 * generally sets effectively in arrears (lag of a few months), so the first optionlet IS relevant. As a result we can
 * do parity tests without a special definition of the YoY cap/floor instrument.
 *
 * @author JQuantLib migration team (Phase 2r C.1)
 */
public class InflationCapFloor extends Instrument {

    private final Type type_;

    //
    // private fields
    //
    private final Leg yoyLeg_;
    private final List< Double > capRates_;
    private final List< Double > floorRates_;
    /**
     * Constructor mirroring C++ first form
     * {@code YoYInflationCapFloor(Type, Leg yoyLeg, vector<Rate> capRates, vector<Rate> floorRates)}.
     */
    public InflationCapFloor(final Type type, final Leg yoyLeg, final List< Double > capRates,
            final List< Double > floorRates) {
        this.type_ = type;
        this.yoyLeg_ = yoyLeg;
        this.capRates_ = new ArrayList<>(capRates);
        this.floorRates_ = new ArrayList<>(floorRates);

        if ( type_ == Type.Cap || type_ == Type.Collar ) {
            QL.require(!capRates_.isEmpty(), "no cap rates given");
            while ( capRates_.size() < yoyLeg_.size() ) {
                capRates_.add(capRates_.get(capRates_.size() - 1));
            }
        }
        if ( type_ == Type.Floor || type_ == Type.Collar ) {
            QL.require(!floorRates_.isEmpty(), "no floor rates given");
            while ( floorRates_.size() < yoyLeg_.size() ) {
                floorRates_.add(floorRates_.get(floorRates_.size() - 1));
            }
        }

        for ( final CashFlow cf : yoyLeg_ ) {
            cf.addObserver(this);
        }
        new Settings().evaluationDate().addObserver(this);
    }

    //
    // public constructors
    //

    /**
     * Constructor mirroring C++ second form
     * {@code YoYInflationCapFloor(Type, Leg yoyLeg, const vector<Rate>& strikes)}.
     */
    public InflationCapFloor(final Type type, final Leg yoyLeg, final List< Double > strikes) {
        this.type_ = type;
        this.yoyLeg_ = yoyLeg;
        QL.require(!strikes.isEmpty(), "no strikes given");
        if ( type_ == Type.Cap ) {
            this.capRates_ = new ArrayList<>(strikes);
            this.floorRates_ = new ArrayList<>();
            while ( capRates_.size() < yoyLeg_.size() ) {
                capRates_.add(capRates_.get(capRates_.size() - 1));
            }
        } else if ( type_ == Type.Floor ) {
            this.capRates_ = new ArrayList<>();
            this.floorRates_ = new ArrayList<>(strikes);
            while ( floorRates_.size() < yoyLeg_.size() ) {
                floorRates_.add(floorRates_.get(floorRates_.size() - 1));
            }
        } else {
            throw new LibraryException("only Cap/Floor types allowed in this constructor");
        }

        for ( final CashFlow cf : yoyLeg_ ) {
            cf.addObserver(this);
        }
        new Settings().evaluationDate().addObserver(this);
    }

    public Type type() {
        return type_;
    }

    //
    // inspectors
    //

    public List< Double > capRates() {
        return capRates_;
    }

    public List< Double > floorRates() {
        return floorRates_;
    }

    public Leg yoyLeg() {
        return yoyLeg_;
    }

    public Date startDate() {
        return CashFlows.getInstance().startDate(yoyLeg_);
    }

    public Date maturityDate() {
        return CashFlows.getInstance().maturityDate(yoyLeg_);
    }

    public YoYInflationCoupon lastYoYInflationCoupon() {
        final CashFlow lastCF = yoyLeg_.get(yoyLeg_.size() - 1);
        if ( lastCF instanceof YoYInflationCoupon ) {
            return (YoYInflationCoupon) lastCF;
        }
        return null;
    }

    /**
     * Returns the n-th optionlet as an inflation cap/floor with only one cash flow. Mirrors C++
     * {@code optionlet(Size i)}.
     */
    public InflationCapFloor optionlet(final int i) {
        QL.require(i < yoyLeg().size(), "optionlet does not exist, only " + yoyLeg().size() + " present");
        final Leg cf = new Leg();
        cf.add(yoyLeg().get(i));

        final List< Double > cap = new ArrayList<>();
        final List< Double > floor = new ArrayList<>();
        if ( type() == Type.Cap || type() == Type.Collar ) {
            cap.add(capRates().get(i));
        }
        if ( type() == Type.Floor || type() == Type.Collar ) {
            floor.add(floorRates().get(i));
        }
        return new InflationCapFloor(type(), cf, cap, floor);
    }

    /**
     * Mirrors C++ {@code atmRate(const YieldTermStructure&)}.
     */
    public double atmRate(final YieldTermStructure discountCurve) {
        return CashFlows.getInstance()
                .atmRate(yoyLeg_, new org.jquantlib.quotes.Handle< YieldTermStructure >(discountCurve));
    }

    @Override
    public boolean isExpired() {
        final Date today = new Settings().evaluationDate();
        for ( int i = yoyLeg_.size(); i > 0; --i ) {
            if ( !yoyLeg_.get(i - 1).hasOccurred(today) ) {
                return false;
            }
        }
        return true;
    }

    //
    // overrides Instrument
    //

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        final InflationCapFloor.ArgumentsImpl arguments = (InflationCapFloor.ArgumentsImpl) args;
        QL.require(arguments != null, "wrong argument type");

        final int n = yoyLeg_.size();
        arguments.startDates = new Date[n];
        arguments.fixingDates = new Date[n];
        arguments.payDates = new Date[n];
        arguments.accrualTimes = new double[n];
        arguments.nominals = new double[n];
        arguments.gearings = new double[n];
        arguments.capRates = new double[n];
        arguments.floorRates = new double[n];
        arguments.spreads = new double[n];
        arguments.type = type_;
        arguments.observationLag =
                yoyLeg_.size() > 0 && yoyLeg_.get(0) instanceof YoYInflationCoupon ? ((YoYInflationCoupon) yoyLeg_.get(
                        0)).observationLag() : new Period();

        for ( int i = 0; i < n; ++i ) {
            final CashFlow cf = yoyLeg_.get(i);
            QL.require(cf instanceof YoYInflationCoupon, "non-YoYInflationCoupon given");
            final YoYInflationCoupon coupon = (YoYInflationCoupon) cf;

            arguments.startDates[i] = coupon.accrualStartDate();
            arguments.fixingDates[i] = coupon.fixingDate();
            arguments.payDates[i] = coupon.date();
            arguments.accrualTimes[i] = coupon.accrualPeriod();
            arguments.nominals[i] = coupon.nominal();

            final double spread = coupon.spread();
            final double gearing = coupon.gearing();
            arguments.gearings[i] = gearing;
            arguments.spreads[i] = spread;

            // Capture the first coupon's index (all coupons share it in
            // practice; engines need a YoYInflationIndex handle to forward).
            if ( i == 0 ) {
                arguments.index = coupon.yoyIndex();
            }

            if ( type_ == Type.Cap || type_ == Type.Collar ) {
                arguments.capRates[i] = (capRates_.get(i) - spread) / gearing;
            } else {
                arguments.capRates[i] = Constants.NULL_REAL;
            }
            if ( type_ == Type.Floor || type_ == Type.Collar ) {
                arguments.floorRates[i] = (floorRates_.get(i) - spread) / gearing;
            } else {
                arguments.floorRates[i] = Constants.NULL_REAL;
            }
        }
    }

    public enum Type {Cap, Floor, Collar}

    //
    // public inner classes — mirror C++ inner types
    //

    /** Marking interface; mirrors C++ {@code YoYInflationCapFloor::arguments}. */
    public interface Arguments extends Instrument.Arguments { /* marker */
    }

    /** Marking interface; mirrors C++ {@code YoYInflationCapFloor::results}. */
    public interface Results extends Instrument.Results { /* marker */
    }

    /**
     * Concrete arguments DTO. Mirrors C++ {@code YoYInflationCapFloor::arguments} fields verbatim.
     */
    public static class ArgumentsImpl implements InflationCapFloor.Arguments {
        public InflationCapFloor.Type type = null;
        public YoYInflationIndex index;
        public Period observationLag;
        public Date[] startDates;
        public Date[] fixingDates;
        public Date[] payDates;
        public double[] accrualTimes;
        public double[] capRates;
        public double[] floorRates;
        public double[] gearings;
        public double[] spreads;
        public double[] nominals;

        @Override
        public void validate() {
            QL.require(payDates.length == startDates.length, "number of start dates different from that of pay dates");
            QL.require(accrualTimes.length == startDates.length,
                    "number of start dates different from that of accrual times");
            QL.require(type == InflationCapFloor.Type.Floor || capRates.length == startDates.length,
                    "number of start dates different from that of cap rates");
            QL.require(type == InflationCapFloor.Type.Cap || floorRates.length == startDates.length,
                    "number of start dates different from that of floor rates");
            QL.require(gearings.length == startDates.length, "number of start dates different from that of gearings");
            QL.require(spreads.length == startDates.length, "number of start dates different from that of spreads");
            QL.require(nominals.length == startDates.length, "number of start dates different from that of nominals");
        }
    }

    /** Concrete results DTO. */
    public static class ResultsImpl extends Instrument.ResultsImpl implements InflationCapFloor.Results {
    }

    /**
     * Base class for inflation cap/floor pricing engines. Mirrors C++
     * {@code YoYInflationCapFloor::engine = GenericEngine<arguments, results>}.
     */
    public abstract static class Engine
            extends GenericEngine< InflationCapFloor.Arguments, InflationCapFloor.Results > {
        protected Engine() {
            super(new InflationCapFloor.ArgumentsImpl(), new InflationCapFloor.ResultsImpl());
        }
    }

    //
    // Concrete subclasses (Cap / Floor / Collar) — mirror C++ inline classes
    //

    /** Concrete YoY inflation cap. */
    public static class Cap extends InflationCapFloor {
        public Cap(final Leg yoyLeg, final List< Double > exerciseRates) {
            super(Type.Cap, yoyLeg, exerciseRates, new ArrayList<>());
        }
    }

    /** Concrete YoY inflation floor. */
    public static class Floor extends InflationCapFloor {
        public Floor(final Leg yoyLeg, final List< Double > exerciseRates) {
            super(Type.Floor, yoyLeg, new ArrayList<>(), exerciseRates);
        }
    }

    /** Concrete YoY inflation collar. */
    public static class Collar extends InflationCapFloor {
        public Collar(final Leg yoyLeg, final List< Double > capRates, final List< Double > floorRates) {
            super(Type.Collar, yoyLeg, capRates, floorRates);
        }
    }
}
