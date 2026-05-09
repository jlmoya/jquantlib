/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2021 Marcin Rybacki
 Copyright (C) 2025 Uzair Beg

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

/*! \file crosscurrencyratehelpers.hpp/.cpp
    \brief mark-to-market cross-currency basis swap rate helper
*/

package org.jquantlib.experimental.termstructures;

import org.jquantlib.QL;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Rate helper for bootstrapping over mark-to-market cross-currency basis swaps.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/experimental/termstructures/crosscurrencyratehelpers.hpp}
 * {@code MtMCrossCurrencyBasisSwapRateHelper}.
 * <p>
 * At each interest payment the notional on the resetting leg is reset to
 * reflect changes in the FX rate, reducing counterparty and FX risk.
 */
public class MtMCrossCurrencyBasisSwapRateHelper
        extends CrossCurrencyBasisSwapRateHelperBase {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final boolean isFxBaseCurrencyLegResettable_;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Full constructor.
     *
     * @param basis                             quoted basis spread
     * @param tenor                             swap tenor
     * @param fixingDays                        fixing lag in days
     * @param calendar                          calendar
     * @param convention                        business-day convention
     * @param endOfMonth                        end-of-month flag
     * @param baseCurrencyIndex                 base-currency ibor index
     * @param quoteCurrencyIndex                quote-currency ibor index
     * @param collateralCurve                   collateral discount curve
     * @param isFxBaseCurrencyCollateralCurrency  collateral in base currency?
     * @param isBasisOnFxBaseCurrencyLeg        basis on base-currency leg?
     * @param isFxBaseCurrencyLegResettable     resetting notional on base-currency leg?
     * @param paymentFrequency                  payment frequency
     * @param paymentLag                        payment lag in days
     */
    public MtMCrossCurrencyBasisSwapRateHelper(
            final Handle<org.jquantlib.quotes.Quote> basis,
            final Period tenor,
            final int fixingDays,
            final Calendar calendar,
            final BusinessDayConvention convention,
            final boolean endOfMonth,
            final IborIndex baseCurrencyIndex,
            final IborIndex quoteCurrencyIndex,
            final Handle<YieldTermStructure> collateralCurve,
            final boolean isFxBaseCurrencyCollateralCurrency,
            final boolean isBasisOnFxBaseCurrencyLeg,
            final boolean isFxBaseCurrencyLegResettable,
            final Frequency paymentFrequency,
            final int paymentLag) {

        super(basis, tenor, fixingDays, calendar, convention, endOfMonth,
              baseCurrencyIndex, quoteCurrencyIndex, collateralCurve,
              isFxBaseCurrencyCollateralCurrency, isBasisOnFxBaseCurrencyLeg,
              paymentFrequency, paymentLag);

        isFxBaseCurrencyLegResettable_ = isFxBaseCurrencyLegResettable;
    }

    /**
     * Constructor with default payment frequency and zero payment lag.
     */
    public MtMCrossCurrencyBasisSwapRateHelper(
            final Handle<org.jquantlib.quotes.Quote> basis,
            final Period tenor,
            final int fixingDays,
            final Calendar calendar,
            final BusinessDayConvention convention,
            final boolean endOfMonth,
            final IborIndex baseCurrencyIndex,
            final IborIndex quoteCurrencyIndex,
            final Handle<YieldTermStructure> collateralCurve,
            final boolean isFxBaseCurrencyCollateralCurrency,
            final boolean isBasisOnFxBaseCurrencyLeg,
            final boolean isFxBaseCurrencyLegResettable) {

        this(basis, tenor, fixingDays, calendar, convention, endOfMonth,
             baseCurrencyIndex, quoteCurrencyIndex, collateralCurve,
             isFxBaseCurrencyCollateralCurrency, isBasisOnFxBaseCurrencyLeg,
             isFxBaseCurrencyLegResettable, Frequency.NoFrequency, 0);
    }

    // -------------------------------------------------------------------------
    // BootstrapHelper interface
    // -------------------------------------------------------------------------

    @Override
    public double impliedQuote() {
        QL.require(termStructure != null, "term structure not set");
        QL.require(!collateralHandle_.empty(), "collateral term structure not set");

        final double[] baseResult;
        final double[] quoteResult;

        if (isFxBaseCurrencyLegResettable_) {
            baseResult = npvbpsResettingLeg(
                    baseCcyIborLeg_, paymentLag_, calendar_, convention_,
                    baseCcyLegDiscountHandle(), quoteCcyLegDiscountHandle());
            quoteResult = npvbpsConstNotionalLeg(
                    quoteCcyIborLeg_,
                    initialNotionalExchangeDate_,
                    finalNotionalExchangeDate_,
                    quoteCcyLegDiscountHandle());
        } else {
            baseResult = npvbpsConstNotionalLeg(
                    baseCcyIborLeg_,
                    initialNotionalExchangeDate_,
                    finalNotionalExchangeDate_,
                    baseCcyLegDiscountHandle());
            quoteResult = npvbpsResettingLeg(
                    quoteCcyIborLeg_, paymentLag_, calendar_, convention_,
                    quoteCcyLegDiscountHandle(), baseCcyLegDiscountHandle());
        }

        final double npvBase  = baseResult[0];
        final double bpsBase  = baseResult[1];
        final double npvQuote = quoteResult[0];
        final double bpsQuote = quoteResult[1];

        final double bps = isBasisOnFxBaseCurrencyLeg_ ? -bpsBase : bpsQuote;
        QL.require(Math.abs(bps) > 0.0, "null BPS");

        return -(npvQuote - npvBase) / bps;
    }

    // -------------------------------------------------------------------------
    // PolymorphicVisitable
    // -------------------------------------------------------------------------

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<MtMCrossCurrencyBasisSwapRateHelper> v =
                (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
