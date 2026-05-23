/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2003 RiskMap srl
 Copyright (C) 2007 StatPro Italia srl
*/

package org.jquantlib.pricingengines.asian;

/**
 * Marker exception thrown by Asian-option MC engines when all averaging fixings have already happened (past fixings
 * only), so the engine returns the deterministic past-fixings-only NPV without running the simulation.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/asian/mcdiscreteasianenginebase.hpp}
 * {@code namespace detail { class PastFixingsOnly : public Error { ... } }}. The Java port surfaces a top-level alias
 * matching the C++ public class identifier ({@code detail::PastFixingsOnly}) while reusing the existing nested
 * {@link MCDiscreteAveragingAsianEngineBase.PastFixingsOnlyException} implementation.
 */
public final class PastFixingsOnly extends MCDiscreteAveragingAsianEngineBase.PastFixingsOnlyException {
    private static final long serialVersionUID = 1L;

    public PastFixingsOnly() {
        super();
    }
}
