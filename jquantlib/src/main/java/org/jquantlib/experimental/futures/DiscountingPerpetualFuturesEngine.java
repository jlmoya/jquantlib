/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2025 Hiroto Ogawa

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.futures;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.TrapezoidIntegral;
import org.jquantlib.math.interpolations.BackwardFlatInterpolation;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;

import java.util.ArrayList;
import java.util.List;

/**
 * Discounting engine for {@link PerpetualFutures}.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code DiscountingPerpetualFuturesEngine} in
 * {@code ql/pricingengines/futures/discountingperpetualfuturesengine.{hpp,cpp}}.
 * <p>
 * Supports {@link PerpetualFutures.PayoffType#Linear} and {@link PerpetualFutures.PayoffType#Inverse} payoff types.
 * Computes a present value by discounting the perpetual cash-flow stream against the supplied domestic and foreign
 * yield curves, using a (possibly extrapolated) interpolation of the funding-rate and interest-rate-differential
 * histories.
 * <p>
 * For details, see Ackerer, Hugonnier, Jermann (2024), "Perpetual Futures Pricing".
 *
 * @author Jose Moya
 */
public class DiscountingPerpetualFuturesEngine extends PerpetualFutures.EngineImpl {

    //
    // public enums
    //

    private final Handle< YieldTermStructure > domesticDiscountCurve;

    //
    // private fields
    //
    private final Handle< YieldTermStructure > foreignDiscountCurve;
    private final Handle< ? extends Quote > assetSpot;
    private final double[] fundingTimes;
    private final double[] fundingRates;
    private final double[] interestRateDiffs;
    private final InterpolationType fundingInterpType;
    private final double maxT;
    public DiscountingPerpetualFuturesEngine(final Handle< YieldTermStructure > domesticDiscountCurve,
            final Handle< YieldTermStructure > foreignDiscountCurve, final Handle< ? extends Quote > assetSpot,
            final double[] fundingTimes, final double[] fundingRates, final double[] interestRateDiffs) {
        this(domesticDiscountCurve, foreignDiscountCurve, assetSpot, fundingTimes, fundingRates, interestRateDiffs,
                InterpolationType.PiecewiseConstant, 60.0);
    }

    //
    // public constructors
    //

    public DiscountingPerpetualFuturesEngine(final Handle< YieldTermStructure > domesticDiscountCurve,
            final Handle< YieldTermStructure > foreignDiscountCurve, final Handle< ? extends Quote > assetSpot,
            final double[] fundingTimes, final double[] fundingRates, final double[] interestRateDiffs,
            final InterpolationType fundingInterpType, final double maxT) {
        super();
        this.domesticDiscountCurve = domesticDiscountCurve;
        this.foreignDiscountCurve = foreignDiscountCurve;
        this.assetSpot = assetSpot;
        this.fundingTimes = fundingTimes.clone();
        this.fundingRates = fundingRates.clone();
        this.interestRateDiffs = interestRateDiffs.clone();
        this.fundingInterpType = fundingInterpType;
        this.maxT = maxT;
        QL.require(this.fundingTimes.length > 0, "fundingTimes is empty");
        QL.require(this.fundingRates.length > 0, "fundingRates is empty");
        QL.require(this.interestRateDiffs.length > 0, "interestRateDiffs is empty");
        QL.require(this.fundingTimes.length == this.fundingRates.length,
                "fundingTimes and fundingRates must have the same size.");
        QL.require(this.fundingTimes.length == this.interestRateDiffs.length,
                "fundingTimes and interestRateDiffs must have the same size.");
        this.domesticDiscountCurve.addObserver(this);
        this.foreignDiscountCurve.addObserver(this);
        this.assetSpot.addObserver(this);
    }

    private static double productIRDiff(final double[] fundingRateGrid, final int upTo) {
        double ret = 1.0;
        for ( int j = 0; j <= upTo; ++j ) {
            ret /= 1.0 + fundingRateGrid[j];
        }
        return ret;
    }

    //
    // public read-only accessors
    //

    public Handle< YieldTermStructure > domesticDiscountCurve() {
        return domesticDiscountCurve;
    }

    public Handle< YieldTermStructure > foreignDiscountCurve() {
        return foreignDiscountCurve;
    }

    public Handle< ? extends Quote > assetSpot() {
        return assetSpot;
    }

    public double[] fundingTimes() {
        return fundingTimes.clone();
    }

    public double[] fundingRates() {
        return fundingRates.clone();
    }

    public double[] interestRateDiffs() {
        return interestRateDiffs.clone();
    }

    //
    // implements PricingEngine
    //

    @Override
    public void calculate() {
        QL.require(!domesticDiscountCurve.empty(), "domestic discounting term structure handle is empty");
        QL.require(!foreignDiscountCurve.empty(), "foreign discounting term structure handle is empty");
        QL.require(!assetSpot.empty(), "asset spot handle is empty");

        results_.value = 0.0;
        results_.errorEstimate = Double.NaN;

        QL.require(arguments_.payoffType == PerpetualFutures.PayoffType.Linear
                        || arguments_.payoffType == PerpetualFutures.PayoffType.Inverse,
                "Only Linear and Inverse payoffs are supported in DiscountingPerpetualFuturesEngine");

        // Linear <--> Inverse symmetry:
        //  - swap domestic and foreign curves
        //  - future price: f <--> 1/f
        final YieldTermStructure effDomCurve = arguments_.payoffType == PerpetualFutures.PayoffType.Linear
                ? domesticDiscountCurve.currentLink()
                : foreignDiscountCurve.currentLink();
        final YieldTermStructure effForCurve = arguments_.payoffType == PerpetualFutures.PayoffType.Linear
                ? foreignDiscountCurve.currentLink()
                : domesticDiscountCurve.currentLink();

        final Period fundingFreq = arguments_.fundingFrequency;
        final Date refDate = new Settings().evaluationDate();

        final Interpolation fundingRateInterp = selectInterpolation(fundingTimes, fundingRates);
        fundingRateInterp.enableExtrapolation();
        QL.require(fundingRateInterp.op(fundingRateInterp.xMax()) > 0,
                "fundingRate at max time is negative. Because the last funding rate is "
                        + "flatly extrapolated, integral diverges.");
        final Interpolation interestRateDiffInterp = selectInterpolation(fundingTimes, interestRateDiffs);
        interestRateDiffInterp.enableExtrapolation();

        final double factor;
        if ( fundingFreq.length() > 0 ) {
            // ---------- discrete-time case ----------
            final List< Double > timeGrid = new ArrayList<>();
            double tGrid = 0.0;
            while ( tGrid < maxT ) {
                timeGrid.add(tGrid);
                final double tUnit;
                switch ( fundingFreq.units() ) {
                case Years:
                    tGrid += fundingFreq.length();
                    break;
                case Months:
                    tUnit = 1.0 / 12.0;
                    tGrid += tUnit * fundingFreq.length();
                    break;
                case Weeks:
                    tUnit = 7.0 / 365.0;
                    tGrid += tUnit * fundingFreq.length();
                    break;
                case Days:
                    tUnit = 1.0 / 365.0;
                    tGrid += tUnit * fundingFreq.length();
                    break;
                default:
                    QL.error("Unknown or unsupported unit in fundingFrequency: " + fundingFreq.units());
                    return;
                }
            }
            final int n = timeGrid.size();
            final double[] fundingRateGrid = new double[n];
            final double[] interestRateDiffGrid = new double[n];
            for ( int i = 0; i < n; ++i ) {
                final double time = timeGrid.get(i);
                fundingRateGrid[i] = fundingRateInterp.op(time);
                interestRateDiffGrid[i] = interestRateDiffInterp.op(time);
            }

            if ( arguments_.fundingType == PerpetualFutures.FundingType.FundingWithCurrentSpot ) {
                double ratio = 1.0;
                int i;
                for ( i = 0; i < n - 1; ++i ) {
                    final double time = timeGrid.get(i);
                    final double nextTime = timeGrid.get(i + 1);
                    ratio = effForCurve.discount(nextTime) / effForCurve.discount(time) / effDomCurve.discount(nextTime)
                            * effDomCurve.discount(time);
                    fundingRateGrid[i] *= ratio;
                    interestRateDiffGrid[i] *= ratio;
                }
                // for i = n - 1 (mirrors C++ loop post-increment leaving i at n-1)
                fundingRateGrid[i] *= ratio;
                interestRateDiffGrid[i] *= ratio;
            }

            double sum = 0.0;
            for ( int i = 0; i < n - 1; ++i ) {
                final double time = timeGrid.get(i);
                sum += productIRDiff(fundingRateGrid, i) * (fundingRateGrid[i] - interestRateDiffGrid[i])
                        * effForCurve.discount(time) / effDomCurve.discount(time);
            }
            final int iLast = n - 1;
            final double timeLast = timeGrid.get(iLast);
            final double productIRDiffLast = productIRDiff(fundingRateGrid, iLast);
            final double fundingRateGridLast = fundingRateGrid[iLast];
            final double interestRateDiffGridLast = interestRateDiffGrid[iLast];

            final double domRateLast = effDomCurve.forwardRate(timeLast, timeLast, Compounding.Continuous,
                    Frequency.NoFrequency).rate();
            final double forRateLast = effForCurve.forwardRate(timeLast, timeLast, Compounding.Continuous,
                    Frequency.NoFrequency).rate();

            // for t > maxT, assume flat extrapolation on all rates
            final double lastTerm =
                    productIRDiffLast * (fundingRateGridLast - interestRateDiffGridLast) * effForCurve.discount(
                            timeLast) / effDomCurve.discount(timeLast);
            final double timeStep = (timeGrid.get(n - 1) - timeGrid.get(0)) / (n - 1);
            final double ratio = 1.0 / (1.0 + fundingRateGridLast) * Math.exp(-timeStep * (forRateLast - domRateLast));
            sum += lastTerm / (1.0 - ratio);
            factor = sum;

        } else {
            // ---------- continuous-time case ----------
            final TrapezoidIntegral< TrapezoidIntegral.Default > integrator = new TrapezoidIntegral<>(
                    TrapezoidIntegral.Default.class, 1.0e-6, 30);
            final double fundingRateXMax = fundingRateInterp.xMax();

            final Ops.DoubleOp expIRDiff = new Ops.DoubleOp() {
                @Override
                public double op(final double s) {
                    if ( s < fundingRateXMax ) {
                        return Math.exp(-integrator.op(fundingRateInterp, 0.0, s));
                    } else {
                        return Math.exp(-integrator.op(fundingRateInterp, 0.0, fundingRateXMax)
                                - fundingRateInterp.op(fundingRateXMax) * (s - fundingRateXMax));
                    }
                }
            };

            final Ops.DoubleOp timeIntegrand = new Ops.DoubleOp() {
                @Override
                public double op(final double s) {
                    return (fundingRateInterp.op(s) - interestRateDiffInterp.op(s)) * expIRDiff.op(s)
                            * effForCurve.discount(s) / effDomCurve.discount(s);
                }
            };
            double f = integrator.op(timeIntegrand, 0.0, maxT);

            // for t > maxT, assume flat extrapolation on all rates
            final double fundingRateLast = fundingRateInterp.op(maxT);
            final double interestRateDiffLast = interestRateDiffInterp.op(maxT);
            final double expIRDiff_last = expIRDiff.op(maxT);
            final double domRateLast = effDomCurve.forwardRate(maxT, maxT, Compounding.Continuous,
                    Frequency.NoFrequency).rate();
            final double forRateLast = effForCurve.forwardRate(maxT, maxT, Compounding.Continuous,
                    Frequency.NoFrequency).rate();
            final double ratio = fundingRateLast + forRateLast - domRateLast;
            f += (fundingRateLast - interestRateDiffLast) * expIRDiff_last * effForCurve.discount(maxT)
                    / effDomCurve.discount(maxT) / ratio;
            factor = f;
        }

        if ( arguments_.payoffType == PerpetualFutures.PayoffType.Linear ) {
            results_.value = assetSpot.currentLink().value() * factor;
        } else {
            results_.value = assetSpot.currentLink().value() / factor;
        }
    }

    //
    // private helpers
    //

    private Interpolation selectInterpolation(final double[] times, final double[] values) {
        final Array vx = new Array(times);
        final Array vy = new Array(values);
        switch ( fundingInterpType ) {
        case Linear:
            return new LinearInterpolation(vx, vy);
        case PiecewiseConstant:
            return new BackwardFlatInterpolation(vx, vy);
        case CubicSpline:
            return new CubicInterpolation(vx, vy, CubicInterpolation.DerivativeApprox.Spline, false,
                    CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                    CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        default:
            QL.error("Unknown interpolation type: " + fundingInterpType);
            return null;
        }
    }

    public enum InterpolationType {PiecewiseConstant, Linear, CubicSpline}

}
