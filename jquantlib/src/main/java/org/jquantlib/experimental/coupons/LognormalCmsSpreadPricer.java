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
 Copyright (C) 2014, 2015, 2018 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.experimental.coupons;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.CmsCoupon;
import org.jquantlib.cashflow.CmsCouponPricer;
import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.integrals.GaussHermiteIntegration;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * CMS-spread coupon pricer using the bivariate-(shifted-)lognormal model in Brigo &amp; Mercurio, <i>Interest Rate
 * Models — Theory and Practice</i>, 2nd ed., Springer 2006, chapter 13.6.2, with extensions for normal dynamics as in
 * <a href="http://ssrn.com/abstract=2686998">Caspers (2015)</a>.
 * <p>
 * The swap-rate adjustments are computed using the underlying CMS coupon-pricer's volatility structure; the bivariate
 * spread model can either inherit the underlying volatility type (default) or use an explicit type + shifts. Pricing of
 * caplets/floorlets is done by Gauss-Hermite quadrature over the conditional Black/Bachelier formula (default 16
 * nodes).
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/experimental/coupons/lognormalcmsspreadpricer.hpp/cpp}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *   <li>The C++ {@code SwaptionVolatilityCube} narrowing branch is absent —
 *       the JQuantLib port currently has only the flat
 *       {@link org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility}
 *       surface available, so we use the
 *       {@link SwaptionVolatilityStructure#volatility(Date, org.jquantlib.time.Period, double)}
 *       overload for both shifted-lognormal and normal cases. Adding the cube
 *       overload is a strict superset and can be wired in once
 *       {@code SwaptionVolatilityCube} is ported.
 *   <li>The C++ fallback to
 *       {@code index_->swapIndex1()->exogenousDiscount() ? discountingTermStructure() : forwardingTermStructure()}
 *       is simplified to
 *       {@link org.jquantlib.indexes.SwapIndex#termStructure()}, because the
 *       Java {@link org.jquantlib.indexes.SwapIndex} does not yet expose an
 *       {@code exogenousDiscount} flag (deferred to a later phase). The two
 *       agree whenever the swap index has no exogenous discount curve, which
 *       is the common case used by all in-tree tests.
 * </ul>
 *
 * @author Peter Caspers (C++ original)
 */
public class LognormalCmsSpreadPricer extends CmsSpreadCouponPricer {

    private final CmsCouponPricer cmsPricer_;
    private final CumulativeNormalDistribution cnd_;
    private final GaussHermiteIntegration integrator_;
    private final boolean inheritedVolatilityType_;
    private final VolatilityType volType_;
    private Handle< YieldTermStructure > couponDiscountCurve_;
    private CmsSpreadCoupon coupon_;
    private Date today_;
    private Date fixingDate_;
    private Date paymentDate_;
    private double fixingTime_;
    private double gearing_;
    private double spread_;
    private double spreadLegValue_;
    private double discount_;
    private SwapSpreadIndex index_;
    private double swapRate1_;
    private double swapRate2_;
    private double gearing1_;
    private double gearing2_;
    private double adjustedRate1_;
    private double adjustedRate2_;
    private double vol1_;
    private double vol2_;
    private double mu1_;
    private double mu2_;
    private double rho_;
    private double shift1_;
    private double shift2_;

    // mutable optionletPrice scratchpad
    private double phi_;
    private double a_;
    private double b_;
    private double s1_;
    private double s2_;
    private double m1_;
    private double m2_;
    private double v1_;
    private double v2_;
    private double k_;
    private double alpha_;
    private double psi_;
    private Option.Type optionType_;

    private CmsCoupon c1_;
    private CmsCoupon c2_;

    //
    // public constructors
    //

    /** Convenience: 16 integration points, inherited volatility type. */
    public LognormalCmsSpreadPricer(final CmsCouponPricer cmsPricer, final Handle< ? extends Quote > correlation,
            final Handle< YieldTermStructure > couponDiscountCurve) {
        this(cmsPricer, correlation, couponDiscountCurve, 16, null, Constants.NULL_REAL, Constants.NULL_REAL);
    }

    public LognormalCmsSpreadPricer(final CmsCouponPricer cmsPricer, final Handle< ? extends Quote > correlation,
            final Handle< YieldTermStructure > couponDiscountCurve, final int integrationPoints) {
        this(cmsPricer, correlation, couponDiscountCurve, integrationPoints, null, Constants.NULL_REAL,
                Constants.NULL_REAL);
    }

    /**
     * Full constructor. Mirrors C++ {@code LognormalCmsSpreadPricer} ctor.
     *
     * @param cmsPricer           underlying CMS coupon pricer (provides swaption vol surface + convexity adjustments)
     * @param correlation         correlation between the two swap rates
     * @param couponDiscountCurve optional discount curve for the coupon's payment date; if empty, falls back to
     *                            {@code index.swapIndex1().termStructure()}
     * @param integrationPoints   Gauss-Hermite quadrature order (>= 4)
     * @param volatilityType      optional override for the spread volatility type; {@code null} = inherit from the
     *                            underlying surface
     * @param shift1              first-rate shift (only used if {@code volatilityType != null} and
     *                            non-{@link Constants#NULL_REAL})
     * @param shift2              second-rate shift (same)
     */
    public LognormalCmsSpreadPricer(final CmsCouponPricer cmsPricer, final Handle< ? extends Quote > correlation,
            final Handle< YieldTermStructure > couponDiscountCurve, final int integrationPoints,
            final VolatilityType volatilityType, final double shift1, final double shift2) {
        super(correlation);
        this.cmsPricer_ = cmsPricer;
        this.couponDiscountCurve_ = (couponDiscountCurve != null)
                ? couponDiscountCurve
                : new Handle< YieldTermStructure >();

        if ( correlation != null && !correlation.empty() ) {
            correlation.addObserver(this);
        }
        if ( this.couponDiscountCurve_ != null && !this.couponDiscountCurve_.empty() ) {
            this.couponDiscountCurve_.addObserver(this);
        }
        if ( cmsPricer_ != null ) {
            cmsPricer_.addObserver(this);
        }

        QL.require(integrationPoints >= 4, "at least 4 integration points should be used (" + integrationPoints + ")");
        this.integrator_ = new GaussHermiteIntegration(integrationPoints);

        this.cnd_ = new CumulativeNormalDistribution(0.0, 1.0);

        if ( volatilityType == null ) {
            QL.require(shift1 == Constants.NULL_REAL && shift2 == Constants.NULL_REAL,
                    "if volatility type is inherited, no shifts should be specified");
            this.inheritedVolatilityType_ = true;
            this.volType_ = cmsPricer.swaptionVolatility().currentLink().volatilityType();
        } else {
            this.shift1_ = (shift1 == Constants.NULL_REAL) ? 0.0 : shift1;
            this.shift2_ = (shift2 == Constants.NULL_REAL) ? 0.0 : shift2;
            this.inheritedVolatilityType_ = false;
            this.volType_ = volatilityType;
        }
    }

    //
    // overrides FloatingRateCouponPricer
    //

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        QL.require(coupon instanceof CmsSpreadCoupon, "CMS spread coupon needed");
        coupon_ = (CmsSpreadCoupon) coupon;
        index_ = coupon_.swapSpreadIndex();
        gearing_ = coupon_.gearing();
        spread_ = coupon_.spread();

        fixingDate_ = coupon_.fixingDate();
        paymentDate_ = coupon_.date();

        today_ = new Settings().evaluationDate();

        // C++ falls back to swapIndex1.exogenousDiscount() ? discountingTS : forwardingTS.
        // The Java port does not yet expose those accessors on SwapIndex, so we
        // use SwapIndex.termStructure() (which delegates to iborIndex.termStructure()).
        // This matches the C++ "forwardingTS" branch — i.e. the non-exogenous case.
        if ( couponDiscountCurve_ == null || couponDiscountCurve_.empty() ) {
            couponDiscountCurve_ = index_.swapIndex1().termStructure();
        }

        discount_ = paymentDate_.gt(couponDiscountCurve_.currentLink().referenceDate())
                ? couponDiscountCurve_.currentLink().discount(paymentDate_)
                : 1.0;

        spreadLegValue_ = spread_ * coupon_.accrualPeriod() * discount_;

        gearing1_ = index_.gearing1();
        gearing2_ = index_.gearing2();

        QL.require(gearing1_ > 0.0 && gearing2_ < 0.0,
                "gearing1 (" + gearing1_ + ") should be positive while gearing2 (" + gearing2_
                        + ") should be negative");

        c1_ = new CmsCoupon(coupon_.date(), coupon_.nominal(), coupon_.accrualStartDate(), coupon_.accrualEndDate(),
                coupon_.fixingDays(), index_.swapIndex1(), 1.0, 0.0, coupon_.referencePeriodStart(),
                coupon_.referencePeriodEnd(), coupon_.dayCounter(), coupon_.isInArrears());

        c2_ = new CmsCoupon(coupon_.date(), coupon_.nominal(), coupon_.accrualStartDate(), coupon_.accrualEndDate(),
                coupon_.fixingDays(), index_.swapIndex2(), 1.0, 0.0, coupon_.referencePeriodStart(),
                coupon_.referencePeriodEnd(), coupon_.dayCounter(), coupon_.isInArrears());

        c1_.setPricer(cmsPricer_);
        c2_.setPricer(cmsPricer_);

        if ( fixingDate_.gt(today_) ) {

            final SwaptionVolatilityStructure swvol = cmsPricer_.swaptionVolatility().currentLink();
            fixingTime_ = swvol.timeFromReference(fixingDate_);

            swapRate1_ = c1_.indexFixing();
            swapRate2_ = c2_.indexFixing();

            adjustedRate1_ = c1_.adjustedFixing();
            adjustedRate2_ = c2_.adjustedFixing();

            if ( inheritedVolatilityType_ && volType_ == VolatilityType.ShiftedLognormal ) {
                shift1_ = swvol.shift();
                shift2_ = swvol.shift();
            }

            // Java port note: the JQuantLib SwaptionVolatilityCube class is not
            // ported yet, so we always use the SwaptionVolatilityStructure
            // volatility lookup (which in the inherited case naturally yields
            // the right type for the one available concrete implementation,
            // ConstantSwaptionVolatility). When a cube is added, an extra
            // SmileSection-based branch can be inserted here for the non-
            // inherited case.
            QL.require(inheritedVolatilityType_,
                    "if only an atm surface is given, the volatility type must be inherited");
            vol1_ = swvol.volatility(fixingDate_, index_.swapIndex1().tenor(), swapRate1_, false);
            vol2_ = swvol.volatility(fixingDate_, index_.swapIndex2().tenor(), swapRate2_, false);

            if ( volType_ == VolatilityType.ShiftedLognormal ) {
                mu1_ = 1.0 / fixingTime_ * Math.log((adjustedRate1_ + shift1_) / (swapRate1_ + shift1_));
                mu2_ = 1.0 / fixingTime_ * Math.log((adjustedRate2_ + shift2_) / (swapRate2_ + shift2_));
            }
            // for normal-vol case, drifts are unused; integrand_normal works
            // directly with adjusted rates.

            // avoid division by zero in integrand
            rho_ = Math.max(Math.min(correlation().currentLink().value(), 0.9999), -0.9999);
        } else {
            // fixing is in the past or today
            adjustedRate1_ = c1_.indexFixing();
            adjustedRate2_ = c2_.indexFixing();
        }
    }

    @Override
    public double swapletPrice() {
        return gearing_ * coupon_.accrualPeriod() * discount_ * (gearing1_ * adjustedRate1_
                + gearing2_ * adjustedRate2_) + spreadLegValue_;
    }

    @Override
    public double swapletRate() {
        return swapletPrice() / (coupon_.accrualPeriod() * discount_);
    }

    @Override
    public double capletPrice(final double effectiveCap) {
        // caplet is equivalent to call option on fixing
        if ( fixingDate_.le(today_) ) {
            // the fixing is determined
            final double Rs = Math.max(coupon_.index().fixing(fixingDate_) - effectiveCap, 0.0);
            return gearing_ * Rs * coupon_.accrualPeriod() * discount_;
        }
        return gearing_ * optionletPrice(Option.Type.Call, effectiveCap);
    }

    @Override
    public double capletRate(final double effectiveCap) {
        return capletPrice(effectiveCap) / (coupon_.accrualPeriod() * discount_);
    }

    @Override
    public double floorletPrice(final double effectiveFloor) {
        // floorlet is equivalent to put option on fixing
        if ( fixingDate_.le(today_) ) {
            final double Rs = Math.max(effectiveFloor - coupon_.index().fixing(fixingDate_), 0.0);
            return gearing_ * Rs * coupon_.accrualPeriod() * discount_;
        }
        return gearing_ * optionletPrice(Option.Type.Put, effectiveFloor);
    }

    @Override
    public double floorletRate(final double effectiveFloor) {
        return floorletPrice(effectiveFloor) / (coupon_.accrualPeriod() * discount_);
    }

    //
    // private helpers
    //

    /**
     * Brigo & Mercurio integrand (chapter 13.16.2) with {@code x = v / sqrt(2)}.
     * <p>
     * Mirrors C++ {@code LognormalCmsSpreadPricer::integrand}.
     */
    private double integrand(final double x) {
        final double v = Constants.M_SQRT2 * x;
        final double h =
                k_ - b_ * s2_ * Math.exp((m2_ - 0.5 * v2_ * v2_) * fixingTime_ + v2_ * Math.sqrt(fixingTime_) * v);
        final double phi1 = cnd_.op(
                phi_ * (Math.log(a_ * s1_ / h) + (m1_ + (0.5 - rho_ * rho_) * v1_ * v1_) * fixingTime_
                        + rho_ * v1_ * Math.sqrt(fixingTime_) * v) / (v1_ * Math.sqrt(
                        fixingTime_ * (1.0 - rho_ * rho_))));
        final double phi2 = cnd_.op(
                phi_ * (Math.log(a_ * s1_ / h) + (m1_ - 0.5 * v1_ * v1_) * fixingTime_ + rho_ * v1_ * Math.sqrt(
                        fixingTime_) * v) / (v1_ * Math.sqrt(fixingTime_ * (1.0 - rho_ * rho_))));
        final double f = a_ * phi_ * s1_ * Math.exp(
                m1_ * fixingTime_ - 0.5 * rho_ * rho_ * v1_ * v1_ * fixingTime_ + rho_ * v1_ * Math.sqrt(fixingTime_)
                        * v) * phi1 - phi_ * h * phi2;
        return Math.exp(-x * x) * f;
    }

    /**
     * Compute optionlet price (call = caplet, put = floorlet). Only invoked for future fixings. Mirrors C++
     * {@code LognormalCmsSpreadPricer::optionletPrice}.
     */
    private double optionletPrice(final Option.Type optionType, final double strike) {
        optionType_ = optionType;
        phi_ = (optionType == Option.Type.Call) ? 1.0 : -1.0;
        double res = 0.0;
        if ( volType_ == VolatilityType.ShiftedLognormal ) {
            // (shifted) lognormal volatility
            if ( strike >= 0.0 ) {
                a_ = gearing1_;
                b_ = gearing2_;
                s1_ = swapRate1_ + shift1_;
                s2_ = swapRate2_ + shift2_;
                m1_ = mu1_;
                m2_ = mu2_;
                v1_ = vol1_;
                v2_ = vol2_;
                k_ = strike + gearing1_ * shift1_ + gearing2_ * shift2_;
            } else {
                a_ = -gearing2_;
                b_ = -gearing1_;
                s1_ = swapRate2_ + shift1_;
                s2_ = swapRate1_ + shift2_;
                m1_ = mu2_;
                m2_ = mu1_;
                v1_ = vol2_;
                v2_ = vol1_;
                k_ = -strike - gearing1_ * shift1_ - gearing2_ * shift2_;
                res += phi_ * (gearing1_ * adjustedRate1_ + gearing2_ * adjustedRate2_ - strike);
            }
            res += 1.0 / Constants.M_SQRTPI * integrator_.op(this::integrand);
        } else {
            // normal volatility — closed-form Bachelier
            final double forward = gearing1_ * adjustedRate1_ + gearing2_ * adjustedRate2_;
            final double stddev = Math.sqrt(
                    fixingTime_ * (gearing1_ * gearing1_ * vol1_ * vol1_ + gearing2_ * gearing2_ * vol2_ * vol2_
                            + 2.0 * gearing1_ * gearing2_ * rho_ * vol1_ * vol2_));
            res = BlackFormula.bachelierBlackFormula(optionType_, strike, forward, stddev, 1.0);
        }
        return res * discount_ * coupon_.accrualPeriod();
    }

    @SuppressWarnings( "unused" )
    private double integrandNormal(final double x) {
        // Currently unused — see Caspers (2015), eq. 3.20. Kept for API parity
        // with C++ in case a follow-up refactor wants the integrand-form
        // normal pricing instead of the closed-form Bachelier branch above.
        final double s = Constants.M_SQRT2 * x;
        final double beta =
                phi_ * (gearing1_ * adjustedRate1_ + gearing2_ * adjustedRate2_ - k_ + Math.sqrt(fixingTime_) * (
                        rho_ * gearing1_ * vol1_ + gearing2_ * vol2_) * s);
        final double f = Closeness.isCloseEnough(alpha_, 0.0)
                ? Math.max(beta, 0.0)
                : psi_ * alpha_ / (Constants.M_SQRTPI * Constants.M_SQRT2) * Math.exp(
                        -beta * beta / (2.0 * alpha_ * alpha_)) + beta * (1.0 - cnd_.op(-psi_ * beta / alpha_));
        return Math.exp(-x * x) * f;
    }
}
