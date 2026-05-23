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
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2004, 2005 StatPro Italia srl

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

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.time.Date;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

import java.util.List;

/**
 * Multi-dimensional stochastic process class.
 * <p>
 * {@latex[ d\mathrm{x}_t = \mu(t,x_t)\mathrm{d}t + \sigma(t,\mathrm{x}_t) \cdot d\mathrm{W}_t }
 *
 * @author Richard Gomes
 */
public abstract class StochasticProcess implements Observable, Observer {

    //
    // private fields
    //

    /**
     * Implements multiple inheritance via delegate pattern to an inner class
     *
     * @see Observable
     * @see DefaultObservable
     */
    // Phase 2x A.4: WeakReferenceObservable to break cumulative
    // observer-list bleed across tests (engines / pricers from
    // completed tests would otherwise stay attached strongly).
    private final Observable delegatedObservable = new org.jquantlib.util.WeakReferenceObservable(this);

    //
    // protected constructors
    //
    private Discretization discretization;

    protected StochasticProcess() {
        // only extended classes can instantiate
    }

    //
    // abstract methods
    //

    /**
     * @param discretization is an Object that <b>must</b> implement {@link Discretization}.
     */
    protected StochasticProcess(final Discretization discretization) {
        QL.require(discretization != null, "null discretization"); // QA:[RG]::verified
        this.discretization = discretization;
    }

    //
    // public methods
    //

    /**
     * Returns the number of dimensions of the stochastic process
     */
    public abstract int size();

    /**
     * Returns the number of independent factors of the process
     */
    public int factors() {
        return size();
    }

    /**
     * Returns the initial values of the state variables
     */
    public abstract Array initialValues() /*@ReadOnly*/;

    /**
     * Returns the drift part of the equation, i.e., {@latex$ \mu(t, \mathrm{x}_t) }
     */
    public abstract Array drift(final /*@Time*/ double t, final Array x) /*@ReadOnly*/;

    /**
     * Returns the diffusion part of the equation, i.e. {@latex$ \sigma(t, \mathrm{x}_t) }
     */
    public abstract Matrix diffusion(final /*@Time*/ double t, final Array x) /*@ReadOnly*/;

    /**
     * Returns the expectation {@latex$ S(\ mathrm { x } _ { t_0 + \ Delta t } | \ mathrm { x } _ { t_0 } = \ mathrm { x } _0) } of the
     * process after a time interval {@latex$ \Delta t } according to the given discretization. This method can be
     * overridden in derived classes which want to hard-code a particular discretization.
     */
    public Array expectation(final /*@Time*/ double t0, final Array x0, final /*@Time*/ double dt) /*@ReadOnly*/ {
        return apply(x0, discretization.driftDiscretization(this, t0, x0, dt));
    }

    /**
     * Returns the standard deviation {@latex$ S(\ mathrm { x } _ { t_0 + \ Delta t } | \ mathrm { x } _ { t_0 } = \ mathrm { x } _0) } of the
     * process after a time interval {@latex$ \Delta t } according to the given discretization. This method can be
     * overridden in derived classes which want to hard-code a particular discretization.
     */
    public Matrix stdDeviation(final /*@Time*/ double t0, final Array x0, final /*@Time*/ double dt) /*@ReadOnly*/ {
        return discretization.diffusionDiscretization(this, t0, x0, dt);
    }

    /**
     * Returns the covariance {@latex$ V(\ mathrm { x } _ { t_0 + \ Delta t } | \ mathrm { x } _ { t_0 } = \ mathrm { x } _0) } of the process
     * after a time interval {@latex$ \Delta t } according to the given discretization. This method can be overridden in
     * derived classes which want to hard-code a particular discretization.
     */
    public Matrix covariance(final /*@Time*/ double t0, final Array x0, final /*@Time*/ double dt) /*@ReadOnly*/ {
        return discretization.covarianceDiscretization(this, t0, x0, dt);
    }

    /**
     * Returns the asset value after a time interval {@latex$ \Delta t } according to the given discretization. By
     * default, it returns
     * {@latex[ E(\ mathrm { x } _0, t_0, \ Delta t) + S(\mathrm{x}_0,t_0,\Delta t) \cdot \Delta \mathrm{w} } where {@latex$ E }
     * is the expectation and {@latex$ S } the standard deviation.
     */
    public Array evolve(final /*@Time*/ double t0, final Array x0, final /*@Time*/ double dt,
            final Array dw) /*@ReadOnly*/ {
        return apply(expectation(t0, x0, dt), stdDeviation(t0, x0, dt).mul(dw));
    }

    /**
     * Applies a change to the asset value.
     *
     * @returns {@latex$ \mathrm{x} + \Delta \mathrm{x} }.
     */
    public Array apply(final Array x0, final Array dx) /*@ReadOnly*/ {
        return x0.add(dx);
    }

    //
    // implements Observer
    //

    /**
     * Returns the time value corresponding to the given date in the reference system of the stochastic process.
     *
     * @note As a number of processes might not need this functionality, a default implementation is given which raises
     * an exception.
     */
    public /*@Time*/ double time(final Date date) /*@ReadOnly*/ {
        throw new UnsupportedOperationException("date/time conversion not supported");
    }

    //
    // implements Observable
    //

    @Override
    public void update() {
        notifyObservers();
    }

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

    //
    // inner interfaces
    //

    /**
     * Discretization of a stochastic process over a given time interval
     *
     * @author Richard Gomes
     */
    public interface Discretization {

        /**
         * Returns the drift part of the equation, i.e., {@latex$ \mu(t, \mathrm{x}_t) }
         */
        Array driftDiscretization(final StochasticProcess sp, final/* @Time */double t0, final Array x0,
                final/* @Time */double dt);

        /**
         * Returns the diffusion part of the equation, i.e. {@latex$ \sigma(t, \mathrm{x}_t) }
         */
        Matrix diffusionDiscretization(final StochasticProcess sp, final/* @Time */double t0, final Array x0,
                final/* @Time */double dt);

        /**
         * Returns the covariance {@latex$ V(\ mathrm { x } _ { t_0 + \ Delta t } | \ mathrm { x } _ { t_0 } = \ mathrm { x } _0) } of the
         * process after a time interval {@latex$ \Delta t } according to the given discretization. This method can be
         * overridden in derived classes which want to hard-code a particular discretization.
         */
        Matrix covarianceDiscretization(final StochasticProcess sp, final/* @Time */double t0, final Array x0,
                final/* @Time */double dt);

    }

}
