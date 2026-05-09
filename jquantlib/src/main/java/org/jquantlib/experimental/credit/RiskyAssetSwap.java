/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2008, 2009 Roland Lichters
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Risky asset-swap instrument.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::RiskyAssetSwap}
 * ({@code ql/experimental/credit/riskyassetswap.{hpp,cpp}}).
 *
 * <p>Phase 4m.5 work-item 12 (instrument only — {@code AssetSwapHelper}
 * for default-probability bootstrap deferred).
 */
public class RiskyAssetSwap extends Instrument {

    private final boolean fixedPayer;
    private final double nominal;
    private final Schedule fixedSchedule;
    private final Schedule floatSchedule;
    private final DayCounter fixedDayCounter;
    private final DayCounter floatDayCounter;
    private final double spread;
    private final double recoveryRate;
    private final Handle<YieldTermStructure> yieldTS;
    private final Handle<DefaultProbabilityTermStructure> defaultTS;
    private double coupon;

    private double fixedAnnuity;
    private double floatAnnuity;
    private double parCoupon;
    private double recoveryValue;
    private double riskyBondPrice;

    public RiskyAssetSwap(final boolean fixedPayer,
                          final double nominal,
                          final Schedule fixedSchedule,
                          final Schedule floatSchedule,
                          final DayCounter fixedDayCounter,
                          final DayCounter floatDayCounter,
                          final double spread,
                          final double recoveryRate,
                          final Handle<YieldTermStructure> yieldTS,
                          final Handle<DefaultProbabilityTermStructure> defaultTS,
                          final double coupon) {
        this.fixedPayer = fixedPayer;
        this.nominal = nominal;
        this.fixedSchedule = fixedSchedule;
        this.floatSchedule = floatSchedule;
        this.fixedDayCounter = fixedDayCounter;
        this.floatDayCounter = floatDayCounter;
        this.spread = spread;
        this.recoveryRate = recoveryRate;
        this.yieldTS = yieldTS;
        this.defaultTS = defaultTS;
        this.coupon = coupon;

        yieldTS.addObserver(this);
        defaultTS.addObserver(this);
    }

    public RiskyAssetSwap(final boolean fixedPayer,
                          final double nominal,
                          final Schedule fixedSchedule,
                          final Schedule floatSchedule,
                          final DayCounter fixedDayCounter,
                          final DayCounter floatDayCounter,
                          final double spread,
                          final double recoveryRate,
                          final Handle<YieldTermStructure> yieldTS,
                          final Handle<DefaultProbabilityTermStructure> defaultTS) {
        this(fixedPayer, nominal, fixedSchedule, floatSchedule, fixedDayCounter,
                floatDayCounter, spread, recoveryRate, yieldTS, defaultTS,
                Constants.NULL_RATE);
    }

    public double nominal() {
        return nominal;
    }

    public double spread() {
        return spread;
    }

    public boolean fixedPayer() {
        return fixedPayer;
    }

    public double floatAnnuity() {
        double annuity = 0.0;
        for (int i = 1; i < floatSchedule.size(); i++) {
            final double dcf = floatDayCounter.yearFraction(floatSchedule.date(i - 1),
                    floatSchedule.date(i));
            annuity += dcf * yieldTS.currentLink().discount(floatSchedule.date(i));
        }
        return annuity;
    }

    @Override
    public boolean isExpired() {
        return fixedSchedule.dates().get(fixedSchedule.size() - 1)
                .compareTo(yieldTS.currentLink().referenceDate()) <= 0;
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
    }

    @Override
    protected void performCalculations() {
        // Order matters
        floatAnnuity = floatAnnuity();
        fixedAnnuity = fixedAnnuity();
        parCoupon = parCoupon();
        if (coupon == Constants.NULL_RATE) {
            coupon = parCoupon;
        }
        recoveryValue = recoveryValue();
        riskyBondPrice = riskyBondPrice();

        NPV = riskyBondPrice
                - coupon * fixedAnnuity
                + yieldTS.currentLink().discount(fixedSchedule.date(0))
                - yieldTS.currentLink().discount(fixedSchedule.date(fixedSchedule.size() - 1))
                + spread * floatAnnuity;

        NPV *= nominal;
        if (!fixedPayer) {
            NPV *= -1;
        }
    }

    private double fixedAnnuity() {
        double annuity = 0.0;
        // Mirrors C++ literal: loops over floatSchedule with fixedDayCounter.
        for (int i = 1; i < floatSchedule.size(); i++) {
            final double dcf = fixedDayCounter.yearFraction(floatSchedule.date(i - 1),
                    floatSchedule.date(i));
            annuity += dcf * yieldTS.currentLink().discount(floatSchedule.date(i));
        }
        return annuity;
    }

    private double parCoupon() {
        return (yieldTS.currentLink().discount(fixedSchedule.date(0))
                - yieldTS.currentLink().discount(fixedSchedule.date(fixedSchedule.size() - 1)))
                / fixedAnnuity;
    }

    private double recoveryValue() {
        double rv = 0.0;
        // Simple Euler integral
        for (int i = 1; i < fixedSchedule.size(); i++) {
            final TimeUnit stepSize = TimeUnit.Days;
            Date d = (fixedSchedule.date(i - 1).compareTo(defaultTS.currentLink().referenceDate()) >= 0)
                    ? fixedSchedule.date(i - 1) : defaultTS.currentLink().referenceDate();
            Date d0 = d;
            do {
                final double disc = yieldTS.currentLink().discount(d);
                final double dd = defaultTS.currentLink().defaultDensity(d, true);
                final double dcf = defaultTS.currentLink().dayCounter().yearFraction(d0, d);
                rv += disc * dd * dcf;
                d0 = d;
                d = new NullCalendar().advance(d0, 1, stepSize, BusinessDayConvention.Unadjusted, false);
            } while (d.compareTo(fixedSchedule.date(i)) < 0);
        }
        rv *= recoveryRate;
        return rv;
    }

    private double riskyBondPrice() {
        double value = 0.0;
        for (int i = 1; i < fixedSchedule.size(); i++) {
            final double dcf = fixedDayCounter.yearFraction(fixedSchedule.date(i - 1),
                    fixedSchedule.date(i));
            value += dcf * yieldTS.currentLink().discount(fixedSchedule.date(i))
                    * defaultTS.currentLink().survivalProbability(fixedSchedule.date(i), true);
        }
        value *= coupon;
        value += yieldTS.currentLink().discount(fixedSchedule.date(fixedSchedule.size() - 1))
                * defaultTS.currentLink().survivalProbability(fixedSchedule.date(fixedSchedule.size() - 1), true);
        return value + recoveryValue;
    }

    public double fairSpread() {
        calculate();
        double value = 0.0;
        for (int i = 1; i < fixedSchedule.size(); i++) {
            final double dcf = fixedDayCounter.yearFraction(fixedSchedule.date(i - 1),
                    fixedSchedule.date(i));
            value += dcf * yieldTS.currentLink().discount(fixedSchedule.date(i))
                    * defaultTS.currentLink().defaultProbability(fixedSchedule.date(i), true);
        }
        value *= coupon;
        value += yieldTS.currentLink().discount(fixedSchedule.date(fixedSchedule.size() - 1))
                * defaultTS.currentLink().defaultProbability(fixedSchedule.date(fixedSchedule.size() - 1), true);
        final double initialDiscount = yieldTS.currentLink().discount(fixedSchedule.date(0));
        return (1.0 - initialDiscount + value - recoveryValue) / fixedAnnuity;
    }
}
