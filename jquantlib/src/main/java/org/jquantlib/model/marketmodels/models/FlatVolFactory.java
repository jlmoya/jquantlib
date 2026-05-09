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
 Copyright (C) 2006, 2007 Ferdinando Ametrano
 Copyright (C) 2006, 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels.models;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.MarketModelFactory;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.correlations.ExponentialForwardCorrelation;
import org.jquantlib.model.marketmodels.correlations.TimeHomogeneousForwardCorrelation;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

/**
 * Factory that builds {@link FlatVol} models from market data.
 *
 * <p>Mirrors {@code FlatVolFactory} from
 * {@code ql/models/marketmodels/models/flatvol.{hpp,cpp}} (QuantLib v1.42.1).
 *
 * <p>C++ {@code FlatVolFactory} extends {@code MarketModelFactory} (which itself
 * extends {@code Observable}) and {@code Observer}; in this Java port we
 * compose a {@link DefaultObservable} delegate and implement {@link Observer}
 * directly.
 *
 * @author Jose Moya
 */
public class FlatVolFactory implements MarketModelFactory, Observable, Observer {

    private final double longTermCorrelation_;
    private final double beta_;
    private final double[] times_;
    private final double[] vols_;
    private final LinearInterpolation volatility_;
    private final Handle<? extends YieldTermStructure> yieldCurve_;
    private final double displacement_;
    private final DefaultObservable obs_ = new DefaultObservable(this);

    /**
     * Builds a FlatVolFactory.
     *
     * @param longTermCorrelation long-term correlation in [0,1]
     * @param beta                exponential decay parameter ({@code >= 0})
     * @param times               times grid for the volatility curve
     * @param vols                volatility values aligned with {@code times}
     * @param yieldCurve          handle to the yield term structure for forward rates
     * @param displacement        displacement applied to all rates
     */
    public FlatVolFactory(final double longTermCorrelation,
                          final double beta,
                          final double[] times,
                          final double[] vols,
                          final Handle<? extends YieldTermStructure> yieldCurve,
                          final double displacement) {
        this.longTermCorrelation_ = longTermCorrelation;
        this.beta_ = beta;
        this.times_ = times.clone();
        this.vols_ = vols.clone();
        this.yieldCurve_ = yieldCurve;
        this.displacement_ = displacement;
        this.volatility_ = new LinearInterpolation(new Array(this.times_), new Array(this.vols_));
        if (yieldCurve != null) {
            yieldCurve.addObserver(this);
        }
    }

    @Override
    public MarketModel create(final EvolutionDescription evolution,
                              final int numberOfFactors) {
        final double[] rateTimes = evolution.rateTimes();
        final int numberOfRates = rateTimes.length - 1;

        // initialRates from yieldCurve forwardRate(simple) over each tenor
        final double[] initialRates = new double[numberOfRates];
        QL.require(yieldCurve_ != null && !yieldCurve_.empty(),
                "yield curve handle must be non-empty for FlatVolFactory.create");
        final YieldTermStructure yc = yieldCurve_.currentLink();
        for (int i = 0; i < numberOfRates; ++i) {
            initialRates[i] = yc.forwardRate(rateTimes[i], rateTimes[i + 1],
                    Compounding.Simple).rate();
        }

        // displaced volatilities: vol(rateTimes[i]) * f / (f + displacement)
        final double[] displacedVolatilities = new double[numberOfRates];
        for (int i = 0; i < numberOfRates; ++i) {
            final double vol = volatility_.op(rateTimes[i], true);
            displacedVolatilities[i] =
                    initialRates[i] * vol / (initialRates[i] + displacement_);
        }

        final double[] displacements = new double[numberOfRates];
        java.util.Arrays.fill(displacements, displacement_);

        // exponential correlations + time-homogeneous evolution
        final List<Double> rateTimesList = new ArrayList<>(rateTimes.length);
        for (final double t : rateTimes) {
            rateTimesList.add(t);
        }
        final Matrix correlations = ExponentialForwardCorrelation.exponentialCorrelations(
                rateTimesList, longTermCorrelation_, beta_, 1.0, 0.0);
        final PiecewiseConstantCorrelation corr = new TimeHomogeneousForwardCorrelation(
                correlations, rateTimesList);

        return new FlatVol(displacedVolatilities, corr, evolution,
                numberOfFactors, initialRates, displacements);
    }

    @Override
    public void update() {
        notifyObservers();
    }

    // -- Observable plumbing --

    @Override public void addObserver(final Observer observer) { obs_.addObserver(observer); }
    @Override public int countObservers() { return obs_.countObservers(); }
    @Override public List<Observer> getObservers() { return obs_.getObservers(); }
    @Override public void deleteObserver(final Observer observer) { obs_.deleteObserver(observer); }
    @Override public void deleteObservers() { obs_.deleteObservers(); }
    @Override public void notifyObservers() { obs_.notifyObservers(); }
    @Override public void notifyObservers(final Object arg) { obs_.notifyObservers(arg); }
}
