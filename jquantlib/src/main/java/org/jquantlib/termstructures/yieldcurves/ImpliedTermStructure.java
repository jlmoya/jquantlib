/*
 Copyright (C) 2008 Richard Gomes

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

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.AbstractYieldTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * Implied term structure at a given date in the future.
 *
 * @note The given date will be the implied reference date.
 * @note This term structure will remain linked to the original structure, i.e., any changes in the latter will be
 * reflected in this structure as well.
 */
//TEST the correctness of the returned values is tested by checking them against numerical calculations.
//TEST observability against changes in the underlying term structure is checked.
public class ImpliedTermStructure< T extends YieldTermStructure > extends AbstractYieldTermStructure {

    private final Handle< T > originalCurve;

    /**
     * Cached discount factor of the original curve at this structure's reference date, and the corresponding time
     * offset. Invalidated in {@link #update()} — which fires whenever the original curve or the evaluation date
     * changes, the only two things that can move them. Added in C++ v1.43
     * ({@code ql/termstructures/yield/impliedtermstructure.hpp}); before that the pair was recomputed on every
     * discount call.
     */
    private /*@DiscountFactor*/ double refDf = Constants.NULL_REAL;
    private /*@Time*/ double refTime = Constants.NULL_REAL;

    public ImpliedTermStructure(final Handle< T > h, final Date referenceDate) {
        super(referenceDate);
        this.originalCurve = h;
        // v1.43: inherit the original curve's extrapolation setting, so an implied view of an extrapolating curve
        // does not silently reject the very dates the original accepts.
        if ( !this.originalCurve.empty() ) {
            if ( this.originalCurve.currentLink().allowsExtrapolation() ) {
                enableExtrapolation();
            } else {
                disableExtrapolation();
            }
        }
        this.originalCurve.addObserver(this);
    }

    @Override
    public void update() {
        refDf = Constants.NULL_REAL;
        refTime = Constants.NULL_REAL;
        if ( !originalCurve.empty() ) {
            if ( originalCurve.currentLink().allowsExtrapolation() ) {
                enableExtrapolation();
            } else {
                disableExtrapolation();
            }
        }
        super.update();
    }

    @Override
    public DayCounter dayCounter() /* @ReadOnly */ {
        return originalCurve.currentLink().dayCounter();
    }

    @Override
    public Calendar calendar() /* @ReadOnly */ {
        return originalCurve.currentLink().calendar();
    }

    @Override
    public /*@Natural*/ int settlementDays() /* @ReadOnly */ {
        return originalCurve.currentLink().settlementDays();
    }

    public Date maxDate() /* @ReadOnly */ {
        return originalCurve.currentLink().maxDate();
    }

    @Override
    protected /*@DiscountFactor*/ double discountImpl(final /*@Time*/ double t) /* @ReadOnly */ {
        /* t is relative to the current reference date
           and needs to be converted to the time relative
           to the reference date of the original curve */
        if ( refDf == Constants.NULL_REAL ) {
            final Date ref = referenceDate();
            refTime = dayCounter().yearFraction(originalCurve.currentLink().referenceDate(), ref);
            refDf = originalCurve.currentLink().discount(ref, true);
        }
        return originalCurve.currentLink().discount(t + refTime, true) / refDf;
    }

}
