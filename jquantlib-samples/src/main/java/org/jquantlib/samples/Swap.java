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
 */

/*  Minimal vanilla interest-rate swap valuation example.

    QuantLib v1.42.1 does not ship an Examples/Swap/ — its swap sample
    is the (much larger) MulticurveBootstrapping example.  This file is
    a standalone JQuantLib sample modelled after the swap-pricing tail
    of that example: a flat-forward Euribor 6M curve is built, a 5-year
    payer vanilla swap is constructed (4% fixed vs Euribor 6M + 0 bp),
    priced with DiscountingSwapEngine, and the per-leg NPVs together
    with the fair fixed rate and fair spread are printed.

    Consistency checks (printed at the end):
      * fairRate is the fixed coupon that makes NPV ≈ 0.
      * fairSpread is the spread on the floating leg that makes NPV ≈ 0.
      * Pricing the swap at fairRate with zero spread should reproduce
        NPV ≈ 0 (sub-basis-point).
 */

package org.jquantlib.samples;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;

public class Swap implements Runnable {

    public static void main(final String[] args) {
        new Swap().run();
    }

    @Override
    public void run() {

        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");

        final Date today = new Date(15, Month.February, 2010);
        new Settings().setEvaluationDate(today);

        final Calendar calendar = new Target();
        final double nominal = 1_000_000.0;
        final double fixedRate = 0.04;
        final double floatingSpread = 0.0;

        final DayCounter fixedDayCount = new Thirty360(Thirty360.Convention.BondBasis);
        final DayCounter floatDayCount = new Actual360();

        // Flat 3% curve, attached to the floating index.
        final RelinkableHandle<YieldTermStructure> discountCurve = new RelinkableHandle<YieldTermStructure>();
        discountCurve.linkTo(new FlatForward(today, 0.03, new Actual360()));
        final IborIndex euribor6m = new Euribor6M(discountCurve);

        // Spot start, 5-year tenor.
        final Date settlement = calendar.advance(today, new Period(2, TimeUnit.Days));
        final Date maturity = calendar.advance(settlement, new Period(5, TimeUnit.Years));

        final Schedule fixedSchedule = new Schedule(settlement, maturity,
                new Period(Frequency.Annual), calendar,
                BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Forward, false);
        final Schedule floatSchedule = new Schedule(settlement, maturity,
                new Period(Frequency.Semiannual), calendar,
                BusinessDayConvention.ModifiedFollowing, BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);

        final VanillaSwap swap = new VanillaSwap(VanillaSwap.Type.Payer, nominal,
                fixedSchedule, fixedRate, fixedDayCount,
                floatSchedule, euribor6m, floatingSpread, floatDayCount);
        swap.setPricingEngine(new DiscountingSwapEngine(discountCurve));

        System.out.printf("Evaluation date     : %s%n", today);
        System.out.printf("Settlement date     : %s%n", settlement);
        System.out.printf("Maturity date       : %s%n", maturity);
        System.out.printf("Nominal             : %,.2f%n", nominal);
        System.out.printf("Fixed coupon (input): %.6f%n", fixedRate);
        System.out.printf("Floating spread     : %.6f%n", floatingSpread);
        System.out.println();
        System.out.printf("NPV                 : %,.6f%n", swap.NPV());
        System.out.printf("Fixed leg NPV       : %,.6f%n", swap.fixedLegNPV());
        System.out.printf("Floating leg NPV    : %,.6f%n", swap.floatingLegNPV());
        System.out.printf("Fair fixed rate     : %.8f%n", swap.fairRate());
        System.out.printf("Fair float spread   : %.8f%n", swap.fairSpread());

        // Consistency check: re-price the swap at the fair fixed rate.
        // NPV should collapse to ~0 (sub-basis-point).
        final VanillaSwap fairSwap = new VanillaSwap(VanillaSwap.Type.Payer, nominal,
                fixedSchedule, swap.fairRate(), fixedDayCount,
                floatSchedule, euribor6m, 0.0, floatDayCount);
        fairSwap.setPricingEngine(new DiscountingSwapEngine(discountCurve));
        System.out.println();
        System.out.printf("Check — NPV at fairRate: %,.10f%n", fairSwap.NPV());
    }
}
