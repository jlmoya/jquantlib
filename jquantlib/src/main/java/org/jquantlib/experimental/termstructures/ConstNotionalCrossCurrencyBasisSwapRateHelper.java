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
    \brief constant-notional cross-currency basis swap rate helper
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
 * Rate helper for bootstrapping over constant-notional cross-currency basis swaps.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/experimental/termstructures/crosscurrencyratehelpers.hpp}
 * {@code ConstNotionalCrossCurrencyBasisSwapRateHelper}.
 * <p>
 * Both notionals (base and quote currency) remain constant throughout the lifetime of the swap.
 */
public class ConstNotionalCrossCurrencyBasisSwapRateHelper extends CrossCurrencyBasisSwapRateHelperBase {

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Full constructor.
     *
     * @param basis                              quoted basis spread
     * @param tenor                              swap tenor
     * @param fixingDays                         fixing lag in days
     * @param calendar                           calendar
     * @param convention                         business-day convention
     * @param endOfMonth                         end-of-month flag
     * @param baseCurrencyIndex                  base-currency ibor index
     * @param quoteCurrencyIndex                 quote-currency ibor index
     * @param collateralCurve                    collateral discount curve
     * @param isFxBaseCurrencyCollateralCurrency true if FX base currency is collateral currency
     * @param isBasisOnFxBaseCurrencyLeg         true if basis is quoted on base-currency leg
     * @param paymentFrequency                   payment frequency (NoFrequency → use index tenor)
     * @param paymentLag                         payment lag in days (typically 0)
     */
    public ConstNotionalCrossCurrencyBasisSwapRateHelper(final Handle< org.jquantlib.quotes.Quote > basis,
            final Period tenor, final int fixingDays, final Calendar calendar, final BusinessDayConvention convention,
            final boolean endOfMonth, final IborIndex baseCurrencyIndex, final IborIndex quoteCurrencyIndex,
            final Handle< YieldTermStructure > collateralCurve, final boolean isFxBaseCurrencyCollateralCurrency,
            final boolean isBasisOnFxBaseCurrencyLeg, final Frequency paymentFrequency, final int paymentLag) {

        super(basis, tenor, fixingDays, calendar, convention, endOfMonth, baseCurrencyIndex, quoteCurrencyIndex,
                collateralCurve, isFxBaseCurrencyCollateralCurrency, isBasisOnFxBaseCurrencyLeg, paymentFrequency,
                paymentLag);
    }

    /**
     * Constructor with default payment frequency (NoFrequency) and zero payment lag.
     */
    public ConstNotionalCrossCurrencyBasisSwapRateHelper(final Handle< org.jquantlib.quotes.Quote > basis,
            final Period tenor, final int fixingDays, final Calendar calendar, final BusinessDayConvention convention,
            final boolean endOfMonth, final IborIndex baseCurrencyIndex, final IborIndex quoteCurrencyIndex,
            final Handle< YieldTermStructure > collateralCurve, final boolean isFxBaseCurrencyCollateralCurrency,
            final boolean isBasisOnFxBaseCurrencyLeg) {

        this(basis, tenor, fixingDays, calendar, convention, endOfMonth, baseCurrencyIndex, quoteCurrencyIndex,
                collateralCurve, isFxBaseCurrencyCollateralCurrency, isBasisOnFxBaseCurrencyLeg, Frequency.NoFrequency,
                0);
    }

    // -------------------------------------------------------------------------
    // BootstrapHelper interface
    // -------------------------------------------------------------------------

    @Override
    public double impliedQuote() {
        QL.require(termStructure != null, "term structure not set");
        QL.require(!collateralHandle_.empty(), "collateral term structure not set");

        final double[] baseResult = npvbpsConstNotionalLeg(baseCcyIborLeg_, initialNotionalExchangeDate_,
                finalNotionalExchangeDate_, baseCcyLegDiscountHandle());

        final double[] quoteResult = npvbpsConstNotionalLeg(quoteCcyIborLeg_, initialNotionalExchangeDate_,
                finalNotionalExchangeDate_, quoteCcyLegDiscountHandle());

        final double npvBase = baseResult[0];
        final double bpsBase = baseResult[1];
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
        final Visitor< ConstNotionalCrossCurrencyBasisSwapRateHelper > v = (pv != null)
                ? pv.visitor(this.getClass())
                : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
