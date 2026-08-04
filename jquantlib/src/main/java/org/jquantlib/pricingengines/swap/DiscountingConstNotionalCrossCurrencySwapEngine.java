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

/*
 Copyright (C) 2016 Quaternion Risk Management Ltd
 Copyright (C) 2025 Paolo D'Elia

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

package org.jquantlib.pricingengines.swap;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.currencies.Currency;
import org.jquantlib.instruments.ConstNotionalCrossCurrencySwap;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.util.Observer;

/**
 * Discounting engine for cross-currency swaps whose legs involve exactly two currencies. The NPV is expressed in
 * {@code domesticCcy}; the evaluation date is the reference date of either discount curve, which must agree.
 * <p>
 * Ported from C++ QuantLib v1.43 {@code ql/pricingengines/swap/discountingconstnotionalcrosscurrencyswapengine.{hpp,cpp}}
 * — new in that release.
 *
 * @author Jose Moya
 * @category pricingengines
 */
public class DiscountingConstNotionalCrossCurrencySwapEngine extends ConstNotionalCrossCurrencySwap.EngineImpl
        implements Observer {

    private final Currency domesticCcy;
    private final Handle< YieldTermStructure > domesticCcyDiscountcurve;
    private final Currency foreignCcy;
    private final Handle< YieldTermStructure > foreignCcyDiscountcurve;
    private final Handle< Quote > spotFX;
    private final Boolean includeSettlementDateFlows;
    private final Date settlementDate;
    private final Date npvDate;
    private final Date spotFXSettleDate;

    //
    // public constructors
    //

    /**
     * Convenience constructor: settlement, NPV and FX-settlement dates all default to the curve reference date, and
     * the settlement-date-flow policy is taken from {@link Settings}.
     */
    public DiscountingConstNotionalCrossCurrencySwapEngine(final Currency domesticCcy,
            final Handle< YieldTermStructure > domesticCcyDiscountCurve, final Currency foreignCcy,
            final Handle< YieldTermStructure > foreignCcyDiscountCurve, final Handle< Quote > spotFX) {
        this(domesticCcy, domesticCcyDiscountCurve, foreignCcy, foreignCcyDiscountCurve, spotFX, null, new Date(),
                new Date(), new Date());
    }

    /**
     * @param domesticCcy                domestic currency; the NPV is expressed in it
     * @param domesticCcyDiscountCurve   discount curve for cashflows in the domestic currency
     * @param foreignCcy                 foreign currency
     * @param foreignCcyDiscountCurve    discount curve for cashflows in the foreign currency
     * @param spotFX                     market spot rate, in units of {@code domesticCcy} per unit of
     *                                   {@code foreignCcy}, quoted for settlement on the NPV date
     * @param includeSettlementDateFlows when {@code null}, {@link Settings#isTodaysPayments()} decides whether
     *                                   cashflows falling on the settlement date count towards the NPV
     * @param settlementDate             cashflows before this date are dropped; a null date means the NPV date
     * @param npvDate                    date the NPV is discounted to; a null date means the evaluation date
     * @param spotFXSettleDate           date the FX conversion applies as of; a null date means the reference date
     */
    public DiscountingConstNotionalCrossCurrencySwapEngine(final Currency domesticCcy,
            final Handle< YieldTermStructure > domesticCcyDiscountCurve, final Currency foreignCcy,
            final Handle< YieldTermStructure > foreignCcyDiscountCurve, final Handle< Quote > spotFX,
            final Boolean includeSettlementDateFlows, final Date settlementDate, final Date npvDate,
            final Date spotFXSettleDate) {
        this.domesticCcy = domesticCcy;
        this.domesticCcyDiscountcurve = domesticCcyDiscountCurve;
        this.foreignCcy = foreignCcy;
        this.foreignCcyDiscountcurve = foreignCcyDiscountCurve;
        this.spotFX = spotFX;
        this.includeSettlementDateFlows = includeSettlementDateFlows;
        this.settlementDate = settlementDate;
        this.npvDate = npvDate;
        this.spotFXSettleDate = spotFXSettleDate;

        this.domesticCcyDiscountcurve.addObserver(this);
        this.foreignCcyDiscountcurve.addObserver(this);
        this.spotFX.addObserver(this);
    }

    //
    // public methods
    //

    public Handle< YieldTermStructure > domesticCcyDiscountCurve() {
        return domesticCcyDiscountcurve;
    }

    public Handle< YieldTermStructure > foreignCcyDiscountCurve() {
        return foreignCcyDiscountcurve;
    }

    public Currency domesticCurrency() {
        return domesticCcy;
    }

    public Currency foreignCurrency() {
        return foreignCcy;
    }

    public Handle< Quote > spotFX() {
        return spotFX;
    }

    //
    // overrides GenericEngine
    //

    @Override
    public void calculate() /* @ReadOnly */ {
        QL.require(!domesticCcyDiscountcurve.empty() && !foreignCcyDiscountcurve.empty(),
                "discounting term structure handle is empty");
        QL.require(!spotFX.empty(), "FX spot quote handle is empty");

        final YieldTermStructure domCurve = domesticCcyDiscountcurve.currentLink();
        final YieldTermStructure forCurve = foreignCcyDiscountcurve.currentLink();
        QL.require(domCurve.referenceDate().eq(forCurve.referenceDate()),
                "term structures should have the same reference date");

        final Date referenceDate = domCurve.referenceDate();

        final Date settlement;
        if ( settlementDate == null || settlementDate.isNull() ) {
            settlement = referenceDate;
        } else {
            QL.require(settlementDate.ge(referenceDate), "settlement date (" + settlementDate
                    + ") cannot be before discount curve reference date (" + referenceDate + ")");
            settlement = settlementDate;
        }

        final ConstNotionalCrossCurrencySwap.ArgumentsImpl a = (ConstNotionalCrossCurrencySwap.ArgumentsImpl) arguments_;
        final ConstNotionalCrossCurrencySwap.ResultsImpl r = (ConstNotionalCrossCurrencySwap.ResultsImpl) results_;

        final int numLegs = a.legs.size();

        if ( npvDate == null || npvDate.isNull() ) {
            r.valuationDate = referenceDate;
        } else {
            QL.require(npvDate.ge(referenceDate), "NPV date (" + npvDate
                    + ") cannot be before discount curve reference date (" + referenceDate + ")");
            r.valuationDate = npvDate;
        }

        final Date fxSettle;
        if ( spotFXSettleDate == null || spotFXSettleDate.isNull() ) {
            fxSettle = referenceDate;
        } else {
            QL.require(spotFXSettleDate.ge(referenceDate), "FX settlement date (" + spotFXSettleDate
                    + ") cannot be before discount curve reference date (" + referenceDate + ")");
            fxSettle = spotFXSettleDate;
        }

        r.value = 0.0;
        r.errorEstimate = Constants.NULL_REAL;
        r.legNPV = new double[numLegs];
        r.legBPS = new double[numLegs];
        r.startDiscounts = new double[numLegs];
        r.endDiscounts = new double[numLegs];
        r.inCcyLegNPV = new double[numLegs];
        r.inCcyLegBPS = new double[numLegs];
        r.npvDateDiscounts = new double[numLegs];

        final boolean includeReferenceDateFlows = includeSettlementDateFlows != null ? includeSettlementDateFlows
                : new Settings().isTodaysPayments();

        final CashFlows cf = CashFlows.getInstance();

        for ( int legNo = 0; legNo < numLegs; ++legNo ) {
            try {
                final YieldTermStructure legDiscountCurve;
                if ( a.currencies.get(legNo).equals(domesticCcy) ) {
                    legDiscountCurve = domCurve;
                } else {
                    QL.require(a.currencies.get(legNo).equals(foreignCcy),
                            "leg ccy (" + a.currencies.get(legNo) + ") must be domesticCcy (" + domesticCcy
                                    + ") or foreignCcy (" + foreignCcy + ")");
                    legDiscountCurve = forCurve;
                }

                final Leg leg = a.legs.get(legNo);

                r.npvDateDiscounts[legNo] = legDiscountCurve.discount(r.valuationDate);

                final double[] npvbps = CashFlows.npvbps(leg, legDiscountCurve, includeReferenceDateFlows, settlement,
                        r.valuationDate);
                r.inCcyLegNPV[legNo] = npvbps[0] * a.payer[legNo];
                r.inCcyLegBPS[legNo] = npvbps[1] * a.payer[legNo];

                r.legNPV[legNo] = r.inCcyLegNPV[legNo];
                r.legBPS[legNo] = r.inCcyLegBPS[legNo];

                if ( !a.currencies.get(legNo).equals(domesticCcy) ) {
                    double spotFXRate = spotFX.currentLink().value();
                    if ( !fxSettle.eq(referenceDate) ) {
                        final double domesticCcyDF = domCurve.discount(fxSettle);
                        final double foreignCcyDF = forCurve.discount(fxSettle);
                        QL.require(foreignCcyDF != 0.0, "discount factor associated with currency " + foreignCcy
                                + " at maturity " + fxSettle + " cannot be zero");
                        spotFXRate *= domesticCcyDF / foreignCcyDF;
                    }
                    r.legNPV[legNo] *= spotFXRate;
                    r.legBPS[legNo] *= spotFXRate;
                }

                final Date startDate = cf.startDate(leg);
                r.startDiscounts[legNo] = startDate.ge(referenceDate) ? legDiscountCurve.discount(startDate)
                        : Constants.NULL_REAL;

                final Date maturityDate = cf.maturityDate(leg);
                r.endDiscounts[legNo] = maturityDate.ge(referenceDate) ? legDiscountCurve.discount(maturityDate)
                        : Constants.NULL_REAL;
            } catch ( final Exception e ) {
                throw new LibraryException("leg #" + (legNo + 1) + ": " + e.getMessage(), e);
            }

            r.value += r.legNPV[legNo];
        }
    }
}
