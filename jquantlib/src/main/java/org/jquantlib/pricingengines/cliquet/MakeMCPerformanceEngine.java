/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2005, 2008 StatPro Italia srl
*/

package org.jquantlib.pricingengines.cliquet;

import org.jquantlib.QL;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Fluent builder for {@link MCPerformanceEngine}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code MakeMCPerformanceEngine}
 * (ql/pricingengines/cliquet/mcperformanceengine.hpp). Phase 2 L3-D port. Note: MCPerformanceEngine is path-driven and
 * does not take explicit time steps (the option's own reset dates determine the time grid).
 *
 * @see MCPerformanceEngine
 */
public class MakeMCPerformanceEngine {

    private final GeneralizedBlackScholesProcess process_;
    private boolean brownianBridge_ = false;
    private boolean antithetic_ = false;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;

    public MakeMCPerformanceEngine(final GeneralizedBlackScholesProcess process) {
        QL.require(process != null, "null GBS process");
        this.process_ = process;
    }

    public MakeMCPerformanceEngine withBrownianBridge(final boolean b) {
        this.brownianBridge_ = b;
        return this;
    }

    public MakeMCPerformanceEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCPerformanceEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    public MakeMCPerformanceEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCPerformanceEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "number of samples and absolute tolerance are mutually exclusive");
        this.samples_ = samples;
        return this;
    }

    public MakeMCPerformanceEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES,
                "number of samples and absolute tolerance are mutually exclusive");
        this.tolerance_ = tolerance;
        return this;
    }

    public MakeMCPerformanceEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    public MakeMCPerformanceEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    public PricingEngine value() {
        return new MCPerformanceEngine(process_, brownianBridge_, antithetic_, samples_, tolerance_, maxSamples_,
                seed_);
    }
}
