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
 Copyright (C) 2012 Klaus Spanderen
 */
package org.jquantlib.experimental.finitedifferences;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * VPP intrinsic value engine using dynamic programming.
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/experimental/finitedifferences/dynprogvppintrinsicvalueengine.{hpp,cpp}}.</p>
 *
 * <p>Prices a {@link VanillaVPPOption} on a fixed deterministic spot-price
 * trajectory ({@code fuelPrices[i]}, {@code powerPrices[i]} per hour) by
 * running the VPP step-condition backward over every hour and reading off
 * the {@link FdmVPPStepCondition#maxValue(Array)} of the resulting state
 * column.</p>
 *
 * @author Phase 5e.5b-CFC-d-287 port
 */
public class DynProgVPPIntrinsicValueEngine
        extends GenericEngine<VanillaVPPOption.ArgumentsImpl,
                              VanillaVPPOption.ResultsImpl> {

    private final double[] fuelPrices_;
    private final double[] powerPrices_;
    private final double fuelCostAddon_;
    @SuppressWarnings("unused")
    private final YieldTermStructure rTS_;

    public DynProgVPPIntrinsicValueEngine(final double[] fuelPrices,
                                          final double[] powerPrices,
                                          final double fuelCostAddon,
                                          final YieldTermStructure rTS) {
        super(new VanillaVPPOption.ArgumentsImpl(),
              new VanillaVPPOption.ResultsImpl());
        QL.require(fuelPrices != null && powerPrices != null,
                "null price arrays");
        QL.require(fuelPrices.length == powerPrices.length,
                "fuel and power price arrays must have the same length");
        this.fuelPrices_ = fuelPrices.clone();
        this.powerPrices_ = powerPrices.clone();
        this.fuelCostAddon_ = fuelCostAddon;
        this.rTS_ = rTS;
    }

    @Override
    public void calculate() {
        final FdmInnerValueCalculator fuelPrice = new FuelPrice(fuelPrices_);
        final FdmInnerValueCalculator sparkSpreadPrice = new SparkSpreadPrice(
                arguments_.heatRate, fuelPrices_, powerPrices_);

        final FdmVPPStepConditionFactory stepConditionFactory =
                new FdmVPPStepConditionFactory(arguments_);

        final FdmMesher mesher = new FdmMesherComposite(
                stepConditionFactory.stateMesher());

        final FdmVPPStepCondition.Mesher mesh =
                new FdmVPPStepCondition.Mesher(0, mesher);

        final FdmVPPStepCondition stepCondition = stepConditionFactory.build(
                mesh, fuelCostAddon_, fuelPrice, sparkSpreadPrice);

        final Array state = new Array(mesher.layout().dim()[0]);
        for (int j = powerPrices_.length; j > 0; --j) {
            stepCondition.applyTo(state, j - 1);
        }

        results_.value = stepCondition.maxValue(state);
    }

    /**
     * Spark-spread inner-value: {@code powerPrices[(int)t] - heatRate *
     * fuelPrices[(int)t]}. Mirrors anonymous C++ class
     * {@code SparkSpreadPrice}.
     */
    private static final class SparkSpreadPrice implements FdmInnerValueCalculator {
        private final double heatRate_;
        private final double[] fuelPrices_;
        private final double[] powerPrices_;

        SparkSpreadPrice(final double heatRate,
                         final double[] fuelPrices,
                         final double[] powerPrices) {
            this.heatRate_ = heatRate;
            this.fuelPrices_ = fuelPrices;
            this.powerPrices_ = powerPrices;
        }

        @Override
        public double innerValue(final FdmLinearOpIterator iter, final double t) {
            final int i = (int) t;
            QL.require(i < powerPrices_.length, "invalid time");
            return powerPrices_[i] - heatRate_ * fuelPrices_[i];
        }

        @Override
        public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
            return innerValue(iter, t);
        }
    }

    /** Fuel-price inner-value: {@code fuelPrices[(int)t]}. */
    private static final class FuelPrice implements FdmInnerValueCalculator {
        private final double[] fuelPrices_;

        FuelPrice(final double[] fuelPrices) {
            this.fuelPrices_ = fuelPrices;
        }

        @Override
        public double innerValue(final FdmLinearOpIterator iter, final double t) {
            final int i = (int) t;
            QL.require(i < fuelPrices_.length, "invalid time");
            return fuelPrices_[i];
        }

        @Override
        public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
            return innerValue(iter, t);
        }
    }
}
