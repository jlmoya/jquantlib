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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2026 Chirag Desai

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.instruments;

import java.util.HashMap;
import java.util.Map;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.currencies.Currency;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * FX Forward instrument.
 *
 * <p>This class represents a foreign exchange forward contract: an
 * agreement to exchange a specified amount of one currency for another
 * currency at a future date at a predetermined exchange rate.
 *
 * <p>Settlement conventions:
 * <ul>
 *   <li>Overnight (O/N): {@code settlementDays = 0}
 *   <li>TomNext (T/N): {@code settlementDays = 1}
 *   <li>SpotNext (S/N): {@code settlementDays = 2} (standard spot, default)
 * </ul>
 * The payment date is computed as the evaluation date plus
 * {@code settlementDays} business days according to the specified calendar.
 *
 * <p>The instrument can be valued using {@link
 * org.jquantlib.pricingengines.forward.DiscountingFxForwardEngine}, which
 * computes the NPV by discounting the source and target legs using their
 * respective yield curves.
 *
 * <p>Phase 5e.5b-CFC-d-69 port of C++ v1.42.1 {@code ql/instruments/fxforward.hpp}
 * and {@code fxforward.cpp} (Copyright 2026 Chirag Desai).
 */
public class FxForward extends Instrument {

    // ── private fields ──────────────────────────────────────────────────
    private final double sourceNominal_;
    private final Currency sourceCurrency_;
    private final double targetNominal_;
    private final Currency targetCurrency_;
    private final Date maturityDate_;
    private final boolean paySourceCurrency_;
    private final int settlementDays_;
    private final Calendar paymentCalendar_;

    private double fairForwardRate_ = Constants.NULL_REAL;
    private double npvSourceCurrency_ = Constants.NULL_REAL;
    private double npvTargetCurrency_ = Constants.NULL_REAL;

    /**
     * Cached snapshot of engine's additional-results map, copied during
     * {@link #fetchResults(PricingEngine.Results)}. Mirrors the C++
     * pattern where {@code Instrument::result<T>(name)} reads from
     * {@code additionalResults}.
     */
    private Map<String, Object> additionalResults_ = new HashMap<String, Object>();


    // ── constructors ────────────────────────────────────────────────────

    /**
     * Constructor for FX Forward using nominal amounts.
     *
     * @param sourceNominal     notional amount in source (domestic) currency
     * @param sourceCurrency    currency of {@code sourceNominal}
     * @param targetNominal     notional amount in target (foreign) currency
     * @param targetCurrency    currency of {@code targetNominal}
     * @param maturityDate      settlement date of the forward contract
     * @param paySourceCurrency if {@code true}, pay source currency and receive target;
     *                          if {@code false}, receive source and pay target
     * @param settlementDays    number of business days for payment settlement
     *                          (0=O/N, 1=T/N, 2=Spot)
     * @param paymentCalendar   calendar for computing payment date (empty → {@link NullCalendar})
     */
    public FxForward(final double sourceNominal,
                     final Currency sourceCurrency,
                     final double targetNominal,
                     final Currency targetCurrency,
                     final Date maturityDate,
                     final boolean paySourceCurrency,
                     final int settlementDays,
                     final Calendar paymentCalendar) {
        super();
        QL.require(sourceCurrency != null && !sourceCurrency.empty(),
                "source currency must not be empty");
        QL.require(targetCurrency != null && !targetCurrency.empty(),
                "target currency must not be empty");
        QL.require(!sourceCurrency.equals(targetCurrency),
                "source and target currencies must be different");
        QL.require(sourceNominal > 0.0, "source nominal must be positive");
        QL.require(targetNominal > 0.0, "target nominal must be positive");

        this.sourceNominal_   = sourceNominal;
        this.sourceCurrency_  = sourceCurrency;
        this.targetNominal_   = targetNominal;
        this.targetCurrency_  = targetCurrency;
        this.maturityDate_    = maturityDate;
        this.paySourceCurrency_ = paySourceCurrency;
        this.settlementDays_  = settlementDays;
        this.paymentCalendar_ = (paymentCalendar == null || paymentCalendar.empty())
                ? new NullCalendar()
                : paymentCalendar;
    }

    /**
     * Constructor for FX Forward using nominal amounts, default Spot (2-day)
     * settlement and a {@link NullCalendar}.
     */
    public FxForward(final double sourceNominal,
                     final Currency sourceCurrency,
                     final double targetNominal,
                     final Currency targetCurrency,
                     final Date maturityDate,
                     final boolean paySourceCurrency) {
        this(sourceNominal, sourceCurrency, targetNominal, targetCurrency,
             maturityDate, paySourceCurrency, 2, new NullCalendar());
    }

    /**
     * Constructor for FX Forward using nominal amounts and explicit
     * settlement days, with a default {@link NullCalendar}.
     */
    public FxForward(final double sourceNominal,
                     final Currency sourceCurrency,
                     final double targetNominal,
                     final Currency targetCurrency,
                     final Date maturityDate,
                     final boolean paySourceCurrency,
                     final int settlementDays) {
        this(sourceNominal, sourceCurrency, targetNominal, targetCurrency,
             maturityDate, paySourceCurrency, settlementDays, new NullCalendar());
    }

    /**
     * Constructor for FX Forward using exchange rate.
     *
     * @param sourceNominal     notional amount in source currency
     * @param sourceCurrency    currency of nominal amount
     * @param targetCurrency    currency to exchange into
     * @param forwardRate       forward exchange rate (target/source)
     * @param maturityDate      settlement date of the forward contract
     * @param paySourceCurrency direction flag
     * @param settlementDays    business days for payment settlement
     * @param paymentCalendar   calendar (empty → {@link NullCalendar})
     */
    public FxForward(final double sourceNominal,
                     final Currency sourceCurrency,
                     final Currency targetCurrency,
                     final double forwardRate,
                     final Date maturityDate,
                     final boolean paySourceCurrency,
                     final int settlementDays,
                     final Calendar paymentCalendar) {
        super();
        QL.require(sourceCurrency != null && !sourceCurrency.empty(),
                "source currency must not be empty");
        QL.require(targetCurrency != null && !targetCurrency.empty(),
                "target currency must not be empty");
        QL.require(!sourceCurrency.equals(targetCurrency),
                "source and target currencies must be different");
        QL.require(sourceNominal > 0.0, "source nominal must be positive");
        QL.require(forwardRate > 0.0, "forward rate must be positive");

        this.sourceNominal_   = sourceNominal;
        this.sourceCurrency_  = sourceCurrency;
        this.targetNominal_   = sourceNominal * forwardRate;
        this.targetCurrency_  = targetCurrency;
        this.maturityDate_    = maturityDate;
        this.paySourceCurrency_ = paySourceCurrency;
        this.settlementDays_  = settlementDays;
        this.paymentCalendar_ = (paymentCalendar == null || paymentCalendar.empty())
                ? new NullCalendar()
                : paymentCalendar;
    }

    /**
     * Constructor for FX Forward using exchange rate, default Spot (2-day)
     * settlement and a {@link NullCalendar}.
     */
    public FxForward(final double sourceNominal,
                     final Currency sourceCurrency,
                     final Currency targetCurrency,
                     final double forwardRate,
                     final Date maturityDate,
                     final boolean paySourceCurrency) {
        this(sourceNominal, sourceCurrency, targetCurrency, forwardRate,
             maturityDate, paySourceCurrency, 2, new NullCalendar());
    }


    // ── inspectors ──────────────────────────────────────────────────────

    public double sourceNominal() { return sourceNominal_; }
    public Currency sourceCurrency() { return sourceCurrency_; }
    public double targetNominal() { return targetNominal_; }
    public Currency targetCurrency() { return targetCurrency_; }
    public Date maturityDate() { return maturityDate_; }
    public boolean paySourceCurrency() { return paySourceCurrency_; }

    /** Contracted forward rate (target currency per unit of source currency). */
    public double forwardRate() { return targetNominal_ / sourceNominal_; }

    /** Number of settlement days (0=O/N, 1=T/N, 2=Spot). */
    public int settlementDays() { return settlementDays_; }

    /** Settlement calendar. */
    public Calendar settlementCalendar() { return paymentCalendar_; }

    /** Settlement date — evaluation date + {@code settlementDays} business days. */
    public Date settlementDate() {
        return paymentCalendar_.advance(
                new Settings().evaluationDate(), settlementDays_, TimeUnit.Days);
    }


    // ── Instrument interface ────────────────────────────────────────────

    @Override
    public boolean isExpired() {
        return maturityDate_.lt(new Settings().evaluationDate());
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        QL.require(args instanceof FxForward.ArgumentsImpl, "wrong argument type");
        final FxForward.ArgumentsImpl a = (FxForward.ArgumentsImpl) args;

        a.sourceNominal     = sourceNominal_;
        a.sourceCurrency    = sourceCurrency_;
        a.targetNominal     = targetNominal_;
        a.targetCurrency    = targetCurrency_;
        a.maturityDate      = maturityDate_;
        a.paySourceCurrency = paySourceCurrency_;
        a.settlementDate    = settlementDate();
    }

    @Override
    protected void fetchResults(final PricingEngine.Results r) {
        super.fetchResults(r);
        QL.require(r instanceof FxForward.ResultsImpl, "wrong result type");
        final FxForward.ResultsImpl results = (FxForward.ResultsImpl) r;

        fairForwardRate_   = results.fairForwardRate;
        npvSourceCurrency_ = results.npvSourceCurrency;
        npvTargetCurrency_ = results.npvTargetCurrency;

        // Snapshot additional results so callers can read them later.
        additionalResults_ = new HashMap<String, Object>(results.additionalResults());
    }


    // ── additional results ──────────────────────────────────────────────

    /** Fair forward rate (target/source) implied by the curves. */
    public double fairForwardRate() {
        calculate();
        QL.require(fairForwardRate_ != Constants.NULL_REAL,
                "fair forward rate not available");
        return fairForwardRate_;
    }

    /** NPV expressed in source-currency terms. */
    public double npvSourceCurrency() {
        calculate();
        QL.require(npvSourceCurrency_ != Constants.NULL_REAL,
                "NPV in source currency not available");
        return npvSourceCurrency_;
    }

    /** NPV expressed in target-currency terms. */
    public double npvTargetCurrency() {
        calculate();
        QL.require(npvTargetCurrency_ != Constants.NULL_REAL,
                "NPV in target currency not available");
        return npvTargetCurrency_;
    }

    /** Read-only access to the engine's additional-results snapshot. */
    public Map<String, Object> additionalResults() {
        calculate();
        return additionalResults_;
    }


    // ── inner classes ───────────────────────────────────────────────────

    /**
     * Arguments for the FX-forward pricing engine. Mirrors C++
     * {@code FxForward::arguments}.
     */
    public static class ArgumentsImpl implements Instrument.Arguments {
        public double   sourceNominal = Constants.NULL_REAL;
        public Currency sourceCurrency;
        public double   targetNominal = Constants.NULL_REAL;
        public Currency targetCurrency;
        public Date     maturityDate;
        public boolean  paySourceCurrency = true;
        public Date     settlementDate;

        @Override
        public void validate() {
            QL.require(sourceNominal != Constants.NULL_REAL, "source nominal not set");
            QL.require(targetNominal != Constants.NULL_REAL, "target nominal not set");
            QL.require(sourceCurrency != null && !sourceCurrency.empty(),
                    "source currency not set");
            QL.require(targetCurrency != null && !targetCurrency.empty(),
                    "target currency not set");
            QL.require(maturityDate != null && !maturityDate.isNull(),
                    "maturity date not set");
            QL.require(settlementDate != null && !settlementDate.isNull(),
                    "settlement date not set");
        }
    }

    /**
     * Results for the FX-forward pricing engine. Mirrors C++
     * {@code FxForward::results}.
     */
    public static class ResultsImpl extends Instrument.ResultsImpl
            implements Instrument.Results {

        public double fairForwardRate   = Constants.NULL_REAL;
        public double npvSourceCurrency = Constants.NULL_REAL;
        public double npvTargetCurrency = Constants.NULL_REAL;

        @Override
        public void reset() {
            super.reset();
            fairForwardRate   = Constants.NULL_REAL;
            npvSourceCurrency = Constants.NULL_REAL;
            npvTargetCurrency = Constants.NULL_REAL;
        }
    }

    /**
     * Base class for FX-forward pricing engines. Mirrors C++
     * {@code FxForward::engine = GenericEngine<arguments, results>}.
     */
    public abstract static class EngineImpl
            extends GenericEngine<Instrument.Arguments, Instrument.Results> {

        protected EngineImpl() {
            super(new FxForward.ArgumentsImpl(), new FxForward.ResultsImpl());
        }
    }
}
