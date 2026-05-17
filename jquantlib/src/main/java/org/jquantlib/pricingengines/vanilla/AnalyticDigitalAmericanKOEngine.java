/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Analytic pricing engine for American knock-out options with digital payoff.
 *
 * <p>Java port of QuantLib v1.42.1 {@code AnalyticDigitalAmericanKOEngine}.
 */
public class AnalyticDigitalAmericanKOEngine extends AnalyticDigitalAmericanEngine {

    public AnalyticDigitalAmericanKOEngine(final GeneralizedBlackScholesProcess process) {
        super(process);
    }

    @Override
    public boolean knock_in() {
        return false;
    }

}
