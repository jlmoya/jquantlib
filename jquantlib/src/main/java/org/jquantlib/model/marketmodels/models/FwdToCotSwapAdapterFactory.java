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
 Copyright (C) 2006 Ferdinando Ametrano
 Copyright (C) 2006 Mark Joshi
 Copyright (C) 2007 StatPro Italia srl
*/

package org.jquantlib.model.marketmodels.models;

import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.MarketModelFactory;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

import java.util.List;

/**
 * Factory for {@link FwdToCotSwapAdapter}: wraps another factory that produces a forward-measure model and converts
 * each result to coterminal-swap measure.
 *
 * <p>Mirrors {@code FwdToCotSwapAdapterFactory} from
 * {@code ql/models/marketmodels/models/fwdtocotswapadapter.{hpp,cpp}} (QuantLib v1.42.1).
 *
 * @author Jose Moya
 */
public class FwdToCotSwapAdapterFactory implements MarketModelFactory, Observable, Observer {

    private final MarketModelFactory forwardFactory_;
    private final DefaultObservable obs_ = new DefaultObservable(this);

    public FwdToCotSwapAdapterFactory(final MarketModelFactory forwardFactory) {
        this.forwardFactory_ = forwardFactory;
        if ( forwardFactory instanceof Observable ) {
            ((Observable) forwardFactory).addObserver(this);
        }
    }

    @Override
    public MarketModel create(final EvolutionDescription evolution, final int numberOfFactors) {
        final MarketModel forwardModel = forwardFactory_.create(evolution, numberOfFactors);
        return new FwdToCotSwapAdapter(forwardModel);
    }

    @Override
    public void update() {
        notifyObservers();
    }

    @Override
    public void addObserver(final Observer observer) {
        obs_.addObserver(observer);
    }

    @Override
    public int countObservers() {
        return obs_.countObservers();
    }

    @Override
    public List< Observer > getObservers() {
        return obs_.getObservers();
    }

    @Override
    public void deleteObserver(final Observer observer) {
        obs_.deleteObserver(observer);
    }

    @Override
    public void deleteObservers() {
        obs_.deleteObservers();
    }

    @Override
    public void notifyObservers() {
        obs_.notifyObservers();
    }

    @Override
    public void notifyObservers(final Object arg) {
        obs_.notifyObservers(arg);
    }
}
