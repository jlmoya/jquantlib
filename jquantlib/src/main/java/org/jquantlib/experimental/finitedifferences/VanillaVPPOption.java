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
 Copyright (C) 2011 Klaus Spanderen
 */
package org.jquantlib.experimental.finitedifferences;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.instruments.AverageBasketPayoff;
import org.jquantlib.instruments.MultiAssetOption;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.SwingExercise;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.pricingengines.PricingEngine;

/**
 * Vanilla virtual power plant (VPP) option.
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/experimental/finitedifferences/vanillavppoption.{hpp,cpp}}.</p>
 *
 * <p>A VPP option models the right (but not the obligation) to operate a
 * power generation asset at any of a discrete set of exercise times. The payoff at each exercise is the spark spread
 * {@code max(P - heatRate * G, 0)}, optionally subject to constraints on minimum up-time, minimum down-time, start-up
 * fuel cost, total starts, and total running hours.</p>
 *
 * <p>The instrument inherits from {@link MultiAssetOption} (power and gas
 * are the two underlying assets) and uses an {@link AverageBasketPayoff} whose weights are {@code (1, -heatRate)} so
 * that the basket-payoff accumulator computes the spark spread.</p>
 *
 * @author Phase 5e.5b-CFC-d-164 port
 */
public class VanillaVPPOption extends MultiAssetOption {

    /** Sentinel for "unlimited" — matches C++ {@code Null<Size>()}. */
    public static final int NULL_INT = Integer.MIN_VALUE;

    private final double heatRate_;
    private final double pMin_;
    private final double pMax_;
    private final int tMinUp_;
    private final int tMinDown_;
    private final double startUpFuel_;
    private final double startUpFixCost_;
    private final int nStarts_;
    private final int nRunningHours_;

    /**
     * Convenience constructor — no start or running-hour limits.
     */
    public VanillaVPPOption(final double heatRate, final double pMin, final double pMax, final int tMinUp,
            final int tMinDown, final double startUpFuel, final double startUpFixCost, final SwingExercise exercise) {
        this(heatRate, pMin, pMax, tMinUp, tMinDown, startUpFuel, startUpFixCost, exercise, NULL_INT, NULL_INT);
    }

    /**
     * Full constructor mirroring the C++ signature.
     *
     * @param heatRate       conversion factor (units of fuel per unit of power)
     * @param pMin           minimum power output when running
     * @param pMax           maximum power output when running
     * @param tMinUp         minimum number of consecutive up periods after a start
     * @param tMinDown       minimum number of consecutive down periods after a stop
     * @param startUpFuel    fuel consumed on a single start-up event
     * @param startUpFixCost fixed cash cost of a single start-up event
     * @param exercise       swing-exercise schedule defining exercise opportunities
     * @param nStarts        total number of starts allowed ({@link #NULL_INT} = unlimited)
     * @param nRunningHours  total number of running hours allowed ({@link #NULL_INT} = unlimited)
     */
    public VanillaVPPOption(final double heatRate, final double pMin, final double pMax, final int tMinUp,
            final int tMinDown, final double startUpFuel, final double startUpFixCost, final SwingExercise exercise,
            final int nStarts, final int nRunningHours) {
        super(buildPayoff(heatRate), exercise);
        this.heatRate_ = heatRate;
        this.pMin_ = pMin;
        this.pMax_ = pMax;
        this.tMinUp_ = tMinUp;
        this.tMinDown_ = tMinDown;
        this.startUpFuel_ = startUpFuel;
        this.startUpFixCost_ = startUpFixCost;
        this.nStarts_ = nStarts;
        this.nRunningHours_ = nRunningHours;
    }

    /**
     * Builds the {@link AverageBasketPayoff} with weights {@code (1, -heatRate)} on top of an {@link IdenticalPayoff}
     * (the basket payoff directly returns the linear combination of underlyings, which is the spark spread).
     *
     * <p>Java equivalent of the C++ constructor body that assigns
     * {@code payoff_} after delegating to the {@code MultiAssetOption} base. Because the Java {@code Option.payoff}
     * field is {@code final}, the payoff is constructed before the {@code super(...)} call.</p>
     */
    private static Payoff buildPayoff(final double heatRate) {
        return new AverageBasketPayoff(new IdenticalPayoff(), new double[] { 1.0, -heatRate });
    }

    @Override
    public boolean isExpired() /* @ReadOnly */ {
        return exercise.lastDate().lt(new Settings().evaluationDate());
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        super.setupArguments(args);
        QL.require(VanillaVPPOption.ArgumentsImpl.class.isAssignableFrom(args.getClass()),
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final VanillaVPPOption.ArgumentsImpl a = (VanillaVPPOption.ArgumentsImpl) args;
        a.heatRate = heatRate_;
        a.pMin = pMin_;
        a.pMax = pMax_;
        a.tMinUp = tMinUp_;
        a.tMinDown = tMinDown_;
        a.startUpFuel = startUpFuel_;
        a.startUpFixCost = startUpFixCost_;
        a.nStarts = nStarts_;
        a.nRunningHours = nRunningHours_;
    }

    //
    // public inner interfaces / classes
    //

    public interface Arguments extends MultiAssetOption.Arguments { /* marking */
    }

    public interface Results extends MultiAssetOption.Results { /* marking */
    }

    /**
     * VPP-option arguments. Mirrors C++ {@code VanillaVPPOption::arguments}.
     */
    public static class ArgumentsImpl extends MultiAssetOption.ArgumentsImpl implements VanillaVPPOption.Arguments {

        public double heatRate;
        public double pMin;
        public double pMax;
        public int tMinUp;
        public int tMinDown;
        public double startUpFuel;
        public double startUpFixCost;
        public int nStarts;
        public int nRunningHours;

        @Override
        public void validate() /* @ReadOnly */ {
            QL.require(exercise != null, "no exercise given");
            QL.require(nStarts == NULL_INT || nRunningHours == NULL_INT,
                    "either a start limit or fuel limit is supported");
        }
    }

    /**
     * VPP-option results. Same shape as {@link MultiAssetOption.ResultsImpl}.
     */
    public static class ResultsImpl extends MultiAssetOption.ResultsImpl
            implements VanillaVPPOption.Results { /* marking */
    }

    /**
     * Trivial identity payoff: {@code op(price) == price}. Used by the VPP option so that the
     * {@link AverageBasketPayoff} accumulator returns the spark spread directly without any further mapping.
     *
     * <p>Mirrors the unnamed {@code IdenticalPayoff} in the C++
     * translation unit.</p>
     */
    static final class IdenticalPayoff extends Payoff {
        @Override
        public String name() {
            return "IdenticalPayoff";
        }

        @Override
        public String description() {
            return name();
        }

        @Override
        public double get(final double price) {
            return price;
        }
    }
}
