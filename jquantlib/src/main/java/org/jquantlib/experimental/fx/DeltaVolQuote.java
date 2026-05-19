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
 Copyright (C) 2010 Dimitri Reiswich

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.experimental.fx;

import org.jquantlib.QL;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.util.Observer;

/**
 * Class for the quotation of delta vs vol.
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/quotes/deltavolquote.hpp}.
 *
 * <p>Includes the various delta quotation types in FX markets as well as ATM
 * types. The C++ class lives under {@code ql/quotes} in v1.42.1; JQuantLib keeps it under {@code experimental.fx} (no
 * behavioural difference — the package path documents that the class is part of the experimental FX tooling).
 *
 * @see org.jquantlib.pricingengines.BlackDeltaCalculator
 */
public class DeltaVolQuote extends Quote implements Observer {

    //
    // public static inner enums
    //

    private final double delta;
    private final Handle< Quote > vol;

    //
    // private fields
    //
    private final DeltaType deltaType;
    private final double maturity;
    private final AtmType atmType;
    /**
     * Standard constructor delta vs vol.
     */
    public DeltaVolQuote(final double delta, final Handle< Quote > vol, final double maturity,
            final DeltaType deltaType) {
        this.delta = delta;
        this.vol = vol;
        this.deltaType = deltaType;
        this.maturity = maturity;
        this.atmType = AtmType.AtmNull;

        this.vol.addObserver(this); // observe vol
    }
    /**
     * Additional constructor, if special atm quote is used.
     */
    public DeltaVolQuote(final Handle< Quote > vol, final DeltaType deltaType, final double maturity,
            final AtmType atmType) {
        this.delta = 0.0; // not used when atmType != AtmNull
        this.vol = vol;
        this.deltaType = deltaType;
        this.maturity = maturity;
        this.atmType = atmType;

        this.vol.addObserver(this);
    }

    //
    // public constructors
    //

    public double delta() {
        return delta;
    }

    public double maturity() {
        return maturity;
    }

    //
    // public methods
    //

    public AtmType atmType() {
        return atmType;
    }

    public DeltaType deltaType() {
        return deltaType;
    }

    @Override
    public double value() /* @ReadOnly */ {
        QL.require(isValid(), "invalid DeltaVolQuote: vol is empty or invalid");
        return vol.currentLink().value();
    }

    @Override
    public boolean isValid() /* @ReadOnly */ {
        return !vol.empty() && vol.currentLink().isValid();
    }

    //
    // implements Quote
    //

    @Override
    public void update() {
        notifyObservers(); // let observers know, that something has changed
    }

    public enum DeltaType {
        Spot,        // Spot Delta, e.g. usual Black Scholes delta
        Fwd,         // Forward Delta
        PaSpot,      // Premium Adjusted Spot Delta
        PaFwd        // Premium Adjusted Forward Delta
    }

    //
    // implements Observer
    //

    public enum AtmType {
        AtmNull,         // Default, if not an atm quote
        AtmSpot,         // K=S_0
        AtmFwd,          // K=F
        AtmDeltaNeutral, // Call Delta = Put Delta
        AtmVegaMax,      // K such that Vega is Maximum
        AtmGammaMax,     // K such that Gamma is Maximum
        AtmPutCall50     // K such that Call Delta=0.50 (only for Fwd Delta)
    }
}
