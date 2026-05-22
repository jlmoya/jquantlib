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
 Copyright (C) 2005 Toyin Akin
 Copyright (C) 2007, 2009 StatPro Italia srl
 Copyright (C) 2008 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.instruments.Bond;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.bond.DiscountingBondEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Generic bond helper for curve bootstrapping / fitting.
 * <p>
 * Java port of QuantLib v1.42.1 {@code BondHelper} in
 * {@code ql/termstructures/yield/bondhelpers.{hpp,cpp}} (Phase1-closure-A2-E-552-bondhelper).
 *
 * <p>Wraps any {@link Bond} as a rate-helper whose quote is the bond's clean
 * price (or dirty, depending on {@link PriceType}). The helper computes the
 * implied quote by re-pricing the bond off the term structure it has been
 * given via {@link #setTermStructure(YieldTermStructure)}.
 *
 * <p>This class is the basis for {@link FittedBondDiscountCurve}'s
 * bond-helper-driven fitting branch (the parametric branch goes through
 * {@code FittedBondDiscountCurve}'s no-fit constructors directly).
 *
 * <p>Warning: Setting a pricing engine on the wrapped bond from external code
 * will cause the bootstrap / fit to fail or to give wrong results. Discard
 * the bond after creating the helper, so the helper has sole ownership.
 */
public class BondHelper extends RateHelper {

    /** Bond price type passed to the helper (clean vs. dirty). */
    public enum PriceType {
        Clean, Dirty
    }

    private final RelinkableHandle< YieldTermStructure > termStructureHandle = new RelinkableHandle< YieldTermStructure >(
            null);

    protected final Bond bond_;
    protected final PriceType priceType_;

    /**
     * Default ctor (clean price).
     */
    public BondHelper(final Handle< Quote > price, final Bond bond) {
        this(price, bond, PriceType.Clean);
    }

    public BondHelper(final Handle< Quote > price, final Bond bond, final PriceType priceType) {
        super(price);
        QL.require(bond != null, "null bond");
        this.bond_ = bond;
        this.priceType_ = priceType;

        // Latest date == bond's last cashflow date (may be later than the
        // bond's maturity date because of payment-day adjustment).
        this.latestDate = bond.cashflows().last().date();
        // Earliest date == bond's next cashflow date (as of bond settlement).
        // QuantLib's C++ Bond exposes nextCashFlowDate(); Java does not, so
        // we replicate using CashFlows.nextCashFlow(...).
        final CashFlow next = CashFlows.getInstance().nextCashFlow(bond.cashflows(), bond.settlementDate());
        this.earliestDate = (next == null) ? bond.cashflows().last().date() : next.date();

        final PricingEngine bondEngine = new DiscountingBondEngine(this.termStructureHandle);
        this.bond_.setPricingEngine(bondEngine);
    }

    //
    // public inspectors
    //

    public Bond bond() {
        return bond_;
    }

    public PriceType priceType() {
        return priceType_;
    }

    /** Pillar date used by the fitter to determine maxDate. */
    public org.jquantlib.time.Date pillarDate() {
        return latestDate;
    }

    //
    // RateHelper interface
    //

    @Override
    public void setTermStructure(final YieldTermStructure t) {
        // do not set the relinkable handle as an observer — force
        // recalculation when needed (mirror C++ ratehelper behaviour).
        this.termStructureHandle.linkTo(t, false);
        super.setTermStructure(t);
    }

    @Override
    public double impliedQuote() {
        QL.require(this.termStructure != null, "term structure not set");
        this.bond_.recalculate();
        return switch (priceType_) {
            case Clean -> bond_.cleanPrice();
            case Dirty -> bond_.dirtyPrice();
            default -> throw new IllegalStateException("This price type isn't implemented.");
        };
    }
}
