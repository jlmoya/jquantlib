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
 Copyright (C) 2006 Warren Chou
 Copyright (C) 2007, 2008 StatPro Italia srl
*/
package org.jquantlib.pricingengines.forward;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VarianceSwap;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Variance-swap pricing engine using a replicating portfolio of European
 * options, as described in Demeterfi, Derman, Kamal &amp; Zou,
 * <em>A Guide to Volatility and Variance Swaps</em> (1999).
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/forward/replicatingvarianceswapengine.hpp}
 * (Phase 5e.5b-CFC-d-180).
 */
public class ReplicatingVarianceSwapEngine extends VarianceSwap.EngineImpl {

    //
    // private fields
    //

    private final GeneralizedBlackScholesProcess process_;
    private final double dk_;
    private final double[] callStrikes_;
    private final double[] putStrikes_;


    //
    // constructors
    //

    public ReplicatingVarianceSwapEngine(final GeneralizedBlackScholesProcess process,
                                         final double dk,
                                         final double[] callStrikes,
                                         final double[] putStrikes) {
        super();
        QL.require(process != null, "no process given");
        QL.require(callStrikes != null && callStrikes.length > 0
                && putStrikes != null && putStrikes.length > 0,
                "no strike(s) given");
        double minPut = Double.POSITIVE_INFINITY;
        double maxPut = Double.NEGATIVE_INFINITY;
        for (final double k : putStrikes) {
            if (k < minPut) { minPut = k; }
            if (k > maxPut) { maxPut = k; }
        }
        QL.require(minPut > 0.0, "min put strike must be positive");
        double minCall = Double.POSITIVE_INFINITY;
        for (final double k : callStrikes) {
            if (k < minCall) { minCall = k; }
        }
        QL.require(minCall == maxPut, "min call and max put strikes differ");

        this.process_ = process;
        this.dk_ = dk;
        this.callStrikes_ = callStrikes.clone();
        this.putStrikes_ = putStrikes.clone();
        this.process_.addObserver(this);
    }

    /**
     * Convenience constructor using the {@code dk = 5.0} default (matches
     * the C++ header default).
     */
    public ReplicatingVarianceSwapEngine(final GeneralizedBlackScholesProcess process,
                                         final double[] callStrikes,
                                         final double[] putStrikes) {
        this(process, 5.0, callStrikes, putStrikes);
    }


    //
    // PricingEngine
    //

    @Override
    public void calculate() {
        final VarianceSwap.ArgumentsImpl a = (VarianceSwap.ArgumentsImpl) arguments_;
        final VarianceSwap.ResultsImpl r = (VarianceSwap.ResultsImpl) results_;

        final List<WeightedPayoff> optionWeights = new ArrayList<WeightedPayoff>();
        computeOptionWeights(callStrikes_, Option.Type.Call, optionWeights);
        computeOptionWeights(putStrikes_,  Option.Type.Put,  optionWeights);

        r.variance = computeReplicatingPortfolio(optionWeights);

        final double riskFreeDiscount =
                process_.riskFreeRate().currentLink().discount(a.maturityDate);
        final double multiplier;
        switch (a.position) {
            case Long:  multiplier = +1.0; break;
            case Short: multiplier = -1.0; break;
            default: throw new RuntimeException("Unknown position");
        }
        r.value = multiplier * riskFreeDiscount * a.notional
                * (r.variance - a.strike);

        // Mirror C++ additionalResults["optionWeights"]
        r.additionalResults().put("optionWeights", optionWeights);
    }


    //
    // helper methods
    //

    /**
     * Mirrors C++ {@code computeOptionWeights}: build piecewise-linear
     * weights for the replicating strip in the given option type.
     */
    private void computeOptionWeights(final double[] availStrikes,
                                      final Option.Type type,
                                      final List<WeightedPayoff> optionWeights) {
        if (availStrikes.length == 0) {
            return;
        }

        // copy + sort + append the end-strike for piecewise approximation
        double[] strikes = availStrikes.clone();
        switch (type) {
            case Call:
                Arrays.sort(strikes);
                strikes = append(strikes, strikes[strikes.length - 1] + dk_);
                break;
            case Put:
                Arrays.sort(strikes);
                reverseInPlace(strikes);
                strikes = append(strikes, Math.max(strikes[strikes.length - 1] - dk_, 0.0));
                break;
            default:
                throw new RuntimeException("invalid option type");
        }

        // remove duplicates (post-sort)
        strikes = dedupAdjacent(strikes);

        // compute weights
        final double f = strikes[0];
        double slope;
        double prevSlope = 0.0;

        for (int k = 0; k < strikes.length - 1; k++) {
            slope = Math.abs((computeLogPayoff(strikes[k + 1], f)
                    - computeLogPayoff(strikes[k], f))
                    / (strikes[k + 1] - strikes[k]));
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strikes[k]);
            if (k == 0) {
                optionWeights.add(new WeightedPayoff(payoff, slope));
            } else {
                optionWeights.add(new WeightedPayoff(payoff, slope - prevSlope));
            }
            prevSlope = slope;
        }
    }

    private double computeLogPayoff(final double strike,
                                    final double callPutStrikeBoundary) {
        final double f = callPutStrikeBoundary;
        return (2.0 / residualTime())
                * (((strike - f) / f) - Math.log(strike / f));
    }

    private double computeReplicatingPortfolio(final List<WeightedPayoff> optionWeights) {
        final VarianceSwap.ArgumentsImpl a = (VarianceSwap.ArgumentsImpl) arguments_;
        final Exercise exercise = new EuropeanExercise(a.maturityDate);
        final AnalyticEuropeanEngine optionEngine = new AnalyticEuropeanEngine(process_);

        double optionsValue = 0.0;
        for (final WeightedPayoff wp : optionWeights) {
            final EuropeanOption option = new EuropeanOption(wp.payoff, exercise);
            option.setPricingEngine(optionEngine);
            optionsValue += option.NPV() * wp.weight;
        }

        final double f = optionWeights.get(0).payoff.strike();
        return 2.0 * riskFreeRate()
                - 2.0 / residualTime()
                  * (((underlying() / riskFreeDiscount() - f) / f)
                     + Math.log(f / underlying()))
                + optionsValue / riskFreeDiscount();
    }


    //
    // inspectors (mirrors C++ protected helpers)
    //

    private double underlying() {
        return process_.x0();
    }

    private double residualTime() {
        final VarianceSwap.ArgumentsImpl a = (VarianceSwap.ArgumentsImpl) arguments_;
        return process_.time(a.maturityDate);
    }

    private double riskFreeRate() {
        return process_.riskFreeRate().currentLink()
                .zeroRate(residualTime(), Compounding.Continuous,
                        Frequency.NoFrequency, true).rate();
    }

    private double riskFreeDiscount() {
        return process_.riskFreeRate().currentLink().discount(residualTime());
    }


    //
    // helpers
    //

    private static double[] append(final double[] a, final double v) {
        final double[] out = new double[a.length + 1];
        System.arraycopy(a, 0, out, 0, a.length);
        out[a.length] = v;
        return out;
    }

    private static void reverseInPlace(final double[] a) {
        for (int i = 0, j = a.length - 1; i < j; i++, j--) {
            final double tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
    }

    /** Removes consecutive duplicates (mirrors C++ {@code std::unique}). */
    private static double[] dedupAdjacent(final double[] a) {
        if (a.length == 0) { return a; }
        final double[] tmp = new double[a.length];
        tmp[0] = a[0];
        int j = 1;
        for (int i = 1; i < a.length; i++) {
            if (a[i] != tmp[j - 1]) {
                tmp[j++] = a[i];
            }
        }
        final double[] out = new double[j];
        System.arraycopy(tmp, 0, out, 0, j);
        return out;
    }


    //
    // public types
    //

    /**
     * (payoff, weight) pair used by the replication. Public so that the
     * {@code optionWeights} additional result is observable from tests
     * that mirror C++ {@code additionalResults["optionWeights"]}.
     */
    public static final class WeightedPayoff {
        public final StrikedTypePayoff payoff;
        public final double weight;
        public WeightedPayoff(final StrikedTypePayoff payoff, final double weight) {
            this.payoff = payoff;
            this.weight = weight;
        }
    }
}
