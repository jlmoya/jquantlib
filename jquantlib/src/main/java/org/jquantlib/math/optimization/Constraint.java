/*
 Copyright (C) 2007 Richard Gomes

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

/*
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb

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

package org.jquantlib.math.optimization;

import org.jquantlib.QL;
import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;

/**
 * Base constraint class
 *
 * @author Richard Gomes
 */
@QualityAssurance( quality = Quality.Q3_DOCUMENTATION, version = Version.V097, reviewers = "Richard Gomes" )
public abstract class Constraint {

    //
    // protected fields
    //

    protected Impl impl;

    //
    // public constructors
    //

    public Constraint() {
        this.impl = null;
    }

    public Constraint(final Impl impl) {
        this.impl = impl;
    }

    //
    // public methods
    //

    public boolean empty() /* @ReadOnly */ {
        return impl == null;
    }

    public boolean test(final Array p) /* @ReadOnly */ {
        return impl.test(p);
    }

    /**
     * Per-component upper bound for the constraint at the supplied parameter vector.
     *
     * <p>Mirrors C++ v1.42.1 {@code Constraint::upperBound(const Array&)}: delegates to
     * {@link Constraint.Impl#upperBound(Array)} which defaults to {@code +QL_MAX_REAL} for unconstrained dimensions.
     */
    public Array upperBound(final Array params) /* @ReadOnly */ {
        final Array result = impl.upperBound(params);
        QL.require(params.size() == result.size(),
                "upper bound size (" + result.size() + ") not equal to params size (" + params.size() + ")");
        return result;
    }

    /**
     * Per-component lower bound for the constraint at the supplied parameter vector.
     *
     * <p>Mirrors C++ v1.42.1 {@code Constraint::lowerBound(const Array&)}: delegates to
     * {@link Constraint.Impl#lowerBound(Array)} which defaults to {@code -QL_MAX_REAL} for unconstrained dimensions.
     */
    public Array lowerBound(final Array params) /* @ReadOnly */ {
        final Array result = impl.lowerBound(params);
        QL.require(params.size() == result.size(),
                "lower bound size (" + result.size() + ") not equal to params size (" + params.size() + ")");
        return result;
    }

    public double update(final Array params, final Array direction, final double beta) {
        double diff = beta;
        Array newParams = params.add(direction.mul(diff));
        boolean valid = test(newParams);

        int icount = 0;
        while ( !valid ) {
            if ( icount > 200 ) {
                throw new LibraryException("can't update parameter vector"); // TODO: message
            }
            diff *= 0.5;
            icount++;
            newParams = params.add(direction.mul(diff));
            valid = test(newParams);
        }
        params.fill(newParams);
        return diff;
    }

    //
    // protected inner classes
    //

    /**
     * Base class for constraint implementations.
     */
    abstract protected class Impl {

        //
        // public abstract methods
        //

        /**
         * <p>Tests if params satisfy the constraint. </p>
         */
        public abstract boolean test(final Array params) /* @ReadOnly */;

        /**
         * Per-component upper bound. Default: an array of size {@code params.size()} filled with {@code +QL_MAX_REAL},
         * matching C++ v1.42.1 default behavior.
         */
        public Array upperBound(final Array params) /* @ReadOnly */ {
            final double[] data = new double[params.size()];
            for ( int i = 0; i < data.length; ++i ) {
                data[i] = Constants.QL_MAX_REAL;
            }
            return new Array(data);
        }

        /**
         * Per-component lower bound. Default: an array of size {@code params.size()} filled with {@code -QL_MAX_REAL},
         * matching C++ v1.42.1 default behavior.
         */
        public Array lowerBound(final Array params) /* @ReadOnly */ {
            final double[] data = new double[params.size()];
            for ( int i = 0; i < data.length; ++i ) {
                data[i] = -Constants.QL_MAX_REAL;
            }
            return new Array(data);
        }

    }

}
