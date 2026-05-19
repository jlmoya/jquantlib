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
package org.jquantlib.methods.finitedifferences.meshers;

/**
 * One-dimensional simple FDM mesher object working on an index.
 * <p>
 * Java port of v1.42.1 ql/methods/finitedifferences/meshers/fdm1dmesher.hpp.
 * <p>
 * Holds the per-cell {@code locations}, {@code dplus} (= {@code locations[i+1] - locations[i]}), and {@code dminus} (=
 * {@code locations[i] - locations[i-1]}) arrays. The class is concrete in C++ (no pure-virtual methods) and is used
 * directly by callers as well as serving as a base for {@link FdmSimpleProcess1dMesher} and other 1D meshers planned
 * for later sub-layers (BlackScholes, Concentrating, ExponentialJump, ...).
 *
 * @author Phase 2h WI-1 port
 */
public class Fdm1dMesher {

    protected final double[] locations;
    protected final double[] dplus;
    protected final double[] dminus;

    /**
     * Allocate a 1D mesh of the requested size with all entries initialised to zero. Subclasses populate
     * {@link #locations}, {@link #dplus}, {@link #dminus}.
     */
    public Fdm1dMesher(final int size) {
        this.locations = new double[size];
        this.dplus = new double[size];
        this.dminus = new double[size];
    }

    /** Number of cells in the 1D mesh. */
    public final int size() {
        return locations.length;
    }

    /**
     * Forward spacing at cell {@code index}: {@code locations[index+1] - locations[index]}. The last cell holds
     * {@link Double#NaN} (matches C++ {@code Null<Real>()}).
     */
    public final double dplus(final int index) {
        return dplus[index];
    }

    /**
     * Backward spacing at cell {@code index}: {@code locations[index] - locations[index-1]}. Cell {@code 0} holds
     * {@link Double#NaN}.
     */
    public final double dminus(final int index) {
        return dminus[index];
    }

    /** Mesh location of cell {@code index}. */
    public final double location(final int index) {
        return locations[index];
    }

    /**
     * Read-only view of the mesh locations.
     * <p>
     * Note: C++ returns a {@code const std::vector<Real>&}; Java returns the live backing array (callers must not
     * mutate). This avoids a defensive clone on every per-cell call from {@link FdmMesherComposite#locations}.
     */
    public final double[] locations() {
        return locations;
    }
}
