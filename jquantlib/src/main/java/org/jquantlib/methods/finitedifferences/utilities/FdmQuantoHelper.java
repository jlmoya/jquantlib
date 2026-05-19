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
 When applicable, the original copyright notice follows this notice.
 */
/*
 Copyright (C) 2008, 2009 Ralph Schreyer
 Copyright (C) 2008, 2009 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

import java.util.List;

/**
 * Helper class storing market data needed for the quanto adjustment.
 *
 * <p>Phase 5e.5b-CFC-d-102 Java port of v1.42.1
 * {@code ql/methods/finitedifferences/utilities/fdmquantohelper.{hpp,cpp}}.
 *
 * <p>Returns the continuous-time quanto drift adjustment
 * {@code r_domestic - r_foreign + equityFxCorrelation * equityVol * fxVol} for use in finite-difference PDE engines
 * that price an asset paid in a domestic currency but driven by foreign dynamics.
 *
 * <p>Implements {@link Observable} via delegation so downstream FDM
 * components (e.g., {@code FdmBlackScholesMesher}) can register for market-data updates.
 */
public class FdmQuantoHelper implements Observable, Observer {

    private final YieldTermStructure rTS_;
    private final YieldTermStructure fTS_;
    private final BlackVolTermStructure fxVolTS_;
    private final double equityFxCorrelation_;
    private final double exchRateATMlevel_;
    private final Observable delegatedObservable = new DefaultObservable(this);

    public FdmQuantoHelper(final YieldTermStructure rTS, final YieldTermStructure fTS,
            final BlackVolTermStructure fxVolTS, final double equityFxCorrelation, final double exchRateATMlevel) {
        QL.require(rTS != null, "null domestic yield term structure");
        QL.require(fTS != null, "null foreign yield term structure");
        QL.require(fxVolTS != null, "null FX vol term structure");
        this.rTS_ = rTS;
        this.fTS_ = fTS;
        this.fxVolTS_ = fxVolTS;
        this.equityFxCorrelation_ = equityFxCorrelation;
        this.exchRateATMlevel_ = exchRateATMlevel;

        this.rTS_.addObserver(this);
        this.fTS_.addObserver(this);
        this.fxVolTS_.addObserver(this);
    }

    /**
     * Quanto drift adjustment for a scalar equity volatility:
     * {@code r_domestic - r_foreign + equityVol * fxVol * equityFxCorrelation}.
     */
    public double quantoAdjustment(final double equityVol, final double t1, final double t2) {
        final double rDomestic = rTS_.forwardRate(t1, t2, Compounding.Continuous).rate();
        final double rForeign = fTS_.forwardRate(t1, t2, Compounding.Continuous).rate();
        final double fxVol = fxVolTS_.blackForwardVol(t1, t2, exchRateATMlevel_, true);
        return rDomestic - rForeign + equityVol * fxVol * equityFxCorrelation_;
    }

    /**
     * Vector overload — applies the scalar quanto adjustment element-wise.
     */
    public Array quantoAdjustment(final Array equityVol, final double t1, final double t2) {
        final double rDomestic = rTS_.forwardRate(t1, t2, Compounding.Continuous).rate();
        final double rForeign = fTS_.forwardRate(t1, t2, Compounding.Continuous).rate();
        final double fxVol = fxVolTS_.blackForwardVol(t1, t2, exchRateATMlevel_, true);
        final int n = equityVol.size();
        final Array out = new Array(n);
        for ( int i = 0; i < n; ++i ) {
            out.set(i, rDomestic - rForeign + equityVol.get(i) * fxVol * equityFxCorrelation_);
        }
        return out;
    }

    public YieldTermStructure rTS() {
        return rTS_;
    }

    public YieldTermStructure fTS() {
        return fTS_;
    }

    public BlackVolTermStructure fxVolTS() {
        return fxVolTS_;
    }

    public double equityFxCorrelation() {
        return equityFxCorrelation_;
    }

    //
    // implements Observer
    //

    public double exchRateATMlevel() {
        return exchRateATMlevel_;
    }

    //
    // implements Observable via delegate pattern
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
