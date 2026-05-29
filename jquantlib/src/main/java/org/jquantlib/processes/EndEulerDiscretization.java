/*
 Copyright (C) 2008 Richard Gomes

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

/*
 Copyright (C) 2008 Frank Hövermann

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.processes;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;

/**
 * Euler <em>end-point</em> discretization for stochastic processes.
 * <p>
 * Identical to {@link EulerDiscretization} except that the drift and diffusion
 * are evaluated at the <b>end</b> of the time step, {@code t0 + dt}, rather
 * than at {@code t0}. Mirrors C++ v1.42.1
 * {@code ql/processes/endeulerdiscretization.cpp}.
 *
 * @author Frank Hövermann
 * @category processes
 */
public class EndEulerDiscretization implements StochasticProcess.Discretization, StochasticProcess1D.Discretization1D {

    //
    // implements StochasticProcess.Discretization
    //

    /**
     * Returns an approximation of the drift defined as
     * {@latex$ \mu(t_0 + \Delta t, \mathbf{x}_0) \Delta t }.
     */
    @Override
    public Array driftDiscretization(final StochasticProcess sp,
            /* @Time */final double t0, /* @Real */ final Array x0, /* @Time */ final double dt) {
        return sp.drift(t0 + dt, x0).mulAssign(dt);
    }

    /**
     * Returns an approximation of the diffusion defined as
     * {@latex$ \sigma(t_0 + \Delta t, \mathbf{x}_0) \sqrt{\Delta t} }.
     */
    @Override
    public Matrix diffusionDiscretization(final StochasticProcess sp,
            /* @Time */final double t0, /* @Real */ final Array x0, /* @Time */final double dt) {
        return sp.diffusion(t0 + dt, x0).mulAssign(Math.sqrt(dt));
    }

    /**
     * Returns an approximation of the covariance defined as
     * {@latex$ \sigma(t_0 + \Delta t, \mathbf{x}_0)^2 \Delta t }.
     */
    @Override
    public Matrix covarianceDiscretization(final StochasticProcess sp,
            /* @Time */final double t0, /* @Real */ final Array x0, /* @Time */final double dt) {
        final Matrix sigma = sp.diffusion(t0 + dt, x0);
        return sigma.mul(sigma.transpose()).mulAssign(dt);
    }

    //
    // implements StochasticProcess1D.Discretization1D
    //

    /**
     * Returns an approximation of the drift defined as
     * {@latex$ \mu(t_0 + \Delta t, x_0) \Delta t }.
     */
    @Override
    public /* @Drift */ double driftDiscretization(final StochasticProcess1D sp,
            /* @Time */final double t0, /* @Real */ final double x0, /* @Time */final double dt) {
        return sp.drift(t0 + dt, x0) * dt;
    }

    /**
     * Returns an approximation of the diffusion defined as
     * {@latex$ \sigma(t_0 + \Delta t, x_0) \sqrt{\Delta t} }.
     */
    @Override
    public /* @Diffusion */ double diffusionDiscretization(final StochasticProcess1D sp,
            /* @Time */final double t0, /* @Real */ final double x0, /* @Time */final double dt) {
        return sp.diffusion(t0 + dt, x0) * Math.sqrt(dt);
    }

    /**
     * Returns an approximation of the variance defined as
     * {@latex$ \sigma(t_0 + \Delta t, x_0)^2 \Delta t }.
     */
    @Override
    public /* @Variance */ double varianceDiscretization(final StochasticProcess1D sp,
            /* @Time */final double t0, /* @Real */ final double x0, /* @Time */final double dt) {
        /* @Diffusion */
        final double sigma = sp.diffusion(t0 + dt, x0);
        return sigma * sigma * dt;
    }

}
