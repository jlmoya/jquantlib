/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2006 Klaus Spanderen
 Copyright (C) 2007 StatPro Italia srl
*/

package org.jquantlib.pricingengines.basket;

import org.jquantlib.QL;
import org.jquantlib.methods.montecarlo.LsmBasisSystem;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.model.shortrate.StochasticProcessArray;

/**
 * Fluent builder for {@link MCAmericanBasketEngine}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code MakeMCAmericanBasketEngine}
 * (ql/pricingengines/basket/mcamericanbasketengine.hpp). C++ defaults: {@code polynomialOrder=2},
 * {@code polynomialType=LsmBasisSystem::Monomial}, calibrationSamples is forwarded to the engine.
 *
 * @see MCAmericanBasketEngine
 */
public class MakeMCAmericanBasketEngine {

    private final StochasticProcessArray process_;
    private boolean brownianBridge_ = false;
    private boolean antithetic_ = false;
    private int steps_ = McSimulation.NULL_SAMPLES;
    private int stepsPerYear_ = McSimulation.NULL_SAMPLES;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private int calibrationSamples_ = McSimulation.NULL_SAMPLES;
    private int polynomialOrder_ = 2;
    private LsmBasisSystem.PolynomialType polynomialType_ = LsmBasisSystem.PolynomialType.Monomial;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;

    public MakeMCAmericanBasketEngine(final StochasticProcessArray process) {
        QL.require(process != null, "null process array");
        this.process_ = process;
    }

    public MakeMCAmericanBasketEngine withSteps(final int steps) {
        QL.require(stepsPerYear_ == McSimulation.NULL_SAMPLES, "number of steps per year already set");
        this.steps_ = steps;
        return this;
    }

    public MakeMCAmericanBasketEngine withStepsPerYear(final int steps) {
        QL.require(steps_ == McSimulation.NULL_SAMPLES, "number of steps already set");
        this.stepsPerYear_ = steps;
        return this;
    }

    public MakeMCAmericanBasketEngine withBrownianBridge(final boolean b) {
        this.brownianBridge_ = b;
        return this;
    }

    public MakeMCAmericanBasketEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCAmericanBasketEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    public MakeMCAmericanBasketEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCAmericanBasketEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "number of samples and absolute tolerance are mutually exclusive");
        this.samples_ = samples;
        return this;
    }

    public MakeMCAmericanBasketEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES,
                "number of samples and absolute tolerance are mutually exclusive");
        this.tolerance_ = tolerance;
        return this;
    }

    public MakeMCAmericanBasketEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    public MakeMCAmericanBasketEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    public MakeMCAmericanBasketEngine withCalibrationSamples(final int samples) {
        this.calibrationSamples_ = samples;
        return this;
    }

    public MakeMCAmericanBasketEngine withPolynomialOrder(final int polynomialOrder) {
        this.polynomialOrder_ = polynomialOrder;
        return this;
    }

    public MakeMCAmericanBasketEngine withBasisSystem(final LsmBasisSystem.PolynomialType polynomialType) {
        this.polynomialType_ = polynomialType;
        return this;
    }

    public PricingEngine value() {
        QL.require(steps_ != McSimulation.NULL_SAMPLES || stepsPerYear_ != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        return new MCAmericanBasketEngine(process_, steps_, stepsPerYear_, brownianBridge_, antithetic_, samples_,
                tolerance_, maxSamples_, seed_, calibrationSamples_, polynomialOrder_, polynomialType_);
    }
}
