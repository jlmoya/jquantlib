/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2015 Thema Consulting SA
*/

package org.jquantlib.pricingengines.barrier;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.barrieroption.DoubleBarrierOption;
import org.jquantlib.experimental.barrieroption.DoubleBarrierType;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Pricing engine for double-barrier European options using the Ikeda/Kunitomo (1992) analytical infinite-series
 * expansion.
 * <p>
 * Mirrors {@code QuantLib::AnalyticDoubleBarrierEngine} from
 * {@code ql/pricingengines/barrier/analyticdoublebarrierengine.{hpp,cpp}} (v1.42.1).
 *
 * <p>The formulas are taken from "The complete guide to option pricing formulas
 * 2nd Ed", E.G. Haug, McGraw-Hill, p.156 and following, which itself implements the Ikeda and Kunitomo series (see
 * "Pricing Options with Curved Boundaries", Mathematical Finance 2/1992). This code handles only flat barriers.
 *
 * <p>Supports {@link DoubleBarrierType#KnockIn} and
 * {@link DoubleBarrierType#KnockOut}. {@code KIKO} and {@code KOKI} are rejected (the C++ engine fails on those).
 *
 * <p>Series truncation defaults to 5 terms (matching the C++ default).
 *
 * @author JQuantLib migration
 */
public class AnalyticDoubleBarrierEngine extends DoubleBarrierOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process_;
    private final int series_;
    private final CumulativeNormalDistribution f_;

    public AnalyticDoubleBarrierEngine(final GeneralizedBlackScholesProcess process) {
        this(process, 5);
    }

    public AnalyticDoubleBarrierEngine(final GeneralizedBlackScholesProcess process, final int series) {
        this.process_ = process;
        this.series_ = series;
        this.f_ = new CumulativeNormalDistribution();
        this.process_.addObserver(this);
    }

    @Override
    public void calculate() {
        final DoubleBarrierOption.ArgumentsImpl a = args();
        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;

        QL.require(a.exercise.type() == Exercise.Type.European, "this engine handles only european options");

        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;

        QL.require(payoff.strike() > 0.0, "strike must be positive");

        final double spot = underlying();
        QL.require(spot > 0.0, "negative or null underlying given");
        QL.require(!triggered(spot), "barrier(s) already touched");

        final DoubleBarrierType barrierType = a.barrierType;

        switch ( payoff.optionType() ) {
        case Call:
            switch ( barrierType ) {
            case KnockIn:
                r.value = callKI();
                break;
            case KnockOut:
                r.value = callKO();
                break;
            case KIKO:
            case KOKI:
                throw new LibraryException("unsupported double-barrier type: " + barrierType);
            default:
                throw new LibraryException("unknown double-barrier type: " + barrierType);
            }
            break;
        case Put:
            switch ( barrierType ) {
            case KnockIn:
                r.value = putKI();
                break;
            case KnockOut:
                r.value = putKO();
                break;
            case KIKO:
            case KOKI:
                throw new LibraryException("unsupported double-barrier type: " + barrierType);
            default:
                throw new LibraryException("unknown double-barrier type: " + barrierType);
            }
            break;
        default:
            throw new LibraryException("unknown type");
        }
    }

    //
    // private helpers (mirror C++ private members)
    //

    private double underlying() {
        return process_.x0();
    }

    private double strike() {
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) args().payoff;
        return payoff.strike();
    }

    private double residualTime() {
        return process_.time(args().exercise.lastDate());
    }

    private double volatility() {
        return process_.blackVolatility().currentLink().blackVol(residualTime(), strike());
    }

    private double volatilitySquared() {
        final double v = volatility();
        return v * v;
    }

    private double stdDeviation() {
        return volatility() * Math.sqrt(residualTime());
    }

    private double barrierLo() {
        return args().barrier_lo;
    }

    private double barrierHi() {
        return args().barrier_hi;
    }

    private double riskFreeRate() {
        return process_.riskFreeRate().currentLink()
                .zeroRate(residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double riskFreeDiscount() {
        return process_.riskFreeRate().currentLink().discount(residualTime());
    }

    private double dividendYield() {
        return process_.dividendYield().currentLink()
                .zeroRate(residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double dividendDiscount() {
        return process_.dividendYield().currentLink().discount(residualTime());
    }

    private double costOfCarry() {
        return riskFreeRate() - dividendYield();
    }

    /**
     * Vanilla equivalent — used by KI variants (KI = vanilla - KO). Mirrors
     * {@code AnalyticDoubleBarrierEngine::vanillaEquivalent()} (C++).
     */
    private double vanillaEquivalent() {
        final StrikedTypePayoff payoff = (StrikedTypePayoff) args().payoff;
        final double forwardPrice = underlying() * dividendDiscount() / riskFreeDiscount();
        final BlackCalculator black = new BlackCalculator(payoff, forwardPrice, stdDeviation(), riskFreeDiscount());
        double vanilla = black.value();
        if ( vanilla < 0.0 ) {
            vanilla = 0.0;
        }
        return vanilla;
    }

    /**
     * Knock-out call (Ikeda/Kunitomo series). Mirrors {@code AnalyticDoubleBarrierEngine::callKO()} (C++).
     */
    private double callKO() {
        // N.B. for flat barriers mu3=mu1 and mu2=0
        final double mu1 = 2.0 * costOfCarry() / volatilitySquared() + 1.0;
        final double bsigma = (costOfCarry() + volatilitySquared() / 2.0) * residualTime() / stdDeviation();
        final double sd = stdDeviation();
        final double L = barrierLo();
        final double H = barrierHi();
        final double S = underlying();
        final double K = strike();

        double acc1 = 0.0;
        double acc2 = 0.0;
        for ( int n = -series_; n <= series_; ++n ) {
            final double L2n = Math.pow(L, 2.0 * n);
            final double U2n = Math.pow(H, 2.0 * n);
            final double d1 = Math.log(S * U2n / (K * L2n)) / sd + bsigma;
            final double d2 = Math.log(S * U2n / (H * L2n)) / sd + bsigma;
            final double d3 = Math.log(Math.pow(L, 2.0 * n + 2.0) / (K * S * U2n)) / sd + bsigma;
            final double d4 = Math.log(Math.pow(L, 2.0 * n + 2.0) / (H * S * U2n)) / sd + bsigma;

            final double Hn = Math.pow(H, n);
            final double Ln = Math.pow(L, n);
            final double Lnp1 = Math.pow(L, n + 1);

            acc1 += Math.pow(Hn / Ln, mu1) * (f_.op(d1) - f_.op(d2)) - Math.pow(Lnp1 / (Hn * S), mu1) * (f_.op(d3)
                    - f_.op(d4));

            acc2 += Math.pow(Hn / Ln, mu1 - 2.0) * (f_.op(d1 - sd) - f_.op(d2 - sd))
                    - Math.pow(Lnp1 / (Hn * S), mu1 - 2.0) * (f_.op(d3 - sd) - f_.op(d4 - sd));
        }

        final double rend = Math.exp(-dividendYield() * residualTime());
        final double kov = S * rend * acc1 - K * riskFreeDiscount() * acc2;
        return Math.max(0.0, kov);
    }

    /**
     * Knock-in call — equates to vanilla - KO. Mirrors {@code AnalyticDoubleBarrierEngine::callKI()} (C++).
     */
    private double callKI() {
        return Math.max(0.0, vanillaEquivalent() - callKO());
    }

    /**
     * Knock-out put (Ikeda/Kunitomo series). Mirrors {@code AnalyticDoubleBarrierEngine::putKO()} (C++).
     */
    private double putKO() {
        final double mu1 = 2.0 * costOfCarry() / volatilitySquared() + 1.0;
        final double bsigma = (costOfCarry() + volatilitySquared() / 2.0) * residualTime() / stdDeviation();
        final double sd = stdDeviation();
        final double L = barrierLo();
        final double H = barrierHi();
        final double S = underlying();
        final double K = strike();

        double acc1 = 0.0;
        double acc2 = 0.0;
        for ( int n = -series_; n <= series_; ++n ) {
            final double L2n = Math.pow(L, 2.0 * n);
            final double U2n = Math.pow(H, 2.0 * n);
            final double y1 = Math.log(S * U2n / Math.pow(L, 2.0 * n + 1.0)) / sd + bsigma;
            final double y2 = Math.log(S * U2n / (K * L2n)) / sd + bsigma;
            final double y3 = Math.log(Math.pow(L, 2.0 * n + 2.0) / (L * S * U2n)) / sd + bsigma;
            final double y4 = Math.log(Math.pow(L, 2.0 * n + 2.0) / (K * S * U2n)) / sd + bsigma;

            final double Hn = Math.pow(H, n);
            final double Ln = Math.pow(L, n);
            final double Lnp1 = Math.pow(L, n + 1);

            acc1 += Math.pow(Hn / Ln, mu1 - 2.0) * (f_.op(y1 - sd) - f_.op(y2 - sd))
                    - Math.pow(Lnp1 / (Hn * S), mu1 - 2.0) * (f_.op(y3 - sd) - f_.op(y4 - sd));

            acc2 += Math.pow(Hn / Ln, mu1) * (f_.op(y1) - f_.op(y2)) - Math.pow(Lnp1 / (Hn * S), mu1) * (f_.op(y3)
                    - f_.op(y4));
        }

        final double rend = Math.exp(-dividendYield() * residualTime());
        final double kov = K * riskFreeDiscount() * acc1 - S * rend * acc2;
        return Math.max(0.0, kov);
    }

    /**
     * Knock-in put — equates to vanilla - KO. Mirrors {@code AnalyticDoubleBarrierEngine::putKI()} (C++).
     */
    private double putKI() {
        return Math.max(0.0, vanillaEquivalent() - putKO());
    }
}
