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
 Copyright (C) 2008 Paul Farrington

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;

/**
 * Quanto version of a barrier option.
 *
 * <p>Phase 5e.5b-CFC-d-102 Java port of v1.42.1
 * {@code ql/instruments/quantobarrieroption.{hpp,cpp}}. The C++ template
 * {@code QuantoOptionResults<BarrierOption::results>} is collapsed to a concrete Java class extending
 * {@link BarrierOption.ResultsImpl}.
 *
 * <p>Provides three extra quanto Greeks on top of the standard barrier
 * sensitivities: {@link #qvega()}, {@link #qrho()}, {@link #qlambda()}.
 *
 * @category instruments
 */
public class QuantoBarrierOption extends BarrierOption {

    private double qvega_;
    private double qrho_;
    private double qlambda_;

    public QuantoBarrierOption(final BarrierType barrierType, final double barrier, final double rebate,
            final StrikedTypePayoff payoff, final Exercise exercise) {
        super(barrierType, barrier, rebate, payoff, exercise);
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
        QL.require(r instanceof QuantoBarrierOption.ResultsImpl,
                "no quanto barrier results returned from pricing engine");
        final QuantoBarrierOption.ResultsImpl qr = (QuantoBarrierOption.ResultsImpl) r;
        qrho_ = qr.qrho;
        qvega_ = qr.qvega;
        qlambda_ = qr.qlambda;
    }

    //
    // Inner types
    //

    /** Marker interface — extra fields in {@link ResultsImpl}. */
    public interface Results extends BarrierOption.Results { /* marker */
    }

    /**
     * Quanto-augmented barrier results: extends BarrierOption.ResultsImpl with qvega / qrho / qlambda quanto-specific
     * Greeks.
     */
    public static class ResultsImpl extends BarrierOption.ResultsImpl implements QuantoBarrierOption.Results {

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
     * Engine base class for Quanto barrier options. Mirrors C++
     * {@code GenericEngine<QuantoBarrierOption::arguments, QuantoBarrierOption::results>}.
     */
    public abstract static class EngineImpl
            extends GenericEngine< BarrierOption.Arguments, QuantoBarrierOption.Results > {

        public EngineImpl() {
            super(new BarrierOption.ArgumentsImpl(), new QuantoBarrierOption.ResultsImpl());
        }
    }
}
