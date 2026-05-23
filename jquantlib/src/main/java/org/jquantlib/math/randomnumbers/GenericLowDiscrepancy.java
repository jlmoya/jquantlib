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
 *
 * @param <T>    represents the sample type
 * @param <URSG> represents the UniformRandomSequenceGenerator<T>
 * @param <IC>   represents the InverseCumulative
 * @author Richard Gomes
 */
public class GenericLowDiscrepancy< RSG extends UniformRandomSequenceGenerator, IC extends InverseCumulative > {

    //
    // static private fields
    //

    // Mirrors C++ QuantLib LowDiscrepancy trait flag:
    //   enum { allowsErrorEstimate = 0 };
    static private final boolean allowsErrorEstimate = false;

    // The C++ template trait never assigns icInstance — it is treated as a
    // null inverse-cumulative template parameter selector. We keep the same
    // null sentinel here so the makeSequenceGenerator branching mirrors C++.
    static final private GenericLowDiscrepancy icInstance = null;

    private Class< ? extends UniformRandomSequenceGenerator > classRSG;
    private Class< ? extends InverseCumulative > classIC;

    protected InverseCumulativeRsg< RSG, IC > makeSequenceGenerator(
            final Class< ? extends UniformRandomSequenceGenerator > classRSG,
            final Class< ? extends InverseCumulative > classIC, final /*@NonNegative*/ int dimension,
            final /*@NonNegative*/ long seed) {

        this.classRSG = classRSG;
        this.classIC = classIC;

        // instantiate a RandomSequenceGenerator given its generic type (first generic parameter)
        final RSG rsg;
        try {
            // obtain RSG Class from first generic parameter
            final Constructor< RSG > c = (Constructor< RSG >) classRSG.getConstructor(int.class, long.class);
            rsg = c.newInstance(dimension, seed);
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
        return (InverseCumulativeRsg< RSG, IC >) ic;
    }

}
