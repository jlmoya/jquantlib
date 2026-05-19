/*
 Copyright (C) 2014 Klaus Spanderen
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
package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;

import java.util.ArrayList;
import java.util.List;

/**
 * Mesher-based recursive integration of a function sampled on an N-D tensor-product grid.
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/utilities/fdmmesherintegral.{hpp,cpp}}.
 *
 * <p>Given a {@link FdmMesherComposite} of {@code N} {@link Fdm1dMesher}s and
 * an array {@code f} of size {@code prod(dim_i)}, the integral is computed as the application of a 1D quadrature rule
 * along the trailing direction, with the inner integral recursively computed by a sub-mesher of dimension {@code N-1}.
 * For 1D this reduces to {@code integrator1d(x_last, f)}.
 *
 * <p>The 1D quadrature is supplied as a {@link Integrator1d} functor — the
 * canonical caller in v1.42.1 is {@link org.jquantlib.math.integrals.DiscreteSimpsonIntegral}.
 *
 * @author Phase 5h.5-SLV-b port
 */
public class FdmMesherIntegral {

    private final List< Fdm1dMesher > meshers;
    private final Integrator1d integrator1d;
    public FdmMesherIntegral(final FdmMesherComposite mesher, final Integrator1d integrator1d) {
        this.meshers = new ArrayList< Fdm1dMesher >(mesher.getFdm1dMeshers());
        this.integrator1d = integrator1d;
    }

    private FdmMesherIntegral(final List< Fdm1dMesher > meshers, final Integrator1d integrator1d) {
        this.meshers = meshers;
        this.integrator1d = integrator1d;
    }

    /**
     * Integrate {@code f} over the N-D mesher.
     */
    public double integrate(final Array f) {
        final Fdm1dMesher last = meshers.get(meshers.size() - 1);
        final double[] locs = last.locations();
        final Array x = new Array(locs.length);
        for ( int i = 0; i < locs.length; ++i ) {
            x.set(i, locs[i]);
        }

        if ( meshers.size() == 1 ) {
            return integrator1d.op(x, f);
        }

        // Build a sub-integral over directions 0..N-2.
        final List< Fdm1dMesher > subMeshers = new ArrayList< Fdm1dMesher >(meshers.subList(0, meshers.size() - 1));
        final FdmMesherIntegral subMesherIntegral = new FdmMesherIntegral(subMeshers, integrator1d);

        // Compute the size of one slice (product of all sub-direction sizes).
        int subSize = 1;
        for ( final Fdm1dMesher m : subMeshers ) {
            subSize *= m.size();
        }

        final Array g = new Array(x.size());
        final Array fSub = new Array(subSize);
        for ( int i = 0; i < x.size(); ++i ) {
            for ( int k = 0; k < subSize; ++k ) {
                fSub.set(k, f.get(i * subSize + k));
            }
            g.set(i, subMesherIntegral.integrate(fSub));
        }
        return integrator1d.op(x, g);
    }

    /** Functional adapter for a 1D non-uniform-grid integrator. */
    public interface Integrator1d {
        double op(Array x, Array f);
    }
}
