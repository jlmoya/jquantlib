/*
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

/*
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2009 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for random default models.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::RandomDefaultModel}
 * ({@code ql/experimental/credit/randomdefaultmodel.{hpp,cpp}}). Pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Provides sequences of random default times for each name in a
 * {@link Pool}. Concrete subclasses (e.g. {@link GaussianRandomDefaultModel})
 * draw correlated systemic factors and invert each name's default-probability
 * curve to obtain a default time, storing the result back into the pool via
 * {@link Pool#setTime(String, double)}.
 *
 * <p>C++ is {@code public Observer, public Observable}; the Java port
 * implements both interfaces and delegates the {@link Observable} surface to a
 * {@link DefaultObservable}, mirroring the rest of the credit package.
 */
public abstract class RandomDefaultModel implements Observer, Observable {

    /** Sentinel returned for "never relevant within tmax": C++ uses {@code QL_MAX_REAL}. */
    public static final double QL_MAX_REAL = Constants.QL_MAX_REAL;

    private final Observable delegatedObservable = new DefaultObservable(this);

    protected final Pool pool_;
    protected final List< DefaultProbKey > defaultKeys_;

    protected RandomDefaultModel(final Pool pool, final List< DefaultProbKey > defaultKeys) {
        // assuming none defaulted this is true.
        QL.require(defaultKeys.size() == pool.size(), "Incompatible pool and keys sizes.");
        this.pool_ = pool;
        this.defaultKeys_ = new ArrayList<>(defaultKeys);
    }

    /**
     * Generate a sequence of random default times, one for each name in the
     * pool, and store the result in the {@link Pool} via
     * {@link Pool#setTime(String, double)}. {@code tmax} denotes the maximum
     * relevant time — default times {@code > tmax} are not computed but set to
     * {@code tmax + 1} instead to save computation time.
     */
    public abstract void nextSequence(double tmax);

    /** Convenience overload using {@code QL_MAX_REAL} as the C++ default argument. */
    public void nextSequence() {
        nextSequence(QL_MAX_REAL);
    }

    public abstract void reset();

    //
    // implements Observer / Observable
    //

    @Override
    public void update() {
        notifyObservers();
    }

    @Override
    public final void addObserver(final Observer observer) {
        delegatedObservable.addObserver(observer);
    }

    @Override
    public final int countObservers() {
        return delegatedObservable.countObservers();
    }

    @Override
    public final void deleteObserver(final Observer observer) {
        delegatedObservable.deleteObserver(observer);
    }

    @Override
    public final void notifyObservers() {
        delegatedObservable.notifyObservers();
    }

    @Override
    public final void notifyObservers(final Object arg) {
        delegatedObservable.notifyObservers(arg);
    }

    @Override
    public final void deleteObservers() {
        delegatedObservable.deleteObservers();
    }

    @Override
    public final List< Observer > getObservers() {
        return delegatedObservable.getObservers();
    }
}
