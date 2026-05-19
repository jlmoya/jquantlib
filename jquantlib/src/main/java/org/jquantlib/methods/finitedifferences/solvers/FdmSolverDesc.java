/*
 Copyright (C) 2010 Klaus Spanderen

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
package org.jquantlib.methods.finitedifferences.solvers;

import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;

/**
 * Plain-old descriptor bundling the parameters of an FDM rollback problem.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/solvers/fdmsolverdesc.hpp} — a {@code struct} on the C++
 * side. Fields are deliberately public and final; instances are immutable POD passed into solver constructors. Mirrors
 * the C++ struct field-by-field with no behavioural changes.
 *
 * @author Phase 2h WI-1 port
 */
public final class FdmSolverDesc {

    /** Multi-dimensional mesh. */
    public final FdmMesher mesher;
    /** Boundary-condition set applied each step. */
    public final FdmBoundaryConditionSet bcSet;
    /** Step conditions applied between time steps. */
    public final FdmStepConditionComposite condition;
    /** Inner-value calculator (payoff at maturity). */
    public final FdmInnerValueCalculator calculator;
    /** Maturity in years (start time of the rollback). */
    public final double maturity;
    /** Number of time steps in the main rollback. */
    public final int timeSteps;
    /** Number of leading implicit-Euler damping steps. */
    public final int dampingSteps;

    public FdmSolverDesc(final FdmMesher mesher, final FdmBoundaryConditionSet bcSet,
            final FdmStepConditionComposite condition, final FdmInnerValueCalculator calculator, final double maturity,
            final int timeSteps, final int dampingSteps) {
        this.mesher = mesher;
        this.bcSet = bcSet;
        this.condition = condition;
        this.calculator = calculator;
        this.maturity = maturity;
        this.timeSteps = timeSteps;
        this.dampingSteps = dampingSteps;
    }
}
