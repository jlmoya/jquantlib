/*
 Copyright (C) 2009 Andreas Gaida
 Copyright (C) 2009 Ralph Schreyer
 Copyright (C) 2009 Klaus Spanderen

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.methods.finitedifferences.schemes;

/**
 * Plain-old descriptor for an Fdm rollback scheme.
 * <p>
 * Java port of v1.42.1 ql/methods/finitedifferences/solvers/fdmbackwardsolver.hpp
 * (the {@code FdmSchemeDesc} struct, which lives in the solver header on the
 * C++ side but is grouped here on the Java side because the schemes
 * package is its only consumer in Phase 2h WI-1).
 * <p>
 * Static factories mirror the C++ {@code FdmSchemeDesc::Hundsdorfer()},
 * {@code FdmSchemeDesc::Douglas()}, etc. Phase 2h WI-1 only ports the
 * factories whose target schemes are also ported in this work item:
 * {@link #Hundsdorfer()} and {@link #Douglas()}. The other factories
 * ({@code CrankNicolson}, {@code CraigSneyd}, ...) are deferred per
 * design decision P2H-7 until an engine actually requires them.
 *
 * @author Phase 2h WI-1 port
 */
public final class FdmSchemeDesc {

    /**
     * Discriminator enum for the rollback scheme variant.
     * Mirrors C++ {@code FdmSchemeDesc::FdmSchemeType}; ordering matches
     * the C++ declaration so a numeric round-trip with reference probes
     * stays bit-exact.
     */
    public enum FdmSchemeType {
        HundsdorferType, DouglasType,
        CraigSneydType, ModifiedCraigSneydType,
        ImplicitEulerType, ExplicitEulerType,
        MethodOfLinesType, TrBDF2Type,
        CrankNicolsonType
    }

    /** Scheme variant. */
    public final FdmSchemeType type;
    /** Theta parameter (implicit/explicit weight). */
    public final double theta;
    /** Mu parameter (corrector weight, scheme-specific). */
    public final double mu;

    /** Direct constructor; mirrors the C++ struct constructor. */
    public FdmSchemeDesc(final FdmSchemeType type, final double theta, final double mu) {
        this.type = type;
        this.theta = theta;
        this.mu = mu;
    }

    /**
     * Hundsdorfer scheme defaults: {@code theta = 0.5 + sqrt(3)/6,
     * mu = 0.5}. Mirrors C++ {@code FdmSchemeDesc::Hundsdorfer()}.
     */
    public static FdmSchemeDesc Hundsdorfer() {
        return new FdmSchemeDesc(FdmSchemeType.HundsdorferType,
                0.5 + Math.sqrt(3.0) / 6.0, 0.5);
    }

    /**
     * Douglas scheme defaults: {@code theta = 0.5, mu = 0.0}. Mirrors C++
     * {@code FdmSchemeDesc::Douglas()}. (Same as Crank-Nicolson in 1
     * dimension.)
     */
    public static FdmSchemeDesc Douglas() {
        return new FdmSchemeDesc(FdmSchemeType.DouglasType, 0.5, 0.0);
    }

    /**
     * Implicit-Euler scheme defaults: {@code theta = 0.0, mu = 0.0}.
     * Mirrors C++ {@code FdmSchemeDesc::ImplicitEuler()}. Used by
     * {@code FdmBackwardSolver} for the optional damping prefix steps.
     */
    public static FdmSchemeDesc ImplicitEuler() {
        return new FdmSchemeDesc(FdmSchemeType.ImplicitEulerType, 0.0, 0.0);
    }
}
