/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2015 Thema Consulting SA
*/

package org.jquantlib.experimental.barrieroption;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;

/**
 * Quanto version of a double-barrier option.
 * <p>
 * Mirrors {@code QuantLib::QuantoDoubleBarrierOption} from
 * {@code ql/experimental/barrieroption/quantodoublebarrieroption.hpp} (v1.42.1).
 *
 * <p>The C++ class declares
 * {@code typedef QuantoOptionResults<DoubleBarrierOption::results> results;} which uses a class template not modelled
 * in JQuantLib (no {@code QuantoVanillaOption} either). We therefore inline the three quanto sensitivities directly
 * into a {@link QuantoResults} marker interface; engines wishing to populate them must provide a results object
 * implementing {@link QuantoResults}. This matches the C++ fetchResults dynamic_cast pattern.
 *
 * <p>No engine is shipped in this commit — Phase 4e ships only the
 * instrument layer for parity with the experimental barrieroption directory. A QuantoEngine could be ported when the
 * wider Quanto* family lands (Phase 4e.5 carry-forward).
 *
 * @author JQuantLib migration
 */
public class QuantoDoubleBarrierOption extends DoubleBarrierOption {

    //
    // mutable result fields (mirror C++ "mutable Real qvega_, qrho_, qlambda_;")
    //
    private double qvega_;
    private double qrho_;
    private double qlambda_;

    public QuantoDoubleBarrierOption(final DoubleBarrierType barrierType, final double barrier_lo,
            final double barrier_hi, final double rebate, final StrikedTypePayoff payoff, final Exercise exercise) {
        super(barrierType, barrier_lo, barrier_hi, rebate, payoff, exercise);
    }

    //
    // Quanto greeks
    //

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

    //
    // overrides
    //

    @Override
    public void setupExpired() {
        super.setupExpired();
        qvega_ = 0.0;
        qrho_ = 0.0;
        qlambda_ = 0.0;
    }

    @Override
    public void fetchResults(final PricingEngine.Results r) {
        super.fetchResults(r);
        QL.ensure(r instanceof QuantoResults, "no quanto results returned from pricing engine");
        final QuantoResults q = (QuantoResults) r;
        qrho_ = q.qrho();
        qvega_ = q.qvega();
        qlambda_ = q.qlambda();
    }

    /**
     * Marker interface that mirrors the public fields of the C++ {@code QuantoOptionResults<ResultsType>} template,
     * restricted to the quanto-specific sensitivities. A pricing-engine results object that implements this interface
     * unlocks {@link #qvega()}, {@link #qrho()} and {@link #qlambda()} on a {@code QuantoDoubleBarrierOption}
     * instance.
     */
    public interface QuantoResults {
        double qvega();

        double qrho();

        double qlambda();
    }
}
