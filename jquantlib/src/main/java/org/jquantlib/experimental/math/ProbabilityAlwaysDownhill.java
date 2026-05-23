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

import org.jquantlib.experimental.math.HybridSimulatedAnnealing.Probability;
import org.jquantlib.math.matrixutilities.Array;

/**
 * "Greedy" acceptance rule — only points that strictly improve on the current solution are accepted. Depending on the
 * problem, this makes it very unlikely that the optimizer will be able to escape a local optimum.
 *
 * <p>Java port of QuantLib v1.42.1 {@code ProbabilityAlwaysDownhill}.
 */
public final class ProbabilityAlwaysDownhill implements Probability {

    @Override
    public boolean accept(final double currentValue, final double newValue, final Array temp) {
        return currentValue > newValue;
    }
}
