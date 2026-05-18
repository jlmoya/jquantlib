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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2010 Adrian O' Neill

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.experimental.variancegamma;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.Complex;
import org.jquantlib.math.FastFourierTransform;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.time.Date;

/**
 * Base class for FFT pricing engines for European vanilla options.
 *
 * <p>Phase 5e.5b-CFC-d-230 port of {@code QuantLib::FFTEngine}
 * (v1.42.1 ql/experimental/variancegamma/fftengine.{hpp,cpp}).
 *
 * <p>The FFT engine calculates the values of all options with the same
 * expiry at the same time using the Carr-Madan algorithm. For that
 * reason it is very inefficient to price options individually. When using
 * this engine you should collect all the options you wish to price in a
 * list and call {@link #precalculate(List)} before calling the
 * {@code NPV()} method of each option.
 *
 * <p>References:
 * Carr, P. and D. B. Madan (1998),
 * "Option Valuation using the fast Fourier transform,"
 * Journal of Computational Finance, 2, 61-73.
 *
 * @see FFTVarianceGammaEngine
 */
public abstract class FFTEngine extends VanillaOption.EngineImpl {

    /** Underlying stochastic process. */
    protected final StochasticProcess1D process_;
    /** Log-strike spacing of the FFT grid. */
    protected final double lambda_;

    /**
     * Cached precomputed prices, keyed first by expiry date then by
     * payoff identity. Mirrors C++ {@code resultMap_}.
     */
    private final Map<Date, Map<StrikedTypePayoff, Double>> resultMap_ =
            new HashMap<Date, Map<StrikedTypePayoff, Double>>();

    protected FFTEngine(final StochasticProcess1D process, final double logStrikeSpacing) {
        super();
        this.process_ = process;
        this.lambda_ = logStrikeSpacing;
        this.process_.addObserver(this);
    }

    @Override
    public void calculate() /*@ReadOnly*/ {
        final OneAssetOption.ArgumentsImpl a = (OneAssetOption.ArgumentsImpl) arguments_;
        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;

        QL.require(a.exercise.type() == Exercise.Type.European,
                "not an European Option");

        QL.require(a.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;

        final Date expiry = a.exercise.lastDate();
        final Map<StrikedTypePayoff, Double> byPayoff = resultMap_.get(expiry);
        if (byPayoff != null) {
            final Double cached = byPayoff.get(payoff);
            if (cached != null) {
                r.value = cached.doubleValue();
                return;
            }
        }

        // Option not precalculated — do entire FFT for one option.
        // Not very efficient — call precalculate!
        calculateUncached(payoff, a.exercise);
    }

    @Override
    public void update() {
        // Process has changed so cached values may no longer be correct.
        resultMap_.clear();
        super.update();
    }

    /**
     * Required by the C++ design: subclasses must be cloneable so that
     * {@link #calculateUncached} can build a temporary engine that does
     * not share the parent's caches/observer state.
     */
    public abstract FFTEngine clone1();

    /** Precompute per-expiry parameters (discount factor, time, etc.). */
    protected abstract void precalculateExpiry(Date d);

    /** Characteristic function evaluated at complex argument {@code u}. */
    protected abstract Complex complexFourierTransform(Complex u);

    /** Risk-free discount factor for the given expiry. */
    protected abstract double discountFactor(Date d);

    /** Dividend discount factor for the given expiry. */
    protected abstract double dividendYield(Date d);

    /**
     * Carr-Madan single-option fallback: builds a one-option list and
     * precalculates via a fresh engine clone, then reads back the price.
     */
    protected void calculateUncached(final StrikedTypePayoff payoff,
                                     final Exercise exercise) {
        final VanillaOption option = new VanillaOption(payoff, exercise);
        final List<Instrument> optionList = new ArrayList<Instrument>(1);
        optionList.add(option);

        final FFTEngine tempEngine = clone1();
        tempEngine.precalculate(optionList);
        option.setPricingEngine(tempEngine);
        ((OneAssetOption.ResultsImpl) results_).value = option.NPV();
    }

    /**
     * Group all supplied vanilla-option instruments by expiry date and run
     * the Carr-Madan FFT once per expiry to populate {@link #resultMap_}.
     *
     * <p>The grid size {@code n = 2^log2_n} is chosen large enough so
     * that {@code n * lambda / 2 >= log(maxStrike) + lambda} —
     * mirroring C++ exactly.
     */
    public void precalculate(final List<? extends Instrument> optionList) {
        // Group payoffs by expiry date.
        resultMap_.clear();

        final Map<Date, List<StrikedTypePayoff>> payoffMap =
                new HashMap<Date, List<StrikedTypePayoff>>();

        for (final Instrument inst : optionList) {
            QL.require(inst instanceof VanillaOption,
                    "instrument must be option");
            final VanillaOption option = (VanillaOption) inst;
            QL.require(option.exercise().type() == Exercise.Type.European,
                    "not an European Option");
            QL.require(option.payoff() instanceof StrikedTypePayoff,
                    "non-striked payoff given");
            final StrikedTypePayoff payoff = (StrikedTypePayoff) option.payoff();
            final Date expiry = option.exercise().lastDate();
            List<StrikedTypePayoff> bucket = payoffMap.get(expiry);
            if (bucket == null) {
                bucket = new ArrayList<StrikedTypePayoff>();
                payoffMap.put(expiry, bucket);
            }
            bucket.add(payoff);
        }

        final Complex i1 = Complex.I;
        final double alpha = 1.25;

        for (final Map.Entry<Date, List<StrikedTypePayoff>> entry : payoffMap.entrySet()) {
            final Date expiryDate = entry.getKey();
            final List<StrikedTypePayoff> payoffs = entry.getValue();

            // Calculate n large enough for maximum strike, round up to power of 2.
            double maxStrike = 0.0;
            for (final StrikedTypePayoff p : payoffs) {
                if (p.strike() > maxStrike) {
                    maxStrike = p.strike();
                }
            }
            final double nR = 2.0 * (Math.log(maxStrike) + lambda_) / lambda_;
            final int log2_n = ((int) (Math.log(nR) / Math.log(2.0))) + 1;
            final int n = 1 << log2_n;

            // Strike range (Carr-Madan eq. 19,20).
            final double b = n * lambda_ / 2.0;

            // Grid spacing (eq. 23).
            final double eta = 2.0 * Math.PI / (lambda_ * n);

            // Discount factor + dividend.
            final double df = discountFactor(expiryDate);
            final double div = dividendYield(expiryDate);

            // Precalculate any per-expiry parameters (e.g. t, vol, etc.).
            precalculateExpiry(expiryDate);

            // Build FFT input.
            final double[] ftiRe = new double[n];
            final double[] ftiIm = new double[n];
            for (int i = 0; i < n; i++) {
                final double v_j = eta * i;
                // Simpson rule weights: (3 + (-1)^i - [i==0]) / 3, times eta.
                final double sw =
                        eta * (3.0 + ((i % 2) == 0 ? -1.0 : 1.0) - (i == 0 ? 1.0 : 0.0)) / 3.0;

                // psi = df * phi(v_j - (alpha+1)*i)
                //         / (alpha^2 + alpha - v_j^2 + i*(2*alpha+1)*v_j)
                final Complex u = Complex.of(v_j, -(alpha + 1.0));
                Complex psi = complexFourierTransform(u).mul(df);
                final Complex denom = Complex.of(
                        alpha * alpha + alpha - v_j * v_j,
                        (2.0 * alpha + 1.0) * v_j);
                psi = psi.div(denom);

                // fti[i] = exp(i * b * v_j) * sw * psi
                final Complex expIbv = i1.mul(b * v_j).exp();
                final Complex fti = expIbv.mul(sw).mul(psi);
                ftiRe[i] = fti.real();
                ftiIm[i] = fti.imag();
            }

            // Forward FFT.
            final FastFourierTransform fft = new FastFourierTransform(log2_n);
            final double[] outRe = new double[n];
            final double[] outIm = new double[n];
            fft.transform(ftiRe, ftiIm, outRe, outIm);

            // Damped call prices.
            final double[] prices = new double[n];
            final double[] strikes = new double[n];
            for (int i = 0; i < n; i++) {
                final double k_u = -b + lambda_ * i;
                prices[i] = (Math.exp(-alpha * k_u) / Math.PI) * outRe[i];
                strikes[i] = Math.exp(k_u);
            }

            // Linear-interpolate at each requested strike, undo the
            // damping (Carr-Madan) — and convert call -> put via parity
            // when needed: P = C - S0 * div + K * df.
            final LinearInterpolation interp =
                    new LinearInterpolation(new Array(strikes), new Array(prices));
            final Map<StrikedTypePayoff, Double> bucket =
                    new HashMap<StrikedTypePayoff, Double>();
            for (final StrikedTypePayoff p : payoffs) {
                final double callPrice = interp.op(p.strike());
                final double price;
                switch (p.optionType()) {
                    case Call:
                        price = callPrice;
                        break;
                    case Put:
                        price = callPrice - process_.x0() * div + p.strike() * df;
                        break;
                    default:
                        throw new IllegalStateException("Invalid option type");
                }
                bucket.put(p, Double.valueOf(price));
            }
            resultMap_.put(expiryDate, bucket);
        }
    }
}
