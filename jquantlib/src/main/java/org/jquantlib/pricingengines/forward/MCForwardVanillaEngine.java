/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2020 Jack Gillett
*/

package org.jquantlib.pricingengines.forward;

import org.jquantlib.QL;
import org.jquantlib.processes.StochasticProcess;

/**
 * Common base for Monte Carlo engines for forward-starting vanilla options.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/forward/mcforwardvanillaengine.hpp}
 * {@code MCForwardVanillaEngine<MC,RNG,S>} (Phase 2 L3-D). The C++ class is a {@code GenericEngine + McSimulation}
 * mixin parameterised by a path-variate trait, an RNG trait, and a Statistics accumulator; Java cannot express the
 * mixin pattern directly, so this base captures only the shared knobs ({@code timeSteps/timeStepsPerYear},
 * {@code brownianBridge}, {@code antitheticVariate}, {@code controlVariate}, sample budget, {@code seed}) and exposes
 * them as protected fields for the concrete subclasses {@link MCForwardEuropeanBSEngine} and
 * {@link MCForwardEuropeanHestonEngine}.
 *
 * <p>The two concrete subclasses are <strong>not</strong> wired through this
 * base today — they carry their own copies of the same fields (a Phase 2 L3-D port deferral). The base exists primarily
 * to surface the C++ class identity so call sites referencing {@code MCForwardVanillaEngine} resolve, and to host
 * shared utility helpers (TimeGrid construction over the reset+exercise pair) as they get factored out.
 */
public abstract class MCForwardVanillaEngine {

    protected final StochasticProcess process_;
    protected final int timeSteps_;
    protected final int timeStepsPerYear_;
    protected final boolean brownianBridge_;
    protected final boolean antitheticVariate_;
    protected final boolean controlVariate_;
    protected final int requiredSamples_;
    protected final int maxSamples_;
    protected final double requiredTolerance_;
    protected final long seed_;

    protected MCForwardVanillaEngine(final StochasticProcess process, final int timeSteps, final int timeStepsPerYear,
            final boolean brownianBridge, final boolean antitheticVariate, final int requiredSamples,
            final double requiredTolerance, final int maxSamples, final long seed, final boolean controlVariate) {
        QL.require(process != null, "null process");
        this.process_ = process;
        this.timeSteps_ = timeSteps;
        this.timeStepsPerYear_ = timeStepsPerYear;
        this.brownianBridge_ = brownianBridge;
        this.antitheticVariate_ = antitheticVariate;
        this.requiredSamples_ = requiredSamples;
        this.maxSamples_ = maxSamples;
        this.requiredTolerance_ = requiredTolerance;
        this.controlVariate_ = controlVariate;
        this.seed_ = seed;
    }
}
