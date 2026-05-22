/*
 Copyright (C) 2007 Richard Gomes

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
 Copyright (C) 2003, 2007 Ferdinando Ametrano
 Copyright (C) 2003, 2007 StatPro Italia srl

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

package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AssetOrNothingPayoff;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.instruments.CashOrNothingPayoff;
import org.jquantlib.instruments.DividendVanillaOption;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.GapPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Option.Type;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.lang.annotation.NonNegative;
import org.jquantlib.math.interpolations.factories.BicubicSpline;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc.FdmSchemeType;
import org.jquantlib.methods.lattices.AdditiveEQPBinomialTree;
import org.jquantlib.methods.lattices.CoxRossRubinstein;
import org.jquantlib.methods.lattices.JarrowRudd;
import org.jquantlib.methods.lattices.Joshi4;
import org.jquantlib.methods.lattices.LeisenReimer;
import org.jquantlib.methods.lattices.Tian;
import org.jquantlib.methods.lattices.Trigeorgis;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticDividendEuropeanEngine;
import org.jquantlib.pricingengines.vanilla.BinomialVanillaEngine;
import org.jquantlib.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import org.jquantlib.pricingengines.vanilla.IntegralEngine;
import org.jquantlib.pricingengines.vanilla.MCEuropeanEngine;
import org.jquantlib.pricingengines.vanilla.MCEuropeanEngineLowDiscrepancy;
import org.jquantlib.experimental.variancegamma.FFTEngine;
import org.jquantlib.experimental.variancegamma.FFTVanillaEngine;
import org.jquantlib.pricingengines.vanilla.finitedifferences.FDEuropeanEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackVarianceSurface;
import org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve;
import org.jquantlib.testsuite.util.Flag;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;


/**
 * European Options test suite
 *
 * @author Richard Gomes
 */
public class EuropeanOptionTest {

    // private final Date today;

    public EuropeanOptionTest() {
        QL.info("::::: "+this.getClass().getSimpleName()+" :::::");
    }


    private static class EuropeanOptionData {
        private final Option.Type type;            // option type
        private final /*@Real*/ double strike;    // option strike price
        private final double s;                    // spot // FIXME: any specific @annotation?
        private final /*@Real*/ double  q;        // dividend
        private final /*@Rate*/ double  r;         // risk-free rate
        private final /*@Time*/ double  t;         // time to maturity
        private final /*@Volatility*/ double v;    // volatility
        private final /*@Real*/ double result;    // expected result
        private final double tol;                  // tolerance // FIXME: any specific @annotation?

        public EuropeanOptionData(
                final Option.Type type,
                /*@Real*/ final double strike,
                final double s, /*@Real*/ final double  q,
                /*@Rate*/ final double  r,
                /*@Time*/ final double  t,
                /*@Volatility*/ final double v,
                /*@Real*/ final double result,
                final double tol) {
            this.type = type;
            this.strike = strike;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
            this.result = result;
            this.tol = tol;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append('[');
            sb.append(type).append(", ");
            sb.append(strike).append(", ");
            sb.append(s).append(", ");
            sb.append(q).append(", ");
            sb.append(r).append(", ");
            sb.append(t).append(", ");
            sb.append(v).append(", ");
            sb.append(result).append(", ");
            sb.append(tol);
            sb.append(']');
            return sb.toString();
        }
    }

    private enum EngineType {
        Analytic,
        JR, CRR, EQP, TGEO, TIAN, LR, JOSHI,
        FiniteDifferences,
        Integral,
        PseudoMonteCarlo, QuasiMonteCarlo; }


    private GeneralizedBlackScholesProcess makeProcess(
            final Quote u,
            final YieldTermStructure q,
            final YieldTermStructure r,
            final BlackVolTermStructure vol) {
        return new BlackScholesMertonProcess(
                new Handle<Quote>(u),
                new Handle<YieldTermStructure>(q),
                new Handle<YieldTermStructure>(r),
                new Handle<BlackVolTermStructure>(vol));
    }


    private VanillaOption makeOption(
            final StrikedTypePayoff payoff,
            final Exercise exercise,
            final SimpleQuote u,
            final YieldTermStructure q,
            final YieldTermStructure r,
            final BlackVolTermStructure vol,
            final EngineType engineType,
            final int binomialSteps,
            final int samples) {

        final GeneralizedBlackScholesProcess stochProcess = makeProcess(u,q,r,vol);
        final PricingEngine engine;

        switch (engineType) {
            case Analytic:
                engine = new AnalyticEuropeanEngine(stochProcess);
                break;
            case JR:
                engine = new BinomialVanillaEngine<JarrowRudd>(JarrowRudd.class, stochProcess, binomialSteps);
                break;
            case CRR:
                engine = new BinomialVanillaEngine<CoxRossRubinstein>(CoxRossRubinstein.class, stochProcess, binomialSteps);
                break;
            case EQP:
                engine = new BinomialVanillaEngine<AdditiveEQPBinomialTree>(AdditiveEQPBinomialTree.class, stochProcess, binomialSteps);
                break;
            case TGEO:
                engine = new BinomialVanillaEngine<Trigeorgis>(Trigeorgis.class, stochProcess, binomialSteps);
                break;
            case TIAN:
                engine = new BinomialVanillaEngine<Tian>(Tian.class, stochProcess, binomialSteps);
                break;
            case LR:
                engine = new BinomialVanillaEngine<LeisenReimer>(LeisenReimer.class, stochProcess, binomialSteps);
                break;
            case JOSHI:
                engine = new BinomialVanillaEngine<Joshi4>(Joshi4.class, stochProcess, binomialSteps);
                break;
            case FiniteDifferences:
                engine = new FDEuropeanEngine(stochProcess, binomialSteps,samples);
                break;
            case Integral:
                engine = new IntegralEngine(stochProcess);
                break;

                //        case PseudoMonteCarlo:
                //          engine = MakeMCEuropeanEngine<PseudoRandom>().withSteps(1)
                //                                                       .withSamples(samples)
                //                                                       .withSeed(42);
                //          break;

                //        case QuasiMonteCarlo:
                //          engine = MakeMCEuropeanEngine<LowDiscrepancy>().withSteps(1)
                //                                                         .withSamples(samples);
                //          break;

            default:
                throw new UnsupportedOperationException("unknown engine type: "+engineType);
        }

        final VanillaOption option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(engine);
        return option;
    }




    //  std::string engineTypeToString(EngineType type) {
    //      switch (type) {
    //        case Analytic:
    //          return "analytic";
    //        case JR:
    //          return "Jarrow-Rudd";
    //        case CRR:
    //          return "Cox-Ross-Rubinstein";
    //        case EQP:
    //          return "EQP";
    //        case TGEO:
    //          return "Trigeorgis";
    //        case TIAN:
    //          return "Tian";
    //        case LR:
    //          return "LeisenReimer";
    //        case JOSHI:
    //          return "Joshi";
    //        case FiniteDifferences:
    //          return "FiniteDifferences";
    //      case Integral:
    //          return "Integral";
    //        case PseudoMonteCarlo:
    //          return "MonteCarlo";
    //        case QuasiMonteCarlo:
    //          return "Quasi-MonteCarlo";
    //        default:
    //          QL_FAIL("unknown engine type");
    //      }
    //  }

    private int timeToDays(/*@Time*/ final double t) {
        return (int) (t*360+0.5);
    }


    @Test
    public void testValues() {

        QL.info("Testing European option values...");

        /**
         *  The data below are from "Option pricing formulas", E.G. Haug, McGraw-Hill 1998
         */
        final EuropeanOptionData values[] = new EuropeanOptionData[] {
                // pag 2-8
                //                              type,     strike,   spot,    q,    r,    t,  vol,   value,    tol
                new EuropeanOptionData( Option.Type.Call,  65.00,  60.00, 0.00, 0.08, 0.25, 0.30,  2.1334, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,   95.00, 100.00, 0.05, 0.10, 0.50, 0.20,  2.4648, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,   19.00,  19.00, 0.10, 0.10, 0.75, 0.28,  1.7011, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call,  19.00,  19.00, 0.10, 0.10, 0.75, 0.28,  1.7011, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call,   1.60,   1.56, 0.08, 0.06, 0.50, 0.12,  0.0291, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,   70.00,  75.00, 0.05, 0.10, 0.50, 0.35,  4.0870, 1.0e-4),
                // pag 24
                new EuropeanOptionData( Option.Type.Call, 100.00,  90.00, 0.10, 0.10, 0.10, 0.15,  0.0205, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.10, 0.15,  1.8734, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.10, 0.15,  9.9413, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00,  90.00, 0.10, 0.10, 0.10, 0.25,  0.3150, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.10, 0.25,  3.1217, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.10, 0.25, 10.3556, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00,  90.00, 0.10, 0.10, 0.10, 0.35,  0.9474, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.10, 0.35,  4.3693, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.10, 0.35, 11.1381, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00,  90.00, 0.10, 0.10, 0.50, 0.15,  0.8069, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.50, 0.15,  4.0232, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.50, 0.15, 10.5769, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00,  90.00, 0.10, 0.10, 0.50, 0.25,  2.7026, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.50, 0.25,  6.6997, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.50, 0.25, 12.7857, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00,  90.00, 0.10, 0.10, 0.50, 0.35,  4.9329, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.50, 0.35,  9.3679, 1.0e-4),
                new EuropeanOptionData( Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.50, 0.35, 15.3086, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00,  90.00, 0.10, 0.10, 0.10, 0.15,  9.9210, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00, 100.00, 0.10, 0.10, 0.10, 0.15,  1.8734, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00, 110.00, 0.10, 0.10, 0.10, 0.15,  0.0408, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00,  90.00, 0.10, 0.10, 0.10, 0.25, 10.2155, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00, 100.00, 0.10, 0.10, 0.10, 0.25,  3.1217, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00, 110.00, 0.10, 0.10, 0.10, 0.25,  0.4551, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00,  90.00, 0.10, 0.10, 0.10, 0.35, 10.8479, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00, 100.00, 0.10, 0.10, 0.10, 0.35,  4.3693, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00, 110.00, 0.10, 0.10, 0.10, 0.35,  1.2376, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00,  90.00, 0.10, 0.10, 0.50, 0.15, 10.3192, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00, 100.00, 0.10, 0.10, 0.50, 0.15,  4.0232, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00, 110.00, 0.10, 0.10, 0.50, 0.15,  1.0646, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00,  90.00, 0.10, 0.10, 0.50, 0.25, 12.2149, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00, 100.00, 0.10, 0.10, 0.50, 0.25,  6.6997, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00, 110.00, 0.10, 0.10, 0.50, 0.25,  3.2734, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00,  90.00, 0.10, 0.10, 0.50, 0.35, 14.4452, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00, 100.00, 0.10, 0.10, 0.50, 0.35,  9.3679, 1.0e-4),
                new EuropeanOptionData( Option.Type.Put,  100.00, 110.00, 0.10, 0.10, 0.50, 0.35,  5.7963, 1.0e-4),
                // pag 27
                new EuropeanOptionData( Option.Type.Call,  40.00,  42.00, 0.08, 0.04, 0.75, 0.35,  5.0975, 1.0e-4)
        };

        final Date today = new Settings().evaluationDate();

        final DayCounter dc = new Actual360();

        final SimpleQuote           spot  = new SimpleQuote(0.0);
        final SimpleQuote           qRate = new SimpleQuote(0.0);
        final YieldTermStructure    qTS   = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote           rRate = new SimpleQuote(0.0);
        final YieldTermStructure    rTS   = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote           vol   = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        for (int i=0; i<values.length-1; i++) {

            QL.debug(values[i].toString());

            final StrikedTypePayoff payoff = new PlainVanillaPayoff(values[i].type, values[i].strike);
            final Date exDate = today.add( timeToDays(values[i].t) );
            final Exercise exercise = new EuropeanExercise(exDate);

            spot.setValue(values[i].s);
            qRate.setValue(values[i].q);
            rRate.setValue(values[i].r);
            vol.setValue(values[i].v);


            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final PricingEngine engine = new AnalyticEuropeanEngine(stochProcess);

            final EuropeanOption option = new EuropeanOption(payoff, exercise);
            option.setPricingEngine(engine);

            final double calculated = option.NPV();
            final double error = Math.abs(calculated-values[i].result);
            final double tolerance = values[i].tol;

            final StringBuilder sb = new StringBuilder();
            sb.append("error ").append(error).append(" .gt. tolerance ").append(tolerance).append('\n');
            sb.append("    calculated ").append(calculated).append('\n');
            sb.append("    type ").append(values[i].type).append('\n');
            sb.append("    strike ").append(values[i].strike).append('\n');
            sb.append("    s ").append(values[i].s).append('\n');
            sb.append("    q ").append(values[i].q).append('\n');
            sb.append("    r ").append(values[i].r).append('\n');
            sb.append("    t ").append(values[i].t).append('\n');
            sb.append("    v ").append(values[i].v).append('\n');
            sb.append("    result ").append(values[i].result).append('\n');
            sb.append("    tol ").append(values[i].tol); // .append('\n');

            if (error<=tolerance) {
                QL.info(" error="+error);
            } else {
                fail(exercise + " " + payoff.optionType() + " option with " + payoff + " payoff:\n"
                        + "    spot value:       " + values[i].s + "\n"
                        + "    strike:           " + payoff.strike() + "\n"
                        + "    dividend yield:   " + values[i].q + "\n"
                        + "    risk-free rate:   " + values[i].r + "\n"
                        + "    reference date:   " + today + "\n"
                        + "    maturity:         " + values[i].t + "\n"
                        + "    volatility:       " + values[i].v + "\n\n"
                        + "    expected:         " + values[i].result + "\n"
                        + "    calculated:       " + calculated + "\n"
                        + "    error:            " + error + "\n"
                        + "    tolerance:        " + tolerance);
            }
        }
    }

    @Test
    public void testGreekValues(){
        QL.info("Testing European option greek values...");

        //
        // The data below are from "Option pricing formulas", E.G. Haug, McGraw-Hill 1998 pag 11-16
        //

        final EuropeanOptionData values[] = {
                //                     type,             strike, spot,   q,    r,    t,        vol,  value,    tolerance
                //                     ================  ======  ======  ====  ====  ========  ====  ========  =========
                new EuropeanOptionData(Option.Type.Call, 100.00, 105.00, 0.10, 0.10, 0.500000, 0.36,   0.5946, 0),
                new EuropeanOptionData(Option.Type.Put,  100.00, 105.00, 0.10, 0.10, 0.500000, 0.36,  -0.3566, 0),
                new EuropeanOptionData(Option.Type.Put,  100.00, 105.00, 0.10, 0.10, 0.500000, 0.36,  -4.8775, 0),
                new EuropeanOptionData(Option.Type.Call,  60.00,  55.00, 0.00, 0.10, 0.750000, 0.30,   0.0278, 0),
                new EuropeanOptionData(Option.Type.Put,   60.00,  55.00, 0.00, 0.10, 0.750000, 0.30,   0.0278, 0),
                new EuropeanOptionData(Option.Type.Call,  60.00,  55.00, 0.00, 0.10, 0.750000, 0.30,  18.9358, 0),
                new EuropeanOptionData(Option.Type.Put,   60.00,  55.00, 0.00, 0.10, 0.750000, 0.30,  18.9358, 0),
                new EuropeanOptionData(Option.Type.Put,  405.00, 430.00, 0.05, 0.07, 1.0/12.0, 0.20, -31.1924, 0),
                new EuropeanOptionData(Option.Type.Put,  405.00, 430.00, 0.05, 0.07, 1.0/12.0, 0.20,  -0.0855, 0),
                new EuropeanOptionData(Option.Type.Call,  75.00,  72.00, 0.00, 0.09, 1.000000, 0.19,  38.7325, 0),
                new EuropeanOptionData(Option.Type.Put,  490.00, 500.00, 0.05, 0.08, 0.250000, 0.15,  42.2254, 0)
        };

        // tolerance is fixed
        final double tolerance = 1e-4;


        final Date today = new Settings().evaluationDate();

        final DayCounter         dc    = new Actual360();
        final SimpleQuote        spot  = new SimpleQuote(0.0);
        final SimpleQuote        qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS   = Utilities.flatRate(today, qRate, dc);

        final SimpleQuote           rRate = new SimpleQuote(0.0);
        final YieldTermStructure    rTS   = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote           vol   = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);


        StrikedTypePayoff payoff;
        Date exDate;
        Exercise exercise;
        double calculated;
        double error;

        int i = -1;

        // testing delta 1
        i++;
        payoff = new PlainVanillaPayoff(values[i].type, values[i].strike);
        exDate = today.add(timeToDays(values[i].t));
        exercise = new EuropeanExercise(exDate);
        spot.setValue(values[i].s);
        qRate.setValue(values[i].q);
        rRate.setValue(values[i].r);
        vol.setValue(values[i].v);

        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));
        final PricingEngine engine = new AnalyticEuropeanEngine(stochProcess);

        VanillaOption option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(engine);

        calculated = option.delta();
        error = Math.abs(calculated - values[i].result);

        if (error > tolerance) {
            REPORT_FAILURE("delta", payoff, exercise, values[i].s, values[i].q, values[i].r, today, values[i].v,
                    values[i].result, calculated, error, tolerance);
        }

        //testing delta 2
        i++;
        payoff = new PlainVanillaPayoff(values[i].type, values[i].strike);
        exDate = today.add(timeToDays(values[i].t));
        exercise = new EuropeanExercise(exDate);
        spot.setValue(values[i].s);
        qRate.setValue(values[i].q);
        rRate.setValue(values[i].r);
        vol.setValue(values[i].v);

        option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(engine);

        calculated = option.delta();
        error = Math.abs(calculated - values[i].result);
        if(error>tolerance) {
            REPORT_FAILURE("delta", payoff, exercise, values[i].s, values[i].q, values[i].r, today, values[i].v,
                    values[i].result, calculated, error, tolerance);
        }

        //testing elasticity
        i++;
        payoff = new PlainVanillaPayoff(values[i].type, values[i].strike);
        exDate = today.add(timeToDays(values[i].t));
        exercise = new EuropeanExercise(exDate);
        spot.setValue(values[i].s);
        qRate.setValue(values[i].q);
        rRate.setValue(values[i].r);
        vol.setValue(values[i].v);

        option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(engine);

        calculated = option.elasticity();
        error = Math.abs(Math.abs(calculated - values[i].result));
        if(error>tolerance) {
            REPORT_FAILURE("elasticity", payoff, exercise, values[i].s, values[i].q, values[i].r, today, values[i].v,
                    values[i].result, calculated, error, tolerance);
        }

        // testing gamma 1
        i++;
        payoff = new PlainVanillaPayoff(values[i].type, values[i].strike);
        exDate = today.add(timeToDays(values[i].t));
        exercise = new EuropeanExercise(exDate);
        spot.setValue(values[i].s);
        qRate.setValue(values[i].q);
        rRate.setValue(values[i].r);
        vol.setValue(values[i].v);

        option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(engine);

        calculated = option.gamma();
        error = Math.abs(Math.abs(calculated - values[i].result));
        if(error>tolerance) {
            REPORT_FAILURE("gamma", payoff, exercise, values[i].s, values[i].q, values[i].r, today, values[i].v,
                    values[i].result, calculated, error, tolerance);
        }

        // testing gamma 2
        i++;
        payoff = new PlainVanillaPayoff(values[i].type, values[i].strike);
        exDate = today.add(timeToDays(values[i].t));
        exercise = new EuropeanExercise(exDate);
        spot.setValue(values[i].s);
        qRate.setValue(values[i].q);
        rRate.setValue(values[i].r);
        vol.setValue(values[i].v);

        option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(engine);

        calculated = option.gamma();
        error = Math.abs(Math.abs(calculated - values[i].result));
        if(error>tolerance) {
            REPORT_FAILURE("gamma", payoff, exercise, values[i].s, values[i].q, values[i].r, today, values[i].v,
                    values[i].result, calculated, error, tolerance);
        }

        //testing vega 1
        i++;
        payoff = new PlainVanillaPayoff(values[i].type, values[i].strike);
        exDate = today.add(timeToDays(values[i].t));
        exercise = new EuropeanExercise(exDate);
        spot.setValue(values[i].s);
        qRate.setValue(values[i].q);
        rRate.setValue(values[i].r);
        vol.setValue(values[i].v);

        option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(engine);

        calculated = option.vega();
        error = Math.abs(Math.abs(calculated - values[i].result));
        if(error>tolerance) {
            REPORT_FAILURE("vega", payoff, exercise, values[i].s, values[i].q, values[i].r, today, values[i].v,
                    values[i].result, calculated, error, tolerance);
        }

        //testing vega 2
        i++;
        payoff = new PlainVanillaPayoff(values[i].type, values[i].strike);
        exDate = today.add(timeToDays(values[i].t));
        exercise = new EuropeanExercise(exDate);
        spot.setValue(values[i].s);
        qRate.setValue(values[i].q);
        rRate.setValue(values[i].r);
        vol.setValue(values[i].v);

        option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(engine);

        calculated = option.vega();
        error = Math.abs(Math.abs(calculated - values[i].result));
        if(error>tolerance) {
            REPORT_FAILURE("vega", payoff, exercise, values[i].s, values[i].q, values[i].r, today, values[i].v,
                    values[i].result, calculated, error, tolerance);
        }

        //testing theta
        i++;
        payoff = new PlainVanillaPayoff(values[i].type, values[i].strike);
        exDate = today.add(timeToDays(values[i].t));
        exercise = new EuropeanExercise(exDate);
        spot.setValue(values[i].s);
        qRate.setValue(values[i].q);
        rRate.setValue(values[i].r);
        vol.setValue(values[i].v);

        option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(engine);

        calculated = option.theta();
        error = Math.abs(Math.abs(calculated - values[i].result));
        if(error>tolerance) {
            REPORT_FAILURE("theta", payoff, exercise, values[i].s, values[i].q, values[i].r, today, values[i].v,
                    values[i].result, calculated, error, tolerance);
        }


        //testing theta per day
        i++;
        payoff = new PlainVanillaPayoff(values[i].type, values[i].strike);
        exDate = today.add(timeToDays(values[i].t));
        exercise = new EuropeanExercise(exDate);
        spot.setValue(values[i].s);
        qRate.setValue(values[i].q);
        rRate.setValue(values[i].r);
        vol.setValue(values[i].v);

        option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(engine);

        calculated = option.thetaPerDay();
        error = Math.abs(Math.abs(calculated - values[i].result));
        if(error>tolerance) {
            REPORT_FAILURE("theta per day", payoff, exercise, values[i].s, values[i].q, values[i].r, today, values[i].v,
                    values[i].result, calculated, error, tolerance);
        }


        //testing rho
        i++;
        payoff = new PlainVanillaPayoff(values[i].type, values[i].strike);
        exDate = today.add(timeToDays(values[i].t));
        exercise = new EuropeanExercise(exDate);
        spot.setValue(values[i].s);
        qRate.setValue(values[i].q);
        rRate.setValue(values[i].r);
        vol.setValue(values[i].v);

        option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(engine);

        calculated = option.rho();
        error = Math.abs(Math.abs(calculated - values[i].result));
        if(error>tolerance) {
            REPORT_FAILURE("rho", payoff, exercise, values[i].s, values[i].q, values[i].r, today, values[i].v,
                    values[i].result, calculated, error, tolerance);
        }

        //testing dividend rho
        i++;
        payoff = new PlainVanillaPayoff(values[i].type, values[i].strike);
        exDate = today.add(timeToDays(values[i].t));
        exercise = new EuropeanExercise(exDate);
        spot.setValue(values[i].s);
        qRate.setValue(values[i].q);
        rRate.setValue(values[i].r);
        vol.setValue(values[i].v);

        option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(engine);

        calculated = option.dividendRho();
        error = Math.abs(Math.abs(calculated - values[i].result));
        if(error>tolerance) {
            REPORT_FAILURE("dividend rho", payoff, exercise, values[i].s, values[i].q, values[i].r, today, values[i].v,
                    values[i].result, calculated, error, tolerance);
        }
    }


    @Test
    public void testGreeks() {
        QL.info("Testing analytic European option greeks...");

        final Map<String,Double> tolerance = new HashMap<>();
        tolerance.put("delta",  1.0e-5);
        tolerance.put("gamma",  1.0e-5);
        tolerance.put("theta",  1.0e-5);
        tolerance.put("rho",    1.0e-5);
        tolerance.put("divRho", 1.0e-5);
        tolerance.put("vega",   1.0e-5);

        final Map<String,Double> expected = new HashMap<>();
        final Map<String,Double> calculated = new HashMap<>();

        final Option.Type types[] = { Option.Type.Call, Option.Type.Put };
        final double strikes[] = { 50.0, 99.5, 100.0, 100.5, 150.0 };
        final double underlyings[] = { 100.0 };
        final double qRates[] = { 0.04, 0.05, 0.06 };
        final double rRates[] = { 0.01, 0.05, 0.15 };
        final double residualTimes[] = { 1.0, 2.0 };
        final double vols[] = { 0.11, 0.50, 1.20 };

        final DayCounter dc = new Actual360();
        new Settings().setEvaluationDate(Date.todaysDate());
        final Date today = new Settings().evaluationDate();

        final SimpleQuote           spot  = new SimpleQuote(0.0);
        final SimpleQuote           qRate = new SimpleQuote(0.0);
        final YieldTermStructure    qTS   = Utilities.flatRate(qRate, dc);
        final SimpleQuote           rRate = new SimpleQuote(0.0);
        final YieldTermStructure    rTS   = Utilities.flatRate(rRate, dc);
        final SimpleQuote           vol   = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(vol, dc);

        for (final Type type : types) {
            for (final double strike : strikes) {
                for (final double residualTime : residualTimes) {

                    final Date exDate = today.add( timeToDays(residualTime) ); //TODO: code review
                    final Exercise exercise = new EuropeanExercise(exDate);

                    for (int kk=0; kk<4; kk++) {
                        StrikedTypePayoff payoff = null;
                        // option to check
                        if (kk==0) {
                            payoff = new PlainVanillaPayoff(type, strike);
                        } else if (kk==1) {
                            //FIXME check constructor
                            payoff = new CashOrNothingPayoff(type, strike, 100);
                        } else if (kk==2) {
                            payoff = new AssetOrNothingPayoff(type, strike);
                        } else if (kk==3) {
                            payoff = new GapPayoff(type, strike, 100);
                        }

                        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                                new Handle<Quote>(spot),
                                new Handle<YieldTermStructure>(qTS),
                                new Handle<YieldTermStructure>(rTS),
                                new Handle<BlackVolTermStructure>(volTS));
                        final PricingEngine engine = new AnalyticEuropeanEngine(stochProcess);

                        if (payoff==null)
                            throw new IllegalArgumentException();

                        final EuropeanOption option = new EuropeanOption(payoff, exercise);
                        option.setPricingEngine(engine);

                        for (final double u : underlyings) {
                            for (final double q : qRates) {
                                for (final double r : rRates) {
                                    for (final double v : vols) {
                                        //something wrong here for vanilla payoff?
                                        spot.setValue(u);
                                        qRate.setValue(q);
                                        rRate.setValue(r);
                                        vol.setValue(v);

                                        final double value = option.NPV();
                                        final double delta = option.delta();
                                        final double gamma = option.gamma();
                                        final double theta = option.theta();
                                        final double rho   = option.rho();
                                        final double drho  = option.dividendRho();
                                        final double vega  = option.vega();

                                        calculated.put("delta",  delta);
                                        calculated.put("gamma",  gamma);
                                        calculated.put("theta",  theta);
                                        calculated.put("rho",    rho);
                                        calculated.put("divRho", drho);
                                        calculated.put("vega",   vega);

                                        if (value > spot.value()*1.0e-5) {
                                            // perturb spot and get delta and gamma
                                            final double du = u*1.0e-4;
                                            spot.setValue(u+du);
                                            double value_p = option.NPV();
                                            final double delta_p = option.delta();
                                            spot.setValue(u-du);

                                            double value_m = option.NPV();
                                            final double delta_m = option.delta();
                                            spot.setValue(u);
                                            expected.put("delta", (value_p - value_m)/(2*du));
                                            expected.put("gamma", (delta_p - delta_m)/(2*du));

                                            // perturb rates and get rho and dividend rho
                                            final double dr = r*1.0e-4;
                                            rRate.setValue(r+dr);
                                            value_p = option.NPV();
                                            rRate.setValue(r-dr);
                                            value_m = option.NPV();
                                            rRate.setValue(r);
                                            expected.put("rho", (value_p - value_m)/(2*dr));

                                            final double dq = q*1.0e-4;
                                            qRate.setValue(q+dq);
                                            value_p = option.NPV();
                                            qRate.setValue(q-dq);
                                            value_m = option.NPV();
                                            qRate.setValue(q);
                                            expected.put("divRho",(value_p - value_m)/(2*dq));

                                            // perturb volatility and get vega
                                            final double dv = v*1.0e-4;
                                            vol.setValue(v+dv);
                                            value_p = option.NPV();
                                            vol.setValue(v-dv);
                                            value_m = option.NPV();
                                            vol.setValue(v);
                                            expected.put("vega",(value_p - value_m)/(2*dv));

                                            // perturb date and get theta
                                            final Date yesterday = today.sub(1);
                                            final Date tomorrow  = today.add(1);
                                            final double dT = dc.yearFraction(yesterday, tomorrow);
                                            new Settings().setEvaluationDate(yesterday);
                                            value_m = option.NPV();
                                            new Settings().setEvaluationDate(tomorrow);
                                            value_p = option.NPV();
                                            new Settings().setEvaluationDate(Date.todaysDate());
                                            expected.put("theta", (value_p - value_m)/dT);

                                            // compare
                                            for (final Entry<String, Double> it: calculated.entrySet()){

                                                final String greek = it.getKey();
                                                final double expct = expected.get(greek);
                                                final double calcl = calculated.get(greek);
                                                final double tol   = tolerance.get(greek);

                                                final double error = Utilities.relativeError(expct,calcl,u);
                                                if (error>tol) {
                                                    REPORT_FAILURE(greek, payoff, exercise, u, q, r, today, v, expct, calcl, error, tol);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }


    @Test
    public void testImpliedVol() {

        QL.info("Testing European option implied volatility...");

        final int maxEvaluations = 100;
        final double tolerance = 1.0e-6;

        // test options
        final Option.Type types[] = { Option.Type.Call, Option.Type.Put };
        final double strikes[] = { 90.0, 99.5, 100.0, 100.5, 110.0 };
        final int lengths[] = { 36, 180, 360, 1080 };

        // test data
        final double underlyings[] = { 90.0, 95.0, 99.9, 100.0, 100.1, 105.0, 110.0 };
        final double qRates[] = { 0.01, 0.05, 0.10 };
        final double rRates[] = { 0.01, 0.05, 0.10 };
        final double vols[] = { 0.01, 0.20, 0.30, 0.70, 0.90 };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();

        final SimpleQuote           spot  = new SimpleQuote(0.0);
        final SimpleQuote           qRate = new SimpleQuote(0.0);
        final YieldTermStructure    qTS   = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote           rRate = new SimpleQuote(0.0);
        final YieldTermStructure    rTS   = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote           vol   = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        for (final Type type : types) {
            for (final double strike2 : strikes) {
                for (final int length : lengths) {
                    // option to check
                    final Date exDate = today.add( length );
                    final Exercise exercise = new EuropeanExercise(exDate);
                    final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike2);
                    final VanillaOption option = makeOption(payoff, exercise, spot, qTS, rTS, volTS, EngineType.Analytic, 0, 0);

                    final GeneralizedBlackScholesProcess process = makeProcess(spot, qTS, rTS,volTS);

                    for (final double u : underlyings) {
                        for (final double q : qRates) {
                            for (final double r : rRates) {
                                for (final double v : vols) {
                                    spot.setValue(u);
                                    qRate.setValue(q);
                                    rRate.setValue(r);
                                    vol.setValue(v);

                                    final double value = option.NPV();
                                    double implVol = 0.0; // just to remove a warning...
                                    if (value != 0.0) {
                                        // shift guess somehow
                                        vol.setValue(v*0.5);
                                        if (Math.abs(value-option.NPV()) <= 1.0e-12) {
                                            // flat price vs vol --- pointless (and
                                            // numerically unstable) to solve
                                            continue;
                                        }

                                        implVol = option.impliedVolatility(value, process, tolerance, maxEvaluations);

                                        if (Math.abs(implVol-v) > tolerance) {
                                            // the difference might not matter
                                            vol.setValue(implVol);
                                            final double value2 = option.NPV();
                                            final double error = Utilities.relativeError(value,value2,u);
                                            if (error > tolerance) {
                                                fail(
                                                        type + " option :\n"
                                                        + "    spot value:          " + u + "\n"
                                                        + "    strike:              "
                                                        + strike2 + "\n"
                                                        + "    dividend yield:      "
                                                        + (q) + "\n"
                                                        + "    risk-free rate:      "
                                                        + (r) + "\n"
                                                        + "    maturity:            "
                                                        + exDate + "\n\n"
                                                        + "    original volatility: "
                                                        + (v) + "\n"
                                                        + "    price:               "
                                                        + value + "\n"
                                                        + "    implied volatility:  "
                                                        + (implVol)
                                                        + "\n"
                                                        + "    corresponding price: "
                                                        + value2 + "\n"
                                                        + "    error:               " + error);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testImpliedVolContainment(){
        QL.info("Testing self-containment of implied volatility calculation... running");

        final int maxEvaluations = 100;
        final double tolerance = 1.0e-6;

        final Date today = new Settings().evaluationDate();

        final DayCounter dc = new Actual360();
        final SimpleQuote           spot  = new SimpleQuote(100.0);
        final Quote                 u     = spot;
        final SimpleQuote           qRate = new SimpleQuote(0.05);
        final YieldTermStructure    qTS   = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote           rRate = new SimpleQuote(0.003);
        final YieldTermStructure    rTS   = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote           vol   = new SimpleQuote(0.20);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        final Date exerciseDate = today.add(Period.ONE_YEAR_FORWARD);
        final Exercise exercise = new EuropeanExercise(exerciseDate);
        final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 100);

        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<Quote>(u),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));
        final PricingEngine engine = new AnalyticEuropeanEngine(stochProcess);

        final EuropeanOption option1 = new EuropeanOption(payoff, exercise);
        final EuropeanOption option2 = new EuropeanOption(payoff, exercise);
        option1.setPricingEngine(engine);
        option2.setPricingEngine(engine);

        // test
        final double refValue = option2.NPV();

        final Flag f = new Flag();
        option2.addObserver(f);

        option1.impliedVolatility(refValue*1.5, stochProcess, tolerance, maxEvaluations);

        if (f.isUp()) {
            fail("implied volatility calculation triggered a change in another instrument");
        }

        option2.recalculate();
        if (Math.abs(option2.NPV() - refValue) >= 1.0e-8) {
            fail("implied volatility calculation changed the value "
                    + "of another instrument: \n"
                    + "previous value: " + refValue + "\n"
                    + "current value:  " + option2.NPV());
        }

        vol.setValue(vol.value()*1.5);

        if (!f.isUp()) {
            fail("volatility change not notified");
        }

        if (Math.abs(option2.NPV() - refValue) <= 1.0e-8) {
            fail("volatility change did not cause the value to change");
        }
    }



    private void testEngineConsistency(
            final EngineType engine,
            final int binomialSteps, final int samples,
            final Map<String, Double> tolerance) {

        testEngineConsistency(engine, binomialSteps, samples, tolerance, false);
    }

    private void testEngineConsistency(
            final EngineType engine,
            final int binomialSteps, final int samples,
            final Map<String, Double> tolerance, final boolean testGreeks) {

        // QL_TEST_START_TIMING

        final Map<String, Double> calculated = new HashMap<>();
        final Map<String, Double> expected = new HashMap<>();

        // test options
        final Option.Type types[] = { Option.Type.Call, Option.Type.Put };
        final double strikes[] = { 75.0, 100.0, 125.0 };
        final int lengths[] = { 1 };

        // test data
        final double underlyings[] = { 100.0 };
        final double /* @Rate */qRates[] = { 0.00, 0.05 };
        final double /* @Rate */rRates[] = { 0.01, 0.05, 0.15 };
        final double /* @Volatility */vols[] = { 0.11, 0.50, 1.20 };

        final Date today = new Settings().evaluationDate();

        final DayCounter dc = new Actual360();

        final SimpleQuote           spot  = new SimpleQuote(0.0);
        final SimpleQuote           qRate = new SimpleQuote(0.0);
        final YieldTermStructure    qTS   = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote           rRate = new SimpleQuote(0.0);
        final YieldTermStructure    rTS   = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote           vol   = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        for (final Type type : types) {
            for (final double strike3 : strikes) {
                for (final int length2 : lengths) {

                    final Date exDate = today.add(timeToDays(length2));
                    final Exercise exercise = new EuropeanExercise(exDate);

                    final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike3);

                    // reference option
                    final VanillaOption refOption = makeOption(payoff, exercise, spot, qTS, rTS, volTS, EngineType.Analytic, 0, 0);
                    // option to check
                    final VanillaOption option = makeOption(payoff, exercise, spot, qTS, rTS, volTS, engine, binomialSteps, samples);

                    for (final double u : underlyings) {
                        for (final double q : qRates) {
                            for (final double r : rRates) {
                                for (final double v : vols) {
                                    spot.setValue(u);
                                    qRate.setValue(q);
                                    rRate.setValue(r);
                                    vol.setValue(v);

                                    expected.clear();
                                    calculated.clear();

                                    final double refNPV = refOption.NPV();
                                    final double optNPV = option.NPV();

                                    expected.put("value", refNPV);
                                    calculated.put("value", optNPV);

                                    if (testGreeks && option.NPV() > spot.value() * 1.0e-5) {
                                        expected.put("delta", refOption.delta());
                                        expected.put("gamma", refOption.gamma());
                                        expected.put("theta", refOption.theta());
                                        calculated.put("delta", option.delta());
                                        calculated.put("gamma", option.gamma());
                                        calculated.put("theta", option.theta());
                                    }

                                    for (final Entry<String, Double> entry : calculated.entrySet()) {
                                        final String greek = entry.getKey();
                                        final double expct = expected.get(greek), calcl = calculated.get(greek), tol = tolerance.get(greek);
                                        final double error = Utilities.relativeError(expct, calcl, u);
                                        if (error > tol) {
                                            REPORT_FAILURE(greek, payoff, exercise, u, q, r, today, v, expct, calcl, error, tol);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    @Test
    public void testJRBinomialEngines() {

        QL.info("Testing JR binomial European engines against analytic results...");

        final EngineType engine = EngineType.JR;
        final int timeSteps = 251;
        final int samples = 0;
        final Map<String,Double> relativeTol = new HashMap<>(4);
        relativeTol.put("value", 0.002);
        relativeTol.put("delta", 1.0e-3);
        relativeTol.put("gamma", 1.0e-4);
        relativeTol.put("theta", 0.03);
        testEngineConsistency(engine, timeSteps, samples, relativeTol, true);
    }


    @Test
    public void testCRRBinomialEngines() {

        QL.info("Testing CRR binomial European engines against analytic results...");

        final EngineType engine = EngineType.CRR;
        final int timeSteps = 501;
        final int samples = 0;
        final Map<String,Double> relativeTol = new HashMap<>(4);
        relativeTol.put("value", 0.002);
        relativeTol.put("delta", 1.0e-3);
        relativeTol.put("gamma", 1.0e-4);
        relativeTol.put("theta", 0.03);
        testEngineConsistency(engine, timeSteps, samples, relativeTol, true);
    }


    @Test
    public void testEQPBinomialEngines() {

        QL.info("Testing EQP binomial European engines against analytic results...");

        final EngineType engine = EngineType.EQP;
        final int timeSteps = 501;
        final int samples = 0;
        final Map<String,Double> relativeTol = new HashMap<>(4);
        relativeTol.put("value", 0.02);
        relativeTol.put("delta", 1.0e-3);
        relativeTol.put("gamma", 1.0e-4);
        relativeTol.put("theta", 0.03);
        testEngineConsistency(engine, timeSteps, samples, relativeTol, true);
    }


    @Test
    public void testTGEOBinomialEngines() {

        QL.info("Testing TGEO binomial European engines against analytic results...");

        final EngineType engine = EngineType.TGEO;
        final int timeSteps = 251;
        final int samples = 0;
        final Map<String,Double> relativeTol = new HashMap<>(4);
        relativeTol.put("value", 0.002);
        relativeTol.put("delta", 1.0e-3);
        relativeTol.put("gamma", 1.0e-4);
        relativeTol.put("theta", 0.03);
        testEngineConsistency(engine, timeSteps, samples, relativeTol, true);
    }


    @Test
    public void testTIANBinomialEngines() {

        QL.info("Testing TIAN binomial European engines against analytic results...");

        final EngineType engine = EngineType.TIAN;
        final int timeSteps = 251;
        final int samples = 0;
        final Map<String,Double> relativeTol = new HashMap<>(4);
        relativeTol.put("value", 0.002);
        relativeTol.put("delta", 1.0e-3);
        relativeTol.put("gamma", 1.0e-4);
        relativeTol.put("theta", 0.03);
        testEngineConsistency(engine, timeSteps, samples, relativeTol, true);
    }


    @Test
    public void testLRBinomialEngines() {

        QL.info("Testing LR binomial European engines against analytic results...");

        final EngineType engine = EngineType.LR;
        final int timeSteps = 251;
        final int samples = 0;
        final Map<String,Double> relativeTol = new HashMap<>(4);
        relativeTol.put("value", 1.0e-6);
        relativeTol.put("delta", 1.0e-3);
        relativeTol.put("gamma", 1.0e-4);
        relativeTol.put("theta", 0.03);
        testEngineConsistency(engine, timeSteps, samples, relativeTol, true);
    }


    @Test
    public void testJOSHIBinomialEngines() {

        QL.info("Testing Joshi binomial European engines against analytic results...");

        final EngineType engine = EngineType.JOSHI;
        final int timeSteps = 251;
        final int samples = 0;
        final Map<String,Double> relativeTol = new HashMap<>(4);
        relativeTol.put("value", 1.0e-7);
        relativeTol.put("delta", 1.0e-3);
        relativeTol.put("gamma", 1.0e-4);
        relativeTol.put("theta", 0.03);
        testEngineConsistency(engine, timeSteps, samples, relativeTol, true);
    }


    @Test
    public void testFdEngines() {

        QL.info("Testing finite-differences European engines against analytic results...");

        final EngineType engine = EngineType.FiniteDifferences;
        final @NonNegative int timeSteps = 300;
        final @NonNegative int gridPoints = 300;
        final Map<String,Double> relativeTol = new HashMap<>(4);
        relativeTol.put("value", 1.0e-4);
        relativeTol.put("delta", 1.0e-6);
        relativeTol.put("gamma", 1.0e-6);
        relativeTol.put("theta", 1.0e-4);
        testEngineConsistency(engine, timeSteps, gridPoints, relativeTol, true);
    }


    @Test
    public void testIntegralEngines() {

        QL.info("Testing integral engines against analytic results...");


        final EngineType engine = EngineType.Integral;
        final int timeSteps = 300;
        final int gridPoints = 300;
        final Map<String,Double> relativeTol = new HashMap<>(1);
        relativeTol.put("value", 0.0001);
        testEngineConsistency(engine, timeSteps, gridPoints, relativeTol);
    }


    //
    //  void EuropeanOptionTest::testMcEngines() {
    //
    //      BOOST_MESSAGE("Testing Monte Carlo European engines "
    //                    "against analytic results...");
    //
    //      EngineType engine = PseudoMonteCarlo;
    //      Size steps = Null<Size>();
    //      Size samples = 40000;
    //      std::map<std::string,Real> relativeTol;
    //      relativeTol["value"] = 0.01;
    //      testEngineConsistency(engine,steps,samples,relativeTol);
    //  }

    /**
     * Faithful port of {@code test-suite/europeanoption.cpp:1282}
     * {@code BOOST_AUTO_TEST_CASE(testQmcEngines)}. Quasi (low-discrepancy)
     * Monte Carlo against analytic. Tolerance value = 0.01 (C++).
     *
     * <p>Routed through the local helper {@link #testEngineConsistencyMc} with
     * {@code lowDiscrepancy = true}. Sobol gives a deterministic error of 0
     * (no seed) and 4095 = 2^12-1 samples per the C++ test.
     */
    @Test
    public void testQmcEngines() {
        QL.info("Testing Quasi Monte Carlo European engines against analytic results...");
        // C++: EngineType engine = QuasiMonteCarlo; Size steps = Null<Size>();
        //      Size samples = 4095; // 2^12-1
        //      tol["value"] = 0.01;
        final int samples = 4095;
        final Map< String, Double > relativeTol = new HashMap<>(1);
        relativeTol.put("value", 0.01);
        testEngineConsistencyMc(samples, /* lowDiscrepancy=*/ true, relativeTol);
    }


    /**
     * Faithful port of {@code test-suite/europeanoption.cpp:1699}
     * {@code BOOST_AUTO_TEST_CASE(testFFTEngines)}. FFT-Carr-Madan engine
     * against analytic. Tolerance value = 0.01 (C++).
     *
     * <p>Routed through a local FFT-specific helper because the existing
     * {@link #testEngineConsistency} helper's engine dispatch does not include the
     * FFT arm (it lives in the experimental package). The FFT engine prices
     * each call to {@link Instrument#NPV} via
     * {@link FFTEngine#precalculate(List)} which must be invoked before the
     * NPV query.
     */
    @Test
    public void testFFTEngines() {
        QL.info("Testing FFT European engines against analytic results...");
        // C++: EngineType engine = FFT; Size steps = Null<Size>(); Size samples = Null<Size>();
        //      tol["value"] = 0.01;
        final Map< String, Double > relativeTol = new HashMap<>(1);
        relativeTol.put("value", 0.01);
        testEngineConsistencyFFT(relativeTol);
    }

    //  void EuropeanOptionTest::testPriceCurve() {
    //
    //      BOOST_MESSAGE("Testing European price curves...");
    //
    //      /* The data below are from
    //         "Option pricing formulas", E.G. Haug, McGraw-Hill 1998
    //      */
    //      EuropeanOptionData values[] = {
    //        // pag 2-8
    //        //        type, strike,   spot,    q,    r,    t,  vol,   value
    //        { Option.Type.Call,  65.00,  60.00, 0.00, 0.08, 0.25, 0.30,  2.1334, 0.0},
    //        { Option.Type.Put,   95.00, 100.00, 0.05, 0.10, 0.50, 0.20,  2.4648, 0.0},
    //      };
    //
    //      DayCounter dc = Actual360();
    //      Date today = Date::todaysDate();
    //      Size timeSteps = 300;
    //      Size gridPoints = 300;
    //
    //      boost::shared_ptr<SimpleQuote> spot(new SimpleQuote(0.0));
    //      boost::shared_ptr<SimpleQuote> qRate(new SimpleQuote(0.0));
    //      boost::shared_ptr<YieldTermStructure> qTS = flatRate(today, qRate, dc);
    //      boost::shared_ptr<SimpleQuote> rRate(new SimpleQuote(0.0));
    //      boost::shared_ptr<YieldTermStructure> rTS = flatRate(today, rRate, dc);
    //      boost::shared_ptr<SimpleQuote> vol(new SimpleQuote(0.0));
    //      boost::shared_ptr<BlackVolTermStructure> volTS = flatVol(today, vol, dc);
    //      boost::shared_ptr<PricingEngine>
    //          engine(new FDEuropeanEngine(timeSteps, gridPoints));
    //
    //      for (Size i=0; i<LENGTH(values); i++) {
    //
    //          boost::shared_ptr<StrikedTypePayoff> payoff(new
    //              PlainVanillaPayoff(values[i].type, values[i].strike));
    //          Date exDate = today + timeToDays(values[i].t);
    //          boost::shared_ptr<Exercise> exercise(new EuropeanExercise(exDate));
    //
    //          spot ->setValue(values[i].s);
    //          qRate->setValue(values[i].q);
    //          rRate->setValue(values[i].r);
    //          vol  ->setValue(values[i].v);
    //
    //          boost::shared_ptr<StochasticProcess> stochProcess(new
    //              BlackScholesMertonProcess(Handle<Quote>(spot),
    //                                        Handle<YieldTermStructure>(qTS),
    //                                        Handle<YieldTermStructure>(rTS),
    //                                        Handle<BlackVolTermStructure>(volTS)));
    //
    //          EuropeanOption option(stochProcess, payoff, exercise, engine);
    //          SampledCurve price_curve = option.result<SampledCurve>("priceCurve");
    //          if (price_curve.empty()) {
    //              REPORT_FAILURE("no price curve", payoff, exercise, values[i].s,
    //                             values[i].q, values[i].r, today,
    //                             values[i].v, values[i].result, 0.0,
    //                             0.0, 0.0);
    //              continue;
    //          }
    //
    //          // Ignore the end points
    //          Size start = price_curve.size() / 4;
    //          Size end = price_curve.size() * 3 / 4;
    //          for (Size i=start; i < end; i++) {
    //              spot->setValue(price_curve.gridValue(i));
    //              boost::shared_ptr<StochasticProcess> stochProcess1(
    //                        new BlackScholesMertonProcess(
    //                                         Handle<Quote>(spot),
    //                                         Handle<YieldTermStructure>(qTS),
    //                                         Handle<YieldTermStructure>(rTS),
    //                                         Handle<BlackVolTermStructure>(volTS)));
    //
    //              EuropeanOption option1(stochProcess, payoff, exercise, engine);
    //              Real calculated = option1.NPV();
    //              Real error = std::fabs(calculated-price_curve.value(i));
    //              Real tolerance = 1e-3;
    //              if (error>tolerance) {
    //                  REPORT_FAILURE("price curve error", payoff, exercise,
    //                                 price_curve.gridValue(i),
    //                                 values[i].q, values[i].r, today,
    //                                 values[i].v,
    //                                 price_curve.value(i), calculated,
    //                                 error, tolerance);
    //                  break;
    //              }
    //          }
    //      }
    //
    //  }


    /**
     * Faithful port of {@code test-suite/europeanoption.cpp:942}
     * {@code BOOST_AUTO_TEST_CASE(testImpliedVolWithDividends)}. Recovers the
     * input volatility from a {@link DividendVanillaOption} priced with
     * {@link AnalyticDividendEuropeanEngine} for a single mid-life dividend
     * of 1.0, across the same {types, strikes, lengths, underlyings,
     * qRates, rRates, vols} cube as the C++ test (tol = 1.0e-6, 100 evals).
     */
    @Test
    public void testImpliedVolWithDividends() {
        QL.info("Testing European option implied volatility with dividends...");

        final int maxEvaluations = 100;
        final double tolerance = 1.0e-6;

        final Option.Type[] types = { Option.Type.Call, Option.Type.Put };
        final double[] strikes = { 90.0, 99.5, 100.0, 100.5, 110.0 };
        final int[] lengths = { 36, 180, 360, 1080 };

        final double[] underlyings = { 90.0, 95.0, 99.9, 100.0, 100.1, 105.0, 110.0 };
        final double[] qRates = { 0.01, 0.05, 0.10 };
        final double[] rRates = { 0.01, 0.05, 0.10 };
        final double[] vols = { 0.01, 0.20, 0.30, 0.70, 0.90 };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote vol = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);

        for ( final Option.Type type : types ) {
            for ( final double strike : strikes ) {
                for ( final int length : lengths ) {
                    final Date exDate = today.add(length);
                    final Exercise exercise = new EuropeanExercise(exDate);
                    final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);

                    // Single mid-life dividend of 1.0 — mirrors C++
                    //   DividendVector({ today + length/2 }, { 1.0 })
                    final List< Date > divDates = new ArrayList<>(1);
                    divDates.add(today.add(length / 2));
                    final List< Double > divs = new ArrayList<>(1);
                    divs.add(1.0);

                    final DividendVanillaOption option = new DividendVanillaOption(payoff, exercise, divDates, divs);
                    final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                            new Handle< Quote >(spot),
                            new Handle< YieldTermStructure >(qTS),
                            new Handle< YieldTermStructure >(rTS),
                            new Handle< BlackVolTermStructure >(volTS));
                    final PricingEngine divEngine = new AnalyticDividendEuropeanEngine(process);
                    option.setPricingEngine(divEngine);

                    for ( final double u : underlyings ) {
                        for ( final double m : qRates ) {
                            for ( final double n : rRates ) {
                                for ( final double v : vols ) {
                                    spot.setValue(u);
                                    qRate.setValue(m);
                                    rRate.setValue(n);
                                    vol.setValue(v);

                                    final double value = option.NPV();
                                    double implVol = 0.0;
                                    if ( value != 0.0 ) {
                                        // shift the guess away from the true vol
                                        vol.setValue(v * 0.5);
                                        if ( Math.abs(value - option.NPV()) <= 1.0e-12 ) {
                                            // flat price vs vol — pointless to solve
                                            continue;
                                        }
                                        try {
                                            implVol = option.impliedVolatility(value, process, tolerance,
                                                    maxEvaluations);
                                        } catch ( final Exception e ) {
                                            fail("implied vol calculation failed:"
                                                    + "\n   option:         " + type
                                                    + "\n   strike:         " + strike
                                                    + "\n   spot value:     " + u
                                                    + "\n   dividend yield: " + m
                                                    + "\n   risk-free rate: " + n
                                                    + "\n   today:          " + today
                                                    + "\n   maturity:       " + exDate
                                                    + "\n   volatility:     " + v
                                                    + "\n   option value:   " + value + "\n"
                                                    + e.getMessage());
                                        }
                                        if ( Math.abs(implVol - v) > tolerance ) {
                                            // recompute with the implied vol; mismatch may not matter
                                            vol.setValue(implVol);
                                            final double value2 = option.NPV();
                                            final double error = relativeError(value, value2, u);
                                            if ( error > tolerance ) {
                                                fail(type
                                                        + " option:\n"
                                                        + "    spot value:          " + u + "\n"
                                                        + "    strike:              " + strike + "\n"
                                                        + "    dividend yield:      " + m + "\n"
                                                        + "    risk-free rate:      " + n + "\n"
                                                        + "    maturity:            " + exDate + "\n\n"
                                                        + "    original volatility: " + v + "\n"
                                                        + "    price:               " + value + "\n"
                                                        + "    implied volatility:  " + implVol + "\n"
                                                        + "    corresponding price: " + value2 + "\n"
                                                        + "    error:               " + error);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Faithful port of {@code test-suite/europeanoption.cpp:1269}
     * {@code BOOST_AUTO_TEST_CASE(testMcEngines)}. Pseudo-random Monte Carlo
     * against analytic. Tolerance value = 0.01 (C++).
     *
     * <p>Routed through the local helper {@link #testEngineConsistencyMc} —
     * the existing {@link #testEngineConsistency} helper has the MC dispatch
     * arms commented out (Phase 5h-MC-INFRA), so we wire the C++ logic with
     * an explicit {@link MCEuropeanEngine} (PseudoRandom).
     */
    @Test
    public void testMcEngines() {
        QL.info("Testing Monte Carlo European engines against analytic results...");

        // C++: EngineType engine = PseudoMonteCarlo; Size steps = Null<Size>();
        //      Size samples = 40000; tol["value"] = 0.01;
        final int samples = 40000;
        final Map< String, Double > relativeTol = new HashMap<>(1);
        relativeTol.put("value", 0.01);
        testEngineConsistencyMc(samples, /* lowDiscrepancy=*/ false, relativeTol);
    }

    /**
     * Faithful port of {@code test-suite/europeanoption.cpp:1295}
     * {@code BOOST_AUTO_TEST_CASE(testLocalVolatility)}. FD with local vol
     * vs analytic. Tolerance 0.001 in C++.
     */
    @Test
    public void testLocalVolatility() {
        QL.info("Testing finite-differences with local volatility...");

        final Date settlementDate = new Date(5, Month.July, 2002);
        new Settings().setEvaluationDate(settlementDate);

        final DayCounter dayCounter = new Actual365Fixed();
        final Calendar calendar = new Target();

        final int[] tArr = { 13, 41, 75, 165, 256, 345, 524, 703 };
        final double[] rArr = { 0.0357, 0.0349, 0.0341, 0.0355, 0.0359, 0.0368, 0.0386, 0.0401 };

        final Date[] dates = new Date[1 + tArr.length];
        final double[] rates = new double[1 + rArr.length];
        dates[0] = settlementDate;
        rates[0] = 0.0357;
        for ( int i = 0; i < tArr.length; i++ ) {
            dates[i + 1] = settlementDate.add(tArr[i]);
            rates[i + 1] = rArr[i];
        }

        final YieldTermStructure rTS = new InterpolatedZeroCurve< Linear >(Linear.class, dates, rates, dayCounter);
        final YieldTermStructure qTS = Utilities.flatRate(settlementDate, 0.0, dayCounter);

        final SimpleQuote s0 = new SimpleQuote(4500.00);

        final double[] strikes = { 100, 500, 2000, 3400, 3600, 3800, 4000, 4200, 4400, 4500,
                4600, 4800, 5000, 5200, 5400, 5600, 7500, 10000, 20000, 30000 };

        final double[] v = {
                1.015873, 1.015873, 1.015873, 0.89729, 0.796493, 0.730914, 0.631335, 0.568895,
                0.711309, 0.711309, 0.711309, 0.641309, 0.635593, 0.583653, 0.508045, 0.463182,
                0.516034, 0.500534, 0.500534, 0.500534, 0.448706, 0.416661, 0.375470, 0.353442,
                0.516034, 0.482263, 0.447713, 0.387703, 0.355064, 0.337438, 0.316966, 0.306859,
                0.497587, 0.464373, 0.430764, 0.374052, 0.344336, 0.328607, 0.310619, 0.301865,
                0.479511, 0.446815, 0.414194, 0.361010, 0.334204, 0.320301, 0.304664, 0.297180,
                0.461866, 0.429645, 0.398092, 0.348638, 0.324680, 0.312512, 0.299082, 0.292785,
                0.444801, 0.413014, 0.382634, 0.337026, 0.315788, 0.305239, 0.293855, 0.288660,
                0.428604, 0.397219, 0.368109, 0.326282, 0.307555, 0.298483, 0.288972, 0.284791,
                0.420971, 0.389782, 0.361317, 0.321274, 0.303697, 0.295302, 0.286655, 0.282948,
                0.413749, 0.382754, 0.354917, 0.316532, 0.300016, 0.292251, 0.284420, 0.281164,
                0.400889, 0.370272, 0.343525, 0.307904, 0.293204, 0.286549, 0.280189, 0.277767,
                0.390685, 0.360399, 0.334344, 0.300507, 0.287149, 0.281380, 0.276271, 0.274588,
                0.383477, 0.353434, 0.327580, 0.294408, 0.281867, 0.276746, 0.272655, 0.271617,
                0.379106, 0.349214, 0.323160, 0.289618, 0.277362, 0.272641, 0.269332, 0.268846,
                0.377073, 0.347258, 0.320776, 0.286077, 0.273617, 0.269057, 0.266293, 0.266265,
                0.399925, 0.369232, 0.338895, 0.289042, 0.265509, 0.255589, 0.249308, 0.249665,
                0.423432, 0.406891, 0.373720, 0.314667, 0.281009, 0.263281, 0.246451, 0.242166,
                0.453704, 0.453704, 0.453704, 0.381255, 0.334578, 0.305527, 0.268909, 0.251367,
                0.517748, 0.517748, 0.517748, 0.416577, 0.364770, 0.331595, 0.287423, 0.264285 };

        final int nStrikes = strikes.length;
        final int nDates = dates.length;
        final Matrix blackVolMatrix = new Matrix(nStrikes, nDates - 1);
        for ( int i = 0; i < nStrikes; i++ ) {
            for ( int j = 1; j < nDates; j++ ) {
                blackVolMatrix.set(i, j - 1, v[i * (nDates - 1) + j - 1]);
            }
        }

        final Date[] surfaceDates = new Date[nDates - 1];
        System.arraycopy(dates, 1, surfaceDates, 0, nDates - 1);

        final BlackVarianceSurface volTS = new BlackVarianceSurface(settlementDate, surfaceDates,
                new Array(strikes), blackVolMatrix, dayCounter);
        // C++: volTS->setInterpolation<Bicubic>();
        volTS.setInterpolation(new BicubicSpline());

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle< Quote >(s0),
                new Handle< YieldTermStructure >(qTS),
                new Handle< YieldTermStructure >(rTS),
                new Handle< BlackVolTermStructure >(volTS));

        final FdmSchemeDesc[] schemeDescs = {
                FdmSchemeDesc.Douglas(),
                FdmSchemeDesc.CrankNicolson(),
                FdmSchemeDesc.ModifiedCraigSneyd()
        };
        final String[] schemeNames = { "Douglas", "Crank-Nicolson", "Mod. Craig-Sneyd" };

        for ( int i = 2; i < nDates; i += 2 ) {
            for ( int j = 3; j < nStrikes - 5; j += 5 ) {
                final Date exDate = dates[i];
                final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Call, strikes[j]);
                final Exercise exercise = new EuropeanExercise(exDate);

                final EuropeanOption option = new EuropeanOption(payoff, exercise);
                option.setPricingEngine(new AnalyticEuropeanEngine(process));

                final double tol = 0.001;
                final double expectedNPV = option.NPV();
                final double expectedDelta = option.delta();
                final double expectedGamma = option.gamma();

                option.setPricingEngine(new FdBlackScholesVanillaEngine(process, 200, 400, 0,
                        FdmSchemeDesc.Douglas()));
                double calculatedNPV = option.NPV();
                final double calculatedDelta = option.delta();
                final double calculatedGamma = option.gamma();

                if ( Math.abs(expectedNPV - calculatedNPV) > tol * expectedNPV ) {
                    fail("Failed to reproduce option price for"
                            + "\n    strike:     " + payoff.strike()
                            + "\n    maturity:   " + exDate
                            + "\n    calculated: " + calculatedNPV
                            + "\n    expected:   " + expectedNPV);
                }
                if ( Math.abs(expectedDelta - calculatedDelta) > tol * expectedDelta ) {
                    fail("Failed to reproduce option delta for"
                            + "\n    strike:     " + payoff.strike()
                            + "\n    maturity:   " + exDate
                            + "\n    calculated: " + calculatedDelta
                            + "\n    expected:   " + expectedDelta);
                }
                if ( Math.abs(expectedGamma - calculatedGamma) > tol * expectedGamma ) {
                    fail("Failed to reproduce option gamma for"
                            + "\n    strike:     " + payoff.strike()
                            + "\n    maturity:   " + exDate
                            + "\n    calculated: " + calculatedGamma
                            + "\n    expected:   " + expectedGamma);
                }

                // local-vol pricing — delta/gamma are model-implied so skip them
                for ( int s = 0; s < schemeDescs.length; s++ ) {
                    option.setPricingEngine(new FdBlackScholesVanillaEngine(process, 25, 100, 0,
                            schemeDescs[s], /* localVol=*/ true, /* illegalLocalVolOverwrite=*/ 0.35));
                    calculatedNPV = option.NPV();
                    if ( Math.abs(expectedNPV - calculatedNPV) > tol * expectedNPV ) {
                        fail("Failed to reproduce local vol option price for"
                                + "\n    strike:     " + payoff.strike()
                                + "\n    maturity:   " + exDate
                                + "\n    calculated: " + calculatedNPV
                                + "\n    expected:   " + expectedNPV
                                + "\n    scheme:     " + schemeNames[s]);
                    }
                }
            }
        }
    }

    /**
     * Faithful port of {@code test-suite/europeanoption.cpp:1433}
     * {@code BOOST_AUTO_TEST_CASE(testAnalyticEngineDiscountCurve)}.
     * Asserts (a) NPV with explicit discount curve equals NPV with default
     * (process risk-free) curve when both share the same rate, and (b) NPV
     * changes when only the discount curve's rate moves.
     */
    @Test
    public void testAnalyticEngineDiscountCurve() {
        QL.info("Testing separate discount curve for analytic European engine...");

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(1000.0);
        final SimpleQuote qRate = new SimpleQuote(0.01);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.015);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.02);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);
        final SimpleQuote discRate = new SimpleQuote(0.015);
        final YieldTermStructure discTS = Utilities.flatRate(today, discRate, dc);

        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle< Quote >(spot),
                new Handle< YieldTermStructure >(qTS),
                new Handle< YieldTermStructure >(rTS),
                new Handle< BlackVolTermStructure >(volTS));
        final PricingEngine engineSingleCurve = new AnalyticEuropeanEngine(stochProcess);
        final PricingEngine engineMultiCurve = new AnalyticEuropeanEngine(stochProcess,
                new Handle< YieldTermStructure >(discTS));

        final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 1025.0);
        final Date exDate = today.add(new Period(1, TimeUnit.Years));
        final Exercise exercise = new EuropeanExercise(exDate);
        final EuropeanOption option = new EuropeanOption(payoff, exercise);

        option.setPricingEngine(engineSingleCurve);
        final double npvSingleCurve = option.NPV();
        option.setPricingEngine(engineMultiCurve);
        double npvMultiCurve = option.NPV();

        // (a) with identical rates, NPVs must agree exactly
        assertEquals("NPVs must match when discount curve == process risk-free curve",
                npvSingleCurve, npvMultiCurve, 0.0);

        // (b) bumping the discount curve only must move the NPV
        discRate.setValue(0.023);
        npvMultiCurve = option.NPV();
        assertNotEquals("NPV must change when discount curve is bumped independently",
                npvSingleCurve, npvMultiCurve);
    }

    /**
     * Faithful port of {@code test-suite/europeanoption.cpp:1479}
     * {@code BOOST_AUTO_TEST_CASE(testPDESchemes)}. Iterates Douglas,
     * CrankNicolson, ImplicitEuler, ExplicitEuler, MethodOfLines,
     * Hundsdorfer, CraigSneyd, ModifiedCraigSneyd, TrBDF2 schemes and
     * checks they agree with the analytic price within tol = 0.006.
     */
    @Test
    public void testPDESchemes() {
        QL.info("Testing different PDE schemes to solve Black-Scholes PDEs...");

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(18, Month.February, 2018);
        new Settings().setEvaluationDate(today);

        final Handle< Quote > spot = new Handle< Quote >(new SimpleQuote(100.0));
        final Handle< YieldTermStructure > qTS = new Handle< YieldTermStructure >(Utilities.flatRate(today, 0.06, dc));
        final Handle< YieldTermStructure > rTS = new Handle< YieldTermStructure >(Utilities.flatRate(today, 0.10, dc));
        final Handle< BlackVolTermStructure > volTS = new Handle< BlackVolTermStructure >(
                Utilities.flatVol(today, 0.35, dc));

        final Date maturity = today.add(new Period(6, TimeUnit.Months));

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(spot, qTS, rTS, volTS);

        final PricingEngine analytic = new AnalyticEuropeanEngine(process);
        // Crank-Nicolson and Douglas are the same in 1D
        final PricingEngine douglas = new FdBlackScholesVanillaEngine(process, 15, 100, 0, FdmSchemeDesc.Douglas());
        final PricingEngine crankNicolson = new FdBlackScholesVanillaEngine(process, 15, 100, 0,
                FdmSchemeDesc.CrankNicolson());
        final PricingEngine implicitEuler = new FdBlackScholesVanillaEngine(process, 500, 100, 0,
                FdmSchemeDesc.ImplicitEuler());
        final PricingEngine explicitEuler = new FdBlackScholesVanillaEngine(process, 1000, 100, 0,
                FdmSchemeDesc.ExplicitEuler());
        final PricingEngine methodOfLines = new FdBlackScholesVanillaEngine(process, 1, 100, 0,
                FdmSchemeDesc.MethodOfLines());
        final PricingEngine hundsdorfer = new FdBlackScholesVanillaEngine(process, 10, 100, 0,
                FdmSchemeDesc.Hundsdorfer());
        final PricingEngine craigSneyd = new FdBlackScholesVanillaEngine(process, 10, 100, 0,
                FdmSchemeDesc.CraigSneyd());
        final PricingEngine modCraigSneyd = new FdBlackScholesVanillaEngine(process, 15, 100, 0,
                FdmSchemeDesc.ModifiedCraigSneyd());
        final PricingEngine trBDF2 = new FdBlackScholesVanillaEngine(process, 15, 100, 0, FdmSchemeDesc.TrBDF2());

        final PricingEngine[] engines = {
                douglas, crankNicolson, implicitEuler, explicitEuler, methodOfLines,
                hundsdorfer, craigSneyd, modCraigSneyd, trBDF2
        };
        final String[] names = {
                "Douglas", "Crank-Nicolson", "Implicit-Euler", "Explicit-Euler", "Method-of-Lines",
                "Hundsdorfer", "Craig-Sneyd", "Modified Craig-Sneyd", "TR-BDF2"
        };

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Put, ((SimpleQuote) spot.currentLink()).value());
        final Exercise exercise = new EuropeanExercise(maturity);
        final VanillaOption option = new VanillaOption(payoff, exercise);

        option.setPricingEngine(analytic);
        final double expected = option.NPV();

        final double tol = 0.006;
        for ( int k = 0; k < engines.length; k++ ) {
            option.setPricingEngine(engines[k]);
            final double calculated = option.NPV();
            final double diff = Math.abs(expected - calculated);
            if ( diff > tol ) {
                fail("Failed to reproduce European option values with the " + names[k] + " PDE scheme"
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected
                        + "\n    difference: " + diff
                        + "\n    tolerance:  " + tol);
            }
        }
    }

    /**
     * Faithful port of {@code test-suite/europeanoption.cpp:1633}
     * {@code BOOST_AUTO_TEST_CASE(testDouglasVsCrankNicolson)}. Sweeps theta
     * across [0.2, 0.8] in steps of 0.1 and verifies (a) the Crank-Nicolson
     * scheme matches the analytic NPV to {@code 1e-2} and (b) the Douglas
     * scheme produces a NPV identical (within {@code 1e-12}) to the
     * Crank-Nicolson scheme — confirming the two are equivalent in 1D.
     */
    @Test
    public void testDouglasVsCrankNicolson() {
        QL.info("Testing Douglas vs Crank-Nicolson scheme for finite-difference European PDE engines...");

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(5, Month.October, 2018);
        new Settings().setEvaluationDate(today);

        final Handle< Quote > spot = new Handle< Quote >(new SimpleQuote(100.0));
        final Handle< YieldTermStructure > qTS = new Handle< YieldTermStructure >(Utilities.flatRate(today, 0.02, dc));
        final Handle< YieldTermStructure > rTS = new Handle< YieldTermStructure >(Utilities.flatRate(today, 0.075, dc));
        final Handle< BlackVolTermStructure > volTS = new Handle< BlackVolTermStructure >(
                Utilities.flatVol(today, 0.25, dc));

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(spot, qTS, rTS, volTS);

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Put,
                ((SimpleQuote) spot.currentLink()).value() + 2);
        final Exercise exercise = new EuropeanExercise(today.add(new Period(6, TimeUnit.Months)));
        final VanillaOption option = new VanillaOption(payoff, exercise);

        option.setPricingEngine(new AnalyticEuropeanEngine(process));
        final double npv = option.NPV();
        final double schemeTol = 1e-12;
        final double npvTol = 1e-2;

        for ( double theta = 0.2; theta < 0.81; theta += 0.1 ) {
            option.setPricingEngine(new FdBlackScholesVanillaEngine(process, 500, 100, 0,
                    new FdmSchemeDesc(FdmSchemeType.CrankNicolsonType, theta, 0.0)));
            final double crankNicolsonNPV = option.NPV();
            final double npvDiff = Math.abs(crankNicolsonNPV - npv);
            if ( npvDiff > npvTol ) {
                fail("Failed to reproduce european option values with the Crank-Nicolson PDE scheme"
                        + "\n    Analytic NPV:       " + npv
                        + "\n    Crank-Nicolson NPV: " + crankNicolsonNPV
                        + "\n    theta:              " + theta
                        + "\n    difference:         " + npvDiff
                        + "\n    tolerance:          " + npvTol);
            }

            option.setPricingEngine(new FdBlackScholesVanillaEngine(process, 500, 100, 0,
                    new FdmSchemeDesc(FdmSchemeType.DouglasType, theta, 0.0)));
            final double douglasNPV = option.NPV();
            final double schemeDiff = Math.abs(crankNicolsonNPV - douglasNPV);
            if ( schemeDiff > schemeTol ) {
                fail("Failed to reproduce Douglas scheme option values with the Crank-Nicolson PDE scheme"
                        + "\n    Douglas NPV:        " + douglasNPV
                        + "\n    Crank-Nicolson NPV: " + crankNicolsonNPV
                        + "\n    difference:         " + schemeDiff
                        + "\n    tolerance:          " + schemeTol);
            }
        }
    }

    /**
     * Faithful port of {@code test-suite/europeanoption.cpp:1578}
     * {@code BOOST_AUTO_TEST_CASE(testFdEngineWithNonConstantParameters)}.
     * Verifies the {@link FdBlackScholesVanillaEngine} prices a European call
     * to within {@code 0.01} of the analytic NPV when the risk-free yield
     * curve is a non-constant {@link InterpolatedForwardCurve} (rates rising
     * from 0% to 1% across 360 days), confirming the FD engine correctly
     * handles term-structure rates that vary along the time grid.
     *
     * <p>The C++ test uses {@code BlackScholesProcess(spot, rTS, volTS)}
     * (no q). Java has no standalone {@code BlackScholesProcess}; we use
     * {@link BlackScholesMertonProcess} with a flat-zero dividend curve,
     * mathematically equivalent.
     *
     * <p>The C++ test uses {@code ForwardCurve = InterpolatedForwardCurve
     * <BackwardFlat>}; this port mirrors that with the explicit
     * {@link BackwardFlat} interpolator factory. The
     * {@link InterpolatedForwardCurve} precondition bug (stale
     * {@code forwards[0] == 1.0} discount-factor check copy-pasted from
     * {@link org.jquantlib.termstructures.yieldcurves.InterpolatedDiscountCurve})
     * is fixed as part of this port — see {@link InterpolatedForwardCurve}'s
     * constructor comment for details.
     */
    @Test
    public void testFdEngineWithNonConstantParameters() {
        QL.info("Testing finite-difference European engine with non-constant parameters...");

        final double u = 190.0;
        final double v = 0.20;

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();

        final SimpleQuote spot = new SimpleQuote(u);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, v, dc);

        final Date[] dates = new Date[] {
                today,
                today.add(90),
                today.add(180),
                today.add(270),
                today.add(360)
        };
        final double[] rates = new double[] { 0.0, 0.001, 0.002, 0.005, 0.01 };
        final YieldTermStructure rTS =
                new org.jquantlib.termstructures.yieldcurves.InterpolatedForwardCurve<
                        org.jquantlib.math.interpolations.factories.BackwardFlat>(
                        org.jquantlib.math.interpolations.factories.BackwardFlat.class,
                        dates, rates, dc);

        // q=0 to mirror C++ BlackScholesProcess (no dividend yield arm).
        final YieldTermStructure qTS = Utilities.flatRate(today, 0.0, dc);

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        final Exercise exercise = new EuropeanExercise(today.add(360));
        final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 190.0);

        final EuropeanOption option = new EuropeanOption(payoff, exercise);

        option.setPricingEngine(new AnalyticEuropeanEngine(process));
        final double expected = option.NPV();

        final int timeSteps = 200;
        final int gridPoints = 201;
        option.setPricingEngine(new FdBlackScholesVanillaEngine(
                process, timeSteps, gridPoints, 0, FdmSchemeDesc.Douglas()));
        final double calculated = option.NPV();

        final double tolerance = 0.01;
        final double error = Math.abs(expected - calculated);
        if ( error > tolerance ) {
            fail("Failed to reproduce European option value with non-constant-parameter FD engine"
                    + "\n    expected:   " + expected
                    + "\n    calculated: " + calculated
                    + "\n    error:      " + error
                    + "\n    tolerance:  " + tolerance);
        }
    }

    /**
     * Local helper mirroring the {@code PseudoMonteCarlo} arm of the C++
     * {@code testEngineConsistency} helper (see
     * {@code test-suite/europeanoption.cpp:167-172}). The class-level
     * {@link #testEngineConsistency} helper has the MC dispatch commented
     * out (Phase 5h-MC-INFRA); we wire MCEuropeanEngine directly here.
     *
     * <p>Quasi Monte Carlo arm (LowDiscrepancy) is BLOCKED in Phase 1
     * because {@link MCEuropeanEngine} is specialised for PseudoRandom only.
     */
    private void testEngineConsistencyMc(final int samples, final boolean lowDiscrepancy,
            final Map< String, Double > tolerance) {
        final Option.Type[] types = { Option.Type.Call, Option.Type.Put };
        final double[] strikes = { 75.0, 100.0, 125.0 };
        final int[] lengths = { 1 };
        final double[] underlyings = { 100.0 };
        final double[] qRates = { 0.00, 0.05 };
        final double[] rRates = { 0.01, 0.05, 0.15 };
        final double[] vols = { 0.11, 0.50, 1.20 };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote vol = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);

        for ( final Option.Type type : types ) {
            for ( final double strike : strikes ) {
                for ( final int length : lengths ) {
                    final Date exDate = today.add(length * 360);
                    final Exercise exercise = new EuropeanExercise(exDate);
                    final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);

                    final BlackScholesMertonProcess refProcess = new BlackScholesMertonProcess(
                            new Handle< Quote >(spot),
                            new Handle< YieldTermStructure >(qTS),
                            new Handle< YieldTermStructure >(rTS),
                            new Handle< BlackVolTermStructure >(volTS));
                    final VanillaOption refOption = new EuropeanOption(payoff, exercise);
                    refOption.setPricingEngine(new AnalyticEuropeanEngine(refProcess));

                    final VanillaOption option = new EuropeanOption(payoff, exercise);
                    // Mirrors MakeMCEuropeanEngine<PseudoRandom>().withSteps(1)
                    //                                             .withSamples(samples)
                    //                                             .withSeed(42);
                    // and the LowDiscrepancy variant (no seed in C++ QMC arm).
                    // NULL_SAMPLES = Integer.MAX_VALUE (matches C++ Null<Size>())
                    final PricingEngine mcEngine = lowDiscrepancy
                            ? new MCEuropeanEngineLowDiscrepancy(refProcess,
                                    /* timeSteps=*/ 1,
                                    /* timeStepsPerYear=*/ org.jquantlib.pricingengines.McSimulation.NULL_SAMPLES,
                                    /* brownianBridge=*/ false, /* antitheticVariate=*/ false,
                                    /* requiredSamples=*/ samples,
                                    /* requiredTolerance=*/ org.jquantlib.pricingengines.McSimulation.NULL_TOLERANCE,
                                    /* maxSamples=*/ org.jquantlib.pricingengines.McSimulation.NULL_SAMPLES,
                                    /* seed=*/ 0L)
                            : new MCEuropeanEngine(refProcess,
                                    /* timeSteps=*/ 1,
                                    /* timeStepsPerYear=*/ org.jquantlib.pricingengines.McSimulation.NULL_SAMPLES,
                                    /* brownianBridge=*/ false, /* antitheticVariate=*/ false,
                                    /* requiredSamples=*/ samples,
                                    /* requiredTolerance=*/ org.jquantlib.pricingengines.McSimulation.NULL_TOLERANCE,
                                    /* maxSamples=*/ org.jquantlib.pricingengines.McSimulation.NULL_SAMPLES,
                                    /* seed=*/ 42L);
                    option.setPricingEngine(mcEngine);

                    for ( final double u : underlyings ) {
                        for ( final double m : qRates ) {
                            for ( final double n : rRates ) {
                                for ( final double v : vols ) {
                                    spot.setValue(u);
                                    qRate.setValue(m);
                                    rRate.setValue(n);
                                    vol.setValue(v);

                                    final double expected = refOption.NPV();
                                    final double calculated = option.NPV();
                                    final double tol = tolerance.get("value");
                                    final double error = relativeError(expected, calculated, u);
                                    if ( error > tol ) {
                                        REPORT_FAILURE("value", payoff, exercise, u, m, n, today, v,
                                                expected, calculated, error, tol);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static double relativeError(final double x1, final double x2, final double ref) {
        if ( ref != 0.0 ) {
            return Math.abs(x1 - x2) / Math.abs(ref);
        }
        return Math.abs(x1 - x2);
    }

    private void REPORT_FAILURE(final String greekName, final StrikedTypePayoff payoff, final Exercise exercise,
            final double s, final double q, final double r, final Date today,
            final double v, final double expected, final double calculated, final double error, final double tolerance) {

        final StringBuilder sb = new StringBuilder();
        sb.append(exercise).append(" ");
        sb.append(payoff.optionType()).append(" option with ");
        sb.append(payoff.getClass().getName() + " payoff:\n");
        sb.append("    spot value:       " + s + "\n");

        sb.append("    strike:           " + payoff.strike() + "\n");
        sb.append("    dividend yield:   " + q + "\n");
        sb.append("    risk-free rate:   " + r + "\n");
        sb.append("    reference date:   " + today + "\n");
        sb.append("    maturity:         " + exercise.lastDate() + "\n");
        sb.append("    volatility:       " + v + "\n\n");
        sb.append("    expected " + greekName + ":   " + expected + "\n" );
        sb.append("    calculated " + greekName + ": " + calculated + "\n");
        sb.append("    error:            " + error + "\n");
        sb.append("    tolerance:        " + tolerance);
        fail(sb.toString());
    }

    /**
     * Local helper mirroring the {@code FFT} arm of the C++
     * {@code testEngineConsistency} helper (see
     * {@code test-suite/europeanoption.cpp:178-181}). The FFT Carr-Madan engine
     * computes a strip of strikes per expiry at once; we drive it option-by-option
     * (sharing the underlying process) and call
     * {@link FFTEngine#precalculate(List)} for each before NPV.
     */
    private void testEngineConsistencyFFT(final Map< String, Double > tolerance) {
        final Option.Type[] types = { Option.Type.Call, Option.Type.Put };
        final double[] strikes = { 75.0, 100.0, 125.0 };
        final int[] lengths = { 1 };
        final double[] underlyings = { 100.0 };
        final double[] qRates = { 0.00, 0.05 };
        final double[] rRates = { 0.01, 0.05, 0.15 };
        final double[] vols = { 0.11, 0.50, 1.20 };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote vol = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle< Quote >(spot),
                new Handle< YieldTermStructure >(qTS),
                new Handle< YieldTermStructure >(rTS),
                new Handle< BlackVolTermStructure >(volTS));

        for ( final Option.Type type : types ) {
            for ( final double strike : strikes ) {
                for ( final int length : lengths ) {
                    final Date exDate = today.add(length * 360);
                    final Exercise exercise = new EuropeanExercise(exDate);
                    final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);

                    final VanillaOption refOption = new EuropeanOption(payoff, exercise);
                    refOption.setPricingEngine(new AnalyticEuropeanEngine(process));

                    for ( final double u : underlyings ) {
                        for ( final double m : qRates ) {
                            for ( final double n : rRates ) {
                                for ( final double v : vols ) {
                                    spot.setValue(u);
                                    qRate.setValue(m);
                                    rRate.setValue(n);
                                    vol.setValue(v);

                                    final VanillaOption option = new EuropeanOption(payoff, exercise);
                                    final FFTVanillaEngine fft = new FFTVanillaEngine(process);
                                    final List< Instrument > batch = new ArrayList<>();
                                    batch.add(option);
                                    fft.precalculate(batch);
                                    option.setPricingEngine(fft);

                                    final double expected = refOption.NPV();
                                    final double calculated = option.NPV();
                                    final double tol = tolerance.get("value");
                                    final double error = relativeError(expected, calculated, u);
                                    if ( error > tol ) {
                                        REPORT_FAILURE("value", payoff, exercise, u, m, n, today, v,
                                                expected, calculated, error, tol);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
