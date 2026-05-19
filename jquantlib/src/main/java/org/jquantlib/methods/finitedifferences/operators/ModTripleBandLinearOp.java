/*
 Copyright (C) 2013 Klaus Spanderen

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
package org.jquantlib.methods.finitedifferences.operators;

import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;

/**
 * Modifiable triple-band linear operator — exposes the protected {@code lower}, {@code diag}, and {@code upper} arrays
 * of {@link TripleBandLinearOp} so that boundary-condition setters can patch individual stencil cells in place.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/operators/modtriplebandlinearop.hpp}.
 * <p>
 * The C++ class uses C++ reference accessors ({@code Real& lower(Size i)}) to allow in-place mutation. In Java we
 * expose explicit setters and getters since Java does not have references.
 *
 * @author Phase 5h.5-SLV port
 */
public class ModTripleBandLinearOp extends TripleBandLinearOp {

    public ModTripleBandLinearOp(final int direction, final FdmMesher mesher) {
        super(direction, mesher);
    }

    /** Copy / promote a TripleBandLinearOp into a Mod variant. */
    public ModTripleBandLinearOp(final TripleBandLinearOp m) {
        super(m);
    }

    public double lowerAt(final int i) {
        return lower[i];
    }

    public double diagAt(final int i) {
        return diag[i];
    }

    public double upperAt(final int i) {
        return upper[i];
    }

    public void setLower(final int i, final double v) {
        lower[i] = v;
    }

    public void setDiag(final int i, final double v) {
        diag[i] = v;
    }

    public void setUpper(final int i, final double v) {
        upper[i] = v;
    }

    public void addLower(final int i, final double v) {
        lower[i] += v;
    }

    public void addDiag(final int i, final double v) {
        diag[i] += v;
    }

    public void addUpper(final int i, final double v) {
        upper[i] += v;
    }
}
