/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Marco Bianchetti
 Copyright (C) 2006, 2007 Giorgio Facchinetti

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.termstructures.volatilities.swaption;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.CmsCouponPricer;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.MeanRevertingPricer;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.MakeCms;
import org.jquantlib.instruments.Swap;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.LazyObject;

/**
 * Set of CMS bid/ask quotes — Java port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/swaption/cmsmarket.{hpp,cpp}} — class {@code CmsMarket}.
 *
 * <p>Holds a grid of CMS bid/ask spread quotes indexed by (swap-length, swap-index) and,
 * given a swaption volatility structure and mean reversion, {@link #reprice} re-derives the
 * model-implied CMS spreads and forward/spot CMS-leg NPVs. The weighted-error accessors feed a
 * Levenberg-Marquardt calibration driven by {@code CmsMarketCalibration}.
 *
 * <p>Extends {@link LazyObject} as in C++ ({@code : public LazyObject}); the heavy work happens in
 * {@link #performCalculations()}.
 */
public class CmsMarket extends LazyObject {

    private final List< Period > swapLengths_;
    private final List< SwapIndex > swapIndexes_;
    private final IborIndex iborIndex_;
    private final List< List< Handle< Quote > > > bidAskSpreads_;
    private final List< CmsCouponPricer > pricers_;
    private final Handle< YieldTermStructure > discTS_;

    private final int nExercise_;
    private final int nSwapIndexes_;
    private final List< Period > swapTenors_;

    private final Matrix spotFloatLegNPV_;
    private final Matrix spotFloatLegBPS_;

    // market spreads
    private final Matrix mktBidSpreads_;
    private final Matrix mktAskSpreads_;
    private final Matrix mktSpreads_;
    // model (mid) spreads
    private final Matrix mdlSpreads_;
    // differences between market and model mid spreads
    private final Matrix errSpreads_;

    // market / model mid prices of spot starting Cms Leg and their difference
    private final Matrix mktSpotCmsLegNPV_;
    private final Matrix mdlSpotCmsLegNPV_;
    private final Matrix errSpotCmsLegNPV_;

    // market / model mid prices of forward starting Cms Leg and their difference
    private final Matrix mktFwdCmsLegNPV_;
    private final Matrix mdlFwdCmsLegNPV_;
    private final Matrix errFwdCmsLegNPV_;

    private final Swap[][] spotSwaps_;
    private final Swap[][] fwdSwaps_;

    //
    // public constructor
    //

    public CmsMarket(final List< Period > swapLengths, final List< SwapIndex > swapIndexes, final IborIndex iborIndex,
            final List< List< Handle< Quote > > > bidAskSpreads, final List< CmsCouponPricer > pricers,
            final Handle< YieldTermStructure > discountingTS) {

        this.swapLengths_ = swapLengths;
        this.swapIndexes_ = swapIndexes;
        this.iborIndex_ = iborIndex;
        this.bidAskSpreads_ = bidAskSpreads;
        this.pricers_ = pricers;
        this.discTS_ = discountingTS;

        this.nExercise_ = swapLengths_.size();
        this.nSwapIndexes_ = swapIndexes_.size();
        this.swapTenors_ = new ArrayList<>(nSwapIndexes_);
        for ( int j = 0; j < nSwapIndexes_; ++j ) {
            swapTenors_.add(null);
        }

        this.spotFloatLegNPV_ = new Matrix(nExercise_, nSwapIndexes_);
        this.spotFloatLegBPS_ = new Matrix(nExercise_, nSwapIndexes_);
        this.mktBidSpreads_ = new Matrix(nExercise_, nSwapIndexes_);
        this.mktAskSpreads_ = new Matrix(nExercise_, nSwapIndexes_);
        this.mktSpreads_ = new Matrix(nExercise_, nSwapIndexes_);
        this.mdlSpreads_ = new Matrix(nExercise_, nSwapIndexes_);
        this.errSpreads_ = new Matrix(nExercise_, nSwapIndexes_);
        this.mktSpotCmsLegNPV_ = new Matrix(nExercise_, nSwapIndexes_);
        this.mdlSpotCmsLegNPV_ = new Matrix(nExercise_, nSwapIndexes_);
        this.errSpotCmsLegNPV_ = new Matrix(nExercise_, nSwapIndexes_);
        this.mktFwdCmsLegNPV_ = new Matrix(nExercise_, nSwapIndexes_);
        this.mdlFwdCmsLegNPV_ = new Matrix(nExercise_, nSwapIndexes_);
        this.errFwdCmsLegNPV_ = new Matrix(nExercise_, nSwapIndexes_);
        this.spotSwaps_ = new Swap[nExercise_][nSwapIndexes_];
        this.fwdSwaps_ = new Swap[nExercise_][nSwapIndexes_];

        QL.require(2 * nSwapIndexes_ == bidAskSpreads.get(0).size(),
                "2*nSwapIndexes_ (" + (2 * nSwapIndexes_) + ") != bidAskSpreads columns() ("
                        + bidAskSpreads.get(0).size() + ")");
        QL.require(nExercise_ == bidAskSpreads.size(),
                "nExercise_ (" + nExercise_ + ") != bidAskSpreads rows() (" + bidAskSpreads.size() + ")");
        QL.require(nSwapIndexes_ == pricers.size(),
                "nSwapIndexes_ (" + nSwapIndexes_ + ") != pricers (" + pricers_.size() + ")");

        for ( int j = 0; j < nSwapIndexes_; ++j ) {
            swapTenors_.set(j, swapIndexes_.get(j).tenor());
            // pricers (C++ registerWith → Java addObserver(this))
            pricers_.get(j).addObserver(this);
            for ( int i = 0; i < nExercise_; ++i ) {
                // market Spread
                bidAskSpreads_.get(i).get(j * 2).addObserver(this);
                bidAskSpreads_.get(i).get(j * 2 + 1).addObserver(this);
            }
        }

        Period start = new Period(0, TimeUnit.Years);
        for ( int i = 0; i < nExercise_; ++i ) {
            if ( i > 0 ) {
                start = swapLengths_.get(i - 1);
            }
            for ( int j = 0; j < nSwapIndexes_; ++j ) {
                // never evaluate the spot swap, only its ibor floating leg
                spotSwaps_[i][j] = new MakeCms(swapLengths_.get(i), swapIndexes_.get(j), iborIndex_, 0.0,
                        new Period()).value();
                fwdSwaps_[i][j] = new MakeCms(swapLengths_.get(i).sub(start), swapIndexes_.get(j), iborIndex_, 0.0,
                        start).withCmsCouponPricer(pricers_.get(j)).withDiscountingTermStructure(discTS_).value();
            }
        }
        // probably useless
        performCalculations();
    }

    //
    // LazyObject interface
    //

    @Override
    protected void performCalculations() {
        for ( int j = 0; j < nSwapIndexes_; ++j ) {
            double mktPrevPart = 0.0;
            double mdlPrevPart = 0.0;
            for ( int i = 0; i < nExercise_; ++i ) {

                // **** market

                mktBidSpreads_.set(i, j, bidAskSpreads_.get(i).get(j * 2).currentLink().value());
                mktAskSpreads_.set(i, j, bidAskSpreads_.get(i).get(j * 2 + 1).currentLink().value());
                mktSpreads_.set(i, j, (mktBidSpreads_.get(i, j) + mktAskSpreads_.get(i, j)) / 2);

                final Leg spotFloatLeg = spotSwaps_[i][j].leg(1);
                spotFloatLegNPV_.set(i, j, CashFlows.npv(spotFloatLeg, discTS_.currentLink(), false,
                        discTS_.currentLink().referenceDate(), new Date()));
                spotFloatLegBPS_.set(i, j, CashFlows.getInstance().bps(spotFloatLeg, discTS_,
                        discTS_.currentLink().referenceDate(), new Date()));

                // imply the spot CMS leg NPV from the spot ibor floating leg NPV
                mktSpotCmsLegNPV_.set(i, j,
                        -(spotFloatLegNPV_.get(i, j) + spotFloatLegBPS_.get(i, j) * mktSpreads_.get(i, j) / 1e-4));
                // fwd CMS legs can be computed as differences between spot legs
                mktFwdCmsLegNPV_.set(i, j, mktSpotCmsLegNPV_.get(i, j) - mktPrevPart);
                mktPrevPart = mktSpotCmsLegNPV_.get(i, j);

                // **** model

                // calculate the forward swap (the time consuming part)
                mdlFwdCmsLegNPV_.set(i, j, fwdSwaps_[i][j].legNPV(0));
                errFwdCmsLegNPV_.set(i, j, mdlFwdCmsLegNPV_.get(i, j) - mktFwdCmsLegNPV_.get(i, j));

                // spot CMS legs can be computed as incremental sum of forward legs
                mdlSpotCmsLegNPV_.set(i, j, mdlPrevPart + mdlFwdCmsLegNPV_.get(i, j));
                mdlPrevPart = mdlSpotCmsLegNPV_.get(i, j);
                errSpotCmsLegNPV_.set(i, j, mdlSpotCmsLegNPV_.get(i, j) - mktSpotCmsLegNPV_.get(i, j));

                // equilibriums spread over ibor leg
                final double npv = spotFloatLegNPV_.get(i, j) + mdlSpotCmsLegNPV_.get(i, j);
                mdlSpreads_.set(i, j, -npv / spotFloatLegBPS_.get(i, j) * 1e-4);
                errSpreads_.set(i, j, mdlSpreads_.get(i, j) - mktSpreads_.get(i, j));
            }
        }
    }

    /** Called during calibration procedure. */
    public void reprice(final Handle< SwaptionVolatilityStructure > v, final double meanReversion) {
        final Handle< Quote > meanReversionQuote = new Handle< Quote >(new SimpleQuote(meanReversion));
        for ( int j = 0; j < nSwapIndexes_; ++j ) {
            // set new volatility structure and new mean reversion
            pricers_.get(j).setSwaptionVolatility(v);
            if ( meanReversion != Constants.NULL_REAL ) {
                QL.require(pricers_.get(j) instanceof MeanRevertingPricer,
                        "mean reverting pricer required at index " + j);
                ((MeanRevertingPricer) pricers_.get(j)).setMeanReversion(meanReversionQuote);
            }
        }
        performCalculations();
    }

    //
    // inspectors
    //

    public List< Period > swapTenors() {
        return swapTenors_;
    }

    public List< Period > swapLengths() {
        return swapLengths_;
    }

    public Matrix impliedCmsSpreads() {
        return mdlSpreads_;
    }

    public Matrix spreadErrors() {
        return errSpreads_;
    }

    //
    // cms market calibration methods (they haven't Lazy behaviour)
    //

    public double weightedSpreadError(final Matrix weights) {
        performCalculations();
        return weightedMean(errSpreads_, weights);
    }

    public double weightedSpotNpvError(final Matrix weights) {
        performCalculations();
        return weightedMean(errSpotCmsLegNPV_, weights);
    }

    public double weightedFwdNpvError(final Matrix weights) {
        performCalculations();
        return weightedMean(errFwdCmsLegNPV_, weights);
    }

    public Array weightedSpreadErrors(final Matrix weights) {
        performCalculations();
        return weightedMeans(errSpreads_, weights);
    }

    public Array weightedSpotNpvErrors(final Matrix weights) {
        performCalculations();
        return weightedMeans(errSpotCmsLegNPV_, weights);
    }

    public Array weightedFwdNpvErrors(final Matrix weights) {
        performCalculations();
        return weightedMeans(errFwdCmsLegNPV_, weights);
    }

    //
    // private weighted-mean helpers (pure arithmetic — deterministic)
    //

    private double weightedMean(final Matrix var, final Matrix w) {
        double mean = 0.0;
        for ( int i = 0; i < nExercise_; ++i ) {
            for ( int j = 0; j < nSwapIndexes_; ++j ) {
                mean += w.get(i, j) * var.get(i, j) * var.get(i, j);
            }
        }
        mean = Math.sqrt(mean / (nExercise_ * nSwapIndexes_));
        return mean;
    }

    private Array weightedMeans(final Matrix var, final Matrix w) {
        final Array weightedVars = new Array(nExercise_ * nSwapIndexes_);
        for ( int i = 0; i < nExercise_; ++i ) {
            for ( int j = 0; j < nSwapIndexes_; ++j ) {
                weightedVars.set(i * nSwapIndexes_ + j, Math.sqrt(w.get(i, j)) * var.get(i, j));
            }
        }
        return weightedVars;
    }

    /**
     * Browse the full CMS-market state into a {@code (nExercise*nSwapIndexes) x 14} matrix — mirrors C++
     * {@code CmsMarket::browse()}. Triggers {@link #calculate()} first.
     */
    public Matrix browse() {
        calculate();
        final Matrix result = new Matrix(nExercise_ * nSwapIndexes_, 14);
        for ( int j = 0; j < nSwapIndexes_; ++j ) {
            for ( int i = 0; i < nExercise_; ++i ) {
                final int row = j * nExercise_ + i;
                result.set(row, 0, swapTenors_.get(j).length());
                result.set(row, 1, swapLengths_.get(i).length());

                // Spreads
                result.set(row, 2, mktBidSpreads_.get(i, j) * 10000);
                result.set(row, 3, mktAskSpreads_.get(i, j) * 10000);
                result.set(row, 4, mktSpreads_.get(i, j) * 10000);
                result.set(row, 5, mdlSpreads_.get(i, j) * 10000);
                result.set(row, 6, errSpreads_.get(i, j) * 10000);
                if ( mdlSpreads_.get(i, j) > mktAskSpreads_.get(i, j) ) {
                    result.set(row, 7, (mdlSpreads_.get(i, j) - mktAskSpreads_.get(i, j)) * 10000);
                } else if ( mdlSpreads_.get(i, j) < mktBidSpreads_.get(i, j) ) {
                    result.set(row, 7, (mktBidSpreads_.get(i, j) - mdlSpreads_.get(i, j)) * 10000);
                } else {
                    result.set(row, 7, 0.0);
                }

                // spot CMS Leg NPVs
                result.set(row, 8, mktSpotCmsLegNPV_.get(i, j));
                result.set(row, 9, mdlSpotCmsLegNPV_.get(i, j));
                result.set(row, 10, errSpotCmsLegNPV_.get(i, j));

                // forward CMS Leg NPVs
                result.set(row, 11, mktFwdCmsLegNPV_.get(i, j));
                result.set(row, 12, mdlFwdCmsLegNPV_.get(i, j));
                result.set(row, 13, errFwdCmsLegNPV_.get(i, j));
            }
        }
        return result;
    }
}
