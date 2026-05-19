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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2007 StatPro Italia srl
*/

package org.jquantlib.pricingengines;

import org.jquantlib.math.statistics.Statistics;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.time.TimeGrid;

/**
 * Base class for Monte Carlo engines.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/mcsimulation.hpp} (Phase 5h.5-MC-INFRA WI-6).
 *
 * <p>Deriving from {@code McSimulation} gives an easy way to write a
 * Monte Carlo engine: subclasses provide {@link #pathGenerator()}, {@link #pathPricer()} and {@link #timeGrid()}, and
 * the base class supplies {@code calculate}, {@code value}, {@code valueWithSamples}, and {@code errorEstimate}.
 *
 * <p>The C++ template parameters {@code MC} and {@code RNG} are erased
 * here (the path/pricer types are carried by the {@code PathType} generic parameter).
 *
 * @param <PathType> {@code Path} for single-asset MC, {@code MultiPath} for multi-asset MC.
 * @author JQuantLib
 */
public abstract class McSimulation< PathType > {

    //
    // sentinel values matching C++'s Null<Real>() / Null<Size>()
    //

    public static final double NULL_TOLERANCE = Double.NaN;
    public static final int NULL_SAMPLES = Integer.MAX_VALUE;

    //
    // protected fields
    //
    protected final boolean antitheticVariate_;
    protected final boolean controlVariate_;
    protected MonteCarloModel< PathType > mcModel_;

    //
    // constructors
    //

    protected McSimulation(final boolean antitheticVariate, final boolean controlVariate) {
        this.antitheticVariate_ = antitheticVariate;
        this.controlVariate_ = controlVariate;
    }

    //
    // hooks for subclasses
    //

    protected abstract PathPricer< PathType > pathPricer();

    protected abstract MonteCarloModel.PathGeneratorAdapter< PathType > pathGenerator();

    protected abstract TimeGrid timeGrid();

    /** Override in subclasses that supply a control-variate pricer. */
    protected PathPricer< PathType > controlPathPricer() {
        return null;
    }

    /** Override in subclasses that supply a separate CV path generator. */
    protected MonteCarloModel.PathGeneratorAdapter< PathType > controlPathGenerator() {
        return null;
    }

    /** Override in subclasses that wrap a control-variate pricing engine. */
    protected PricingEngine controlPricingEngine() {
        return null;
    }

    /** Override in subclasses that supply a control-variate value. */
    protected double controlVariateValue() {
        return Double.NaN;
    }

    //
    // public API
    //

    /**
     * Mirrors C++ {@code McSimulation::value(tolerance, maxSamples, minSamples)}: add samples until the required
     * absolute tolerance is reached.
     */
    public double value(final double tolerance, final int maxSamples, final int minSamples) {
        int sampleNumber = mcModel_.sampleAccumulator().samples();
        if ( sampleNumber < minSamples ) {
            mcModel_.addSamples(minSamples - sampleNumber);
            sampleNumber = mcModel_.sampleAccumulator().samples();
        }

        double error = mcModel_.sampleAccumulator().errorEstimate();
        while ( error > tolerance ) {
            if ( sampleNumber >= maxSamples ) {
                throw new RuntimeException("max number of samples (" + maxSamples + ") reached, while error (" + error
                        + ") is still above tolerance (" + tolerance + ")");
            }

            // Conservative estimate of how many additional samples are needed.
            final double order = (error * error) / (tolerance * tolerance);
            int nextBatch = (int) Math.max(sampleNumber * order * 0.8 - sampleNumber, minSamples);
            nextBatch = Math.min(nextBatch, maxSamples - sampleNumber);
            sampleNumber += nextBatch;
            mcModel_.addSamples(nextBatch);
            error = mcModel_.sampleAccumulator().errorEstimate();
        }
        return mcModel_.sampleAccumulator().mean();
    }

    public double value(final double tolerance, final int maxSamples) {
        return value(tolerance, maxSamples, 1023);
    }

    public double value(final double tolerance) {
        return value(tolerance, NULL_SAMPLES, 1023);
    }

    /**
     * Mirrors C++ {@code McSimulation::valueWithSamples(samples)}.
     */
    public double valueWithSamples(final int samples) {
        final int sampleNumber = mcModel_.sampleAccumulator().samples();
        if ( samples < sampleNumber ) {
            throw new IllegalArgumentException(
                    "number of already simulated samples (" + sampleNumber + ") greater than requested samples ("
                            + samples + ")");
        }
        mcModel_.addSamples(samples - sampleNumber);
        return mcModel_.sampleAccumulator().mean();
    }

    public double errorEstimate() {
        return mcModel_.sampleAccumulator().errorEstimate();
    }

    public Statistics sampleAccumulator() {
        return mcModel_.sampleAccumulator();
    }

    /**
     * Basic calculate method provided to inherited pricing engines. Mirrors C++
     * {@code McSimulation::calculate(requiredTolerance, requiredSamples, maxSamples)}.
     *
     * <p>Pass {@link #NULL_TOLERANCE} (NaN) or {@link #NULL_SAMPLES}
     * to indicate "not specified".
     */
    public void calculate(final double requiredTolerance, final int requiredSamples, final int maxSamples) {
        if ( Double.isNaN(requiredTolerance) && requiredSamples == NULL_SAMPLES ) {
            throw new IllegalArgumentException("neither tolerance nor number of samples set");
        }

        if ( controlVariate_ ) {
            final double cvValue = controlVariateValue();
            if ( Double.isNaN(cvValue) ) {
                throw new RuntimeException("engine does not provide control-variation price");
            }
            final PathPricer< PathType > controlPP = controlPathPricer();
            if ( controlPP == null ) {
                throw new RuntimeException("engine does not provide control-variation path pricer");
            }
            final MonteCarloModel.PathGeneratorAdapter< PathType > controlPG = controlPathGenerator();
            this.mcModel_ = new MonteCarloModel< PathType >(pathGenerator(), pathPricer(), new Statistics(),
                    antitheticVariate_, controlPP, cvValue, controlPG);
        } else {
            this.mcModel_ = new MonteCarloModel< PathType >(pathGenerator(), pathPricer(), new Statistics(),
                    antitheticVariate_);
        }

        if ( !Double.isNaN(requiredTolerance) ) {
            if ( maxSamples != NULL_SAMPLES ) {
                value(requiredTolerance, maxSamples);
            } else {
                value(requiredTolerance);
            }
        } else {
            valueWithSamples(requiredSamples);
        }
    }
}
