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

 JQuantLib is based on QuantLib. http://quantlib.org/
*/

/*
 Copyright (C) 2007 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.volatility;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.InterestRateIndex;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.volatilities.SabrInterpolatedSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.Observer;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * SABR volatility (smile) surface.
 *
 * <p>Faithful port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/sabrvolsurface.{hpp,cpp}}. Constructs a
 * {@link SabrInterpolatedSmileSection} at every option time on demand. ATM
 * vol is obtained from an external {@link BlackAtmVolCurve}; vol spreads at
 * configured ATM-rate spreads ({@code atmRateSpreads}) are linearly
 * interpolated across option times and fed to the SABR calibration.
 *
 * <p>Per-tenor SABR guesses are stored as 4-element {@code [alpha, beta, rho, nu]}
 * arrays (mirrors C++ {@code std::array<Real,4>}).
 */
public class SabrVolSurface extends InterestRateVolSurface {

    private final Handle< BlackAtmVolCurve > atmCurve_;
    private final List< Period > optionTenors_;
    private final List< Double > optionTimes_;
    private final List< Date > optionDates_;
    private final List< Double > atmRateSpreads_;
    private final List< List< Handle< Quote > > > volSpreads_;

    private final boolean isAlphaFixed_;
    private final boolean isBetaFixed_;
    private final boolean isNuFixed_;
    private final boolean isRhoFixed_;
    private final boolean vegaWeighted_;

    private final List< double[] > sabrGuesses_;

    public SabrVolSurface(final InterestRateIndex index, final Handle< BlackAtmVolCurve > atmCurve,
            final List< Period > optionTenors, final List< Double > atmRateSpreads,
            final List< List< Handle< Quote > > > volSpreads) {
        super(index, BusinessDayConvention.Following, new DayCounter());
        this.atmCurve_ = atmCurve;
        this.optionTenors_ = new ArrayList<>(optionTenors);
        this.optionTimes_ = new ArrayList<>(optionTenors.size());
        this.optionDates_ = new ArrayList<>(optionTenors.size());
        for ( int i = 0; i < optionTenors.size(); ++i ) {
            optionDates_.add(null);
            optionTimes_.add(0.0);
        }
        this.atmRateSpreads_ = new ArrayList<>(atmRateSpreads);
        this.volSpreads_ = new ArrayList<>(volSpreads.size());
        for ( final List< Handle< Quote > > row : volSpreads ) {
            volSpreads_.add(new ArrayList<>(row));
        }

        checkInputs();

        // Hard-coded defaults (mirrors C++ ctor body).
        this.isAlphaFixed_ = false;
        this.isBetaFixed_ = false;
        this.isNuFixed_ = false;
        this.isRhoFixed_ = false;
        this.vegaWeighted_ = true;

        this.sabrGuesses_ = new ArrayList<>(optionTenors.size());
        for ( int i = 0; i < optionTenors_.size(); ++i ) {
            optionDates_.set(i, optionDateFromTenor(optionTenors_.get(i)));
            optionTimes_.set(i, timeFromReference(optionDates_.get(i)));
            // Hard-coded initial guess (mirrors C++ ctor body).
            final double[] g = new double[4];
            g[0] = 0.025; // alpha
            g[1] = 0.5;   // beta
            g[2] = 0.3;   // rho
            g[3] = 0.0;   // nu
            sabrGuesses_.add(g);
        }
        registerWithMarketData();
    }

    private void checkInputs() {
        final int nStrikes = atmRateSpreads_.size();
        QL.require(nStrikes > 1, "too few strikes (" + nStrikes + ")");
        for ( int i = 1; i < nStrikes; ++i ) {
            QL.require(atmRateSpreads_.get(i - 1) < atmRateSpreads_.get(i),
                    "non increasing strike spreads at index " + i);
        }
        for ( int i = 0; i < volSpreads_.size(); ++i ) {
            QL.require(atmRateSpreads_.size() == volSpreads_.get(i).size(),
                    "mismatch between number of strikes (" + atmRateSpreads_.size() + ") and number of columns ("
                            + volSpreads_.get(i).size() + ") in the row " + (i + 1));
        }
    }

    private void registerWithMarketData() {
        for ( int i = 0; i < optionTenors_.size(); ++i ) {
            for ( int j = 0; j < atmRateSpreads_.size(); ++j ) {
                final Handle< Quote > q = volSpreads_.get(i).get(j);
                if ( q != null && q.currentLink() != null ) {
                    q.currentLink().addObserver(new Observer() {
                        @Override
                        public void update() {
                            SabrVolSurface.this.notifyObservers();
                        }
                    });
                }
            }
        }
    }

    /** SABR guess for a given date — piecewise-constant on optionDates_ (mirrors C++ {@code sabrGuesses(d)}). */
    protected double[] sabrGuesses(final Date d) {
        if ( d.le(optionDates_.get(0)) ) {
            return sabrGuesses_.get(0).clone();
        }
        int i = 0;
        while ( i < optionDates_.size() - 1 && d.lt(optionDates_.get(i)) ) {
            ++i;
        }
        return sabrGuesses_.get(i).clone();
    }

    /** Update the SABR guess at the date just below {@code d}; mirrors C++ {@code updateSabrGuesses}. */
    public void updateSabrGuesses(final Date d, final double[] newGuesses) {
        int i = 0;
        while ( i < optionDates_.size() && d.le(optionDates_.get(i)) ) {
            ++i;
        }
        if ( i >= sabrGuesses_.size() ) {
            return; // boundary protection vs C++ which had undefined behavior here
        }
        final double[] g = sabrGuesses_.get(i);
        g[0] = newGuesses[0];
        g[1] = newGuesses[1];
        g[2] = newGuesses[2];
        g[3] = newGuesses[3];
    }

    public List< Double > volatilitySpreads(final Period p) {
        return volatilitySpreads(optionDateFromTenor(p));
    }

    public List< Double > volatilitySpreads(final Date d) {
        final int nOptionTimes = optionTimes_.size();
        final int nAtmRateSpreads = atmRateSpreads_.size();
        final List< Double > interpolatedVols = new ArrayList<>(nAtmRateSpreads);
        final double tQuery = timeFromReference(d);

        for ( int i = 0; i < nAtmRateSpreads; ++i ) {
            final double[] xs = new double[nOptionTimes];
            final double[] ys = new double[nOptionTimes];
            for ( int j = 0; j < nOptionTimes; ++j ) {
                xs[j] = optionTimes_.get(j);
                ys[j] = volSpreads_.get(j).get(i).currentLink().value();
            }
            final LinearInterpolation interp = new LinearInterpolation(new Array(xs), new Array(ys));
            interp.update();
            interpolatedVols.add(interp.op(tQuery, true));
        }
        return interpolatedVols;
    }

    @Override
    public void update() {
        // TermStructure side: refresh optionDates / optionTimes.
        for ( int i = 0; i < optionTenors_.size(); ++i ) {
            optionDates_.set(i, optionDateFromTenor(optionTenors_.get(i)));
            optionTimes_.set(i, timeFromReference(optionDates_.get(i)));
        }
        super.update();
        notifyObservers();
    }

    @Override
    protected SmileSection smileSectionImpl(final double t) {
        final long n = (long) (t * 365.0);
        final Date d = referenceDate().add(new Period((int) n, TimeUnit.Days));
        // Interpolating on reference smile sections.
        final List< Double > volSpreads = volatilitySpreads(d);

        final double[] sabrParameters1 = sabrGuesses(d);
        final double forward = index_.fixing(d, true);
        final double atmVol = atmCurve_.currentLink().atmVol(d, true);

        return new SabrInterpolatedSmileSection(d, forward, atmRateSpreads_, true, atmVol, volSpreads,
                sabrParameters1[0], sabrParameters1[1], sabrParameters1[2], sabrParameters1[3], isAlphaFixed_,
                isBetaFixed_, isNuFixed_, isRhoFixed_, vegaWeighted_, null, null, dayCounter(), 0.0);
    }

    public Handle< BlackAtmVolCurve > atmCurve() {
        return atmCurve_;
    }

    @Override
    public DayCounter dayCounter() {
        return atmCurve_.currentLink().dayCounter();
    }

    @Override
    public Date maxDate() {
        return atmCurve_.currentLink().maxDate();
    }

    @Override
    public double maxTime() {
        return atmCurve_.currentLink().maxTime();
    }

    @Override
    public Date referenceDate() {
        return atmCurve_.currentLink().referenceDate();
    }

    @Override
    public Calendar calendar() {
        return atmCurve_.currentLink().calendar();
    }

    @Override
    public int settlementDays() {
        return atmCurve_.currentLink().settlementDays();
    }

    @Override
    public double minStrike() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public double maxStrike() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< SabrVolSurface > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
