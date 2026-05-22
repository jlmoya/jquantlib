/*
 Copyright (C) 2026 JQuantLib contributors

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
 Copyright (C) 2013, 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.model.shortrate.onefactormodels.gaussian1d;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.model.ConstantParameter;
import org.jquantlib.model.NullParameter;
import org.jquantlib.model.Parameter;
import org.jquantlib.model.PiecewiseConstantParameter;
import org.jquantlib.processes.gsr.GsrProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

import java.util.ArrayList;
import java.util.List;

/**
 * One-factor GSR (Gaussian Short Rate) model in the forward measure.
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/models/shortrate/onefactormodels/gsr.{hpp,cpp}} (commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}). Phase 2j WI-1.3.
 * <p>
 * <b>Multiple-inheritance note.</b> The C++ {@code Gsr} inherits from both
 * {@code Gaussian1dModel} and {@code CalibratedModel}. Java cannot do this directly, and {@link Gaussian1dModel} (per
 * WI-1.1's design decision) extends {@link org.jquantlib.util.LazyObject} only. We replicate the
 * {@code CalibratedModel} parameter-storage surface (the {@code arguments_[0] = reversion},
 * {@code arguments_[1] = sigma} ref-binding pattern, plus {@code params()} / {@code setParams()}) directly on this
 * class via a private {@code arguments_} list. Full {@code calibrate()} integration (cost-function dispatch into
 * {@link org.jquantlib.model.CalibratedModel}) is deferred to a future work item — this WI ports the model surface used
 * by pricing engines, which only need parameter readback and the numeraire/zerobond pricing surface.
 * <p>
 * <b>Friend pattern.</b> The C++ {@code GsrProcess} uses {@code friend class Gsr}
 * to expose package-private setVols/setReversions/setTimes mutators. Java has no friend mechanism and
 * {@link GsrProcess} lives in a different package, so those setters are public — see the alignment commit on
 * {@code GsrProcess.java}.
 *
 * @author Peter Caspers (C++ original)
 * @author JQuantLib contributors (Java port)
 */
public class Gsr extends Gaussian1dModel {

    //
    // ──────────────────────────────────────────────────────────────────────
    //   CalibratedModel surrogate — argument storage
    // ──────────────────────────────────────────────────────────────────────
    //

    /**
     * Mirrors C++ {@code CalibratedModel::arguments_}. Slot 0 = reversion, slot 1 = sigma. The reference-binding
     * members below (reversion_, sigma_) point into this list and are reassigned in {@link #initialize(double)}.
     */
    private final List< Parameter > arguments_;
    private final List< Handle< Quote > > volatilities_;
    private final List< Handle< Quote > > reversions_;

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Per-step Quote handles + step dates
    // ──────────────────────────────────────────────────────────────────────
    //
    private final List< Date > volstepdates_;
    /**
     * Reversion parameter (slot {@code arguments_.get(0)}). May be a {@link ConstantParameter} (single reversion) or a
     * {@link PiecewiseConstantParameter} (one reversion per vol step).
     */
    private Parameter reversion_;
    /** Forwards Quote-update notifications to {@link #updateReversion()}. */
    private final Observer reversionObserver_ = new Observer() {
        @Override
        public void update() {
            updateReversion();
        }
    };
    /** Sigma (volatility) parameter — always a {@link PiecewiseConstantParameter}. */
    private Parameter sigma_;

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Private observers — wire each Quote's notification to the model
    // ──────────────────────────────────────────────────────────────────────
    //
    /** Forwards Quote-update notifications to {@link #updateVolatility()}. */
    private final Observer volatilityObserver_ = new Observer() {
        @Override
        public void update() {
            updateVolatility();
        }
    };
    /** Cached step times (computed via termStructure().timeFromReference). */
    private double[] volsteptimes_;

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Constructors (4 variants, mirror gsr.cpp 1:1)
    // ──────────────────────────────────────────────────────────────────────
    //

    /** Constant mean reversion, real-valued vols. T defaults to 60.0. */
    public Gsr(final Handle< YieldTermStructure > termStructure, final List< Date > volstepdates,
            final double[] volatilities, final double reversion) {
        this(termStructure, volstepdates, volatilities, reversion, 60.0);
    }

    /** Constant mean reversion, real-valued vols. */
    public Gsr(final Handle< YieldTermStructure > termStructure, final List< Date > volstepdates,
            final double[] volatilities, final double reversion, final double T) {
        super(termStructure);
        QL.require(termStructure != null && !termStructure.empty(), "yield term structure handle is empty");

        this.arguments_ = newArgumentsSlots();
        this.volstepdates_ = new ArrayList<>(volstepdates);
        this.volatilities_ = new ArrayList<>(volatilities.length);
        for ( int i = 0; i < volatilities.length; i++ ) {
            volatilities_.add(new Handle< Quote >(new SimpleQuote(volatilities[i])));
        }
        this.reversions_ = new ArrayList<>(1);
        reversions_.add(new Handle< Quote >(new SimpleQuote(reversion)));

        initialize(T);
    }

    /** Piecewise-constant mean reversion (per vol step), real-valued. T=60.0. */
    public Gsr(final Handle< YieldTermStructure > termStructure, final List< Date > volstepdates,
            final double[] volatilities, final double[] reversions) {
        this(termStructure, volstepdates, volatilities, reversions, 60.0);
    }

    /** Piecewise-constant mean reversion (per vol step), real-valued. */
    public Gsr(final Handle< YieldTermStructure > termStructure, final List< Date > volstepdates,
            final double[] volatilities, final double[] reversions, final double T) {
        super(termStructure);
        QL.require(termStructure != null && !termStructure.empty(), "yield term structure handle is empty");

        this.arguments_ = newArgumentsSlots();
        this.volstepdates_ = new ArrayList<>(volstepdates);
        this.volatilities_ = new ArrayList<>(volatilities.length);
        for ( int i = 0; i < volatilities.length; i++ ) {
            volatilities_.add(new Handle< Quote >(new SimpleQuote(volatilities[i])));
        }
        this.reversions_ = new ArrayList<>(reversions.length);
        for ( int i = 0; i < reversions.length; i++ ) {
            reversions_.add(new Handle< Quote >(new SimpleQuote(reversions[i])));
        }

        initialize(T);
    }

    /** Constant mean reversion with floating Quote-backed vols. T=60.0. */
    public Gsr(final Handle< YieldTermStructure > termStructure, final List< Date > volstepdates,
            final List< Handle< Quote > > volatilities, final Handle< Quote > reversion) {
        this(termStructure, volstepdates, volatilities, reversion, 60.0);
    }

    /** Constant mean reversion with floating Quote-backed vols. */
    public Gsr(final Handle< YieldTermStructure > termStructure, final List< Date > volstepdates,
            final List< Handle< Quote > > volatilities, final Handle< Quote > reversion, final double T) {
        super(termStructure);
        QL.require(termStructure != null && !termStructure.empty(), "yield term structure handle is empty");

        this.arguments_ = newArgumentsSlots();
        this.volstepdates_ = new ArrayList<>(volstepdates);
        this.volatilities_ = new ArrayList<>(volatilities);
        this.reversions_ = new ArrayList<>(1);
        reversions_.add(reversion);

        initialize(T);
    }

    /** Piecewise-constant Quote-backed reversion + vols. T=60.0. */
    public Gsr(final Handle< YieldTermStructure > termStructure, final List< Date > volstepdates,
            final List< Handle< Quote > > volatilities, final List< Handle< Quote > > reversions) {
        this(termStructure, volstepdates, volatilities, reversions, 60.0);
    }

    /** Piecewise-constant Quote-backed reversion + vols. */
    public Gsr(final Handle< YieldTermStructure > termStructure, final List< Date > volstepdates,
            final List< Handle< Quote > > volatilities, final List< Handle< Quote > > reversions, final double T) {
        super(termStructure);
        QL.require(termStructure != null && !termStructure.empty(), "yield term structure handle is empty");

        this.arguments_ = newArgumentsSlots();
        this.volstepdates_ = new ArrayList<>(volstepdates);
        this.volatilities_ = new ArrayList<>(volatilities);
        this.reversions_ = new ArrayList<>(reversions);

        initialize(T);
    }

    private static List< Parameter > newArgumentsSlots() {
        final List< Parameter > a = new ArrayList<>(2);
        // Pre-fill slots so set(0,...) / set(1,...) work in initialize().
        a.add(new NullParameter());
        a.add(new NullParameter());
        return a;
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Initialization (mirror gsr.cpp Gsr::initialize)
    // ──────────────────────────────────────────────────────────────────────
    //

    private static double[] arrayCopy(final Array a) {
        final double[] out = new double[a.size()];
        for ( int i = 0; i < out.length; i++ )
            out[i] = a.get(i);
        return out;
    }

    private void initialize(final double T) {
        // updateTimes() needs an array of the right size before it indexes into it
        volsteptimes_ = new double[volstepdates_.size()];
        updateTimes();

        QL.require(volatilities_.size() == volsteptimes_.length + 1,
                "there must be n+1 volatilities (%d) for n volatility step times (%d)", volatilities_.size(),
                volsteptimes_.length);

        QL.require(reversions_.size() == 1 || reversions_.size() == volsteptimes_.length + 1,
                "there must be 1 or n+1 reversions (%d) for n volatility step times (%d)", reversions_.size(),
                volsteptimes_.length);

        if ( reversions_.size() == 1 ) {
            reversion_ = new ConstantParameter(reversions_.get(0).currentLink().value(), new NoConstraint());
        } else {
            reversion_ = new PiecewiseConstantParameter(volsteptimes_);
            for ( int i = 0; i < reversion_.size(); i++ ) {
                reversion_.setParam(i, reversions_.get(i).currentLink().value());
            }
        }
        arguments_.set(0, reversion_);

        sigma_ = new PiecewiseConstantParameter(volsteptimes_);
        for ( int i = 0; i < sigma_.size(); i++ ) {
            sigma_.setParam(i, volatilities_.get(i).currentLink().value());
        }
        arguments_.set(1, sigma_);

        // Build the state process. Note: this assigns to the protected
        // stateProcess_ field defined in Gaussian1dModel.
        stateProcess_ = new GsrProcess(volsteptimes_, arrayCopy(sigma_.params()), arrayCopy(reversion_.params()), T);

        // termStructure() observer registration is already done by
        // Gaussian1dModel's constructor; no need to repeat.

        // C++ deliberately does NOT register with stateProcess_ to avoid
        // an infinite notification loop — the Gsr model owns the process
        // lifecycle and pushes parameter changes into it explicitly.

        for ( Handle< Quote > r : reversions_ ) {
            r.addObserver(reversionObserver_);
        }
        for ( Handle< Quote > v : volatilities_ ) {
            v.addObserver(volatilityObserver_);
        }
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Update flow (mirror gsr.cpp Gsr::update / updateTimes / etc.)
    // ──────────────────────────────────────────────────────────────────────
    //

    private void updateTimes() {
        final YieldTermStructure ts = termStructure().currentLink();
        // Resize defensively in case volstepdates_ changed (no public mutator,
        // but kept for parity with C++).
        if ( volsteptimes_ == null || volsteptimes_.length != volstepdates_.size() ) {
            volsteptimes_ = new double[volstepdates_.size()];
        }
        for ( int j = 0; j < volstepdates_.size(); j++ ) {
            volsteptimes_[j] = ts.timeFromReference(volstepdates_.get(j));
            if ( j == 0 ) {
                QL.require(volsteptimes_[0] > 0.0, "volsteptimes must be positive (%f)", volsteptimes_[0]);
            } else {
                QL.require(volsteptimes_[j] > volsteptimes_[j - 1],
                        "volsteptimes must be strictly increasing (%f@%d, %f@%d)", volsteptimes_[j - 1], j - 1,
                        volsteptimes_[j], j);
            }
        }
        if ( stateProcess_ != null ) {
            final GsrProcess p = (GsrProcess) stateProcess_;
            p.flushCache();
            p.setTimes(volsteptimes_.clone());
        }
    }

    private void updateVolatility() {
        for ( int i = 0; i < sigma_.size(); i++ ) {
            sigma_.setParam(i, volatilities_.get(i).currentLink().value());
        }
        ((GsrProcess) stateProcess_).setVols(arrayCopy(sigma_.params()));
        update();
    }

    private void updateReversion() {
        for ( int i = 0; i < reversion_.size(); i++ ) {
            reversion_.setParam(i, reversions_.get(i).currentLink().value());
        }
        ((GsrProcess) stateProcess_).setReversions(arrayCopy(reversion_.params()));
        update();
    }

    /**
     * Mirrors C++ {@code Gsr::update()}: flush the GSR process cache and delegate to
     * {@link org.jquantlib.util.LazyObject}'s update flow which marks the model dirty and notifies observers (parity
     * with the C++ {@code LazyObject::update()} call at the bottom of {@code Gsr::update}).
     */
    @Override
    public void update() {
        if ( stateProcess_ != null ) {
            ((GsrProcess) stateProcess_).flushCache();
            // C++ also calls stateProcess_->notifyObservers(), but per
            // initialize() we do not register as a stateProcess_ observer
            // (would form an infinite loop). Anyone else who registered with
            // the process directly will need to be notified another way; this
            // path is unused by current Java callers.
        }
        super.update();
    }

    @Override
    protected void generateArguments() {
        final GsrProcess p = (GsrProcess) stateProcess_;
        p.flushCache();
        p.setVols(arrayCopy(sigma_.params()));
        p.setReversions(arrayCopy(reversion_.params()));
        super.notifyObservers();
    }

    @Override
    protected void performCalculations() {
        super.performCalculations();
        updateTimes();
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   numeraireImpl / zerobondImpl  (overrides Gaussian1dModel hooks)
    // ──────────────────────────────────────────────────────────────────────
    //

    @Override
    protected double zerobondImpl(final double T, final double t, final double y,
            final Handle< YieldTermStructure > yts) {
        calculate();

        if ( t == 0.0 ) {
            // C++: yts.empty() ? this->termStructure()->discount(T, true)
            //                  : yts->discount(T, true)
            final YieldTermStructure ts = (yts == null || yts.empty())
                    ? termStructure().currentLink()
                    : yts.currentLink();
            return ts.discount(T, true);
        }

        final GsrProcess p = (GsrProcess) stateProcess_;

        // x = y * stdDev(0, 0, t) + expectation(0, 0, t)
        final double x = y * stateProcess_.stdDeviation(0.0, 0.0, t) + stateProcess_.expectation(0.0, 0.0, t);
        final double gtT = p.G(t, T, x);

        final double d;
        if ( yts == null || yts.empty() ) {
            final YieldTermStructure ts = termStructure().currentLink();
            d = ts.discount(T, true) / ts.discount(t, true);
        } else {
            final YieldTermStructure other = yts.currentLink();
            d = other.discount(T, true) / other.discount(t, true);
        }

        // d * exp(-x*gtT - 0.5 * y(t) * gtT^2)
        return d * JQuantMath.exp(-x * gtT - 0.5 * p.y(t) * gtT * gtT);
    }

    @Override
    protected double numeraireImpl(final double t, final double y, final Handle< YieldTermStructure > yts) {
        calculate();

        final GsrProcess p = (GsrProcess) stateProcess_;
        final double T = p.getForwardMeasureTime();

        if ( t == 0.0 ) {
            final YieldTermStructure ts = (yts == null || yts.empty())
                    ? termStructure().currentLink()
                    : yts.currentLink();
            // C++ branch difference (mirrored verbatim):
            // - empty yts: discount(T, true)  (extrapolation allowed)
            // - non-empty yts: discount(T)    (no explicit extrapolation flag)
            if ( yts == null || yts.empty() ) {
                return ts.discount(T, true);
            }
            return ts.discount(T);
        }
        return zerobond(T, t, y, yts);
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Inspectors (mirror gsr.hpp inline accessors)
    // ──────────────────────────────────────────────────────────────────────
    //

    /** @return the reversion parameter array. */
    public Array reversion() {
        return reversion_.params();
    }

    /** @return the volatility parameter array. */
    public Array volatility() {
        return sigma_.params();
    }

    /** @return the T-forward-measure horizon time used by the state process. */
    public double numeraireTime() {
        return ((GsrProcess) stateProcess_).getForwardMeasureTime();
    }

    /**
     * Sets the T-forward-measure horizon time. Per C++, this delegates to the GSR process which flushes its caches.
     */
    public void numeraireTime(final double T) {
        ((GsrProcess) stateProcess_).setForwardMeasureTime(T);
    }

    /**
     * Read-only view of the calibration arguments slots (slot 0 = reversion, slot 1 = sigma).
     * <p>
     * Provided for parity with C++ {@code CalibratedModel::arguments_}; pricing engines that introspect calibration
     * parameters can call this.
     */
    public List< Parameter > arguments() {
        return java.util.Collections.unmodifiableList(arguments_);
    }

    //
    // ──────────────────────────────────────────────────────────────────────
    //   Observer wiring — Quote handles
    // ──────────────────────────────────────────────────────────────────────
    //
    // The two anonymous-Observer fields above (volatilityObserver_,
    // reversionObserver_) replace the C++ inner-class
    // VolatilityObserver/ReversionObserver structs, which exist in C++ only
    // because Observer is a stateful interface that needs a back-pointer.
    // Java's anonymous inner classes solve this directly.

    // Suppress an unused-import warning in some IDEs:
    @SuppressWarnings( "unused" )
    private void __unused_observable_ref(final Observable o) { /* no-op */ }
}
