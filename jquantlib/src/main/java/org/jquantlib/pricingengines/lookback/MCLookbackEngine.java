/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2020 Lew Wei Hao
*/

package org.jquantlib.pricingengines.lookback;

/**
 * Marker base for Monte Carlo lookback-option engines.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/lookback/mclookbackengine.hpp}
 * {@code MCLookbackEngine<I,RNG,S>} (Phase 2 L3-D). The C++ template instantiates once per supported instrument type
 * {@code I} ({@code ContinuousFixedLookbackOption}, {@code ContinuousPartialFixedLookbackOption},
 * {@code ContinuousFloatingLookbackOption}, {@code ContinuousPartialFloatingLookbackOption}); Java cannot specialise on
 * type, so each instantiation is represented by a dedicated subclass:
 *
 * <ul>
 *   <li>{@link MCContinuousFixedLookbackEngine}</li>
 *   <li>{@link MCContinuousPartialFixedLookbackEngine}</li>
 *   <li>{@link MCContinuousFloatingLookbackEngine}</li>
 *   <li>{@link MCContinuousPartialFloatingLookbackEngine}</li>
 * </ul>
 *
 * <p>This marker exists primarily so call sites referencing
 * {@code MCLookbackEngine} resolve — see also the C++-named factory {@link MakeMCLookbackEngine}.
 */
public abstract class MCLookbackEngine {
    /* marker only — see concrete subclasses */
}
