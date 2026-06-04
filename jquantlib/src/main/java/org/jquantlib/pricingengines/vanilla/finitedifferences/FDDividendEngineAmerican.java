/*
 Copyright (C) 2026

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
package org.jquantlib.pricingengines.vanilla.finitedifferences;

import org.jquantlib.methods.finitedifferences.AmericanCondition;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Finite-difference dividend engine carrying an American early-exercise step condition.
 *
 * <p>This is the Java expression of the C++ idiom {@code FDAmericanCondition<FDDividendEngine>}: it <em>is</em> an
 * {@link FDDividendEngineMerton73} — so it inherits the full discrete-dividend handling
 * ({@link FDDividendEngineBase#setupArguments reads the dividend schedule}, and the grid is rescaled at each dividend
 * date) — and additionally installs an {@link AmericanCondition} as its step condition, so early exercise is enforced
 * at every finite-difference step (including between dividend dates).
 *
 * <p>The generic {@link FDAmericanCondition} wrapper cannot serve this purpose: Java cannot {@code extends T}, so that
 * class always extends the dividend-free {@link FDStepConditionEngine} and would discard the dividend engine entirely.
 */
public class FDDividendEngineAmerican extends FDDividendEngineMerton73 {

    public FDDividendEngineAmerican(final GeneralizedBlackScholesProcess process, final int timeSteps,
            final int gridPoints, final boolean timeDependent) {
        super(process, timeSteps, gridPoints, timeDependent);
    }

    @Override
    protected void initializeStepCondition() {
        stepCondition = new AmericanCondition(intrinsicValues.values());
    }
}
