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
 Copyright (C) 2005 Klaus Spanderen
*/
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.HestonProcess;

/**
 * Fluent builder for {@link MCEuropeanHestonEngine}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code MakeMCEuropeanHestonEngine}
 * (ql/pricingengines/vanilla/mceuropeanhestonengine.hpp). The C++ class
 * is a template parameterised by an {@code RNG} traits type (PseudoRandom
 * or LowDiscrepancy) and a {@code Statistics} accumulator; the underlying
 * Java {@link MCEuropeanHestonEngine} is specialised for the
 * Mersenne-Twister + InverseCumulativeNormal Pseudo-Random combination
 * only (see {@code MCEuropeanHestonEngine} JavaDoc for the rationale).
 *
 * <p>Phase 5e.5b-CFC-d-129 port.
 *
 * @see MCEuropeanHestonEngine
 */
public class MakeMCEuropeanHestonEngine {

    private final HestonProcess process_;
    private boolean antithetic_ = false;
    private int steps_ = McSimulation.NULL_SAMPLES;
    private int stepsPerYear_ = McSimulation.NULL_SAMPLES;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;

    public MakeMCEuropeanHestonEngine(final HestonProcess process) {
        QL.require(process != null, "null Heston process");
        this.process_ = process;
    }

    public MakeMCEuropeanHestonEngine withSteps(final int steps) {
        QL.require(stepsPerYear_ == McSimulation.NULL_SAMPLES,
                "number of steps per year already set");
        this.steps_ = steps;
        return this;
    }

    public MakeMCEuropeanHestonEngine withStepsPerYear(final int steps) {
        QL.require(steps_ == McSimulation.NULL_SAMPLES,
                "number of steps already set");
        this.stepsPerYear_ = steps;
        return this;
    }

    public MakeMCEuropeanHestonEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    public MakeMCEuropeanHestonEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCEuropeanHestonEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_),
                "number of samples and absolute tolerance are mutually exclusive");
        this.samples_ = samples;
        return this;
    }

    public MakeMCEuropeanHestonEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES,
                "number of samples and absolute tolerance are mutually exclusive");
        this.tolerance_ = tolerance;
        return this;
    }

    public MakeMCEuropeanHestonEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    public MakeMCEuropeanHestonEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    /**
     * Build the configured {@link MCEuropeanHestonEngine}. At least one of
     * {@link #withSteps(int)} / {@link #withStepsPerYear(int)} must have
     * been called.
     */
    public PricingEngine value() {
        QL.require(steps_ != McSimulation.NULL_SAMPLES
                || stepsPerYear_ != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        return new MCEuropeanHestonEngine(
                process_,
                steps_,
                stepsPerYear_,
                antithetic_,
                samples_,
                tolerance_,
                maxSamples_,
                seed_);
    }
}
