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
package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

/**
 * Quanto version of a forward vanilla option.
 *
 * <p>Phase 5i.5-MGR port of {@code QuantLib::QuantoForwardVanillaOption}
 * (v1.42.1 ql/instruments/quantoforwardvanillaoption.{hpp,cpp}).
 */
public class QuantoForwardVanillaOption extends ForwardVanillaOption {

    private double qvega_;
    private double qrho_;
    private double qlambda_;

    public QuantoForwardVanillaOption(final double moneyness, final Date resetDate, final StrikedTypePayoff payoff,
            final Exercise exercise) {
        super(moneyness, resetDate, payoff, exercise);
    }

    public double qvega() {
        calculate();
        QL.require(qvega_ != Constants.NULL_REAL, "exchange rate vega calculation failed");
        return qvega_;
    }

    public double qrho() {
        calculate();
        QL.require(qrho_ != Constants.NULL_REAL, "foreign interest rate rho calculation failed");
        return qrho_;
    }

    public double qlambda() {
        calculate();
        QL.require(qlambda_ != Constants.NULL_REAL, "quanto correlation sensitivity calculation failed");
        return qlambda_;
    }

    @Override
    public void setupExpired() {
        super.setupExpired();
        qvega_ = qrho_ = qlambda_ = 0.0;
    }

    @Override
    public void fetchResults(final PricingEngine.Results r) {
        super.fetchResults(r);
        QL.require(r instanceof QuantoForwardVanillaOption.ResultsImpl,
                "no quanto results returned from pricing engine");
        final QuantoForwardVanillaOption.ResultsImpl qr = (QuantoForwardVanillaOption.ResultsImpl) r;
        qrho_ = qr.qrho;
        qvega_ = qr.qvega;
        qlambda_ = qr.qlambda;
    }

    //
    // Inner types
    //

    public interface Results extends ForwardVanillaOption.Results { /* marker */
    }

    /**
     * Quanto-augmented forward results (delta1/qvega/qrho/qlambda overlays).
     */
    public static class ResultsImpl extends ForwardVanillaOption.ResultsImpl
            implements QuantoForwardVanillaOption.Results {

        public double qvega = Constants.NULL_REAL;
        public double qrho = Constants.NULL_REAL;
        public double qlambda = Constants.NULL_REAL;

        @Override
        public void reset() {
            super.reset();
            qvega = qrho = qlambda = Constants.NULL_REAL;
        }
    }

    /**
     * Engine base class for quanto forward vanilla options.
     */
    public abstract static class EngineImpl
            extends GenericEngine< ForwardVanillaOption.Arguments, QuantoForwardVanillaOption.Results > {
        public EngineImpl() {
            super(new ForwardVanillaOption.ArgumentsImpl(), new QuantoForwardVanillaOption.ResultsImpl());
        }
    }
}
