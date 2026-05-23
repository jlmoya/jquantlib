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
 Copyright (C) 2016 Peter Caspers
 Copyright (C) 2022 Jonghee Lee
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.methods.montecarlo.LsmBasisSystem;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Fluent builder for {@link MCAmericanEngine}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code MakeMCAmericanEngine}
 * (ql/pricingengines/vanilla/mcamericanengine.hpp). C++ defaults: {@code polynomialOrder = 2},
 * {@code polynomialType = LsmBasisSystem::Monomial}, {@code calibrationSamples = 2048}.
 *
 * @see MCAmericanEngine
 */
public class MakeMCAmericanEngine {

    private final GeneralizedBlackScholesProcess process_;
    private boolean antithetic_ = false;
    private boolean controlVariate_ = false;
    private int steps_ = McSimulation.NULL_SAMPLES;
    private int stepsPerYear_ = McSimulation.NULL_SAMPLES;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private int calibrationSamples_ = 2048;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;
    private int polynomialOrder_ = 2;
    private LsmBasisSystem.PolynomialType polynomialType_ = LsmBasisSystem.PolynomialType.Monomial;
    private Boolean antitheticCalibration_ = null;
    private long seedCalibration_ = 0L;

    public MakeMCAmericanEngine(final GeneralizedBlackScholesProcess process) {
        QL.require(process != null, "null GBS process");
        this.process_ = process;
    }

    public MakeMCAmericanEngine withSteps(final int steps) {
        QL.require(stepsPerYear_ == McSimulation.NULL_SAMPLES, "number of steps per year already set");
        this.steps_ = steps;
        return this;
    }

    public MakeMCAmericanEngine withStepsPerYear(final int steps) {
        QL.require(steps_ == McSimulation.NULL_SAMPLES, "number of steps already set");
        this.stepsPerYear_ = steps;
        return this;
    }

    public MakeMCAmericanEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    public MakeMCAmericanEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCAmericanEngine withControlVariate(final boolean b) {
        this.controlVariate_ = b;
        return this;
    }

    public MakeMCAmericanEngine withControlVariate() {
        return withControlVariate(true);
    }

    public MakeMCAmericanEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "number of samples and absolute tolerance are mutually exclusive");
        this.samples_ = samples;
        return this;
    }

    public MakeMCAmericanEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES,
                "number of samples and absolute tolerance are mutually exclusive");
        this.tolerance_ = tolerance;
        return this;
    }

    public MakeMCAmericanEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    public MakeMCAmericanEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    public MakeMCAmericanEngine withPolynomialOrder(final int polynomialOrder) {
        this.polynomialOrder_ = polynomialOrder;
        return this;
    }

    public MakeMCAmericanEngine withBasisSystem(final LsmBasisSystem.PolynomialType polynomialType) {
        this.polynomialType_ = polynomialType;
        return this;
    }

    public MakeMCAmericanEngine withCalibrationSamples(final int calibrationSamples) {
        this.calibrationSamples_ = calibrationSamples;
        return this;
    }

    public MakeMCAmericanEngine withAntitheticVariateCalibration(final boolean b) {
        this.antitheticCalibration_ = b;
        return this;
    }

    public MakeMCAmericanEngine withAntitheticVariateCalibration() {
        return withAntitheticVariateCalibration(true);
    }

    public MakeMCAmericanEngine withSeedCalibration(final long seed) {
        this.seedCalibration_ = seed;
        return this;
    }

    public PricingEngine value() {
        QL.require(steps_ != McSimulation.NULL_SAMPLES || stepsPerYear_ != McSimulation.NULL_SAMPLES,
                "number of steps not given");
        QL.require(steps_ == McSimulation.NULL_SAMPLES || stepsPerYear_ == McSimulation.NULL_SAMPLES,
                "number of steps overspecified");
        return new MCAmericanEngine(process_, steps_, stepsPerYear_, antithetic_, controlVariate_, samples_,
                tolerance_, maxSamples_, seed_, polynomialOrder_, polynomialType_, calibrationSamples_,
                antitheticCalibration_, seedCalibration_);
    }
}
