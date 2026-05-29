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
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2006 StatPro Italia srl

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

package org.jquantlib.exercise;

import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for exercise dates
 *
 * <p>JDK 25 sealed hierarchy (JEP 409): permits the QuantLib v1.42.1 exercise styles
 * — {@link EuropeanExercise} and {@link EarlyExercise} (the abstract parent for American/Bermudan/Swing),
 * plus {@link RebatedExercise} which (per C++ v1.42.1 {@code ql/rebatedexercise.hpp}) derives
 * directly from {@code Exercise}.</p>
 *
 * @author Richard Gomes
 */
@QualityAssurance( quality = Quality.Q3_DOCUMENTATION, version = Version.V097, reviewers = { "Richard Gomes" } )
public abstract sealed class Exercise
        permits EuropeanExercise, EarlyExercise, RebatedExercise {

    //
    // protected fields
    //

    protected final List< Date > dates;
    protected Exercise.Type type;

    //
    // protected constructors
    //

    /**
     * Constructs an exercise and defines the exercise type
     *
     * @param type is the type of exercise
     * @see Exercise.Type
     */
    protected Exercise(final Exercise.Type type) {
        this.type = type;
        this.dates = new ArrayList<>(5); // some reasonable prime number
    }

    //
    // public final methods
    //

    /**
     * Returns the exercise type
     *
     * @return the exercise type
     * @see Exercise.Type
     */
    public final Exercise.Type type() {
        return type;
    }

    public List< Date > dates() {
        return dates;
    }

    public final int size() {
        return dates.size();
    }

    public final Date date(final int index) /* @ReadOnly */ {
        return dates.get(index);
    }

    public final Date lastDate() /* @ReadOnly */ {
        return date(dates.size() - 1);
    }

    //
    // public static inner enums
    //

    /**
     * Defines the exercise type. It can be American, Bermudan or European
     *
     * @author Richard Gomes
     */
    public enum Type {
        American, Bermudan, European
    }

}
