/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2002, 2003 Ferdinando Ametrano
 Copyright (C) 2007 StatPro Italia srl
*/

package org.jquantlib.pricingengines.quanto;

import org.jquantlib.QL;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Common base for quanto pricing engines.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/quanto/quantoengine.hpp} {@code QuantoEngine<Instr, Engine>} template (Phase 2 L3-D). The C++ class
 * is a {@code GenericEngine<typename Instr::arguments, QuantoOptionResults<typename Instr::results>>} parameterised by
 * an instrument type and a base engine class; Java cannot specialise on templates so this base captures only the shared
 * state ({@code process}, {@code foreignRiskFreeRate}, {@code exchangeRateVolatility}, {@code correlation}) and the
 * concrete subclasses implement {@code calculate()} via the chosen inner engine. See
 * {@link QuantoVanillaEngine}, {@link QuantoBarrierEngine}, {@link QuantoForwardVanillaEngine}.
 *
 * <p>For the time being only the simple {@link GeneralizedBlackScholesProcess}
 * (no Merton jumps) is supported — mirrors the C++ warning at the top of
 * {@code quantoengine.hpp}.
 */
public abstract class QuantoEngine {

    protected final GeneralizedBlackScholesProcess process_;
    protected final Handle< YieldTermStructure > foreignRiskFreeRate_;
    protected final Handle< BlackVolTermStructure > exchangeRateVolatility_;
    protected final Handle< ? extends Quote > correlation_;

    protected QuantoEngine(final GeneralizedBlackScholesProcess process,
            final Handle< YieldTermStructure > foreignRiskFreeRate,
            final Handle< BlackVolTermStructure > exchangeRateVolatility,
            final Handle< ? extends Quote > correlation) {
        QL.require(process != null, "null GBS process");
        QL.require(foreignRiskFreeRate != null, "null foreign risk-free rate handle");
        QL.require(exchangeRateVolatility != null, "null exchange-rate volatility handle");
        QL.require(correlation != null, "null correlation handle");
        this.process_ = process;
        this.foreignRiskFreeRate_ = foreignRiskFreeRate;
        this.exchangeRateVolatility_ = exchangeRateVolatility;
        this.correlation_ = correlation;
    }

    public GeneralizedBlackScholesProcess process() {
        return process_;
    }

    public Handle< YieldTermStructure > foreignRiskFreeRate() {
        return foreignRiskFreeRate_;
    }

    public Handle< BlackVolTermStructure > exchangeRateVolatility() {
        return exchangeRateVolatility_;
    }

    public Handle< ? extends Quote > correlation() {
        return correlation_;
    }
}
