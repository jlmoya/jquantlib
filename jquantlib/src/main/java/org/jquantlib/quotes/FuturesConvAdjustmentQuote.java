/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2006 Giorgio Facchinetti

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.quotes;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.time.Date;
import org.jquantlib.time.IMM;
import org.jquantlib.util.Observer;

/**
 * Quote for the futures-convexity adjustment of an Ibor index.
 *
 * <p>Faithful port of QuantLib v1.42.1 {@code ql/quotes/futuresconvadjustmentquote.{hpp,cpp}}
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}). Returns the Hull-White
 * convexity bias evaluated at the futures expiry / index-maturity year-fractions via
 * {@link HullWhite#convexityBias}. Three underlying handles drive the quote:
 *
 * <ul>
 *   <li>{@code futuresQuote}  — futures price quote (e.g. 100 - market rate).</li>
 *   <li>{@code volatility}    — Hull-White volatility {@code sigma}.</li>
 *   <li>{@code meanReversion} — Hull-White mean-reversion {@code a}.</li>
 * </ul>
 *
 * <p>The quote registers as an {@link Observer} of each handle; {@link #update()}
 * invalidates the cached rate and notifies downstream observers.
 *
 * <p>The C++ {@code registerWith(Settings::instance().evaluationDate())} call is
 * intentionally omitted in this Java port; downstream consumers (rate helpers,
 * bootstrap loops) re-evaluate via their own evaluation-date observer chain.
 *
 * @see HullWhite#convexityBias(double, double, double, double, double)
 */
public class FuturesConvAdjustmentQuote extends Quote implements Observer {

    //
    // private fields
    //

    private final DayCounter dc_;
    private final Date futuresDate_;
    private final Date indexMaturityDate_;
    private final Handle< Quote > futuresQuote_;
    private final Handle< Quote > volatility_;
    private final Handle< Quote > meanReversion_;
    private double rate_ = Constants.NULL_REAL;

    //
    // public constructors
    //

    /**
     * Mirrors C++ ctor at {@code futuresconvadjustmentquote.cpp:27}.
     *
     * @param index         the underlying Ibor index — supplies the day-counter and the
     *                      index maturity date {@code index.maturityDate(futuresDate)}.
     * @param futuresDate   the futures expiry date (IMM date).
     * @param futuresQuote  the futures price handle.
     * @param volatility    the Hull-White volatility handle.
     * @param meanReversion the Hull-White mean-reversion handle.
     */
    public FuturesConvAdjustmentQuote(final IborIndex index, final Date futuresDate,
            final Handle< Quote > futuresQuote, final Handle< Quote > volatility,
            final Handle< Quote > meanReversion) {
        this.dc_ = index.dayCounter();
        this.futuresDate_ = futuresDate;
        this.indexMaturityDate_ = index.maturityDate(futuresDate);
        this.futuresQuote_ = futuresQuote;
        this.volatility_ = volatility;
        this.meanReversion_ = meanReversion;

        // Mirror C++ registerWith(...) — the handle's Link forwards notifications.
        futuresQuote_.addObserver(this);
        volatility_.addObserver(this);
        meanReversion_.addObserver(this);
    }

    /**
     * Convenience ctor — looks up the IMM date for {@code immCode} via {@link IMM#date(String)}.
     * Mirrors C++ ctor at {@code futuresconvadjustmentquote.cpp:42}.
     */
    public FuturesConvAdjustmentQuote(final IborIndex index, final String immCode,
            final Handle< Quote > futuresQuote, final Handle< Quote > volatility,
            final Handle< Quote > meanReversion) {
        this(index, IMM.date(immCode), futuresQuote, volatility, meanReversion);
    }

    //
    // Inspectors (mirror inline accessors in the C++ header)
    //

    public double futuresValue() {
        return futuresQuote_.currentLink().value();
    }

    public double volatility() {
        return volatility_.currentLink().value();
    }

    public double meanReversion() {
        return meanReversion_.currentLink().value();
    }

    public Date immDate() {
        return futuresDate_;
    }

    //
    // implements Quote
    //

    @Override
    public double value() {
        if ( rate_ == Constants.NULL_REAL ) {
            // C++ computes year-fractions relative to Settings::instance().evaluationDate().
            // Use the same anchor here.
            final Date settlementDate = new org.jquantlib.Settings().evaluationDate();
            final double startTime = dc_.yearFraction(settlementDate, futuresDate_);
            final double indexMaturity = dc_.yearFraction(settlementDate, indexMaturityDate_);
            rate_ = HullWhite.convexityBias(futuresQuote_.currentLink().value(), startTime,
                    indexMaturity, volatility_.currentLink().value(),
                    meanReversion_.currentLink().value());
        }
        return rate_;
    }

    @Override
    public boolean isValid() {
        return !futuresQuote_.empty() && !volatility_.empty() && !meanReversion_.empty()
                && futuresQuote_.currentLink().isValid() && volatility_.currentLink().isValid()
                && meanReversion_.currentLink().isValid();
    }

    //
    // implements Observer
    //

    @Override
    public void update() {
        rate_ = Constants.NULL_REAL;
        notifyObservers();
    }
}
