/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.optionlet;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.capfloor.CapFloorTermVolSurface;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link StrippedOptionletBase} specialization. Concrete subclasses implement {@link #performCalculations()} to
 * populate the optionlet vols matrix.
 *
 * <p>Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/optionlet/optionletstripper.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>{@code ext::optional<Period>} → nullable {@link Period} reference;
 *      Java's {@code null} is the optional-empty equivalent.</li>
 *  <li>The protected mutable state (optionlet dates / times / strikes /
 *      vols / atm rates / payment dates / accrual periods) follows the C++
 *      member layout to enable subclass implementations to populate it
 *      directly.</li>
 * </ul>
 */
public abstract class OptionletStripper extends StrippedOptionletBase {

    //
    // protected state — accessible by subclasses (mirrors C++ protected members)
    //

    protected final CapFloorTermVolSurface termVolSurface_;
    protected final IborIndex iborIndex_;
    protected final Handle< YieldTermStructure > discount_;
    protected final int nStrikes_;
    protected final List< List< Double > > optionletStrikes_;
    protected final List< List< Double > > optionletVolatilities_;
    protected final List< Double > optionletTimes_;
    protected final List< Date > optionletDates_;
    protected final List< Period > optionletTenors_;
    protected final List< Double > atmOptionletRate_;
    protected final List< Date > optionletPaymentDates_;
    protected final List< Double > optionletAccrualPeriods_;
    protected final List< Period > capFloorLengths_;
    protected final VolatilityType volatilityType_;
    protected final double displacement_;
    /** Nullable; mirrors C++ {@code ext::optional<Period>}. */
    protected final Period optionletFrequency_;
    protected int nOptionletTenors_;

    //
    // protected constructor
    //

    /**
     * @param termVolSurface     required (non-null)
     * @param iborIndex          required (non-null)
     * @param discount           optional discount curve handle; pass empty handle to fall back to iborIndex forwarding
     *                           curve
     * @param type               volatility type (ShiftedLognormal or Normal)
     * @param displacement       strike displacement (zero for Normal)
     * @param optionletFrequency nullable; required when iborIndex is an {@link OvernightIndex}
     */
    protected OptionletStripper(final CapFloorTermVolSurface termVolSurface, final IborIndex iborIndex,
            final Handle< YieldTermStructure > discount, final VolatilityType type, final double displacement,
            final Period optionletFrequency) {
        this.termVolSurface_ = termVolSurface;
        this.iborIndex_ = iborIndex;
        this.discount_ = discount;
        this.nStrikes_ = termVolSurface.strikes().length;
        this.volatilityType_ = type;
        this.displacement_ = displacement;
        this.optionletFrequency_ = optionletFrequency;

        if ( volatilityType_ == VolatilityType.Normal ) {
            QL.require(displacement_ == 0.0, "non-null displacement is not allowed with Normal model");
        }
        if ( iborIndex_ instanceof OvernightIndex ) {
            QL.require(optionletFrequency_ != null, "an optionlet frequency is required when using an overnight index");
        }

        termVolSurface.addObserver(this);
        iborIndex.addObserver(this);
        if ( discount != null ) {
            discount.addObserver(this);
        }
        new Settings().evaluationDate().addObserver(this);

        final Period indexTenor = (optionletFrequency_ != null) ? optionletFrequency_ : iborIndex_.tenor();
        final List< Period > termTenors = termVolSurface.optionTenors();
        final Period maxCapFloorTenor = termTenors.get(termTenors.size() - 1);

        this.optionletTenors_ = new ArrayList< Period >();
        this.capFloorLengths_ = new ArrayList< Period >();
        optionletTenors_.add(indexTenor);
        capFloorLengths_.add(optionletTenors_.get(optionletTenors_.size() - 1).add(indexTenor));
        QL.require(maxCapFloorTenor.ge(capFloorLengths_.get(capFloorLengths_.size() - 1)),
                "too short (" + maxCapFloorTenor + ") capfloor term vol termVolSurface");
        Period nextCapFloorLength = capFloorLengths_.get(capFloorLengths_.size() - 1).add(indexTenor);
        while ( nextCapFloorLength.le(maxCapFloorTenor) ) {
            optionletTenors_.add(capFloorLengths_.get(capFloorLengths_.size() - 1));
            capFloorLengths_.add(nextCapFloorLength);
            nextCapFloorLength = nextCapFloorLength.add(indexTenor);
        }
        this.nOptionletTenors_ = optionletTenors_.size();

        this.optionletVolatilities_ = new ArrayList< List< Double > >(nOptionletTenors_);
        this.optionletStrikes_ = new ArrayList< List< Double > >(nOptionletTenors_);
        final double[] surfStrikes = termVolSurface.strikes();
        for ( int i = 0; i < nOptionletTenors_; ++i ) {
            final List< Double > volRow = new ArrayList< Double >(nStrikes_);
            final List< Double > strikeRow = new ArrayList< Double >(nStrikes_);
            for ( int j = 0; j < nStrikes_; ++j ) {
                volRow.add(0.0);
                strikeRow.add(surfStrikes[j]);
            }
            optionletVolatilities_.add(volRow);
            optionletStrikes_.add(strikeRow);
        }
        this.optionletDates_ = newFilledList(nOptionletTenors_, null);
        this.optionletTimes_ = newFilledList(nOptionletTenors_, 0.0);
        this.atmOptionletRate_ = newFilledList(nOptionletTenors_, 0.0);
        this.optionletPaymentDates_ = newFilledList(nOptionletTenors_, null);
        this.optionletAccrualPeriods_ = newFilledList(nOptionletTenors_, 0.0);
    }

    //
    // StrippedOptionletBase interface
    //

    private static < T > List< T > newFilledList(final int n, final T initial) {
        final List< T > out = new ArrayList< T >(n);
        for ( int i = 0; i < n; ++i ) {
            out.add(initial);
        }
        return out;
    }

    @Override
    public List< Double > optionletStrikes(final int i) {
        calculate();
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

    public List< Period > optionletFixingTenors() {
        return optionletTenors_;
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
        return optionletTenors_.size();
    }

    public List< Date > optionletPaymentDates() {
        calculate();
        return optionletPaymentDates_;
    }

    public List< Double > optionletAccrualPeriods() {
        calculate();
        return optionletAccrualPeriods_;
    }

    @Override
    public List< Double > atmOptionletRates() {
        calculate();
        return atmOptionletRate_;
    }

    @Override
    public DayCounter dayCounter() {
        return termVolSurface_.dayCounter();
    }

    @Override
    public Calendar calendar() {
        return termVolSurface_.calendar();
    }

    @Override
    public int settlementDays() {
        return termVolSurface_.settlementDays();
    }

    @Override
    public BusinessDayConvention businessDayConvention() {
        return termVolSurface_.businessDayConvention();
    }

    public CapFloorTermVolSurface termVolSurface() {
        return termVolSurface_;
    }

    public IborIndex iborIndex() {
        return iborIndex_;
    }

    @Override
    public double displacement() {
        return displacement_;
    }

    @Override
    public VolatilityType volatilityType() {
        return volatilityType_;
    }

    //
    // helpers
    //

    /** Nullable: returns the optionlet frequency (or {@code null} if unset). */
    public Period optionletFrequency() {
        return optionletFrequency_;
    }
}
