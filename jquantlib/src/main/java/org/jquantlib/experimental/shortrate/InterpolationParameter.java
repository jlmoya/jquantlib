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

/*
 Copyright (C) 2010 SunTrust Bank
 Copyright (C) 2010, 2014 Cavit Hafizoglu

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.experimental.shortrate;

import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.model.Parameter;

/**
 * Parameter that holds an {@link Interpolation} object.
 *
 * <p>Phase 4c port of {@code QuantLib::InterpolationParameter}
 * (declared at the top of v1.42.1
 * ql/experimental/shortrate/generalizedhullwhite.hpp).
 *
 * <p>This is the storage class for piecewise-linear (or other) parameters
 * in {@link GeneralizedHullWhite}: parameter values live as ordinates
 * in {@link Parameter#params()}, and the abscissae are external; the
 * driving interpolation is bound at {@link #reset(Interpolation)} time
 * after the underlying parameter array has been populated.
 *
 * <p>{@link Parameter.Impl} is a {@code protected} interface in
 * {@link Parameter}; this class is in a different package, so we expose
 * a package-private {@link InterpolationImpl} and a public
 * {@link Parameter#implementation()} hop to reset the interpolator.
 */
public class InterpolationParameter extends Parameter {

    public InterpolationParameter(final int /*@Size*/ size) {
        this(size, new NoConstraint());
    }

    public InterpolationParameter(final int /*@Size*/ size, final Constraint constraint) {
        super(size, new InterpolationImpl(), constraint);
    }

    /**
     * Bind the interpolation. The interpolator's y-values must be the same
     * underlying buffer as {@link #params()} for parameter updates to
     * propagate; {@code Linear/BackwardFlat/LinearFlat} factories all
     * follow this convention when given {@code params().constIterator()}
     * style backing.
     */
    public void reset(final Interpolation interp) {
        if (impl instanceof InterpolationImpl) {
            ((InterpolationImpl) impl).interpolator = interp;
        }
    }

    /**
     * Package-private {@link Parameter.Impl} that defers to the bound
     * {@link Interpolation}. Mutable {@code interpolator} field per the
     * C++ {@code reset()} pattern.
     */
    static final class InterpolationImpl implements Parameter.Impl {
        Interpolation interpolator;

        @Override
        public double value(final Array params, final /*@Time*/ double t) {
            return interpolator.op(t);
        }
    }
}
