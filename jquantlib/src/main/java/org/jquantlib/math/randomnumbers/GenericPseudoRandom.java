/*
 Copyright (C) 2007 Richard Gomes

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
 Copyright (C) 2004 Ferdinando Ametrano
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2004 Walter Penschke

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

package org.jquantlib.math.randomnumbers;

import org.jquantlib.lang.exceptions.LibraryException;

import java.lang.reflect.Constructor;

/**
 * @param <RNG> represents the RandomNumberGenerator<T>
 * @param <IC>  represents the InverseCumulative
 * @author Richard Gomes
 */
@SuppressWarnings("unchecked")
public abstract class GenericPseudoRandom< RNG extends RandomNumberGenerator, IC extends InverseCumulative > {

    //
    // static private fields
    //

    // Mirrors C++ QuantLib PseudoRandom trait flag:
    //   enum { allowsErrorEstimate = 1 };
    static private final boolean allowsErrorEstimate = true;

    // The C++ template trait never assigns icInstance — it is treated as a
    // null inverse-cumulative template parameter selector. We keep the same
    // null sentinel here so the makeSequenceGenerator branching mirrors C++.
    static final private GenericPseudoRandom icInstance = null;

    private final Class< ? extends UniformRandomSequenceGenerator > classRNG;
    private final Class< ? extends InverseCumulative > classIC;

    protected GenericPseudoRandom(final Class< ? extends UniformRandomSequenceGenerator > classRNG,
            final Class< ? extends InverseCumulative > classIC) {
        this.classRNG = classRNG;
        this.classIC = classIC;
    }

    protected InverseCumulativeRsg< RandomSequenceGenerator< RNG >, IC > makeSequenceGenerator(
            final /*@NonNegative*/ int dimension, final /*@NonNegative*/ long seed) {

        // instantiate a RandomNumberGenerator given its generic type (first generic parameter)
        final RNG rng;
        try {
            // obtain RNG Class from first generic parameter
            final Constructor< RNG > c = (Constructor< RNG >) classRNG.getConstructor(long.class);
            rng = c.newInstance(seed);
        } catch ( final Exception e ) {
            throw new LibraryException(e); // QA:[RG]::verified
        }

        // instantiate a RandomSequenceGenerator given a RNG type
        final RandomSequenceGenerator< RNG > rsg;
        try {
            // obtain Class from previously created RNG variable

            // NOTE: in C++ the RSG type comes from a typedef on the RNG
            // traits class. Java erasure cannot recover the parameterised
            // class from RNG, so callers must pass it explicitly or this
            // branch will throw NPE on the getConstructor below.
            final Class< RandomSequenceGenerator< RNG > > rsgClass = null;

            final Constructor< RandomSequenceGenerator< RNG > > c = rsgClass.getConstructor(int.class, rng.getClass());
            rsg = c.newInstance(dimension, rng);
        } catch ( final Exception e ) {
            throw new LibraryException(e); // QA:[RG]::verified
        }

        // instantiate a InverseCumulative given its generic type (second generic parameter)
        final IC ic;
        try {
            // obtain IC Class from second generic parameter
            final Constructor< IC > c;
            if ( icInstance != null ) {
                c = (Constructor< IC >) classIC.getConstructor(rsg.getClass(), classIC.getClass());
                ic = c.newInstance(rsg, icInstance);
            } else {
                c = (Constructor< IC >) classIC.getConstructor(rsg.getClass());
                ic = c.newInstance(rsg);
            }
        } catch ( final Exception e ) {
            throw new LibraryException(e); // QA:[RG]::verified
        }
        return (InverseCumulativeRsg< RandomSequenceGenerator< RNG >, IC >) ic;
    }

}
