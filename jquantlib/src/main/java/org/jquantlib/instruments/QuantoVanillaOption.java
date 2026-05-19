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

/**
 * Quanto version of a vanilla option.
 *
 * <p>Phase 5i.5-MGR port of {@code QuantLib::QuantoVanillaOption}
 * (v1.42.1 ql/instruments/quantovanillaoption.{hpp,cpp}). The C++ template {@code QuantoOptionResults<ResultsType>} is
 * collapsed to a concrete Java class extending {@link OneAssetOption.ResultsImpl}.
 */
public class QuantoVanillaOption extends OneAssetOption {

    private double qvega_;
    private double qrho_;
    private double qlambda_;

    public QuantoVanillaOption(final StrikedTypePayoff payoff, final Exercise exercise) {
        super(payoff, exercise);
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
        QL.require(r instanceof QuantoVanillaOption.ResultsImpl, "no quanto results returned from pricing engine");
        final QuantoVanillaOption.ResultsImpl qr = (QuantoVanillaOption.ResultsImpl) r;
        qrho_ = qr.qrho;
        qvega_ = qr.qvega;
        qlambda_ = qr.qlambda;
    }

    //
    // Inner types
    //

    /** Marker — extra fields in {@link ResultsImpl}. */
    public interface Results extends OneAssetOption.Results { /* marker */
    }

    /**
     * Quanto-augmented results: extends OneAssetOption.ResultsImpl with qvega / qrho / qlambda quanto-specific Greeks.
     */
    public static class ResultsImpl extends OneAssetOption.ResultsImpl implements QuantoVanillaOption.Results {

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
     * Engine base class for Quanto vanilla options. Mirrors C++
     * {@code GenericEngine<QuantoVanillaOption::arguments, QuantoVanillaOption::results>}.
     */
    public abstract static class EngineImpl
            extends GenericEngine< OneAssetOption.Arguments, QuantoVanillaOption.Results > {

        public EngineImpl() {
            super(new OneAssetOption.ArgumentsImpl(), new QuantoVanillaOption.ResultsImpl());
        }
    }
}
