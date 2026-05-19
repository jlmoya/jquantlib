/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008 Klaus Spanderen

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
package org.jquantlib.methods.finitedifferences.stepconditions;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.StepCondition;

/**
 * Step condition that snapshots the state at a single target time.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/stepconditions/fdmsnapshotcondition.{hpp,cpp}}.
 * <p>
 * The {@code Fdm1DimSolver} / {@code Fdm2DimSolver} prepend a snapshot condition just before the first stopping time so
 * they can recover an earlier-time state for finite-difference theta evaluation. Equality on {@code t} is checked via
 * {@code ==} to mirror C++.
 *
 * @author Phase 2h WI-1 port
 */
public final class FdmSnapshotCondition implements StepCondition< Array > {

    private final double t;
    private Array values;

    public FdmSnapshotCondition(final double t) {
        this.t = t;
    }

    @Override
    public void applyTo(final Array a, final double t) {
        if ( t == this.t ) {
            this.values = a.clone();
        }
    }

    /** The target time at which a snapshot is taken. */
    public double getTime() {
        return t;
    }

    /**
     * The snapshot taken at {@link #getTime()}, or {@code null} if no snapshot has been recorded yet.
     */
    public Array getValues() {
        return values;
    }
}
