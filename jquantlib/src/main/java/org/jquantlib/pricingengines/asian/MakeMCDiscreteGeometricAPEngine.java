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
 * Fluent builder for {@link MCDiscreteGeometricAPEngine}.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/asian/mc_discr_geom_av_price.hpp}
 * {@code MakeMCDiscreteGeometricAPEngine<RNG,S>} factory
 * (Phase 5e.5b-CFC-d-114).
 *
 * @author JQuantLib
 */
public class MakeMCDiscreteGeometricAPEngine {

    private final GeneralizedBlackScholesProcess process_;
    private boolean brownianBridge_ = true;
    private boolean antithetic_ = false;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;

    public MakeMCDiscreteGeometricAPEngine(final GeneralizedBlackScholesProcess process) {
        this.process_ = process;
    }

    public MakeMCDiscreteGeometricAPEngine withBrownianBridge(final boolean b) {
        this.brownianBridge_ = b;
        return this;
    }
    public MakeMCDiscreteGeometricAPEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }
    public MakeMCDiscreteGeometricAPEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }
    public MakeMCDiscreteGeometricAPEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }
    public MakeMCDiscreteGeometricAPEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "tolerance already set");
        this.samples_ = samples;
        return this;
    }
    public MakeMCDiscreteGeometricAPEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES, "number of samples already set");
        this.tolerance_ = tolerance;
        return this;
    }
    public MakeMCDiscreteGeometricAPEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }
    public MakeMCDiscreteGeometricAPEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    public PricingEngine value() {
        return new MCDiscreteGeometricAPEngine(
                process_, brownianBridge_, antithetic_,
                samples_, tolerance_, maxSamples_, seed_);
    }
}
