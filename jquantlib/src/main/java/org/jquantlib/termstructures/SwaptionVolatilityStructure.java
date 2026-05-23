package org.jquantlib.termstructures;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Rounding;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.util.Pair;

public abstract class SwaptionVolatilityStructure extends AbstractTermStructure {

    private final BusinessDayConvention bdc_;

    public SwaptionVolatilityStructure(final DayCounter dc, final BusinessDayConvention bdc) {
        super(dc);
        this.bdc_ = bdc;
    }

    public SwaptionVolatilityStructure(final Date referenceDate, final Calendar calendar, final DayCounter dc,
            final BusinessDayConvention bdc) {
        super(referenceDate, calendar, dc);
        this.bdc_ = bdc;
    }

    public SwaptionVolatilityStructure(final int settlementDays, final Calendar calendar, final DayCounter dc,
            final BusinessDayConvention bdc) {
        super(settlementDays, calendar, dc);
        this.bdc_ = bdc;
    }

    // ! returns the volatility for a given option time and swapLength

    public double volatility(final double optionTime, final double swapLength, final double strike) {
        return volatility(optionTime, swapLength, strike, false);
    }

    // ! returns the Black variance for a given option time and swapLength
    public abstract double blackVariance(double optionTime, double swapLength, double strike, boolean extrapolate);

    public double blackVariance(final double optionTime, final double swapLength, final double strike) {

        return blackVariance(optionTime, swapLength, strike, false);
    }

    // overloaded (at least) in SwaptionVolCube2
    /*
     * public SmileSection smileSection( Date optionDate, Period swapTenor) {
     *
     * } Pair<Double, Double> p = null;//convertDates(optionDate, swapTenor); return smileSectionImpl(p.first, p.second); }
     */

    // ! returns the volatility for a given option tenor and swap tenor
    public double volatility(final Period optionTenor, final Period swapTenor, final double strike) {
        return volatility(optionTenor, swapTenor, strike, false);
    }

    // ! returns the Black variance for a given option tenor and swap tenor

    public double blackVariance(final Period optionTenor, final Period swapTenor, final double strike) {
        return blackVariance(optionTenor, swapTenor, strike, false);
    }

    // @}
    // ! \name Limits
    // @{
    // ! the largest length for which the term structure can return vols
    public abstract Period maxSwapTenor();

    // ! the largest swapLength for which the term structure can return vols
    // ! the minimum strike for which the term structure can return vols
    public abstract double minStrike();

    // ! the maximum strike for which the term structure can return vols
    public abstract double maxStrike();

    // @}

    // ! the business day convention used for option date calculation
    public abstract BusinessDayConvention businessDayConvention();

    /**
     * Volatility quoting convention.
     * <p>
     * Mirrors C++ QuantLib v1.42.1 {@code SwaptionVolatilityStructure::volatilityType()}
     * (ql/termstructures/volatility/swaption/swaptionvolstructure.hpp lines 188-191): the base implementation returns
     * {@link VolatilityType#ShiftedLognormal} so that legacy concrete subclasses (which never overrode this hook)
     * continue to be quoted in lognormal terms.
     */
    public VolatilityType volatilityType() {
        return VolatilityType.ShiftedLognormal;
    }

    /**
     * Shift applied to the underlying rate when {@link #volatilityType()} is {@link VolatilityType#ShiftedLognormal};
     * zero by default for legacy lognormal quoting. Mirrors C++ QuantLib v1.42.1
     * {@code SwaptionVolatilityStructure::shiftImpl} default (see
     * ql/termstructures/volatility/swaption/swaptionvolstructure.hpp lines 478-480), exposed here as a convenience
     * accessor used by Bachelier-aware pricing engines.
     */
    public double shift() {
        return 0.0;
    }

    // ! implements the conversion between optionTenors and optionDates
    // public abstract Date optionDateFromTenor( Period optionTenor);

    // ! return smile section
    protected abstract SmileSection smileSectionImpl(double optionTime, double swapLength);

    protected abstract SmileSection smileSectionImpl(Date optionDate, Period swapTenor);

    // ! implements the actual volatility calculation in derived classes
    public abstract double volatilityImpl(double optionTime, double swapLength, double strike);

    protected double volatilityImpl(final Date optionDate, final Period swapTenor, final double strike) {
        final Pair< Double, Double > p = convertDates(optionDate, swapTenor);
        return volatilityImpl(p.first(), p.second(), strike);
    }

    public Date optionDateFromTenor(final Period optionTenor) {
        return calendar().advance(referenceDate(), optionTenor, businessDayConvention());
    }

    public double volatility(final double optionTime, final double swapLength, final double strike,
            final boolean extrapolate) {
        checkRange(optionTime, swapLength, strike, extrapolate);
        return volatilityImpl(optionTime, swapLength, strike);
    }

    public double blackVariance(final double optionTime, final double swapLength, final double strike,
            final Boolean extrapolate) {
        checkRange(optionTime, swapLength, strike, extrapolate);
        final double vol = volatilityImpl(optionTime, swapLength, strike);
        return vol * vol * optionTime;
    }

    public double volatility(final Date optionDate, final Period swapTenor, final double strike,
            final boolean extrapolate) {
        checkRange(optionDate, swapTenor, strike, extrapolate);
        return volatilityImpl(optionDate, swapTenor, strike);
    }

    public double blackVariance(final Date optionDate, final Period swapTenor, final double strike,
            final boolean extrapolate) {
        final double vol = volatility(optionDate, swapTenor, strike, extrapolate);
        final Pair< Double, Double > p = convertDates(optionDate, swapTenor);
        return vol * vol * p.first();
    }

    public double volatility(final Period optionTenor, final Period swapTenor, final double strike,
            final boolean extrapolate) {
        final Date optionDate = optionDateFromTenor(optionTenor);
        return volatility(optionDate, swapTenor, strike, extrapolate);
    }

    public double blackVariance(final Period optionTenor, final Period swapTenor, final double strike,
            final boolean extrapolate) {
        final Date optionDate = optionDateFromTenor(optionTenor);
        final double vol = volatility(optionDate, swapTenor, strike, extrapolate);
        final Pair< Double, Double > p = convertDates(optionDate, swapTenor);
        return vol * vol * p.first();
    }

    public SmileSection smileSection(final Period optionTenor, final Period swapTenor) {
        final Date optionDate = optionDateFromTenor(optionTenor);
        return smileSectionImpl(optionDate, swapTenor);
    }

    /**
     * Smile section at a fixed option date and swap tenor. Mirrors C++
     * {@code SwaptionVolatilityStructure::smileSection(Date, Period, bool)}. The {@code extrapolate} flag is accepted
     * for API parity but currently ignored by the constant-vol implementation.
     */
    public SmileSection smileSection(final Date optionDate, final Period swapTenor, final boolean extrapolate) {
        // C++ calls checkSwapTenor + checkRange; Java's Constant impl does not
        // need these for a flat surface. Forward to smileSectionImpl directly.
        return smileSectionImpl(optionDate, swapTenor);
    }

    /**
     * Smile section at a fixed option date and swap tenor (no extrapolation). Convenience overload — see
     * {@link #smileSection(Date, Period, boolean)}.
     */
    public SmileSection smileSection(final Date optionDate, final Period swapTenor) {
        return smileSection(optionDate, swapTenor, false);
    }

    public void checkRange(final double optionTime, final double swapLength, final double k,
            final boolean extrapolate) {
        super.checkRange(optionTime, extrapolate);
        if ( swapLength < 0.0 ) {
            throw new IllegalArgumentException("negative swapLength (" + swapLength + ") given");
        }
        if ( !extrapolate && !allowsExtrapolation() && swapLength > maxSwapLength() ) {
            throw new IllegalArgumentException(
                    "swapLength (" + swapLength + ") is past max curve swapLength (" + maxSwapLength() + ")");
        }
        if ( !extrapolate && !allowsExtrapolation() && (k < minStrike() || k > maxStrike()) ) {
            throw new IllegalArgumentException(
                    "strike (" + k + ") is outside the curve domain [" + minStrike() + "," + maxStrike() + "]");
        }
    }

    /**
     * Largest swap length (in years) for which the term structure can return vols. Mirrors C++
     * {@code SwaptionVolatilityStructure::maxSwapLength()}
     * (ql/termstructures/volatility/swaption/swaptionvolstructure.hpp lines 485-487): delegates to
     * {@link #swapLength(Period)} so that the result is the (rounded) tenor length in years rather than a day-count
     * year fraction.
     */
    public double maxSwapLength() {
        return swapLength(maxSwapTenor());
    }

    /**
     * Convert a swap-tenor {@link Period} into a tenor-length in years.
     * <p>
     * Mirrors C++ {@code SwaptionVolatilityStructure::swapLength(const Period&)}
     * (ql/termstructures/volatility/swaption/swaptionvolstructure.cpp lines 47-58): Months → length/12, Years → length,
     * anything else → fail. This is intentionally NOT day-count driven (it is the convention used to index the vol
     * matrix's swap axis), so we deliberately do not call {@code dayCounter().yearFraction(...)} here.
     */
    public double swapLength(final Period p) {
        QL.require(p.length() > 0, "non-positive swap tenor (" + p + ") given");
        return switch (p.units()) {
            case Months -> p.length() / 12.0;
            case Years -> p.length();
            default -> throw new IllegalArgumentException("invalid TimeUnit (" + p.units() + ") for swap length");
        };
    }

    /**
     * Convert a (start, end) swap-date pair into a tenor length in years.
     * <p>
     * Mirrors C++ {@code SwaptionVolatilityStructure::swapLength(const Date&, const Date&)}
     * (ql/termstructures/volatility/swaption/swaptionvolstructure.cpp lines 60-68): computes (end-start)/365.25*12,
     * rounds to the nearest integer (number of months), then divides by 12 to obtain tenor length in years.
     */
    public double swapLength(final Date start, final Date end) {
        QL.require(end.gt(start), "swap end date (" + end + ") must be greater than start (" + start + ")");
        double result = (end.sub(start)) / 365.25 * 12.0; // month unit
        result = new Rounding(0).operator(result);
        result /= 12.0;
        return result;
    }

    public Pair< Double, Double > convertDates(final Date optionDate, final Period swapTenor) {
        final Date end = optionDate.add(swapTenor);
        QL.require(end.gt(optionDate), "negative swap tenorgiven");
        final double optionTime = timeFromReference(optionDate);
        // Use the C++ tenor-length convention rather than day-count year fraction
        // so that the swap-axis lookup matches SwaptionVolatilityMatrix.
        final double timeLength = swapLength(swapTenor);
        return new Pair< Double, Double >(optionTime, timeLength);
    }

    protected void checkRange(final Date optionDate, final Period swapTenor, final double k,
            final boolean extrapolate) {
        super.checkRange(timeFromReference(optionDate), extrapolate);
        if ( swapTenor.length() <= 0 ) {
            throw new IllegalArgumentException("negative swap tenor (" + swapTenor + ") given");
        }
        if ( !extrapolate && !allowsExtrapolation() && swapTenor.gt(maxSwapTenor()) ) {
            throw new IllegalArgumentException(
                    "swap tenor (" + swapTenor + ") is past max tenor (" + maxSwapTenor() + ")");
        }
        // Mirrors C++ checkStrike: throw when strike is OUTSIDE the
        // curve domain. (The Java version had the inequality inverted.)
        if ( !extrapolate && !allowsExtrapolation() && (k < minStrike() || k > maxStrike()) ) {
            throw new IllegalArgumentException(
                    "strike (" + k + ") is outside the curve domain [" + minStrike() + "," + maxStrike() + "]");
        }

    }
}
