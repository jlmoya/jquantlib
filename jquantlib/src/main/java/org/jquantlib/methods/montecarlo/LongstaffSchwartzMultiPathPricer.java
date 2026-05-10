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
package org.jquantlib.methods.montecarlo;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.TimeGrid;

/**
 * Convenience subclass of {@link LongstaffSchwartzPathPricer} pinned to
 * the multi-asset {@link MultiPath} / {@link Array}-state instantiation.
 *
 * <p>Java port of C++ template specialisation
 * {@code QuantLib::LongstaffSchwartzPathPricer<MultiPath>} (Phase MC-extras
 * WI-4). Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>This is a typedef-style class: the C++ template-specialisation idiom
 * {@code LongstaffSchwartzPathPricer<MultiPath>} maps to a Java
 * subclass that fills in the {@code <MultiPath, Array>} type parameters.
 *
 * <p>All algorithm work lives in the generic
 * {@link LongstaffSchwartzPathPricer} parent — including the
 * multi-variate {@code calibrate()} pipeline added in Phase MC-extras WI-2
 * and the {@link MultiPath}-aware {@link #deepCopyPath} default.
 *
 * <p>Distinct from the experimental
 * {@link org.jquantlib.experimental.mcbasket.LongstaffSchwartzMultiPathPricer}
 * which is the Phase 4i.5 scaffold for the {@code mcbasket} subsystem
 * (with {@code PathPayoff} / per-step bookkeeping). Use the
 * {@code methods.montecarlo} class here for vanilla multi-asset
 * Longstaff-Schwartz pricing; use the {@code mcbasket} class for
 * {@code MCLongstaffSchwartzPathEngine} pricers.
 *
 * @see LongstaffSchwartzPathPricer
 * @see AmericanMaxPathPricer
 */
public class LongstaffSchwartzMultiPathPricer
        extends LongstaffSchwartzPathPricer<MultiPath, Array> {

    public LongstaffSchwartzMultiPathPricer(
            final TimeGrid times,
            final EarlyExercisePathPricer<MultiPath, Array> pathPricer,
            final YieldTermStructure termStructure) {
        super(times, pathPricer, termStructure);
    }
}
