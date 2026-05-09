/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

/*
 Copyright (C) 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;

/**
 * ABCD-form implementation of {@link VolatilityInterpolationSpecifier}.
 *
 * <p>Java port of {@code ql/models/marketmodels/models/volatilityinterpolationspecifierabcd.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Phase 3j B.9 (Track B).
 */
public final class VolatilityInterpolationSpecifierAbcd implements VolatilityInterpolationSpecifier {

    private final int period_;
    private final int offset_;
    private final int noBigRates_;
    private final int noSmallRates_;
    private final double[] timesForSmallRates_;

    // mutable
    private List<PiecewiseConstantVariance> interpolatedVariances_;
    private final List<PiecewiseConstantVariance> originalVariances_;
    private final List<PiecewiseConstantAbcdVariance> originalABCDVariances_;
    private final List<PiecewiseConstantAbcdVariance> originalABCDVariancesScaled_;
    private double lastCapletVol_;
    private double[] scalingFactors_;

    public VolatilityInterpolationSpecifierAbcd(final int period,
                                                final int offset,
                                                final List<PiecewiseConstantAbcdVariance> originalVariances,
                                                final double[] timesForSmallRates,
                                                final double lastCapletVol) {
        this.period_ = period;
        this.offset_ = offset;
        this.noBigRates_ = originalVariances.size();
        this.noSmallRates_ = timesForSmallRates.length - 1;
        this.timesForSmallRates_ = timesForSmallRates.clone();
        this.originalABCDVariances_ = new ArrayList<>();
        this.originalABCDVariancesScaled_ = new ArrayList<>();
        for (final PiecewiseConstantAbcdVariance v : originalVariances) {
            this.originalABCDVariances_.add(new PiecewiseConstantAbcdVariance(v));
            this.originalABCDVariancesScaled_.add(new PiecewiseConstantAbcdVariance(v));
        }
        this.scalingFactors_ = new double[noBigRates_];
        Arrays.fill(this.scalingFactors_, 1.0);

        QL.require((noSmallRates_ - offset) / period == noBigRates_,
                "size mismatch in VolatilityInterpolationSpecifierAbcd");

        for (int i = 0; i < noBigRates_; ++i) {
            for (int j = 0; j < originalVariances.get(i).rateTimes().length; ++j) {
                QL.require(originalVariances.get(i).rateTimes()[j] == timesForSmallRates[offset + j * period],
                        "rate times in variances passed in don't match small times");
            }
        }

        // change type of array to PiecewiseConstantVariance for client
        this.originalVariances_ = new ArrayList<>();
        for (int i = 0; i < noBigRates_; ++i) {
            this.originalVariances_.add(new PiecewiseConstantAbcdVariance(originalVariances.get(i)));
        }

        this.lastCapletVol_ = (lastCapletVol == 0.0)
                ? originalVariances.get(noBigRates_ - 1).totalVolatility(noBigRates_ - 1)
                : lastCapletVol;

        this.interpolatedVariances_ = new ArrayList<>();
        for (int i = 0; i < noSmallRates_; ++i) {
            this.interpolatedVariances_.add(null);
        }
        recompute();
    }

    /** Convenience overload — lastCapletVol = 0.0 → take from last original variance. */
    public VolatilityInterpolationSpecifierAbcd(final int period,
                                                final int offset,
                                                final List<PiecewiseConstantAbcdVariance> originalVariances,
                                                final double[] timesForSmallRates) {
        this(period, offset, originalVariances, timesForSmallRates, 0.0);
    }

    @Override
    public void setScalingFactors(final double[] scales) {
        QL.require(scalingFactors_.length == scales.length,
                "inappropriate number of scales passed in to setScalingFactors");
        scalingFactors_ = scales.clone();
        recompute();
    }

    @Override
    public void setLastCapletVol(final double vol) {
        this.lastCapletVol_ = vol;
        recompute();
    }

    @Override public List<PiecewiseConstantVariance> interpolatedVariances() { return interpolatedVariances_; }
    @Override public List<PiecewiseConstantVariance> originalVariances() { return originalVariances_; }
    @Override public int getPeriod() { return period_; }
    @Override public int getOffset() { return offset_; }
    @Override public int getNoBigRates() { return noBigRates_; }
    @Override public int getNoSmallRates() { return noSmallRates_; }

    private void recompute() {
        // First, scale each original ABCD variance by its scalingFactor
        for (int i = 0; i < noBigRates_; ++i) {
            final double[] abcd = new double[4];
            originalABCDVariances_.get(i).getABCD(abcd);
            final double s = scalingFactors_[i];
            // c is not scaled; a, b, d are
            originalABCDVariancesScaled_.set(i, new PiecewiseConstantAbcdVariance(
                    abcd[0] * s, abcd[1] * s, abcd[2], abcd[3] * s, i,
                    originalABCDVariances_.get(i).rateTimes()));
        }

        // before offset: use ABCD of first scaled
        {
            final double[] abcd0 = new double[4];
            originalABCDVariancesScaled_.get(0).getABCD(abcd0);
            for (int i = 0; i < offset_; ++i) {
                interpolatedVariances_.set(i,
                        new PiecewiseConstantAbcdVariance(abcd0[0], abcd0[1], abcd0[2], abcd0[3],
                                i, timesForSmallRates_));
            }
        }

        // in between rates: average of adjacent ABCD
        for (int j = 0; j < noBigRates_ - 1; ++j) {
            final double[] abcd0 = new double[4];
            final double[] abcd1 = new double[4];
            originalABCDVariancesScaled_.get(j).getABCD(abcd0);
            originalABCDVariancesScaled_.get(j + 1).getABCD(abcd1);
            final double a = 0.5 * (abcd0[0] + abcd1[0]);
            final double b = 0.5 * (abcd0[1] + abcd1[1]);
            final double c = 0.5 * (abcd0[2] + abcd1[2]);
            final double d = 0.5 * (abcd0[3] + abcd1[3]);
            for (int i = 0; i < period_; ++i) {
                interpolatedVariances_.set(i + j * period_ + offset_,
                        new PiecewiseConstantAbcdVariance(a, b, c, d,
                                i + j * period_, timesForSmallRates_));
            }
        }

        // after last big rate
        {
            final double[] abcd = new double[4];
            originalABCDVariancesScaled_.get(noBigRates_ - 1).getABCD(abcd);
            double a = abcd[0], b = abcd[1], c = abcd[2], d = abcd[3];
            for (int i = offset_ + (noBigRates_ - 1) * period_; i < noSmallRates_; ++i) {
                interpolatedVariances_.set(i,
                        new PiecewiseConstantAbcdVariance(a, b, c, d, i, timesForSmallRates_));
            }

            // last rate: scale to match caplet vol
            final double vol = interpolatedVariances_.get(noSmallRates_ - 1).totalVolatility(noSmallRates_ - 1);
            final double scale = lastCapletVol_ / vol;
            a *= scale;
            b *= scale;
            d *= scale;
            interpolatedVariances_.set(noSmallRates_ - 1,
                    new PiecewiseConstantAbcdVariance(a, b, c, d, noSmallRates_ - 1, timesForSmallRates_));
        }
    }
}
