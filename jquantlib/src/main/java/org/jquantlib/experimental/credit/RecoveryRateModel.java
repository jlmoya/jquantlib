/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2009 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.time.Date;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

import java.util.List;

/**
 * Models of the recovery rate provide future values of a recovery rate in the event of a default.
 *
 * <p>Java port of QuantLib v1.42.1 abstract {@code QuantLib::RecoveryRateModel}
 * ({@code ql/experimental/credit/recoveryratemodel.{hpp,cpp}}).
 *
 * <p>Phase 4m foundation.
 */
public abstract class RecoveryRateModel implements Observable {

    private final Observable delegatedObservable = new DefaultObservable(this);

    @Override
    public void addObserver(final Observer observer) {
        delegatedObservable.addObserver(observer);
    }

    @Override
    public int countObservers() {
        return delegatedObservable.countObservers();
    }

    @Override
    public void deleteObserver(final Observer observer) {
        delegatedObservable.deleteObserver(observer);
    }

    @Override
    public void notifyObservers() {
        delegatedObservable.notifyObservers();
    }

    @Override
    public void notifyObservers(final Object arg) {
        delegatedObservable.notifyObservers(arg);
    }

    @Override
    public void deleteObservers() {
        delegatedObservable.deleteObservers();
    }

    @Override
    public List< Observer > getObservers() {
        return delegatedObservable.getObservers();
    }

    /**
     * Returns the expected recovery rate at a future time conditional on some default event type and seniority.
     */
    public double recoveryValue(final Date defaultDate, final DefaultProbKey defaultKey) {
        return recoveryValueImpl(defaultDate, defaultKey);
    }

    public double recoveryValue(final Date defaultDate) {
        return recoveryValue(defaultDate, new DefaultProbKey());
    }

    /** Returns true if the model returns recovery rates for the requested seniority. */
    public abstract boolean appliesToSeniority(Seniority sen);

    /**
     * Returns {@code Constants.NULL_REAL} (corresponds to C++ {@code Null<Real>()}) if the model is unable to produce a
     * recovery for the requested seniority.
     */
    protected abstract double recoveryValueImpl(Date date, DefaultProbKey defaultKey);
}
