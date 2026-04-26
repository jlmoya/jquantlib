/*
Copyright (C) 2026 JQuantLib migration

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
package org.jquantlib.model;

import java.util.ArrayList;

import org.jquantlib.lang.annotation.Time;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.util.LazyObject;

/**
 * Liquid Black76 market instrument used during calibration.
 * Port of C++ v1.42.1 ql/models/calibrationhelper.hpp lines 47-115.
 */
public abstract class BlackCalibrationHelper extends LazyObject
        implements CalibrationHelper {

    public enum CalibrationErrorType {
        RelativePriceError, PriceError, ImpliedVolError
    }

    protected double marketValue_;
    protected final Handle<Quote> volatility_;
    protected final VolatilityType volatilityType_;
    protected final double shift_;
    protected final CalibrationErrorType calibrationErrorType_;
    protected PricingEngine engine_;

    public BlackCalibrationHelper(final Handle<Quote> volatility) {
        this(volatility, CalibrationErrorType.RelativePriceError,
                VolatilityType.ShiftedLognormal, 0.0);
    }

    public BlackCalibrationHelper(
            final Handle<Quote> volatility,
            final CalibrationErrorType calibrationErrorType,
            final VolatilityType type,
            final double shift) {
        this.volatility_ = volatility;
        this.calibrationErrorType_ = calibrationErrorType;
        this.volatilityType_ = type;
        this.shift_ = shift;
        this.volatility_.addObserver(this);
    }

    @Override
    protected void performCalculations() {
        marketValue_ = blackPrice(volatility_.currentLink().value());
    }

    public Handle<Quote> volatility() { return volatility_; }
    public VolatilityType volatilityType() { return volatilityType_; }

    public double marketValue() {
        calculate();
        return marketValue_;
    }

    public abstract double modelValue();

    @Override
    public double calibrationError() {
        switch (calibrationErrorType_) {
            case RelativePriceError:
                return Math.abs(marketValue() - modelValue()) / marketValue();
            case PriceError:
                return marketValue() - modelValue();
            case ImpliedVolError: {
                final double lowerPrice = blackPrice(0.001);
                final double upperPrice = blackPrice(10.0);
                final double modelPrice = modelValue();
                final double implied;
                if (modelPrice <= lowerPrice) {
                    implied = 0.001;
                } else if (modelPrice >= upperPrice) {
                    implied = 10.0;
                } else {
                    implied = impliedVolatility(modelPrice, 1e-12, 5000, 0.001, 10.0);
                }
                return implied - volatility_.currentLink().value();
            }
            default:
                throw new IllegalStateException("unknown CalibrationErrorType");
        }
    }

    public abstract void addTimesTo(ArrayList<Time> times);
    public abstract double blackPrice(double volatility);

    public double impliedVolatility(final double targetValue, final double accuracy,
            final int maxEvaluations, final double minVol, final double maxVol) {
        final ImpliedVolatilityHelper f = new ImpliedVolatilityHelper(this, targetValue);
        final Brent solver = new Brent();
        solver.setMaxEvaluations(maxEvaluations);
        return solver.solve(f, accuracy, volatility_.currentLink().value(), minVol, maxVol);
    }

    public void setPricingEngine(final PricingEngine engine) {
        this.engine_ = engine;
    }

    private static class ImpliedVolatilityHelper implements Ops.DoubleOp {
        private final BlackCalibrationHelper helper_;
        private final double value_;

        ImpliedVolatilityHelper(final BlackCalibrationHelper helper, final double value) {
            this.helper_ = helper;
            this.value_ = value;
        }

        @Override
        public double op(final double x) {
            return value_ - helper_.blackPrice(x);
        }
    }
}
