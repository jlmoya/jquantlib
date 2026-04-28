/*
 Copyright (C) 2011 Klaus Spanderen

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
package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.model.AffineModel;
import org.jquantlib.termstructures.AbstractYieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * Yield term structure implied by an affine short-rate model at a given
 * mesh state vector.
 * <p>
 * Java port of QuantLib v1.42.1
 * {@code ql/methods/finitedifferences/utilities/fdmaffinemodeltermstructure.{hpp,cpp}}.
 *
 * <p>The structure pins a fixed evaluation time {@code t} (year-fraction
 * from the model's reference date to this term-structure's reference date)
 * and computes the discount factor at relative time {@code T} as
 * {@code model.discountBond(t, t + T, r)}, where {@code r} is the affine
 * factor vector. The factor vector is mutable via {@link #setVariable}; the
 * Fdm framework rebinds it on every grid node before re-pricing the
 * underlying instrument.
 *
 * <p><strong>Java-port note.</strong> The C++ class registers as an
 * {@code Observer} of the model so any model recalibration triggers an
 * observer notification. The Java port mirrors this via
 * {@link #addObserver}.
 *
 * @author Phase 2h WI-2 port
 */
public class FdmAffineModelTermStructure extends AbstractYieldTermStructure {

    private Array r_;
    private final double t_;
    private final AffineModel model_;

    public FdmAffineModelTermStructure(final Array r,
                                       final Calendar cal,
                                       final DayCounter dayCounter,
                                       final Date referenceDate,
                                       final Date modelReferenceDate,
                                       final AffineModel model) {
        super(referenceDate, cal, dayCounter);
        this.r_ = r;
        this.t_ = dayCounter.yearFraction(modelReferenceDate, referenceDate);
        this.model_ = model;
        // Mirrors C++ registerWith(model_).
        this.model_.addObserver(this);
    }

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    /** Update the affine factor vector and notify observers. */
    public void setVariable(final Array r) {
        this.r_ = r;
        notifyObservers();
    }

    @Override
    protected double discountImpl(final double T) {
        return model_.discountBond(t_, T + t_, r_);
    }
}
