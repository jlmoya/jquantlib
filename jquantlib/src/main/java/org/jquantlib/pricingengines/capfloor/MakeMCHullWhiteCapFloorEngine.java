/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2006 Banca Profilo S.p.A.
 Copyright (C) 2006 StatPro Italia srl
*/

package org.jquantlib.pricingengines.capfloor;

import org.jquantlib.QL;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;

/**
 * Fluent builder for {@link MCHullWhiteCapFloorEngine}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code MakeMCHullWhiteCapFloorEngine}
 * (ql/pricingengines/capfloor/mchullwhiteengine.hpp). Phase 2 L3-D port.
 *
 * @see MCHullWhiteCapFloorEngine
 */
public class MakeMCHullWhiteCapFloorEngine {

    private final HullWhite model_;
    private boolean antithetic_ = false;
    private boolean brownianBridge_ = false;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;

    public MakeMCHullWhiteCapFloorEngine(final HullWhite model) {
        QL.require(model != null, "null Hull-White model");
        this.model_ = model;
    }

    public MakeMCHullWhiteCapFloorEngine withBrownianBridge(final boolean b) {
        this.brownianBridge_ = b;
        return this;
    }

    public MakeMCHullWhiteCapFloorEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCHullWhiteCapFloorEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    public MakeMCHullWhiteCapFloorEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCHullWhiteCapFloorEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "number of samples and absolute tolerance are mutually exclusive");
        this.samples_ = samples;
        return this;
    }

    public MakeMCHullWhiteCapFloorEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES,
                "number of samples and absolute tolerance are mutually exclusive");
        this.tolerance_ = tolerance;
        return this;
    }

    public MakeMCHullWhiteCapFloorEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    public MakeMCHullWhiteCapFloorEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    public PricingEngine value() {
        return new MCHullWhiteCapFloorEngine(model_, brownianBridge_, antithetic_, samples_, tolerance_, maxSamples_,
                seed_);
    }
}
