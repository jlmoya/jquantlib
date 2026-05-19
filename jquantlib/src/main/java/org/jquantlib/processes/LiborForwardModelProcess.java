/*
 Copyright (C) 2009 Ueli Hofstetter

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

package org.jquantlib.processes;

import org.jquantlib.QL;
import org.jquantlib.cashflow.IborCoupon;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;

import java.util.ArrayList;
import java.util.List;

/**
 * Libor-forward-model process
 * <p>
 * Stochastic process of a libor forward model using the rolling forward measure including predictor-corrector step
 * <p>
 * References:
 * <li>Glasserman, Paul, 2004, Monte Carlo Methods in Financial Engineering, Springer, Section 3.7</li>
 * <li>Antoon Pelsser, 2000, Efficient Methods for Valuing Interest Rate Derivatives, Springer, 8</li>
 * <li>Hull, John, White, Alan, 1999, Forward Rate Volatilities, Swap Rate Volatilities and the Implementation of the
 * Libor Market
 * Model<li>
 *
 * @author Ueli Hofstetter
 * @category processes
 * @see <a href="http://www.rotman.utoronto.ca/~amackay/fin/libormktmodel2.pdf">FORWARD RATE VOLATILITIES, SWAP RATE
 * VOLATILITIES,
 * AND THE IMPLEMENTATION OF THE LIBOR MARKET MODEL</a>
 */
// Phase 5e.5b-CFC-d-135 — port C++ {@code ql/legacy/libormarketmodels/lfmprocess.{hpp,cpp}}
// v1.42.1 in full. Previous Java port had a broken constructor (null cashFlows,
// {@code List.set(i, ...)} on an empty {@code ArrayList}, no {@code size()} /
// {@code factors()} / {@code nextIndexReset()} / {@code discountBond()} / accessor
// methods, and an {@code evolve()} that unconditionally threw).
public class LiborForwardModelProcess extends StochasticProcess {

    //Exception messages
    private static final String wrong_number_of_cashflows = "wrong number of cashflows";
    private static final String irregular_coupon_types = "irregular coupon types are not suppported";

    private final int size_;
    private final IborIndex index_;
    private final Array initialValues_;
    private final List</*@Time*/Double > fixingTimes_;
    private final List</*@Time*/Date > fixingDates_;
    private final List</*@Time*/Double > accrualStartTimes_;
    private final List</*@Time*/Double > accrualEndTimes_;
    private final List</*@Time*/Double > accrualPeriod_;
    private final Array m1, m2;
    private LfmCovarianceParameterization lfmParam_;

    public LiborForwardModelProcess(final int size, final IborIndex index) {
        super(new EulerDiscretization());

        this.size_ = size;
        this.index_ = index;
        this.initialValues_ = new Array(size_);
        // Phase 5e.5b-CFC-d-135 — pre-populate the parallel arrays so that
        // the per-coupon loop below can index them with {@code List.set(i, ...)}.
        // The previous port used {@code new ArrayList<>(size_)} (capacity hint
        // only) and then {@code set(i, …)} on the empty list, which threw
        // {@code IndexOutOfBoundsException} on first iteration.
        this.fixingDates_ = new ArrayList< Date >(size_);
        this.fixingTimes_ = new ArrayList< Double >(size_);
        this.accrualStartTimes_ = new ArrayList< Double >(size_);
        this.accrualEndTimes_ = new ArrayList< Double >(size_);
        this.accrualPeriod_ = new ArrayList< Double >(size_);
        for ( int i = 0; i < size_; ++i ) {
            fixingDates_.add(null);
            fixingTimes_.add(0.0);
            accrualStartTimes_.add(0.0);
            accrualEndTimes_.add(0.0);
            accrualPeriod_.add(0.0);
        }
        this.m1 = new Array(size_);
        this.m2 = new Array(size_);

        final DayCounter dayCounter = index_.dayCounter();
        final Leg flows = cashFlows(1.0);

        QL.require(this.size_ == flows.size(), wrong_number_of_cashflows); // TODO: message

        final Date settlement = index_.termStructure().currentLink().referenceDate();
        final Date startDate = ((IborCoupon) flows.get(0)).fixingDate();
        for ( int i = 0; i < size_; ++i ) {
            final IborCoupon coupon = (IborCoupon) flows.get(i);
            QL.require(coupon.date().eq(coupon.accrualEndDate()), irregular_coupon_types); // TODO: message

            initialValues_.set(i, coupon.rate());
            accrualPeriod_.set(i, coupon.accrualPeriod());

            fixingDates_.set(i, coupon.fixingDate());
            fixingTimes_.set(i, dayCounter.yearFraction(startDate, coupon.fixingDate()));
            accrualStartTimes_.set(i, dayCounter.yearFraction(settlement, coupon.accrualStartDate()));
            accrualEndTimes_.set(i, dayCounter.yearFraction(settlement, coupon.accrualEndDate()));
        }
    }

    //
    // public methods
    //

    public void setCovarParam(final LfmCovarianceParameterization param) {
        lfmParam_ = param;
    }

    public LfmCovarianceParameterization covarParam() {
        return lfmParam_;
    }

    public IborIndex index() {
        return index_;
    }

    /**
     * Mirror of C++ {@code Leg LiborForwardModelProcess::cashFlows(Real amount) const}
     * (ql/legacy/libormarketmodels/lfmprocess.cpp v1.42.1). Builds an {@link IborLeg} of unit-tenor coupons over the
     * {@code size_} rolling forward periods of the index.
     */
    public Leg cashFlows(final /*@Real*/ double amount) {
        final Date refDate = index_.termStructure().currentLink().referenceDate();
        final Schedule schedule = new Schedule(refDate,
                refDate.add(new Period(index_.tenor().length() * size_, index_.tenor().units())), index_.tenor(),
                index_.fixingCalendar(), index_.businessDayConvention(), index_.businessDayConvention(),
                DateGeneration.Rule.Forward, false);
        // IborLeg.Leg() installs a default BlackIborCouponPricer with an
        // empty CapletVolatilityStructure when caps/floors are empty and
        // inArrears is false, equivalent to the fictitious pricer the C++
        // ctor sets in ql/legacy/libormarketmodels/lfmprocess.cpp.
        return new IborLeg(schedule, index_).withNotionals(amount).withPaymentDayCounter(index_.dayCounter())
                .withPaymentAdjustment(index_.businessDayConvention()).withFixingDays(index_.fixingDays()).Leg();
    }

    public Leg cashFlows() {
        return cashFlows(1.0);
    }

    /**
     * Mirror of C++ {@code Size LiborForwardModelProcess::nextIndexReset(Time t)}. Returns the index of the first
     * fixing time strictly greater than {@code t} (i.e. an upper-bound search).
     */
    public /*Size*/ int nextIndexReset(/*Time*/ final double t) {
        // std::upper_bound equivalent on a sorted ascending list of Doubles.
        int lo = 0;
        int hi = fixingTimes_.size();
        while ( lo < hi ) {
            final int mid = (lo + hi) >>> 1;
            if ( t < fixingTimes_.get(mid) ) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    public List</*@Time*/Double > fixingTimes() {
        return fixingTimes_;
    }

    public List</*@Time*/Date > fixingDates() {
        return fixingDates_;
    }

    public List</*@Time*/Double > accrualStartTimes() {
        return accrualStartTimes_;
    }

    public List</*@Time*/Double > accrualEndTimes() {
        return accrualEndTimes_;
    }

    /**
     * Mirror of C++
     * {@code std::vector<DiscountFactor> LiborForwardModelProcess::discountBond(const std::vector<Rate>& rates)}.
     */
    public /*@DiscountFactor*/ double[] discountBond(final /*@Rate*/ double[] rates) {
        final double[] discountFactors = new double[size_];
        discountFactors[0] = 1.0 / (1.0 + rates[0] * accrualPeriod_.get(0));
        for ( int i = 1; i < size_; ++i ) {
            discountFactors[i] = discountFactors[i - 1] / (1.0 + rates[i] * accrualPeriod_.get(i));
        }
        return discountFactors;
    }

    //
    // Overrides StochasticProcess
    //

    @Override
    public Array initialValues() {
        return initialValues_.clone();
    }

    @Override
    public int size() {
        return size_;
    }

    @Override
    public int factors() {
        return lfmParam_.factors();
    }

    @Override
    public Array drift(/* @Time */final double t, final Array x) {
        final Array f = new Array(size_);
        final Matrix covariance = lfmParam_.covariance(t, x);
        final int m = nextIndexReset(t);
        for ( int k = m; k < size_; ++k ) {
            m1.set(k, accrualPeriod_.get(k) * x.get(k) / (1 + accrualPeriod_.get(k) * x.get(k)));
            // Mirror C++: inner_product(m1[m..k+1), col(k)[m..k+1)).
            final double ip = m1.range(m, k + 1).innerProduct(covariance.constRangeCol(k, m, k + 1));
            f.set(k, ip - 0.5 * covariance.get(k, k));
        }
        return f;
    }

    @Override
    public Matrix diffusion(/*@Time*/ final double t, final Array x) {
        return lfmParam_.diffusion(t, x);
    }

    @Override
    public Matrix covariance(/*@Time*/final double t, final Array x, /*@Time*/ final double dt) {
        // Phase 5e.5b-CFC-d-135 — fix to mirror C++:
        //   return lfmParam_->covariance(t, x) * dt;
        // Previous port multiplied two covariance matrices together which is
        // both wrong and uses mulAssign destructively.
        return lfmParam_.covariance(t, x).mul(dt);
    }

    @Override
    public Array apply(final Array x0, final Array dx) {
        final Array tmp = new Array(size_);
        for ( int k = 0; k < size_; ++k ) {
            tmp.set(k, x0.get(k) * Math.exp(dx.get(k)));
        }
        return tmp;
    }

    @Override
    public Array evolve(/*@Time*/ final double t0, final Array x0, /*@Time*/ final double dt, final Array dw) {

        /* predictor-corrector step to reduce discretization errors.

           Short - but slow - solution would be

           Array rnd_0     = stdDeviation(t0, x0, dt)*dw;
           Array drift_0   = discretization_->drift(*this, t0, x0, dt);

           return apply(x0, ( drift_0 + discretization_
                ->drift(*this,t0,apply(x0, drift_0 + rnd_0),dt) )*0.5 + rnd_0);

           The following implementation does the same but is faster.
        */

        final int m = nextIndexReset(t0);
        final double sdt = Math.sqrt(dt);

        final Array f = x0.clone();
        final Matrix diff = lfmParam_.diffusion(t0, x0);
        final Matrix covariance = lfmParam_.covariance(t0, x0);

        for ( int k = m; k < size_; ++k ) {
            final double y = accrualPeriod_.get(k) * x0.get(k);
            m1.set(k, y / (1 + y));

            // Mirror C++ inner_product(m1[m..k+1), col(k)[m..k+1)).
            final double m1ip = m1.range(m, k + 1).innerProduct(covariance.constRangeCol(k, m, k + 1));
            final double d = (m1ip - 0.5 * covariance.get(k, k)) * dt;
            final double r = diff.rangeRow(k).innerProduct(dw) * sdt;
            final double x = y * Math.exp(d + r);
            m2.set(k, x / (1 + x));

            final double m2ip = m2.range(m, k + 1).innerProduct(covariance.constRangeCol(k, m, k + 1));
            // Mirror C++ literally: f[k] = x0[k] * exp(0.5*(d + (ip - 0.5*cov[k][k])*dt) + r);
            final double value = x0.get(k) * Math.exp(0.5 * (d + (m2ip - 0.5 * covariance.get(k, k)) * dt) + r);
            f.set(k, value);
        }

        return f;
    }
}
