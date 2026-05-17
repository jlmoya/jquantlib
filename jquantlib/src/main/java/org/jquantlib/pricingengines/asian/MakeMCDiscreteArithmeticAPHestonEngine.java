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

package org.jquantlib.pricingengines.asian;

import org.jquantlib.QL;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.HestonProcess;

/**
 * Fluent builder for {@link MCDiscreteArithmeticAPHestonEngine}.
 *
 * @author JQuantLib
 */
public class MakeMCDiscreteArithmeticAPHestonEngine {

    private final HestonProcess process_;
    private boolean antithetic_ = false;
    private boolean controlVariate_ = false;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private int steps_ = McSimulation.NULL_SAMPLES;
    private int stepsPerYear_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;

    public MakeMCDiscreteArithmeticAPHestonEngine(final HestonProcess process) {
        this.process_ = process;
    }

    public MakeMCDiscreteArithmeticAPHestonEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }
    public MakeMCDiscreteArithmeticAPHestonEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }
    public MakeMCDiscreteArithmeticAPHestonEngine withControlVariate(final boolean b) {
        this.controlVariate_ = b;
        return this;
    }
    public MakeMCDiscreteArithmeticAPHestonEngine withControlVariate() {
        return withControlVariate(true);
    }
    public MakeMCDiscreteArithmeticAPHestonEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "tolerance already set");
        this.samples_ = samples;
        return this;
    }
    public MakeMCDiscreteArithmeticAPHestonEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES, "number of samples already set");
        this.tolerance_ = tolerance;
        return this;
    }
    public MakeMCDiscreteArithmeticAPHestonEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }
    public MakeMCDiscreteArithmeticAPHestonEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }
    public MakeMCDiscreteArithmeticAPHestonEngine withSteps(final int steps) {
        QL.require(stepsPerYear_ == McSimulation.NULL_SAMPLES,
                "number of steps per year already set");
        this.steps_ = steps;
        return this;
    }
    public MakeMCDiscreteArithmeticAPHestonEngine withStepsPerYear(final int steps) {
        QL.require(steps_ == McSimulation.NULL_SAMPLES,
                "number of steps already set");
        this.stepsPerYear_ = steps;
        return this;
    }

    public PricingEngine value() {
        return new MCDiscreteArithmeticAPHestonEngine(
                process_, antithetic_, controlVariate_,
                samples_, tolerance_, maxSamples_,
                seed_, steps_, stepsPerYear_);
    }
}
