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
 Copyright (C) 2013 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.pricingengines.swaption;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.IborCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.InterestRateIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.*;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.model.BlackCalibrationHelper;
import org.jquantlib.model.shortrate.calibrationhelpers.SwaptionHelper;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gaussian1dModel;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for pricing engines that can generate a calibration basket of vanilla swaptions.
 *
 * <p>Java port of C++ QuantLib v1.42.1
 * {@code ql/pricingengines/swaption/basketgeneratingengine.{hpp,cpp}} (commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}). Phase 2k Track B.
 *
 * <p>Concrete subclasses ({@link org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dNonstandardSwaptionEngine}
 * and {@link org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dFloatFloatSwaptionEngine}) implement the four
 * abstract hooks:
 * <ul>
 * <li>{@link #underlyingNpv(Date, double)} — NPV of the exotic underlying
 *     at state {@code y} given exercise date.
 * <li>{@link #underlyingType()} — whether the underlying is a Payer or Receiver swap.
 * <li>{@link #underlyingLastDate()} — last payment date of the underlying.
 * <li>{@link #initialGuess(Date)} — initial optimizer guess (nominal, maturity, rate).
 * </ul>
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>{@code RebatedExercise} is not yet ported; rebate is treated as zero.
 * <li>C++ {@code SwapIndex} exposes {@code forwardingTermStructure()},
 *     {@code discountingTermStructure()} and {@code exogenousDiscount()}.
 *     Java {@link SwapIndex} does not; the forwarding curve is always
 *     {@code standardSwapBase.iborIndex().termStructure()}, and there is
 *     no separate discount curve (equivalent to {@code exogenousDiscount=false}).
 * <li>C++ obtains a {@code shared_ptr<Gaussian1dModel>} from the handle
 *     via {@code *onefactormodel_}. Java passes the model directly.
 * </ul>
 *
 * @see org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dNonstandardSwaptionEngine
 * @see org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dFloatFloatSwaptionEngine
 */
public abstract class BasketGeneratingEngine {

    protected final Gaussian1dModel onefactormodel_;

    // ── protected fields (mirrors C++ private fields — accessible to subclasses) ──
    protected final Handle< Quote > oas_;
    protected final Handle< YieldTermStructure > discountCurve_;
    /**
     * Protected constructor (mirrors C++ protected ctor from {@code shared_ptr<Gaussian1dModel>}).
     */
    protected BasketGeneratingEngine(final Gaussian1dModel model, final Handle< Quote > oas,
            final Handle< YieldTermStructure > discountCurve) {
        this.onefactormodel_ = model;
        this.oas_ = (oas != null) ? oas : new Handle< Quote >();
        this.discountCurve_ = (discountCurve != null) ? discountCurve : new Handle< YieldTermStructure >();
    }

    // ── constructors ──────────────────────────────────────────────────────────

    /**
     * NPV of the exotic underlying conditional on state {@code y} at {@code expiry}. Mirrors C++
     * {@code virtual Real underlyingNpv(const Date& expiry, Real y) = 0}.
     */
    protected abstract double underlyingNpv(Date expiry, double y);

    // ── abstract hooks (mirrors C++ pure virtual methods) ─────────────────────

    /**
     * Payer or Receiver type of the exotic underlying. Mirrors C++ {@code virtual Swap::Type underlyingType() = 0}.
     */
    protected abstract VanillaSwap.Type underlyingType();

    /**
     * Last cashflow payment date of the exotic underlying. Mirrors C++
     * {@code virtual const Date underlyingLastDate() = 0}.
     */
    protected abstract Date underlyingLastDate();

    /**
     * Initial optimizer guess {@code [nominal, maturity, rate]}. Mirrors C++
     * {@code virtual const Array initialGuess(const Date&) = 0}.
     */
    protected abstract double[] initialGuess(Date expiry);

    /**
     * Generates the calibration basket for a given exercise schedule, swap index, and swaption volatility surface.
     *
     * <p>Mirrors C++ {@code BasketGeneratingEngine::calibrationBasket}.
     *
     * @param exercise           exercise schedule (may be Bermudan or European)
     * @param standardSwapBase   swap index defining standard swaption attributes
     * @param swaptionVolatility vol surface for calibration helper construction
     * @param basketType         {@link CalibrationBasketType#Naive} or
     *                           {@link CalibrationBasketType#MaturityStrikeByDeltaGamma}
     * @return list of {@link SwaptionHelper} calibration instruments, one per live exercise date
     */
    public List< BlackCalibrationHelper > calibrationBasket(final Exercise exercise, final SwapIndex standardSwapBase,
            final SwaptionVolatilityStructure swaptionVolatility, final CalibrationBasketType basketType) {

        QL.require(!standardSwapBase.iborIndex().termStructure().empty(),
                "standard swap base forwarding term structure must not be empty.");
        // Java SwapIndex has no exogenousDiscount — always treat as false
        // (no separate discounting curve check needed)

        final List< BlackCalibrationHelper > result = new ArrayList< BlackCalibrationHelper >();

        final Date today = new Settings().evaluationDate();

        final List< Date > exDates = exercise.dates();
        // C++ upper_bound(dates, today) — find first date strictly > today
        int minIdxAlive = 0;
        for ( int i = 0; i < exDates.size(); i++ ) {
            if ( exDates.get(i).gt(today) ) {
                minIdxAlive = i;
                break;
            }
            minIdxAlive = exDates.size(); // all expired
        }
        if ( exDates.isEmpty() || !exDates.get(exDates.size() - 1).gt(today) ) {
            return result; // all exercise dates expired
        }
        // Correctly compute minIdxAlive as first index > today
        minIdxAlive = exDates.size();
        for ( int i = 0; i < exDates.size(); i++ ) {
            if ( exDates.get(i).gt(today) ) {
                minIdxAlive = i;
                break;
            }
        }

        for ( int i = minIdxAlive; i < exDates.size(); i++ ) {

            final Date expiry = exDates.get(i);
            // RebatedExercise not ported — treat rebate as zero
            final double rebate = 0.0;
            final Date rebateDate = expiry;

            final SwaptionHelper helper;

            switch ( basketType ) {

            case Naive: {
                // swapLength = yearFraction(valueDate(expiry), underlyingLastDate())
                final Date valueDate = standardSwapBase.valueDate(expiry);
                final double swapLength = swaptionVolatility.dayCounter().yearFraction(valueDate, underlyingLastDate());

                // Tenored period = round(swapLength * 12) months
                final long tenorMonths = Math.round(swapLength * 12.0);
                final Period tenorPeriod = new Period((int) tenorMonths, TimeUnit.Months);

                final SmileSection sec = swaptionVolatility.smileSection(expiry, tenorPeriod, true);
                final double atmStrike = sec.atmLevel();
                final double atmVol;
                if ( Double.isNaN(atmStrike) || atmStrike == org.jquantlib.math.Constants.NULL_REAL ) {
                    atmVol = sec.volatility(0.03);
                } else {
                    atmVol = sec.volatility(atmStrike);
                }
                final double shift = sec.shift();

                // Use forwarding term structure (no exogenous discount in Java SwapIndex)
                final Handle< YieldTermStructure > discTs = standardSwapBase.iborIndex().termStructure();

                helper = new SwaptionHelper(expiry, underlyingLastDate(), new Handle< Quote >(new SimpleQuote(atmVol)),
                        standardSwapBase.iborIndex(), standardSwapBase.fixedLegTenor(), standardSwapBase.dayCounter(),
                        standardSwapBase.iborIndex().dayCounter(), discTs,
                        BlackCalibrationHelper.CalibrationErrorType.RelativePriceError,
                        org.jquantlib.math.Constants.NULL_REAL, 1.0, swaptionVolatility.volatilityType(), shift);
                break;
            }

            case MaturityStrikeByDeltaGamma: {

                // Finite difference step h in y-state
                final double h = 0.0001;

                // OAS zero-spread discount (for rebate, zero if no OAS)
                final double zSpreadDsc;
                if ( oas_.empty() ) {
                    zSpreadDsc = 1.0;
                } else {
                    final double yf = onefactormodel_.termStructure().currentLink().dayCounter()
                            .yearFraction(expiry, rebateDate);
                    zSpreadDsc = JQuantMath.exp(-oas_.currentLink().value() * yf);
                }

                // NPV at y = -h, 0, +h
                final double npvm =
                        underlyingNpv(expiry, -h) + rebate * onefactormodel_.zerobond(rebateDate, expiry, -h,
                                discountCurve_) * zSpreadDsc;
                final double npv =
                        underlyingNpv(expiry, 0.0) + rebate * onefactormodel_.zerobond(rebateDate, expiry, 0.0,
                                discountCurve_) * zSpreadDsc;
                final double npvp = underlyingNpv(expiry, h) + rebate * onefactormodel_.zerobond(rebateDate, expiry, h,
                        discountCurve_) * zSpreadDsc;

                final double delta = (npvp - npvm) / (2.0 * h);
                final double gamma = (npvp - 2.0 * npv + npvm) / (h * h);

                QL.require(npv * npv + delta * delta + gamma * gamma > 0.0,
                        "(npv,delta,gamma) must have a positive norm");

                // Maximum maturity: yearFraction(expiry, maxDate - 365 days)
                final double maxMaturity = swaptionVolatility.dayCounter()
                        .yearFraction(expiry, Date.maxDate().add(-365));

                // Build MatchHelper cost function
                final MatchHelper matchHelper = new MatchHelper(underlyingType(), npv, delta, gamma, onefactormodel_,
                        standardSwapBase, expiry, maxMaturity, h);

                // Initial guess [nominal, maturity, rate]
                final double[] ig = initialGuess(expiry);
                QL.require(ig.length == 3, "initial guess must have size 3 (but is " + ig.length + ")");
                final Array initial = new Array(3);
                initial.set(0, ig[0]);
                initial.set(1, ig[1]);
                initial.set(2, ig[2]);

                // LM optimization
                final EndCriteria ec = new EndCriteria(1000, 200, 1e-8, 1e-8, 1e-8);
                final Constraint constraint = new NoConstraint();
                final Problem p = new Problem(matchHelper, constraint, initial);
                final LevenbergMarquardt lm = new LevenbergMarquardt();

                final EndCriteria.Type ret = lm.minimize(p, ec);
                QL.require(ret != EndCriteria.Type.None && ret != EndCriteria.Type.Unknown
                        && ret != EndCriteria.Type.MaxIterations, "optimizer returns error (" + ret + ")");

                final Array solution = p.currentValue();

                double maturity = Math.abs(solution.get(1));
                int years = (int) maturity;
                maturity -= years;
                maturity *= 12.0;
                int months = (int) (maturity + 0.5);
                if ( years == 0 && months == 0 ) {
                    months = 1;
                }

                final Period matPeriod = new Period(years, TimeUnit.Years).add(new Period(months, TimeUnit.Months));

                final SmileSection sec = swaptionVolatility.smileSection(expiry, matPeriod, true);
                final double shift = sec.shift();

                // Floor strike at 0.1bp - shift
                double strike = solution.get(2);
                strike = Math.max(strike, 0.00001 - shift);
                // Floor nominal at 0.01bp
                double nom = Math.max(solution.get(0), 0.000001);

                final double vol = sec.volatility(strike);

                // Use forwarding term structure (no exogenous discount in Java SwapIndex)
                final Handle< YieldTermStructure > discTs = standardSwapBase.iborIndex().termStructure();

                helper = new SwaptionHelper(expiry, matPeriod, new Handle< Quote >(new SimpleQuote(vol)),
                        standardSwapBase.iborIndex(), standardSwapBase.fixedLegTenor(), standardSwapBase.dayCounter(),
                        standardSwapBase.iborIndex().dayCounter(), discTs,
                        BlackCalibrationHelper.CalibrationErrorType.RelativePriceError, strike, nom,
                        swaptionVolatility.volatilityType(), shift);
                break;
            }

            default:
                throw new IllegalArgumentException("Calibration basket type not known (" + basketType + ")");
            }

            result.add(helper);
        }

        return result;
    }

    // ── calibrationBasket() ───────────────────────────────────────────────────

    /** Calibration basket type. Mirrors C++ {@code CalibrationBasketType} enum. */
    public enum CalibrationBasketType {
        /**
         * ATM swaption at each exercise date. Strike = ATM from the smile section; maturity = time from exercise to the
         * underlying's last date.
         */
        Naive,
        /**
         * Swaption whose (nominal, maturity, strike) match the NPV, delta, and gamma of the exotic underlying at each
         * exercise date via LM optimization.
         */
        MaturityStrikeByDeltaGamma
    }

    // ── Inner class: MatchHelper ───────────────────────────────────────────────

    /**
     * Cost function for the LM optimizer in the MaturityStrikeByDeltaGamma basket.
     *
     * <p>Given a parameter vector {@code v = [nominal, maturity, fixedRate]}, builds
     * a vanilla swap on the standard index with those parameters and returns the residual vector
     * {@code [(npv-npv_target)/delta_, (delta-delta_target)/delta_, (gamma-gamma_target)/gamma_]}.
     *
     * <p>Mirrors C++ {@code BasketGeneratingEngine::MatchHelper}.
     */
    private static final class MatchHelper extends CostFunction {

        private final VanillaSwap.Type type_;
        private final double npv_, delta_, gamma_, h_;
        private final double maxMaturity_;
        private final Gaussian1dModel mdl_;
        private final SwapIndex indexBase_;
        private final Date expiry_;

        MatchHelper(final VanillaSwap.Type type, final double npv, final double delta, final double gamma,
                final Gaussian1dModel model, final SwapIndex indexBase, final Date expiry, final double maxMaturity,
                final double h) {
            this.type_ = type;
            this.npv_ = npv;
            this.delta_ = delta;
            this.gamma_ = gamma;
            this.mdl_ = model;
            this.indexBase_ = indexBase;
            this.expiry_ = expiry;
            this.maxMaturity_ = maxMaturity;
            this.h_ = h;
        }

        /**
         * NPV of a vanilla swap (with given fixed rate and nominal) at state {@code y}. Mirrors C++
         * {@code MatchHelper::NPV}.
         */
        private double swapNpv(final VanillaSwap swap, final double fixedRate, final double nominal, final double y,
                final int signType) {
            double swapNpvResult = 0.0;

            // Fixed leg: subtract fixed cashflows
            final Leg fixed = swap.fixedLeg();
            for ( final CashFlow cf : fixed ) {
                if ( cf instanceof FixedRateCoupon ) {
                    final FixedRateCoupon c = (FixedRateCoupon) cf;
                    swapNpvResult -= fixedRate * c.accrualPeriod() * nominal * mdl_.zerobond(c.date(), expiry_, y,
                            indexBase_.iborIndex().termStructure());
                }
            }

            // Floating leg: add floating cashflows
            final Leg flt = swap.floatingLeg();
            for ( final CashFlow cf : flt ) {
                if ( cf instanceof IborCoupon ) {
                    final IborCoupon c = (IborCoupon) cf;
                    final InterestRateIndex idx = c.index();
                    if ( !(idx instanceof IborIndex) )
                        continue; // safety guard
                    swapNpvResult +=
                            mdl_.forwardRate(c.fixingDate(), expiry_, y, (IborIndex) idx) * c.accrualPeriod() * nominal
                                    * mdl_.zerobond(c.date(), expiry_, y, indexBase_.iborIndex().termStructure());
                }
            }

            return (double) signType * swapNpvResult;
        }

        @Override
        public double value(final Array v) {
            final Array vals = values(v);
            double res = 0.0;
            for ( int i = 0; i < vals.size(); i++ ) {
                res += vals.get(i) * vals.get(i);
            }
            return Math.sqrt(res / vals.size());
        }

        @Override
        public Array values(final Array v) {
            // Transformations mirroring C++
            int type = (type_ == VanillaSwap.Type.Payer) ? 1 : -1;
            double nominal = Math.abs(v.get(0));
            if ( v.get(0) < 0.0 )
                type = -type;

            double maturity = Math.min(Math.abs(v.get(1)), maxMaturity_);
            final double fixedRate = v.get(2);

            int years = (int) maturity;
            maturity -= years;
            maturity *= 12.0;
            int months = (int) maturity;
            double alpha = 1.0 - (maturity - months);
            if ( years == 0 && months == 0 ) {
                months = 1;
                alpha = 1.0;
            }

            // Lower period = years*Years + months*Months
            // Upper period = lower + 1 Month
            final Period lowerPeriod = new Period(years, TimeUnit.Years).add(new Period(months, TimeUnit.Months));
            final Period upperPeriod = lowerPeriod.add(new Period(1, TimeUnit.Months));

            final SwapIndex tmpIdxLower = indexBase_.clone(lowerPeriod);
            final SwapIndex tmpIdxUpper = indexBase_.clone(upperPeriod);
            final VanillaSwap swapLower = tmpIdxLower.underlyingSwap(expiry_);
            final VanillaSwap swapUpper = tmpIdxUpper.underlyingSwap(expiry_);

            // Compute NPV, delta, gamma at y = -h, 0, +h
            final double npvm =
                    alpha * swapNpv(swapLower, fixedRate, nominal, -h_, type) + (1.0 - alpha) * swapNpv(swapUpper,
                            fixedRate, nominal, -h_, type);
            final double npv =
                    alpha * swapNpv(swapLower, fixedRate, nominal, 0.0, type) + (1.0 - alpha) * swapNpv(swapUpper,
                            fixedRate, nominal, 0.0, type);
            final double npvu =
                    alpha * swapNpv(swapLower, fixedRate, nominal, h_, type) + (1.0 - alpha) * swapNpv(swapUpper,
                            fixedRate, nominal, h_, type);

            final double delta = (npvu - npvm) / (2.0 * h_);
            final double gamma = (npvu - 2.0 * npv + npvm) / (h_ * h_);

            // Return residuals
            final Array res = new Array(3);
            res.set(0, (npv - npv_) / delta_);
            res.set(1, (delta - delta_) / delta_);
            res.set(2, (gamma - gamma_) / gamma_);
            return res;
        }
    }
}
