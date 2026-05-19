/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2008 Ferdinando Ametrano
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2015 Peter Caspers
 Copyright (C) 2015 Michael von den Driesch

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.optionlet;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to wrap in a {@link StrippedOptionletBase} object a matrix of exogenously calculated optionlet (i.e.
 * caplet/floorlet) volatilities (a.k.a. forward-forward volatilities).
 *
 * <p>Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/optionlet/strippedoptionlet.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>{@code Handle<Quote>} → {@link Handle}{@code <}{@link Quote}{@code >}
 *      with bounded wildcard for compatibility with subclasses of Quote.</li>
 *  <li>Two ctors mirror C++ overloads (single strikes vector vs per-tenor
 *      strikes matrix).</li>
 * </ul>
 */
public class StrippedOptionlet extends StrippedOptionletBase {

    //
    // private fields
    //

    private final Calendar calendar_;
    private final int settlementDays_;
    private final BusinessDayConvention businessDayConvention_;
    private final DayCounter dc_;
    private final IborIndex iborIndex_;
    private final VolatilityType type_;
    private final double displacement_;

    private final int nOptionletDates_;
    private final List< Date > optionletDates_;
    private final List< Double > optionletTimes_;
    private final List< Double > optionletAtmRates_;
    private final List< List< Double > > optionletStrikes_;
    private final List< List< Handle< ? extends Quote > > > optionletVolQuotes_;
    private final List< List< Double > > optionletVolatilities_;

    //
    // public constructors
    //

    /** Per-tenor strikes (mirrors C++ ctor 2). */
    public StrippedOptionlet(final int settlementDays, final Calendar calendar, final BusinessDayConvention bdc,
            final IborIndex iborIndex, final List< Date > optionletDates, final List< List< Double > > strikes,
            final List< List< Handle< ? extends Quote > > > v, final DayCounter dc, final VolatilityType type,
            final double displacement) {
        super();
        this.calendar_ = calendar;
        this.settlementDays_ = settlementDays;
        this.businessDayConvention_ = bdc;
        this.dc_ = dc;
        this.iborIndex_ = iborIndex;
        this.type_ = type;
        this.displacement_ = displacement;
        this.nOptionletDates_ = optionletDates.size();
        this.optionletDates_ = new ArrayList< Date >(optionletDates);
        this.optionletTimes_ = new ArrayList< Double >(nOptionletDates_);
        this.optionletAtmRates_ = new ArrayList< Double >(nOptionletDates_);
        this.optionletStrikes_ = new ArrayList< List< Double > >(strikes);
        this.optionletVolQuotes_ = v;
        this.optionletVolatilities_ = new ArrayList< List< Double > >(nOptionletDates_);

        for ( int i = 0; i < nOptionletDates_; ++i ) {
            optionletTimes_.add(0.0);
            optionletAtmRates_.add(0.0);
        }
        checkInputs();
        for ( int i = 0; i < nOptionletDates_; ++i ) {
            final List< Double > row = new ArrayList< Double >(strikes.get(i).size());
            for ( int j = 0; j < strikes.get(i).size(); ++j ) {
                row.add(0.0);
            }
            optionletVolatilities_.add(row);
        }

        // Mirrors C++: registerWith(Settings::instance().evaluationDate());
        new Settings().evaluationDate().addObserver(this);
        registerWithMarketData();

        final Date refDate = calendar.advance(new Settings().evaluationDate(), settlementDays, TimeUnit.Days);
        for ( int i = 0; i < nOptionletDates_; ++i ) {
            optionletTimes_.set(i, dc_.yearFraction(refDate, optionletDates_.get(i)));
        }
    }

    /**
     * Static factory: single strikes vector applied to all option dates (mirrors C++ ctor 1). Java erasure forces this
     * into a static factory because {@code List<Double>} and {@code List<List<Double>>} share a JVM signature with the
     * per-tenor ctor below.
     */
    public static StrippedOptionlet ofUniformStrikes(final int settlementDays, final Calendar calendar,
            final BusinessDayConvention bdc, final IborIndex iborIndex, final List< Date > optionletDates,
            final List< Double > strikes, final List< List< Handle< ? extends Quote > > > v, final DayCounter dc,
            final VolatilityType type, final double displacement) {
        return new StrippedOptionlet(settlementDays, calendar, bdc, iborIndex, optionletDates,
                broadcast(strikes, optionletDates.size()), v, dc, type, displacement);
    }

    /** Static factory convenience: ShiftedLognormal vol type, zero displacement. */
    public static StrippedOptionlet ofUniformStrikes(final int settlementDays, final Calendar calendar,
            final BusinessDayConvention bdc, final IborIndex iborIndex, final List< Date > optionletDates,
            final List< Double > strikes, final List< List< Handle< ? extends Quote > > > v, final DayCounter dc) {
        return ofUniformStrikes(settlementDays, calendar, bdc, iborIndex, optionletDates, strikes, v, dc,
                VolatilityType.ShiftedLognormal, 0.0);
    }

    //
    // StrippedOptionletBase interface
    //

    private static List< List< Double > > broadcast(final List< Double > strikes, final int n) {
        final List< List< Double > > out = new ArrayList< List< Double > >(n);
        for ( int i = 0; i < n; ++i ) {
            out.add(new ArrayList< Double >(strikes));
        }
        return out;
    }

    @Override
    public List< Double > optionletStrikes(final int i) {
        QL.require(i < optionletStrikes_.size(),
                "index (" + i + ") must be less than optionletStrikes size (" + optionletStrikes_.size() + ")");
        return optionletStrikes_.get(i);
    }

    @Override
    public List< Double > optionletVolatilities(final int i) {
        calculate();
        QL.require(i < optionletVolatilities_.size(),
                "index (" + i + ") must be less than optionletVolatilities size (" + optionletVolatilities_.size()
                        + ")");
        return optionletVolatilities_.get(i);
    }

    @Override
    public List< Date > optionletFixingDates() {
        calculate();
        return optionletDates_;
    }

    @Override
    public List< Double > optionletFixingTimes() {
        calculate();
        return optionletTimes_;
    }

    @Override
    public int optionletMaturities() {
        return nOptionletDates_;
    }

    @Override
    public List< Double > atmOptionletRates() {
        calculate();
        for ( int i = 0; i < nOptionletDates_; ++i ) {
            optionletAtmRates_.set(i, iborIndex_.fixing(optionletDates_.get(i), true));
        }
        return optionletAtmRates_;
    }

    @Override
    public DayCounter dayCounter() {
        return dc_;
    }

    @Override
    public Calendar calendar() {
        return calendar_;
    }

    @Override
    public int settlementDays() {
        return settlementDays_;
    }

    @Override
    public BusinessDayConvention businessDayConvention() {
        return businessDayConvention_;
    }

    @Override
    public VolatilityType volatilityType() {
        return type_;
    }

    //
    // LazyObject interface
    //

    @Override
    public double displacement() {
        return displacement_;
    }

    //
    // helpers
    //

    @Override
    protected void performCalculations() {
        for ( int i = 0; i < nOptionletDates_; ++i ) {
            for ( int j = 0; j < optionletVolQuotes_.get(i).size(); ++j ) {
                optionletVolatilities_.get(i).set(j, optionletVolQuotes_.get(i).get(j).currentLink().value());
            }
        }
    }

    private void checkInputs() {
        QL.require(!optionletDates_.isEmpty(), "empty optionlet tenor vector");
        QL.require(nOptionletDates_ == optionletVolQuotes_.size(),
                "mismatch between number of option tenors (" + nOptionletDates_ + ") and number of volatility rows ("
                        + optionletVolQuotes_.size() + ")");
        QL.require(optionletDates_.get(0).gt(new Settings().evaluationDate()),
                "first option date (" + optionletDates_.get(0) + ") is in the past");
        for ( int i = 1; i < nOptionletDates_; ++i ) {
            QL.require(optionletDates_.get(i).gt(optionletDates_.get(i - 1)),
                    "non increasing option dates: position " + i + " is " + optionletDates_.get(i - 1) + ", position "
                            + (i + 1) + " is " + optionletDates_.get(i));
        }
        QL.require(nOptionletDates_ == optionletStrikes_.size(),
                "mismatch between number of option tenors (" + nOptionletDates_ + ") and number of strikes ("
                        + optionletStrikes_.size() + ")");
        for ( int i = 0; i < nOptionletDates_; ++i ) {
            QL.require(optionletStrikes_.get(i).size() == optionletVolQuotes_.get(i).size(),
                    "mismatch between number of option tenors (" + nOptionletDates_
                            + ") and number of vol columns at date " + i + " (" + optionletVolQuotes_.get(i).size()
                            + ")");
            for ( int j = 1; j < optionletStrikes_.get(i).size(); ++j ) {
                QL.require(optionletStrikes_.get(i).get(j - 1) < optionletStrikes_.get(i).get(j),
                        "non increasing strikes at date " + i + ": position " + j + " is " + optionletStrikes_.get(0)
                                .get(j - 1) + ", position " + (j + 1) + " is " + optionletStrikes_.get(0).get(j));
            }
        }
    }

    private void registerWithMarketData() {
        for ( int i = 0; i < nOptionletDates_; ++i ) {
            for ( int j = 0; j < optionletVolQuotes_.get(i).size(); ++j ) {
                optionletVolQuotes_.get(i).get(j).addObserver(this);
            }
        }
    }
}
