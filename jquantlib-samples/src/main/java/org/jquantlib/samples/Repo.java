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

/*!
 Copyright (C) 2006 Allen Kuo

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

/* Java port of QuantLib v1.42.1 Examples/Repo/Repo.cpp.

   A Repo calculation done using the BondForward class
   (cf. aaBondFwd() repo example at
   http://www.fincad.com/support/developerFunc/mathref/BFWD.htm).

   This repo is set up to use the repo rate to do all discounting
   (including the underlying bond income).  Forward delivery price
   is also obtained using this repo rate.  All this is done by
   supplying the BondForward constructor with a flat repo
   YieldTermStructure.
 */

package org.jquantlib.samples;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.instruments.BondForward;
import org.jquantlib.instruments.Position;
import org.jquantlib.instruments.bonds.FixedRateBond;
import org.jquantlib.pricingengines.bond.DiscountingBondEngine;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.Compounding;
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
import org.jquantlib.time.calendars.NullCalendar;

public class Repo implements Runnable {

    public static void main(final String[] args) {
        new Repo().run();
    }

    @Override
    public void run() {

        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");

        final Date repoSettlementDate = new Date(14, Month.February, 2000);
        final Date repoDeliveryDate = new Date(15, Month.August, 2000);
        final double repoRate = 0.05;
        final DayCounter repoDayCountConvention = new Actual360();
        final int repoSettlementDays = 0;
        final Compounding repoCompounding = Compounding.Simple;
        final Frequency repoCompoundFreq = Frequency.Annual;

        // assume a ten year bond — this is irrelevant
        final Date bondIssueDate = new Date(15, Month.September, 1995);
        final Date bondDatedDate = new Date(15, Month.September, 1995);
        final Date bondMaturityDate = new Date(15, Month.September, 2005);
        final double bondCoupon = 0.08;
        final Frequency bondCouponFrequency = Frequency.Semiannual;
        // unknown what calendar fincad is using
        final Calendar bondCalendar = new NullCalendar();
        final DayCounter bondDayCountConvention = new Thirty360(Thirty360.Convention.BondBasis);
        // unknown what fincad is using; this may affect accrued calculation
        final int bondSettlementDays = 0;
        final BusinessDayConvention bondBusinessDayConvention = BusinessDayConvention.Unadjusted;
        final double bondCleanPrice = 89.97693786;
        final double bondRedemption = 100.0;
        final double faceAmount = 100.0;

        new Settings().setEvaluationDate(repoSettlementDate);

        final RelinkableHandle<YieldTermStructure> bondCurve = new RelinkableHandle<YieldTermStructure>();
        bondCurve.linkTo(new FlatForward(repoSettlementDate,
                                         0.01, // dummy rate; relinked below to the bond's implied yield
                                         bondDayCountConvention,
                                         Compounding.Compounded,
                                         bondCouponFrequency));

        final Schedule bondSchedule = new Schedule(bondDatedDate, bondMaturityDate,
                new Period(bondCouponFrequency),
                bondCalendar, bondBusinessDayConvention, bondBusinessDayConvention,
                DateGeneration.Rule.Backward, false);

        final FixedRateBond bond = new FixedRateBond(bondSettlementDays,
                faceAmount,
                bondSchedule,
                new double[] { bondCoupon },
                bondDayCountConvention,
                bondBusinessDayConvention,
                bondRedemption,
                bondIssueDate);
        bond.setPricingEngine(new DiscountingBondEngine(bondCurve));

        bondCurve.linkTo(new FlatForward(repoSettlementDate,
                                         bond.yield(bondCleanPrice,
                                                    bondDayCountConvention,
                                                    Compounding.Compounded,
                                                    bondCouponFrequency),
                                         bondDayCountConvention,
                                         Compounding.Compounded,
                                         bondCouponFrequency));

        final Position fwdType = Position.Long;
        final double dummyStrike = 91.5745;

        final RelinkableHandle<YieldTermStructure> repoCurve = new RelinkableHandle<YieldTermStructure>();
        repoCurve.linkTo(new FlatForward(repoSettlementDate,
                                         repoRate,
                                         repoDayCountConvention,
                                         repoCompounding,
                                         repoCompoundFreq));

        final BondForward bondFwd = new BondForward(repoSettlementDate, repoDeliveryDate, fwdType, dummyStrike,
                repoSettlementDays, repoDayCountConvention, bondCalendar,
                bondBusinessDayConvention, bond, repoCurve, repoCurve);

        System.out.println("Underlying bond clean price: " + bond.cleanPrice());
        System.out.println("Underlying bond dirty price: " + bond.dirtyPrice());
        System.out.println("Underlying bond accrued at settlement: "
                + bond.accruedAmount(repoSettlementDate));
        System.out.println("Underlying bond accrued at delivery:   "
                + bond.accruedAmount(repoDeliveryDate));
        System.out.println("Underlying bond spot income: " + bondFwd.spotIncome(repoCurve));
        System.out.println("Underlying bond fwd income:  "
                + bondFwd.spotIncome(repoCurve) / repoCurve.currentLink().discount(repoDeliveryDate));
        System.out.println("Repo strike: " + dummyStrike);
        System.out.println("Repo NPV:    " + bondFwd.NPV());
        System.out.println("Repo clean forward price: " + bondFwd.cleanForwardPrice());
        System.out.println("Repo dirty forward price: " + bondFwd.forwardPrice());
        System.out.println("Repo implied yield: "
                + bondFwd.impliedYield(bond.dirtyPrice(),
                                       dummyStrike,
                                       repoSettlementDate,
                                       repoCompounding,
                                       repoDayCountConvention).rate());
        System.out.println("Market repo rate:   "
                + repoCurve.currentLink().zeroRate(repoDeliveryDate,
                                                   repoDayCountConvention,
                                                   repoCompounding,
                                                   repoCompoundFreq).rate());
        System.out.println();

        System.out.println("Compare with example given at");
        System.out.println("http://www.fincad.com/support/developerFunc/mathref/BFWD.htm");
        System.out.println("Clean forward price = 88.2408");
        System.out.println();
        System.out.println("In that example, it is unknown what bond calendar they are");
        System.out.println("using, as well as settlement Days. For that reason, we have");
        System.out.println("made the simplest possible assumptions here: NullCalendar");
        System.out.println("and 0 settlement days.");
    }
}
