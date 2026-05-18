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

package org.jquantlib.pricingengines.lookback;

import org.jquantlib.QL;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Fluent builders for the four {@code MCContinuous*LookbackEngine}
 * variants.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/lookback/mclookbackengine.hpp}
 * {@code MakeMCLookbackEngine<I,RNG,S>} factory (Phase
 * 5e.5b-CFC-d-183). C++ instantiates the factory once per instrument
 * type; here we expose four flavour-suffixed builder classes (one per
 * supported {@code I}). Each builder mirrors the same named-parameter
 * API: {@code withSteps}, {@code withStepsPerYear},
 * {@code withBrownianBridge}, {@code withAntitheticVariate},
 * {@code withSamples}, {@code withAbsoluteTolerance},
 * {@code withMaxSamples}, {@code withSeed}.
 */
public final class MakeMCLookbackEngine {

    private MakeMCLookbackEngine() { /* static-only */ }

    public static FixedBuilder fixed(final GeneralizedBlackScholesProcess process) {
        return new FixedBuilder(process);
    }

    public static PartialFixedBuilder partialFixed(final GeneralizedBlackScholesProcess process) {
        return new PartialFixedBuilder(process);
    }

    public static FloatingBuilder floating(final GeneralizedBlackScholesProcess process) {
        return new FloatingBuilder(process);
    }

    public static PartialFloatingBuilder partialFloating(final GeneralizedBlackScholesProcess process) {
        return new PartialFloatingBuilder(process);
    }

    //
    // Shared bookkeeping for the named-parameter builders.
    //
    private abstract static class Base<B extends Base<B>> {
        protected final GeneralizedBlackScholesProcess process_;
        protected int steps_ = McSimulation.NULL_SAMPLES;
        protected int stepsPerYear_ = McSimulation.NULL_SAMPLES;
        protected boolean brownianBridge_ = false;
        protected boolean antithetic_ = false;
        protected int samples_ = McSimulation.NULL_SAMPLES;
        protected int maxSamples_ = McSimulation.NULL_SAMPLES;
        protected double tolerance_ = McSimulation.NULL_TOLERANCE;
        protected long seed_ = 0L;

        Base(final GeneralizedBlackScholesProcess process) { this.process_ = process; }

        @SuppressWarnings("unchecked")
        private B self() { return (B) this; }

        public B withSteps(final int steps) { this.steps_ = steps; return self(); }
        public B withStepsPerYear(final int steps) { this.stepsPerYear_ = steps; return self(); }
        public B withBrownianBridge(final boolean b) { this.brownianBridge_ = b; return self(); }
        public B withBrownianBridge() { return withBrownianBridge(true); }
        public B withAntitheticVariate(final boolean b) { this.antithetic_ = b; return self(); }
        public B withAntitheticVariate() { return withAntitheticVariate(true); }

        public B withSamples(final int samples) {
            QL.require(Double.isNaN(tolerance_), "tolerance already set");
            this.samples_ = samples;
            return self();
        }
        public B withAbsoluteTolerance(final double tolerance) {
            QL.require(samples_ == McSimulation.NULL_SAMPLES, "number of samples already set");
            this.tolerance_ = tolerance;
            return self();
        }
        public B withMaxSamples(final int samples) { this.maxSamples_ = samples; return self(); }
        public B withSeed(final long seed) { this.seed_ = seed; return self(); }

        protected void checkSteps() {
            QL.require(steps_ != McSimulation.NULL_SAMPLES || stepsPerYear_ != McSimulation.NULL_SAMPLES,
                    "number of steps not given");
            QL.require(steps_ == McSimulation.NULL_SAMPLES || stepsPerYear_ == McSimulation.NULL_SAMPLES,
                    "number of steps overspecified");
        }
    }

    public static final class FixedBuilder extends Base<FixedBuilder> {
        FixedBuilder(final GeneralizedBlackScholesProcess process) { super(process); }
        public PricingEngine value() {
            checkSteps();
            return new MCContinuousFixedLookbackEngine(
                    process_, steps_, stepsPerYear_, brownianBridge_, antithetic_,
                    samples_, tolerance_, maxSamples_, seed_);
        }
    }

    public static final class PartialFixedBuilder extends Base<PartialFixedBuilder> {
        PartialFixedBuilder(final GeneralizedBlackScholesProcess process) { super(process); }
        public PricingEngine value() {
            checkSteps();
            return new MCContinuousPartialFixedLookbackEngine(
                    process_, steps_, stepsPerYear_, brownianBridge_, antithetic_,
                    samples_, tolerance_, maxSamples_, seed_);
        }
    }

    public static final class FloatingBuilder extends Base<FloatingBuilder> {
        FloatingBuilder(final GeneralizedBlackScholesProcess process) { super(process); }
        public PricingEngine value() {
            checkSteps();
            return new MCContinuousFloatingLookbackEngine(
                    process_, steps_, stepsPerYear_, brownianBridge_, antithetic_,
                    samples_, tolerance_, maxSamples_, seed_);
        }
    }

    public static final class PartialFloatingBuilder extends Base<PartialFloatingBuilder> {
        PartialFloatingBuilder(final GeneralizedBlackScholesProcess process) { super(process); }
        public PricingEngine value() {
            checkSteps();
            return new MCContinuousPartialFloatingLookbackEngine(
                    process_, steps_, stepsPerYear_, brownianBridge_, antithetic_,
                    samples_, tolerance_, maxSamples_, seed_);
        }
    }
}
