/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2020 Lew Wei Hao

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.experimental.barrieroption;

import org.jquantlib.QL;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Fluent builder for {@link MCDoubleBarrierEngine}.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/experimental/barrieroption/mcdoublebarrierengine.hpp} {@code MakeMCDoubleBarrierEngine<RNG,S>} factory (Phase
 * 5e.5b-CFC-d-278). Specialised for {@code RNG = PseudoRandom} — see {@link MCDoubleBarrierEngine} for that
 * limitation.
 */
public final class MakeMCDoubleBarrierEngine {

    private final GeneralizedBlackScholesProcess process_;
    private int steps_ = McSimulation.NULL_SAMPLES;
    private int stepsPerYear_ = McSimulation.NULL_SAMPLES;
    private boolean brownianBridge_ = false;
    private boolean antithetic_ = false;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;

    public MakeMCDoubleBarrierEngine(final GeneralizedBlackScholesProcess process) {
        this.process_ = process;
    }

    public MakeMCDoubleBarrierEngine withSteps(final int steps) {
        this.steps_ = steps;
        return this;
    }

    public MakeMCDoubleBarrierEngine withStepsPerYear(final int steps) {
        this.stepsPerYear_ = steps;
        return this;
    }

    public MakeMCDoubleBarrierEngine withBrownianBridge(final boolean b) {
        this.brownianBridge_ = b;
        return this;
    }

    public MakeMCDoubleBarrierEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCDoubleBarrierEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    public MakeMCDoubleBarrierEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCDoubleBarrierEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "tolerance already set");
        this.samples_ = samples;
        return this;
    }

    public MakeMCDoubleBarrierEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES, "number of samples already set");
        this.tolerance_ = tolerance;
        return this;
    }

    public MakeMCDoubleBarrierEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    public MakeMCDoubleBarrierEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    public PricingEngine value() {
        QL.require(steps_ != McSimulation.NULL_SAMPLES || stepsPerYear_ != McSimulation.NULL_SAMPLES,
                "number of steps not given");
        QL.require(steps_ == McSimulation.NULL_SAMPLES || stepsPerYear_ == McSimulation.NULL_SAMPLES,
                "number of steps overspecified");
        return new MCDoubleBarrierEngine(process_, steps_, stepsPerYear_, brownianBridge_, antithetic_, samples_,
                tolerance_, maxSamples_, seed_);
    }
}
