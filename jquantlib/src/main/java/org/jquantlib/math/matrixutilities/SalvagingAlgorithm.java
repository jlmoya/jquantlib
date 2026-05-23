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
package org.jquantlib.math.matrixutilities;

/**
 * Top-level enumeration of pseudo-square-root salvaging algorithms.
 *
 * <p>Faithful Java port of {@code QuantLib::SalvagingAlgorithm}
 * (v1.42.1 {@code ql/math/matrixutilities/pseudosqrt.hpp}, pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>This is a top-level convenience alias for {@link PseudoSqrt.SalvagingAlgorithm};
 * use the nested form in legacy call sites or this top-level form going forward.
 *
 * <p>JDK 25 enum surface — values match C++ one-for-one.
 *
 * <p>Phase 2 L1-D port.
 */
public enum SalvagingAlgorithm {
    None,
    Spectral,
    Hypersphere,
    LowerDiagonal,
    Higham,
    Principal;

    /** Translate to the nested {@link PseudoSqrt.SalvagingAlgorithm}. */
    public PseudoSqrt.SalvagingAlgorithm toNested() {
        return switch (this) {
            case None -> PseudoSqrt.SalvagingAlgorithm.None;
            case Spectral -> PseudoSqrt.SalvagingAlgorithm.Spectral;
            case Hypersphere -> PseudoSqrt.SalvagingAlgorithm.Hypersphere;
            case LowerDiagonal -> PseudoSqrt.SalvagingAlgorithm.LowerDiagonal;
            case Higham -> PseudoSqrt.SalvagingAlgorithm.Higham;
            case Principal -> PseudoSqrt.SalvagingAlgorithm.Principal;
        };
    }

    /** Translate from the nested {@link PseudoSqrt.SalvagingAlgorithm}. */
    public static SalvagingAlgorithm fromNested(final PseudoSqrt.SalvagingAlgorithm s) {
        return switch (s) {
            case None -> None;
            case Spectral -> Spectral;
            case Hypersphere -> Hypersphere;
            case LowerDiagonal -> LowerDiagonal;
            case Higham -> Higham;
            case Principal -> Principal;
        };
    }
}
