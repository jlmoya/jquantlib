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
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

import org.jquantlib.time.Date;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

import java.util.List;

/**
 * Base inflation-coupon pricer.
 *
 * <p>The main reason we can't use {@link FloatingRateCouponPricer} as the base
 * is that it takes a {@link FloatingRateCoupon} which takes an {@code InterestRateIndex} and we need an inflation index
 * (these are lagged).
 *
 * <p>The basic inflation-specific thing that the pricer has to do is deal with
 * different lags in the index and the option, e.g. the option could look 3 months back and the index 2.
 *
 * <p>We add the requirement that pricers do inverseCap/Floor-lets. These are
 * cap/floor-lets as usually defined, i.e. pay out if underlying is above/below a strike. The non-inverse (usual)
 * versions are from a coupon point of view (a capped coupon has a maximum at the strike).
 *
 * <p>Mirrors C++ {@code QuantLib::InflationCouponPricer} at v1.42.1
 * (cashflows/inflationcouponpricer.{hpp,cpp}).
 *
 * @author JQuantLib migration team (Phase 2p A.2)
 */
public abstract class InflationCouponPricer implements Observer, Observable {

    //
    // protected fields
    //

    // Phase 2x A.4: WeakReferenceObservable to break cumulative
    // observer-list bleed across tests.
    private final DefaultObservable delegatedObservable = new org.jquantlib.util.WeakReferenceObservable(this);
    /** Payment date of the coupon being priced (initialized in {@link #initialize}). */
    protected Date paymentDate_;

    //
    // public abstract methods
    //

    public abstract double swapletPrice();

    public abstract double swapletRate();

    public abstract double capletPrice(double effectiveCap);

    public abstract double capletRate(double effectiveCap);

    public abstract double floorletPrice(double effectiveFloor);

    public abstract double floorletRate(double effectiveFloor);

    public abstract void initialize(InflationCoupon coupon);

    //
    // implements Observer
    //

    @Override
    public void update() {
        notifyObservers();
    }

    //
    // implements Observable
    //

    @Override
    public void addObserver(final Observer observer) {
        delegatedObservable.addObserver(observer);
    }

    @Override
    public int countObservers() {
        return delegatedObservable.countObservers();
    }

    @Override
    public void deleteObserver(final Observer observer) {
        delegatedObservable.deleteObserver(observer);
    }

    @Override
    public void deleteObservers() {
        delegatedObservable.deleteObservers();
    }

    @Override
    public List< Observer > getObservers() {
        return delegatedObservable.getObservers();
    }

    @Override
    public void notifyObservers() {
        delegatedObservable.notifyObservers();
    }

    @Override
    public void notifyObservers(final Object arg) {
        delegatedObservable.notifyObservers(arg);
    }
}
