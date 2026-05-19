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
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Fluent builder for {@link MCDiscreteArithmeticAPEngine}.
 *
 * @author JQuantLib
 */
public class MakeMCDiscreteArithmeticAPEngine {

    private final GeneralizedBlackScholesProcess process_;
    private boolean brownianBridge_ = true;
    private boolean antithetic_ = false;
    private boolean controlVariate_ = false;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;

    public MakeMCDiscreteArithmeticAPEngine(final GeneralizedBlackScholesProcess process) {
        this.process_ = process;
    }

    public MakeMCDiscreteArithmeticAPEngine withBrownianBridge(final boolean b) {
        this.brownianBridge_ = b;
        return this;
    }

    public MakeMCDiscreteArithmeticAPEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCDiscreteArithmeticAPEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    public MakeMCDiscreteArithmeticAPEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCDiscreteArithmeticAPEngine withControlVariate(final boolean b) {
        this.controlVariate_ = b;
        return this;
    }

    public MakeMCDiscreteArithmeticAPEngine withControlVariate() {
        return withControlVariate(true);
    }

    public MakeMCDiscreteArithmeticAPEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "tolerance already set");
        this.samples_ = samples;
        return this;
    }

    public MakeMCDiscreteArithmeticAPEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES, "number of samples already set");
        this.tolerance_ = tolerance;
        return this;
    }

    public MakeMCDiscreteArithmeticAPEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    public MakeMCDiscreteArithmeticAPEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    public PricingEngine value() {
        return new MCDiscreteArithmeticAPEngine(process_, brownianBridge_, antithetic_, controlVariate_, samples_,
                tolerance_, maxSamples_, seed_);
    }
}
