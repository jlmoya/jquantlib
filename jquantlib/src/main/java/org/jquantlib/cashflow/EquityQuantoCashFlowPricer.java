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
 Copyright (C) 2023 Marcin Rybacki

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.indexes.EquityIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Frequency;

/**
 * Quanto-corrected pricer for {@link EquityCashFlow}.
 *
 * <p>Mirrors C++ {@code EquityQuantoCashFlowPricer} at v1.42.1
 * ({@code ql/cashflows/equitycashflow.{hpp,cpp}}). The quanto-adjusted
 * forward index value is
 * <pre>
 *     I(T) = spot * exp((r_f - q - rho * sigma_eq * sigma_fx) * t)
 * </pre>
 * where {@code r_f} is the index's interest-rate curve (foreign — i.e. the
 * currency of the equity), {@code q} is the index's dividend curve (zero if
 * absent), {@code sigma_eq / sigma_fx} are the equity / FX implied vols at the
 * fixing date, and {@code rho} is the equity-FX correlation. The base
 * {@code I(0)} is a regular fixing on the index baseDate.
 *
 * <p>Java implementation note: rather than route through a
 * {@code QuantoTermStructure} + cloned {@code EquityIndex} (as the C++ does
 * literally — see {@code ql/cashflows/equitycashflow.cpp:116-134}), we inline
 * the closed-form expansion. The two paths are algebraically identical
 * (verified by symbolic expansion of {@code spot * P_quanto(T) / P_qccy(T)}
 * against {@code QuantoTermStructure::zeroYieldImpl} at
 * {@code ql/termstructures/yield/quantotermstructure.hpp:123-131}). The C++
 * {@code equitycashflow.cpp} test itself uses this closed-form formula as the
 * expected value, so the Java implementation matches the test exactly.
 *
 * @author JQuantLib migration team (Phase 5d.5-EQ)
 */
public class EquityQuantoCashFlowPricer extends EquityCashFlowPricer {

    private final Handle<YieldTermStructure> quantoCurrencyTermStructure_;
    private final Handle<BlackVolTermStructure> equityVolatility_;
    private final Handle<BlackVolTermStructure> fxVolatility_;
    private final Handle<? extends Quote> correlation_;

    //
    // public constructors
    //

    public EquityQuantoCashFlowPricer(
            final Handle<YieldTermStructure> quantoCurrencyTermStructure,
            final Handle<BlackVolTermStructure> equityVolatility,
            final Handle<BlackVolTermStructure> fxVolatility,
            final Handle<? extends Quote> correlation) {
        this.quantoCurrencyTermStructure_ = quantoCurrencyTermStructure;
        this.equityVolatility_ = equityVolatility;
        this.fxVolatility_ = fxVolatility;
        this.correlation_ = correlation;

        if (this.quantoCurrencyTermStructure_ != null
                && !this.quantoCurrencyTermStructure_.empty()) {
            this.quantoCurrencyTermStructure_.addObserver(this);
        }
        if (this.equityVolatility_ != null && !this.equityVolatility_.empty()) {
            this.equityVolatility_.addObserver(this);
        }
        if (this.fxVolatility_ != null && !this.fxVolatility_.empty()) {
            this.fxVolatility_.addObserver(this);
        }
        if (this.correlation_ != null && !this.correlation_.empty()) {
            this.correlation_.addObserver(this);
        }
    }

    //
    // implements EquityCashFlowPricer
    //

    @Override
    public void initialize(final EquityCashFlow cashFlow) {
        if (!(cashFlow.index() instanceof EquityIndex)) {
            QL.require(false, "Equity index required.");
        }
        index_ = (EquityIndex) cashFlow.index();
        baseDate_ = cashFlow.baseDate();
        fixingDate_ = cashFlow.fixingDate();
        QL.require(!fixingDate_.lt(baseDate_), "Fixing date cannot fall before base date.");
        growthOnlyPayoff_ = cashFlow.growthOnly();

        QL.require(quantoCurrencyTermStructure_ != null
                        && !quantoCurrencyTermStructure_.empty(),
                "Quanto currency term structure handle cannot be empty.");
        QL.require(equityVolatility_ != null && !equityVolatility_.empty(),
                "Equity volatility term structure handle cannot be empty.");
        QL.require(fxVolatility_ != null && !fxVolatility_.empty(),
                "FX volatility term structure handle cannot be empty.");
        QL.require(correlation_ != null && !correlation_.empty(),
                "Correlation handle cannot be empty.");

        QL.require(quantoCurrencyTermStructure_.currentLink().referenceDate()
                        .equals(equityVolatility_.currentLink().referenceDate())
                && equityVolatility_.currentLink().referenceDate()
                        .equals(fxVolatility_.currentLink().referenceDate()),
                "Quanto currency term structure, equity and FX volatility need to have "
                        + "the same reference date.");
    }

    @Override
    public double price() {
        // Equity-leg interest curve (the foreign rf relative to the quanto
        // currency). Mirrors the C++ pricer's use of
        // index_->equityInterestRateCurve() inside the QuantoTermStructure.
        final Handle<YieldTermStructure> rfHandle = index_.equityInterestRateCurve();
        QL.require(rfHandle != null && !rfHandle.empty(),
                "null interest rate term structure set to this instance of " + index_.name());
        final YieldTermStructure rfTS = rfHandle.currentLink();

        final double t = rfTS.timeFromReference(fixingDate_);
        final double rf = rfTS.zeroRate(t, Compounding.Continuous, Frequency.NoFrequency, true)
                .rate();

        // Dividend yield: zero if the index has no dividend curve.
        double q = 0.0;
        final Handle<YieldTermStructure> qHandle = index_.equityDividendCurve();
        if (qHandle != null && !qHandle.empty()) {
            q = qHandle.currentLink()
                    .zeroRate(t, Compounding.Continuous, Frequency.NoFrequency, true).rate();
        }

        // Strike for the equity vol surface = today's index forecast for the
        // fixing date (mirrors C++ pricer line 117).
        final double strike = index_.fixing(fixingDate_);
        final double eqVol = equityVolatility_.currentLink().blackVol(fixingDate_, strike, true);
        // FX vol struck at unity (ATM forex by convention) — mirrors C++ line
        // {@code fxVolatility_->blackVol(fixingDate_, 1.0)}.
        final double fxVol = fxVolatility_.currentLink().blackVol(fixingDate_, 1.0, true);
        final double rho = correlation_.currentLink().value();

        final double spot;
        if (index_.spot() != null && !index_.spot().empty()) {
            spot = index_.spot().currentLink().value();
        } else {
            // Fall back to today's pastFixing per EquityIndex.forecastFixing
            // semantics — but resolved by EquityIndex.fixing(today) when spot
            // is empty (which throws if neither is present).
            spot = index_.fixing(rfTS.referenceDate());
        }

        final double quantoForward =
                spot * Math.exp((rf - q - rho * eqVol * fxVol) * t);
        final double i0 = index_.fixing(baseDate_);

        if (growthOnlyPayoff_) {
            return quantoForward / i0 - 1.0;
        }
        return quantoForward / i0;
    }
}
