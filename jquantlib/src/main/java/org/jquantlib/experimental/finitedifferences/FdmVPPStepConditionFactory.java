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
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;

/**
 * Factory for VPP step conditions.
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/experimental/finitedifferences/fdmvppstepconditionfactory.{hpp,cpp}}.</p>
 *
 * <p>Selects between vanilla, start-limited, and running-hour-limited
 * variants based on the {@code (nStarts, nRunningHours)} pair on the
 * {@link VanillaVPPOption.ArgumentsImpl}. Running-hour limits are not yet
 * supported by the Java port (the C++ implementation likewise stops at
 * the "running-hour" branch).</p>
 *
 * @author Phase 5e.5b-CFC-d-287 port
 */
public class FdmVPPStepConditionFactory {

    private enum Type { Vanilla, StartLimit, RunningHourLimit }

    private final VanillaVPPOption.ArgumentsImpl args_;
    private final Type type_;

    public FdmVPPStepConditionFactory(final VanillaVPPOption.ArgumentsImpl args) {
        this.args_ = args;
        QL.require(!(args.nStarts != VanillaVPPOption.NULL_INT
                      && args.nRunningHours != VanillaVPPOption.NULL_INT),
                "start and running hour limit together is not supported");

        if (args.nRunningHours == VanillaVPPOption.NULL_INT
                && args.nStarts == VanillaVPPOption.NULL_INT) {
            this.type_ = Type.Vanilla;
        } else if (args.nRunningHours == VanillaVPPOption.NULL_INT) {
            this.type_ = Type.StartLimit;
        } else {
            this.type_ = Type.RunningHourLimit;
        }
    }

    public Fdm1dMesher stateMesher() {
        final int nStates = switch (type_) {
            case Vanilla -> 2 * args_.tMinUp + args_.tMinDown;
            case StartLimit -> FdmVPPStartLimitStepCondition.nStates(
                        args_.tMinUp, args_.tMinDown, args_.nStarts);
            default -> throw new IllegalStateException("vpp type is not supported");
        };

        return new Uniform1dMesher(0.0, 1.0, nStates);
    }

    public FdmVPPStepCondition build(
            final FdmVPPStepCondition.Mesher mesh,
            final double fuelCostAddon,
            final FdmInnerValueCalculator fuel,
            final FdmInnerValueCalculator spark) {

        final FdmVPPStepCondition.Params params = new FdmVPPStepCondition.Params(
                args_.heatRate, args_.pMin, args_.pMax,
                args_.tMinUp, args_.tMinDown,
                args_.startUpFuel, args_.startUpFixCost,
                fuelCostAddon);

        switch (type_) {
            case Vanilla:
            case StartLimit:
                return new FdmVPPStartLimitStepCondition(
                        params, args_.nStarts, mesh, fuel, spark);
            default:
                throw new IllegalStateException("vpp type is not supported");
        }
    }
}
