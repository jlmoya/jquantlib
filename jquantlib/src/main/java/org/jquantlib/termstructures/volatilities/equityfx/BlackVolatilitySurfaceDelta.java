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
 Copyright (C) 2019 Quaternion Risk Management Ltd
 Copyright (C) 2022 Skandinaviska Enskilda Banken AB (publ)
 Copyright (C) 2025 Paolo D'Elia

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.termstructures.volatilities.equityfx;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.fx.DeltaVolQuote;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.factories.Cubic;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackDeltaCalculator;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVolatilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.FlatSmileSection;
import org.jquantlib.termstructures.volatilities.InterpolatedSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/**
 * Black volatility surface parameterized by market deltas.
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/termstructures/volatility/equityfx/blackvolsurfacedelta.{hpp,cpp}}.
 *
 * <p>{@code BlackVolatilitySurfaceDelta} represents a Black volatility term
 * structure where market quotes are expressed as delta-based points (put deltas, call deltas and optionally an ATM
 * quote) for a set of option expiries. The surface converts the provided deltas to strikes (using spot and
 * domestic/foreign yield curves and the configured delta/ATM conventions) and builds per-expiry smile sections by
 * interpolating/extrapolating the input volatility matrix.
 *
 * <h3>Java port deviations</h3>
 * <ul>
 *  <li>C++ uses {@code BlackVarianceCurve} per delta as the time
 *      interpolator and lets it carry the {@code BlackVolTimeExtrapolation}
 *      enum into the variance-extrapolation step. The Java
 *      {@code BlackVarianceCurve} predates the enum and lacks
 *      {@code setInterpolation()} bootstrap; to avoid touching it we keep
 *      per-delta {@code (times, variances)} arrays directly and apply
 *      {@link BlackVolTimeExtrapolation} to the variance values.</li>
 *  <li>C++ {@code ext::optional} for the override delta-types maps to
 *      Java overloads where {@code null} means "fall back to the
 *      corresponding {@code deltaType}".</li>
 *  <li>The C++ smile section builder caches strikes in a
 *      {@code std::map<Real,Real, comp>} where {@code comp} treats two
 *      values that are {@code close()} as equal. We use the same logic on
 *      top of a {@code TreeMap<Double,Double>} with an explicit linear
 *      scan to detect "close" duplicates before insertion (the JDK
 *      {@code TreeMap} comparator must be a total order, so we cannot
 *      reuse the non-transitive C++ comparator literally).</li>
 * </ul>
 */
public class BlackVolatilitySurfaceDelta extends BlackVolatilityTermStructure {

    private final Date[] dates_;

    //
    // private fields — mirror C++ member layout
    //
    private final double[] times_;
    private final double[] putDeltas_;
    private final double[] callDeltas_;
    private final boolean hasAtm_;
    /** Per-delta variance series — {@code variances_[i][j] = vol_ij^2 * times_[j]}. */
    private final double[][] variances_;
    /** Per-delta linear interpolation on {@code (times, variances)} including {@code (0,0)}. */
    private final Interpolation[] interpolators_;
    /** Per-delta time grid with a leading {@code 0.0} prepended (mirrors C++ BlackVarianceCurve). */
    private final double[] interpTimes_;
    private final Handle< ? extends Quote > spot_;
    private final Handle< ? extends YieldTermStructure > domesticTS_;
    private final Handle< ? extends YieldTermStructure > foreignTS_;
    private final DeltaVolQuote.DeltaType deltaType_;
    private final DeltaVolQuote.AtmType atmType_;
    private final DeltaVolQuote.DeltaType atmDeltaType_;
    private final SmileInterpolationMethod interpolationMethod_;
    private final boolean flatStrikeExtrapolation_;
    private final BlackVolTimeExtrapolation.Type timeExtrapolationType_;
    private final Period switchTenor_;
    private final DeltaVolQuote.DeltaType longTermDeltaType_;
    private final DeltaVolQuote.AtmType longTermAtmType_;
    private final DeltaVolQuote.DeltaType longTermAtmDeltaType_;
    private final double switchTime_;
    /**
     * Convenience constructor — all defaults applied (Spot delta, AtmDeltaNeutral ATM, Linear smile, flat-vol time
     * extrapolation, no switch tenor).
     */
    public BlackVolatilitySurfaceDelta(final Date referenceDate, final Date[] dates, final double[] putDeltas,
            final double[] callDeltas, final boolean hasAtm, final Matrix blackVolMatrix, final DayCounter dayCounter,
            final Calendar cal, final Handle< ? extends Quote > spot,
            final Handle< ? extends YieldTermStructure > domesticTS,
            final Handle< ? extends YieldTermStructure > foreignTS) {
        this(referenceDate, dates, putDeltas, callDeltas, hasAtm, blackVolMatrix, dayCounter, cal, spot, domesticTS,
                foreignTS, DeltaVolQuote.DeltaType.Spot, DeltaVolQuote.AtmType.AtmDeltaNeutral, null,
                SmileInterpolationMethod.Linear, false, BlackVolTimeExtrapolation.Type.FlatVolatility,
                new Period(0, TimeUnit.Days), DeltaVolQuote.DeltaType.Fwd, DeltaVolQuote.AtmType.AtmDeltaNeutral, null);
    }

    //
    // public constructors
    //

    /**
     * Full constructor mirroring the C++ v1.42.1 signature.
     *
     * @param atmDeltaType         optional override delta-type for ATM computation; {@code null} ⇒ use
     *                             {@code deltaType}.
     * @param longTermAtmDeltaType optional long-term override; {@code null} ⇒ use {@code longTermDeltaType}.
     */
    public BlackVolatilitySurfaceDelta(final Date referenceDate, final Date[] dates, final double[] putDeltas,
            final double[] callDeltas, final boolean hasAtm, final Matrix blackVolMatrix, final DayCounter dayCounter,
            final Calendar cal, final Handle< ? extends Quote > spot,
            final Handle< ? extends YieldTermStructure > domesticTS,
            final Handle< ? extends YieldTermStructure > foreignTS, final DeltaVolQuote.DeltaType deltaType,
            final DeltaVolQuote.AtmType atmType, final DeltaVolQuote.DeltaType atmDeltaType,
            final SmileInterpolationMethod interpolationMethod, final boolean flatStrikeExtrapolation,
            final BlackVolTimeExtrapolation.Type timeExtrapolationType, final Period switchTenor,
            final DeltaVolQuote.DeltaType longTermDeltaType, final DeltaVolQuote.AtmType longTermAtmType,
            final DeltaVolQuote.DeltaType longTermAtmDeltaType) {
        super(referenceDate, cal, BusinessDayConvention.Following, dayCounter);

        this.dates_ = dates.clone();
        this.times_ = new double[dates.length];
        this.putDeltas_ = putDeltas.clone();
        this.callDeltas_ = callDeltas.clone();
        this.hasAtm_ = hasAtm;
        this.spot_ = spot;
        this.domesticTS_ = domesticTS;
        this.foreignTS_ = foreignTS;
        this.deltaType_ = deltaType;
        this.atmType_ = atmType;
        this.atmDeltaType_ = (atmDeltaType != null) ? atmDeltaType : deltaType;
        this.interpolationMethod_ = interpolationMethod;
        this.flatStrikeExtrapolation_ = flatStrikeExtrapolation;
        this.timeExtrapolationType_ = timeExtrapolationType;
        this.switchTenor_ = switchTenor;
        this.longTermDeltaType_ = longTermDeltaType;
        this.longTermAtmType_ = longTermAtmType;
        this.longTermAtmDeltaType_ = (longTermAtmDeltaType != null) ? longTermAtmDeltaType : longTermDeltaType;

        // switch time
        if ( switchTenor_.length() == 0 ) {
            this.switchTime_ = Constants.QL_MAX_REAL;
        } else {
            this.switchTime_ = timeFromReference(optionDateFromTenor(switchTenor_));
        }

        QL.require(dates.length > 1, "at least 1 date required");
        for ( int i = 0; i < dates.length; ++i ) {
            QL.require(referenceDate.lt(dates[i]), "Dates must be greater than reference date");
            this.times_[i] = timeFromReference(dates[i]);
            if ( i > 0 ) {
                QL.require(this.times_[i] > this.times_[i - 1], "dates must be sorted unique!");
            }
        }

        // matrix shape
        final int n = putDeltas.length + (hasAtm ? 1 : 0) + callDeltas.length;
        QL.require(n > 0, "Need at least one delta");
        QL.require(blackVolMatrix.cols() == n,
                "Invalid number of columns in blackVolMatrix, got " + blackVolMatrix.cols() + " but have " + n
                        + " deltas");
        QL.require(blackVolMatrix.rows() == dates.length,
                "Invalid number of rows in blackVolMatrix, got " + blackVolMatrix.rows() + " but have " + dates.length
                        + " dates");

        // build per-delta time-variance interpolators
        // C++ uses BlackVarianceCurve which internally prepends (t=0, var=0)
        // and linearly interpolates variance; we replicate the prepended node.
        this.interpTimes_ = new double[dates.length + 1];
        this.interpTimes_[0] = 0.0;
        System.arraycopy(this.times_, 0, this.interpTimes_, 1, dates.length);
        this.variances_ = new double[n][dates.length];
        this.interpolators_ = new Interpolation[n];
        final Linear linearFactory = new Linear();
        for ( int i = 0; i < n; ++i ) {
            final double[] varRow = new double[dates.length + 1];
            varRow[0] = 0.0;
            for ( int j = 0; j < dates.length; ++j ) {
                final double vol = blackVolMatrix.get(j, i);
                this.variances_[i][j] = vol * vol * this.times_[j];
                varRow[j + 1] = this.variances_[i][j];
            }
            this.interpolators_[i] = linearFactory.interpolate(new Array(this.interpTimes_), new Array(varRow));
            this.interpolators_[i].enableExtrapolation();
            this.interpolators_[i].update();
        }

        // register with market quotes / curves
        spot_.addObserver(this);
        domesticTS_.addObserver(this);
        foreignTS_.addObserver(this);
    }

    /**
     * Mirror C++ {@code map<Real,Real, !close(a,b) && a<b>}: skip insertion if the candidate strike is already "close"
     * to a stored key.
     */
    private static void insertIfNotClose(final TreeMap< Double, Double > map, final double strike, final double vol) {
        // C++ uses close() (1 ulp by default); we use isClose with default n=42 ulps,
        // which matches QL's macro-close tolerance used elsewhere in JQuantLib.
        final Iterator< Double > it = map.keySet().iterator();
        while ( it.hasNext() ) {
            final double k = it.next();
            if ( Closeness.isClose(k, strike) ) {
                return; // already present
            }
        }
        map.put(strike, vol);
    }

    //
    // BlackVolatilityTermStructure / VolatilityTermStructure overrides
    //

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    @Override
    public double minStrike() {
        return 0.0;
    }

    @Override
    public double maxStrike() {
        return Constants.QL_MAX_REAL;
    }

    //
    // public inspectors
    //

    public Date[] dates() {
        return dates_.clone();
    }

    //
    // public smile-section access
    //

    /**
     * Return an FxSmile for the time {@code t} (year fraction from the reference date). Mirrors C++
     * {@code blackVolSmile(Time t)}.
     */
    public SmileSection blackVolSmile(final double t) {

        final double spot = spot_.currentLink().value();
        final double dDiscount = domesticTS_.currentLink().discount(t);
        final double fDiscount = foreignTS_.currentLink().discount(t);
        final double sqrtT = Math.sqrt(t);

        final DeltaVolQuote.AtmType at;
        final DeltaVolQuote.DeltaType dt;
        final DeltaVolQuote.DeltaType atmDt;
        if ( t < switchTime_ && !Closeness.isCloseEnough(t, switchTime_) ) {
            at = atmType_;
            dt = deltaType_;
            atmDt = atmDeltaType_;
        } else {
            at = longTermAtmType_;
            dt = longTermDeltaType_;
            atmDt = longTermAtmDeltaType_;
        }

        // Mirror C++ comp: skip insertion of strikes already "close" to one stored.
        final var smileMap = new TreeMap< Double, Double >();
        int i = 0;
        double atmLevel = 1.0;

        for ( final double delta : putDeltas_ ) {
            final double vol = blackVolAt(t, i);
            try {
                final BlackDeltaCalculator bdc = new BlackDeltaCalculator(Option.Type.Put, dt, spot, dDiscount,
                        fDiscount, vol * sqrtT);
                final double strike = bdc.strikeFromDelta(delta);
                insertIfNotClose(smileMap, strike, vol);
            } catch ( final RuntimeException e ) {
                QL.error("BlackVolatilitySurfaceDelta: Error during calculating put strike at delta " + delta + ": "
                        + e.getMessage());
            }
            ++i;
        }
        if ( hasAtm_ ) {
            final double vol = blackVolAt(t, i);
            atmLevel = vol;
            try {
                final BlackDeltaCalculator bdc = new BlackDeltaCalculator(Option.Type.Put, atmDt, spot, dDiscount,
                        fDiscount, vol * sqrtT);
                final double strike = bdc.atmStrike(at);
                insertIfNotClose(smileMap, strike, vol);
            } catch ( final RuntimeException e ) {
                QL.error("BlackVolatilitySurfaceDelta: Error during calculating atm strike: " + e.getMessage());
            }
            ++i;
        }
        for ( final double delta : callDeltas_ ) {
            final double vol = blackVolAt(t, i);
            try {
                final BlackDeltaCalculator bdc = new BlackDeltaCalculator(Option.Type.Call, dt, spot, dDiscount,
                        fDiscount, vol * sqrtT);
                final double strike = bdc.strikeFromDelta(delta);
                insertIfNotClose(smileMap, strike, vol);
            } catch ( final RuntimeException e ) {
                QL.error("BlackVolatilitySurfaceDelta: Error during calculating call strike at delta " + delta + ": "
                        + e.getMessage());
            }
            ++i;
        }

        QL.require(!smileMap.isEmpty(),
                "BlackVolatilitySurfaceDelta::blackVolSmile(" + t + "): no strikes given, this is unexpected.");

        final List< Double > strikesList = new ArrayList<>(smileMap.keySet());
        final double[] strikes = new double[strikesList.size()];
        final double[] stdDevs = new double[strikesList.size()];
        int k = 0;
        for ( final java.util.Map.Entry< Double, Double > kv : smileMap.entrySet() ) {
            strikes[k] = kv.getKey();
            stdDevs[k] = kv.getValue() * sqrtT;
            ++k;
        }

        if ( stdDevs.length == 1 ) {
            // single-strike fallback (e.g. for t == 0)
            return new FlatSmileSection(t, stdDevs[0] / sqrtT, dayCounter(), atmLevel);
        }

        switch ( interpolationMethod_ ) {
        case Linear:
            return new InterpolatedSmileSection(t, strikes, stdDevs, atmLevel, new Linear(), dayCounter(),
                    VolatilityType.ShiftedLognormal, 0.0, flatStrikeExtrapolation_);
        case NaturalCubic:
            return new InterpolatedSmileSection(t, strikes, stdDevs, atmLevel,
                    new Cubic(CubicInterpolation.DerivativeApprox.Kruger, false,
                            CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                            CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0), dayCounter(),
                    VolatilityType.ShiftedLognormal, 0.0, flatStrikeExtrapolation_);
        case FinancialCubic:
            return new InterpolatedSmileSection(t, strikes, stdDevs, atmLevel,
                    new Cubic(CubicInterpolation.DerivativeApprox.Kruger, true,
                            CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                            CubicInterpolation.BoundaryCondition.FirstDerivative, 0.0), dayCounter(),
                    VolatilityType.ShiftedLognormal, 0.0, flatStrikeExtrapolation_);
        case CubicSpline:
            return new InterpolatedSmileSection(t, strikes, stdDevs, atmLevel,
                    new Cubic(CubicInterpolation.DerivativeApprox.Spline, false,
                            CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                            CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0), dayCounter(),
                    VolatilityType.ShiftedLognormal, 0.0, flatStrikeExtrapolation_);
        default:
            QL.error("Invalid method " + interpolationMethod_);
            return null; // unreachable
        }
    }

    /** Convenience overload — resolve a calendar Date to time first. */
    public SmileSection blackVolSmile(final Date d) {
        return blackVolSmile(timeFromReference(d));
    }

    //
    // BlackVolatilityTermStructure override
    //

    @Override
    protected double blackVolImpl(final double t, final double strikeIn) {
        // For flat-vol time extrapolation we collapse the time argument to
        // the last node before delegating to the smile builder, so the smile
        // section sees the latest in-bound vols.
        final double tme = (t > times_[times_.length - 1]
                && timeExtrapolationType_ == BlackVolTimeExtrapolation.Type.FlatVolatility)
                ? times_[times_.length - 1]
                : t;

        double strike = strikeIn;
        // ATM hack — C++ treats strike == 0 or Null<Real>() as "use ATM".
        if ( strike == 0 || Double.isNaN(strike) ) {
            if ( hasAtm_ ) {
                return blackVolAt(tme, putDeltas_.length);
            } else {
                strike = atmLevel(tme);
            }
        }
        return blackVolSmile(tme).volatility(strike);
    }

    /**
     * At-the-money level (the FX-style forward) at time {@code t}: {@code spot * df_foreign / df_domestic}.
     * <p>
     * C++ v1.43 renamed the private {@code forward(Time)} helper to a public
     * {@code atmLevel(Time) const override} ({@code blackvolsurfacedelta.{hpp,cpp}}), which is what makes the
     * {@link org.jquantlib.termstructures.volatilities.SmileSection} returned by
     * {@code smileSection()} report a usable {@code atmLevel()} — and therefore lets its
     * {@code optionPrice()} work instead of failing the base class's requirement.
     */
    @Override
    public double atmLevel(final double t) {
        return spot_.currentLink().value() * foreignTS_.currentLink().discount(t) / domesticTS_.currentLink()
                .discount(t);
    }

    //
    // private helpers
    //

    /**
     * Black volatility for the i-th delta column at time {@code t}, applying the configured
     * {@link BlackVolTimeExtrapolation.Type} for {@code t} beyond the last node. Mirrors
     * {@code BlackVarianceCurve::blackVol(t,1,true)} with the configured extrapolation, since the C++ code threads the
     * enum into the per-delta {@code BlackVarianceCurve}.
     */
    private double blackVolAt(final double t, final int deltaIdx) {
        final double variance;
        if ( t <= times_[times_.length - 1] || Closeness.isCloseEnough(t, times_[times_.length - 1]) ) {
            variance = interpolators_[deltaIdx].op(t);
        } else {
            final int idx = deltaIdx;
            variance = BlackVolTimeExtrapolation.extrapolatedVariance(timeExtrapolationType_, t, times_,
                    new BlackVolTimeExtrapolation.VarianceCurve() {
                        @Override
                        public double op(final double tt) {
                            return interpolators_[idx].op(tt);
                        }
                    });
        }
        return Math.sqrt(Math.max(variance, 0.0) / t);
    }

    /** Supported interpolation methods for the smile sections. */
    public enum SmileInterpolationMethod {
        Linear, NaturalCubic, FinancialCubic, CubicSpline
    }
}
