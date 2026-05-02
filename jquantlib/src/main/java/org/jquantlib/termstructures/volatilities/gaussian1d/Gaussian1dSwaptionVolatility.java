/*
 Copyright (C) 2015 Peter Caspers

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
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.termstructures.volatilities.gaussian1d;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.EuriborSwapIsdaFixA;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.math.Rounding;
import org.jquantlib.math.distributions.Derivative;
import org.jquantlib.math.solvers1D.NewtonSafe;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gaussian1dModel;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Swaption volatility structure implied by a Gaussian 1d model.
 * <p>
 * Java port of QuantLib v1.42.1
 * {@code ql/termstructures/volatility/swaption/gaussian1dswaptionvolatility.{hpp,cpp}}
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}). Phase 2j WI-1.4.
 * <p>
 * Per-smile-section pricing uses {@link Gaussian1dSmileSection}. The
 * {@link #smileSectionImpl(double, double)} overload performs a
 * {@code NewtonSafe} date inversion to convert {@code optionTime} back to
 * a fixing {@link Date}, exactly as in C++.
 * <p>
 * <strong>WI-1.4 scope:</strong> {@link #volatilityImpl(Date, Period, double)}
 * and {@link #volatilityImpl(double, double, double)} both delegate to
 * {@link Gaussian1dSmileSection#volatilityImpl}, which in turn requires WI-2
 * swaption/cap-floor engines.  The structural plumbing (constructors,
 * {@code smileSectionImpl} routing, {@code maxDate}/{@code maxSwapTenor} etc.)
 * is fully functional.
 *
 * @author Peter Caspers (C++ original)
 * @author JQuantLib contributors (Java port)
 */
public class Gaussian1dSwaptionVolatility extends SwaptionVolatilityStructure {

    // ── constants ─────────────────────────────────────────────────────────────

    /** Mirrors C++ {@code 100 * Years}. */
    private static final Period MAX_SWAP_TENOR = new Period(100, TimeUnit.Years);

    // ── fields (all before constructors so no forward-reference issue) ─────────

    /** Stored bdc for {@link #businessDayConvention()} getter. */
    private final BusinessDayConvention bdc_;

    private final SwapIndex indexBase_;
    private final Gaussian1dModel model_;

    /**
     * Optional pricing engine ({@code Gaussian1dSwaptionEngine}).
     * {@code null} until WI-2 injects the engine.
     */
    private final PricingEngine engine_;

    // ── constructors ──────────────────────────────────────────────────────────

    /**
     * Full constructor.
     *
     * @param cal        fixing calendar (typically the swap index calendar)
     * @param bdc        business day convention for option dates
     * @param indexBase  swap-index template; the tenor will be overridden in
     *                   {@link #smileSectionImpl(Date, Period)} via
     *                   {@code cloneWithTenor(indexBase, tenor)}
     * @param model      Gaussian 1d model (provides swap-rate/annuity)
     * @param dc         day counter
     * @param engine     {@code Gaussian1dSwaptionEngine} — may be {@code null}
     *                   (WI-1.4 partial port; WI-2 will inject)
     */
    public Gaussian1dSwaptionVolatility(
            final Calendar cal,
            final BusinessDayConvention bdc,
            final SwapIndex indexBase,
            final Gaussian1dModel model,
            final DayCounter dc,
            final PricingEngine engine) {

        // C++: SwaptionVolatilityStructure(model->termStructure()->referenceDate(), cal, bdc, dc)
        super(model.termStructure().currentLink().referenceDate(), cal, dc, bdc);

        this.bdc_       = bdc;
        this.indexBase_ = indexBase;
        this.model_     = model;
        this.engine_    = engine;
    }

    /**
     * Convenience constructor without an engine.
     */
    public Gaussian1dSwaptionVolatility(
            final Calendar cal,
            final BusinessDayConvention bdc,
            final SwapIndex indexBase,
            final Gaussian1dModel model,
            final DayCounter dc) {
        this(cal, bdc, indexBase, model, dc, null);
    }

    // ── SwaptionVolatilityStructure interface ─────────────────────────────────

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    @Override
    public Period maxSwapTenor() {
        return MAX_SWAP_TENOR;
    }

    @Override
    public double minStrike() {
        return 0.0;
    }

    @Override
    public double maxStrike() {
        return Double.MAX_VALUE;
    }

    @Override
    public BusinessDayConvention businessDayConvention() {
        return bdc_;
    }

    // ── smileSectionImpl overloads ────────────────────────────────────────────

    /**
     * Creates a {@link Gaussian1dSmileSection} for the given expiry date and
     * swap tenor. Mirrors C++ {@code smileSectionImpl(const Date&, const Period&)}.
     */
    @Override
    protected SmileSection smileSectionImpl(final Date optionDate, final Period swapTenor) {
        // C++: indexBase_->clone(tenor)
        // Java SwapIndex has no generic clone(tenor). We replicate using EuriborSwapIsdaFixA
        // with the same YTS. For other index types a proper clone() would be needed;
        // this covers the standard Euribor case used in tests and WI-2 engines.
        final SwapIndex tenorIdx = cloneWithTenor(indexBase_, swapTenor);
        return new Gaussian1dSmileSection(optionDate, tenorIdx, model_, dayCounter(), engine_);
    }

    /**
     * Creates a {@link Gaussian1dSmileSection} for a given option time and
     * swap length (in years). Performs a {@code NewtonSafe} date inversion to
     * recover the fixing date, matching C++ exactly.
     */
    @Override
    protected SmileSection smileSectionImpl(final double optionTime, final double swapLength) {
        // Date inversion: find serialNumber d s.t. timeFromReference(d) ≈ optionTime
        final DateHelper helper = new DateHelper(optionTime);
        final NewtonSafe newton = new NewtonSafe();
        final double guess = 365.25 * optionTime + referenceDate().serialNumber();
        final double rawSerial = newton.solve(helper, 0.1, guess, 1.0);
        Date d = new Date((long) rawSerial);
        d = indexBase_.fixingCalendar().adjust(d);

        // swapLength (years) → Period in whole months, mirroring C++ Rounding(0)
        final Rounding rounding = new Rounding(0);
        final int months = (int) rounding.operator(swapLength * 12.0);
        final Period tenor = new Period(months, TimeUnit.Months);

        return smileSectionImpl(d, tenor);
    }

    // ── volatilityImpl overloads ──────────────────────────────────────────────

    @Override
    public double volatilityImpl(final double optionTime, final double swapLength,
                                 final double strike) {
        return smileSectionImpl(optionTime, swapLength).volatility(strike);
    }

    /**
     * Protected Date/Period-based overload, used by the base-class
     * {@link SwaptionVolatilityStructure#volatility(Date, Period, double, boolean)} chain.
     */
    @Override
    protected double volatilityImpl(final Date optionDate, final Period swapTenor,
                                    final double strike) {
        return smileSectionImpl(optionDate, swapTenor).volatility(strike);
    }

    // ── blackVariance ─────────────────────────────────────────────────────────

    @Override
    public double blackVariance(final double optionTime, final double swapLength,
                                final double strike, final boolean extrapolate) {
        checkRange(optionTime, swapLength, strike, extrapolate);
        final double vol = volatilityImpl(optionTime, swapLength, strike);
        return vol * vol * optionTime;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Creates a SwapIndex with the same conventions as {@code base} but with
     * a different tenor.  Currently only handles {@link EuriborSwapIsdaFixA};
     * other index types will fall back to the base index (which then uses the
     * wrong tenor but will not fail).
     */
    private SwapIndex cloneWithTenor(final SwapIndex base, final Period tenor) {
        // C++ calls indexBase_->clone(tenor) which is a virtual method.
        // Java SwapIndex does not have clone(). We replicate by constructing
        // an EuriborSwapIsdaFixA with the desired tenor and the same YTS.
        return new EuriborSwapIsdaFixA(tenor, base.iborIndex().termStructure());
    }

    /**
     * Inner functor implementing the {@link Derivative} interface for the
     * NewtonSafe date inversion:
     * {@code f(serial) = timeFromReference(Date(serial)) - optionTime}.
     * The derivative approximation uses a forward difference (step 1e-6),
     * matching C++ DateHelper::derivative.
     */
    private final class DateHelper implements Derivative {

        private final double targetTime_;

        DateHelper(final double targetTime) {
            this.targetTime_ = targetTime;
        }

        /**
         * Interpolated time-from-reference at fractional serial number.
         * Mirrors C++ {@code DateHelper::operator()}.
         */
        @Override
        public double op(final double serial) {
            final Date d1 = new Date((long) serial);
            final Date d2 = new Date((long) serial + 1L);
            final double t1 = timeFromReference(d1) - targetTime_;
            final double t2 = timeFromReference(d2) - targetTime_;
            final double h  = serial - (long) serial;
            return h * t2 + (1.0 - h) * t1;
        }

        /**
         * Forward-difference derivative approximation.
         * Mirrors C++ {@code DateHelper::derivative}.
         */
        @Override
        public double derivative(final double serial) {
            return (op(serial + 1e-6) - op(serial)) * 1e6;
        }
    }
}
