/*
 Copyright (C) 2015 Andres Hernandez
 Copyright (C) 2026 JQuantLib migration contributors.

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
package org.jquantlib.experimental.math;

import org.jquantlib.experimental.math.HybridSimulatedAnnealing.Reannealing;
import org.jquantlib.math.matrixutilities.Array;

/**
 * No-op reannealing. Mirrors C++ {@code ReannealingTrivial}.
 */
public final class ReannealingTrivial implements Reannealing {

    @Override
    public void reanneal(final Array steps, final Array currentPoint, final double currentValue, final Array currTemp) {
        // intentionally empty
    }
}
