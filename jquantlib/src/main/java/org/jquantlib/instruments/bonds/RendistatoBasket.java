/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2010, 2011 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
*/

package org.jquantlib.instruments.bonds;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;
import org.jquantlib.util.WeakReferenceObservable;

/**
 * Basket of Italian BTPs used by the Rendistato (Italian-bond average-yield)
 * index calculator.
 *
 * <p>Java port of QuantLib v1.42.1 {@code RendistatoBasket}
 * (ql/instruments/bonds/btp.{hpp,cpp}).
 *
 * <p>Acts as both Observer (of the input clean-price quotes — forwards
 * change notifications) and Observable (downstream
 * {@link RendistatoCalculator} observes this basket).
 *
 * @author Jose Moya
 */
public class RendistatoBasket implements Observer, Observable {

    private final List< BTP > btps_;
    private final List< Double > outstandings_;
    private final List< Handle< Quote > > quotes_;
    private final double outstanding_;
    private final int n_;
    private final List< Double > weights_;

    /**
     * Implements multiple inheritance via delegate pattern. Mirrors
     * the WeakReferenceObservable pattern used elsewhere in the tree
     * to avoid observer-list accumulation across tests.
     */
    private final DefaultObservable delegatedObservable = new WeakReferenceObservable(this);

    public RendistatoBasket(final List< BTP > btps, final List< Double > outstandings,
            final List< Handle< Quote > > cleanPriceQuotes) {
        QL.require(btps != null && !btps.isEmpty(), "empty RendistatoCalculator Basket");
        final int k = btps.size();

        QL.require(outstandings.size() == k, "mismatch between number of BTPs (" + k
                + ") and number of outstandings (" + outstandings.size() + ")");
        QL.require(cleanPriceQuotes.size() == k, "mismatch between number of BTPs (" + k
                + ") and number of clean prices quotes (" + cleanPriceQuotes.size() + ")");

        // require non-negative outstanding
        for (int i = 0; i < k; ++i) {
            QL.require(outstandings.get(i) >= 0,
                    "negative outstanding for bond #" + i + ", maturity " + btps.get(i).maturityDate());
            // add check for prices ??
        }

        this.btps_ = new ArrayList<>(btps);
        this.outstandings_ = new ArrayList<>(outstandings);
        this.quotes_ = new ArrayList<>(cleanPriceQuotes);
        this.n_ = btps_.size();

        double sum = 0.0;
        for (int i = 0; i < n_; ++i) {
            sum += outstandings_.get(i);
        }
        this.outstanding_ = sum;

        this.weights_ = new ArrayList<>(n_);
        for (int i = 0; i < n_; ++i) {
            weights_.add(outstandings_.get(i) / outstanding_);
            // registerWith(quotes_[i]) -> quotes_[i].addObserver(this)
            quotes_.get(i).addObserver(this);
        }
    }

    //
    // Inspectors
    //

    public int size() {
        return n_;
    }

    public List< BTP > btps() {
        return btps_;
    }

    public List< Handle< Quote > > cleanPriceQuotes() {
        return quotes_;
    }

    public List< Double > outstandings() {
        return outstandings_;
    }

    public List< Double > weights() {
        return weights_;
    }

    public double outstanding() {
        return outstanding_;
    }

    //
    // Observer interface — forward notifications from input quotes
    //
    @Override
    public void update() {
        notifyObservers();
    }

    //
    // Observable interface — delegate to inner WeakReferenceObservable
    //
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
}
