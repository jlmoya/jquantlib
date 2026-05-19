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

package org.jquantlib.pricingengines.forward;

import org.jquantlib.QL;
import org.jquantlib.instruments.FxForward;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Discounting engine for FX Forwards.
 *
 * <p>The two legs of an FX forward are discounted using their respective
 * currency discount curves. The NPV (in source-currency terms) is:
 * <pre>
 *   NPV = +/- N_source * D_source(T,S) +/- N_target * D_target(T,S) / spotFx
 * </pre>
 * with the sign chosen by {@code paySourceCurrency}, where {@code D_*(T,S) = D_*(T) / D_*(S)} is the forward discount
 * factor from settlement {@code S} to maturity {@code T}.
 *
 * <p>The fair forward rate is:
 * <pre>
 *   F = spotFx * D_target(T,S) / D_source(T,S)
 * </pre>
 *
 * <p>Phase 5e.5b-CFC-d-69 port of C++ v1.42.1
 * {@code ql/pricingengines/forward/discountingfxforwardengine.hpp} and {@code discountingfxforwardengine.cpp}
 * (Copyright 2026 Chirag Desai).
 */
public class DiscountingFxForwardEngine extends FxForward.EngineImpl {

    private final Handle< YieldTermStructure > sourceCurrencyDiscountCurve_;
    private final Handle< YieldTermStructure > targetCurrencyDiscountCurve_;
    private final Handle< ? extends Quote > spotFx_;

    /**
     * @param sourceCurrencyDiscountCurve discount curve for source currency
     * @param targetCurrencyDiscountCurve discount curve for target currency
     * @param spotFx                      spot FX rate (target/source); one unit of source currency equals
     *                                    {@code spotFx} units of target currency
     */
    public DiscountingFxForwardEngine(final Handle< YieldTermStructure > sourceCurrencyDiscountCurve,
            final Handle< YieldTermStructure > targetCurrencyDiscountCurve, final Handle< ? extends Quote > spotFx) {
        super();
        this.sourceCurrencyDiscountCurve_ = sourceCurrencyDiscountCurve;
        this.targetCurrencyDiscountCurve_ = targetCurrencyDiscountCurve;
        this.spotFx_ = spotFx;

        if ( sourceCurrencyDiscountCurve_ != null )
            sourceCurrencyDiscountCurve_.addObserver(this);
        if ( targetCurrencyDiscountCurve_ != null )
            targetCurrencyDiscountCurve_.addObserver(this);
        if ( spotFx_ != null )
            spotFx_.addObserver(this);
    }

    // ── inspectors ──────────────────────────────────────────────────────

    public Handle< YieldTermStructure > sourceCurrencyDiscountCurve() {
        return sourceCurrencyDiscountCurve_;
    }

    public Handle< YieldTermStructure > targetCurrencyDiscountCurve() {
        return targetCurrencyDiscountCurve_;
    }

    public Handle< ? extends Quote > spotFx() {
        return spotFx_;
    }

    // ── calculate ───────────────────────────────────────────────────────

    @Override
    public void calculate() {
        QL.require(sourceCurrencyDiscountCurve_ != null && !sourceCurrencyDiscountCurve_.empty(),
                "source currency discount curve handle is empty");
        QL.require(targetCurrencyDiscountCurve_ != null && !targetCurrencyDiscountCurve_.empty(),
                "target currency discount curve handle is empty");
        QL.require(spotFx_ != null && !spotFx_.empty(), "spot FX quote handle is empty");

        final FxForward.ArgumentsImpl a = (FxForward.ArgumentsImpl) arguments_;
        final FxForward.ResultsImpl r = (FxForward.ResultsImpl) results_;

        r.value = 0.0;
        r.errorEstimate = Constants.NULL_REAL;

        final Date maturityDate = a.maturityDate;
        final Date settlementDate = a.settlementDate;

        final Date sourceRefDate = sourceCurrencyDiscountCurve_.currentLink().referenceDate();
        final Date targetRefDate = targetCurrencyDiscountCurve_.currentLink().referenceDate();
        QL.require(sourceRefDate.le(settlementDate), "source currency discount curve reference date (" + sourceRefDate
                + ") must be on or before settlement date (" + settlementDate + ")");
        QL.require(targetRefDate.le(settlementDate), "target currency discount curve reference date (" + targetRefDate
                + ") must be on or before settlement date (" + settlementDate + ")");

        final double spotFxRate = spotFx_.currentLink().value();
        QL.require(spotFxRate > 0.0, "spot FX rate must be positive");

        // Forward discount factors from settlement → maturity.
        final double dfSource = sourceCurrencyDiscountCurve_.currentLink().discount(maturityDate)
                / sourceCurrencyDiscountCurve_.currentLink().discount(settlementDate);
        final double dfTarget = targetCurrencyDiscountCurve_.currentLink().discount(maturityDate)
                / targetCurrencyDiscountCurve_.currentLink().discount(settlementDate);

        // Fair forward rate: F = S * dfTarget / dfSource (target/source).
        r.fairForwardRate = spotFxRate * dfTarget / dfSource;

        // Present values of each leg (in their own currency).
        final double pvSource = a.sourceNominal * dfSource;
        final double pvTarget = a.targetNominal * dfTarget;

        // Convert target-leg PV into source currency.
        final double pvTargetInSourceCurrency = pvTarget / spotFxRate;

        final double npvInSourceCurrency;
        if ( a.paySourceCurrency ) {
            // pay source, receive target
            npvInSourceCurrency = -pvSource + pvTargetInSourceCurrency;
        } else {
            // receive source, pay target
            npvInSourceCurrency = pvSource - pvTargetInSourceCurrency;
        }

        r.value = npvInSourceCurrency;
        r.npvSourceCurrency = npvInSourceCurrency;
        r.npvTargetCurrency = npvInSourceCurrency * spotFxRate;

        // Engine-only additional results — copied by FxForward.fetchResults.
        r.additionalResults().put("spotFx", Double.valueOf(spotFxRate));
        r.additionalResults().put("sourceCurrencyDiscountFactor", Double.valueOf(dfSource));
        r.additionalResults().put("targetCurrencyDiscountFactor", Double.valueOf(dfTarget));
        r.additionalResults().put("sourceCurrencyPV", Double.valueOf(pvSource));
        r.additionalResults().put("targetCurrencyPV", Double.valueOf(pvTarget));
    }
}
