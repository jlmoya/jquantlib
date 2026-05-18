/*
Copyright (C) 2009 Ueli Hofstetter
Copyright (C) 2009 John Martin
Copyright (C) 2010 Zahid Hussein
Copyright (C) 2011 Richard Gomes

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
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005 StatPro Italia srl
 Copyright (C) 2006, 2007 Cristina Duminuco
 Copyright (C) 2006, 2007 Giorgio Facchinetti
 Copyright (C) 2006 Mario Pucci
 Copyright (C) 2007 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.InterestRateIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;

/**
 * Cash flow vector builder
 *
 * @author Ueli Hofstetter
 * @author John Martin
 * @author Zahid Hussain
 * @author Richard Gomes
 */
public class FloatingLeg< InterestRateIndexType extends InterestRateIndex,
                          FloatingCouponType extends FloatingRateCoupon,
                          CappedFlooredCouponType
                         > extends Leg {
	//Make compiler happy
	private static final long serialVersionUID = 1L;

    private final Class<?> typeIRT;
    private final Class<?> typeFCT;
    private final Class<?> typeCFC;

    
    //
    // public constructors
    //
    
	public FloatingLeg(
	        final Class<?> typeIRT,
	        final Class<?> typeFCT,
	        final Class<?> typeCFC,
            final Array nominals,
            final Schedule schedule,
            final InterestRateIndexType index,
            final DayCounter paymentDayCounter,
            final BusinessDayConvention paymentAdj,
            final Array fixingDays,
            final Array gearings,
            final Array spreads,
            final Array caps,
            final Array floors,
            final boolean isInArrears,
            final boolean isZero) {
        this(typeIRT, typeFCT, typeCFC, nominals, schedule, index,
                paymentDayCounter, paymentAdj,
                fixingDays, gearings, spreads, caps, floors,
                isInArrears, isZero,
                /* paymentCalendar */ null, /* paymentLag */ 0);
	}

    /** Phase 5d.5-Bonds-b — extended ctor accepting payment calendar and
     *  payment lag (mirrors C++ IborLeg::operator Leg() at
     *  ql/cashflows/iborcoupon.cpp). When {@code paymentCalendar} is
     *  {@code null}, falls back to {@code schedule.calendar()} so existing
     *  call sites are unchanged. */
	public FloatingLeg(
	        final Class<?> typeIRT,
	        final Class<?> typeFCT,
	        final Class<?> typeCFC,
            final Array nominals,
            final Schedule schedule,
            final InterestRateIndexType index,
            final DayCounter paymentDayCounter,
            final BusinessDayConvention paymentAdj,
            final Array fixingDays,
            final Array gearings,
            final Array spreads,
            final Array caps,
            final Array floors,
            final boolean isInArrears,
            final boolean isZero,
            final Calendar paymentCalendar,
            final int paymentLag) {
        super(schedule.size() - 1);
        this.typeIRT = typeIRT;
        this.typeFCT = typeFCT;
        this.typeCFC = typeCFC;
        constructor(
                nominals,
                schedule,
                index,
                paymentDayCounter, paymentAdj,
                fixingDays, gearings, spreads, caps, floors,
                isInArrears, isZero,
                paymentCalendar, paymentLag);
	}

	private void constructor(
            final Array nominals,
            final Schedule schedule,
            final InterestRateIndexType index,
            final DayCounter paymentDayCounter,
            final BusinessDayConvention paymentAdj,
            final Array fixingDays,
            final Array gearings,
            final Array spreads,
            final Array caps,
            final Array floors,
            final boolean isInArrears,
            final boolean isZero,
            final Calendar paymentCalendar,
            final int paymentLag) {
        final int n = schedule.size()-1;
        QL.require(nominals != null && nominals.size() <= n,
                   "too many nominals (" + nominals.size() +
                   "), only " + n + " required");
        QL.require(gearings != null && gearings.size()<=n,
                   "too many gearings (" + gearings.size() +
                   "), only " + n + " required");
        QL.require(spreads != null && spreads.size()<=n,
                   "too many spreads (" + spreads.size() +
                   "), only " + n + " required");
        QL.require(caps != null && caps.size()<=n,
                   "too many caps (" + caps.size() +
                   "), only " + n + " required");
        QL.require(floors != null && floors.size()<=n,
                   "too many floors (" + floors.size() +
                   "), only " + n + " required");
        QL.require(!isZero || !isInArrears,
                   "in-arrears and zero features are not compatible");

        // the following is not always correct
        final Calendar calendar = schedule.calendar();
        // Phase 5d.5-Bonds-b — payment-date calendar may be overridden by
        // IborLeg.withPaymentCalendar; defaults to schedule.calendar().
        // paymentLag advances the period-end by N business days before
        // the BusinessDayConvention is applied. Mirrors C++
        // IborLeg::operator Leg() in ql/cashflows/iborcoupon.cpp.
        final Calendar payCal = (paymentCalendar == null) ? calendar : paymentCalendar;

        Date refStart, start, refEnd, end;
        final Date lastPaymentDate = payCal.advance(schedule.date(n), paymentLag, TimeUnit.Days, paymentAdj, false);

        for (int i=0; i<n; ++i) {
            refStart = start = schedule.date(i);
            refEnd   =   end = schedule.date(i+1);
            final Date paymentDate =
                isZero ? lastPaymentDate : payCal.advance(end, paymentLag, TimeUnit.Days, paymentAdj, false);
            // Mirrors C++ ql/cashflows/cashflowvectors.hpp:119-123 — guard
            // the irregular-period reference-date adjustment on the
            // schedule actually exposing tenor + isRegular metadata.
            // Without these guards a date-vector-only Schedule (no
            // tenor/isRegular meta-info — e.g. the third variant in
            // CashFlowsTest.testPartialScheduleLegConstruction) trips
            // "full interface (isRegular) not available". When the
            // metadata is absent the C++ code falls through to the
            // bare schedule-period reference dates already assigned
            // above, which is exactly what we want. Phase 5e.5b-CFC-d-191.
            if (i == 0 && schedule.hasIsRegular() && schedule.hasTenor() && !schedule.isRegular(i+1)) {
                final BusinessDayConvention bdc = schedule.businessDayConvention();
                refStart = calendar.adjust(end.sub(schedule.tenor()), bdc);
            }
            if (i == n-1 && schedule.hasIsRegular() && schedule.hasTenor() && !schedule.isRegular(i+1)) {
                final BusinessDayConvention bdc = schedule.businessDayConvention();
                refEnd = calendar.adjust(start.add(schedule.tenor()), bdc);
            }
            if (get(gearings, i, 1.0) == 0.0) { // fixed coupon
                add(new FixedRateCoupon(get(nominals, i, 1.0),
                                    paymentDate,
                                    effectiveFixedRate(spreads,caps,floors,i),
                                    paymentDayCounter,
                                    start, end, refStart, refEnd));
            }
           else if (noOption(caps, floors, i)) { //// floating coupon
        	   // construct a new instance using reflection. first get the
        	   FloatingCouponType frc;
        	   try {
        		   frc = (FloatingCouponType) typeFCT.getConstructor(
                      Date.class, // paymentdate
                      double.class, // nominal
                      Date.class, // start date
                      Date.class, // enddate
                      int.class, // fixing days
                      typeIRT,    // InterestRateIndex subclass (IborIndex or SwapIndex)
                      double.class, // gearing
                      double.class, // spread
                      Date.class, // refperiodstart
                      Date.class, // refperiodend
                      DayCounter.class,// daycounter
                      boolean.class) // inarrears
                      // then create a new instance
                      .newInstance(
                              paymentDate,
                              get(nominals, i, 1.0),
                              start,
                              end,
                              (int)get(fixingDays, i, index.fixingDays()),
                              index,
                              get(gearings, i, 1.0),
                              get(spreads, i, 0.0), 
                              refStart,
                              refEnd, 
                              paymentDayCounter, 
                              isInArrears);
        	   } catch (final Exception e) {
        		   throw new LibraryException(
        		   "Couldn't construct new instance from generic type for floating coupon"); // QA:[RG]::verified
        	   }
        	   add(frc);
          }
         else {
                CappedFlooredCouponType cfctc;
                try {
                    cfctc = (CappedFlooredCouponType) typeCFC.getConstructor(
                            Date.class, // paymentdate
                            double.class, // nominal
                            Date.class, // start date
                            Date.class, // enddate
                            int.class, // fixing days
                            typeIRT,    // InterestRateIndex subclass (IborIndex or SwapIndex)
                            double.class, // gearing
                            double.class, // spread
                            double.class, //caps
                            double.class, //floors
                            Date.class, // refperiodstart
                            Date.class, // refperiodend
                            DayCounter.class,// daycounter
                            boolean.class) // inarrears
                            // then create a new instance
                            .newInstance (
                                    paymentDate,
                                    get(nominals,i, 1.0),
                                    start,
                                    end,
                                    (int)get(fixingDays, i, index.fixingDays()),
                                    index,
                                    get(gearings, i, 1.0),
                                    get(spreads, i, 0.0),
                                    get(caps, i, Constants.NULL_RATE),
                                    get(floors, i, Constants.NULL_RATE),
                                    refStart,
                                    refEnd,
                                    paymentDayCounter,
                                    isInArrears);
                } catch (final Exception e) {
                    throw new LibraryException("Couldn't construct new instance from generic type:CappedFlooredCouponType");
                }
                add((CashFlow) cfctc);
            }
        }
    }


	//
	// public methods
	//
	
    public double get(
            final Array v,
            final int i,
            final double defaultValue) {
        if (v == null || v.empty())
            return defaultValue;
        else if (i < v.size())
            return v.get(i);
        else
            return v.get(v.size() - 1);
    }

    public /* @Rate */ double effectiveFixedRate(
            final Array spreads,
            final Array caps,
            final Array floors,
            final /* @Size */ int i) {
       
        double result = get(spreads, i, 0.0);
        final double floor = get(floors, i, Constants.NULL_RATE);
        if (floor != Constants.NULL_RATE ) {
            result = Math.max(floor, result);
        }
        final double cap = get(caps, i, Constants.NULL_RATE);
        if (cap != Constants.NULL_RATE ) {
            result = Math.min(cap, result);
        }
        return result;
    }

    public boolean noOption(final Array caps, 
                                   final Array floors, 
                                   final int /* @Size */ i) {
        return (get(caps, i, Constants.NULL_RATE) == Constants.NULL_RATE) && 
               (get(floors, i, Constants.NULL_REAL) == Constants.NULL_RATE);
    }

}
