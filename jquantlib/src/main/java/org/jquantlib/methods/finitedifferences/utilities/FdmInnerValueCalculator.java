/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008, 2009 Ralph Schreyer
 Copyright (C) 2008, 2009 Klaus Spanderen

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
package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;

/**
 * Layer of abstraction to calculate the inner value of an FDM grid cell.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/utilities/fdminnervaluecalculator.hpp}
 * (the {@code FdmInnerValueCalculator} abstract base class).
 * <p>
 * Implementations include payoff-driven calculators (vanilla, log,
 * cell-averaging) and model-driven calculators
 * ({@link FdmAffineModelSwapInnerValue}). The {@code innerValue} method
 * returns the payoff at the cell's mesh location at time {@code t};
 * {@code avgInnerValue} returns a cell-averaged value (for smoother
 * convergence) — for non-cell-averaging implementations it can simply
 * delegate to {@code innerValue}.
 *
 * @author Phase 2h WI-1 port
 */
public interface FdmInnerValueCalculator {

    /**
     * Inner value at the mesh cell pointed to by {@code iter} at time
     * {@code t}.
     */
    double innerValue(final FdmLinearOpIterator iter, final double t);

    /**
     * Cell-averaged inner value at the mesh cell pointed to by
     * {@code iter} at time {@code t}.
     */
    double avgInnerValue(final FdmLinearOpIterator iter, final double t);
}
