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
 * Fluent builder for {@link MCDiscreteArithmeticASEngine}.
 *
 * <p>Java port of {@code MakeMCDiscreteArithmeticASEngine} from
 * {@code QuantLib v1.42.1 ql/pricingengines/asian/mc_discr_arith_av_strike.hpp} (Phase 5e.5b-CFC-d-243).
 *
 * @author JQuantLib
 */
public class MakeMCDiscreteArithmeticASEngine {

    private final GeneralizedBlackScholesProcess process_;
    private boolean brownianBridge_ = true;
    private boolean antithetic_ = false;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;

    public MakeMCDiscreteArithmeticASEngine(final GeneralizedBlackScholesProcess process) {
        this.process_ = process;
    }

    public MakeMCDiscreteArithmeticASEngine withBrownianBridge(final boolean b) {
        this.brownianBridge_ = b;
        return this;
    }

    public MakeMCDiscreteArithmeticASEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCDiscreteArithmeticASEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    public MakeMCDiscreteArithmeticASEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCDiscreteArithmeticASEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "tolerance already set");
        this.samples_ = samples;
        return this;
    }

    public MakeMCDiscreteArithmeticASEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES, "number of samples already set");
        this.tolerance_ = tolerance;
        return this;
    }

    public MakeMCDiscreteArithmeticASEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    public MakeMCDiscreteArithmeticASEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    public PricingEngine value() {
        return new MCDiscreteArithmeticASEngine(process_, brownianBridge_, antithetic_, samples_, tolerance_,
                maxSamples_, seed_);
    }
}
