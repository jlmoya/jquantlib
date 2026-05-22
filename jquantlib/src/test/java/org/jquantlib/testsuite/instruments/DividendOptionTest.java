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
 Copyright (C) 2004, 2005, 2007 StatPro Italia srl

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


import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.DividendVanillaOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.instruments.Option.Type;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticDividendEuropeanEngine;
import org.jquantlib.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import org.jquantlib.pricingengines.vanilla.finitedifferences.FDDividendAmericanEngine;
import org.jquantlib.pricingengines.vanilla.finitedifferences.FDDividendEuropeanEngine;
import org.jquantlib.pricingengines.vanilla.finitedifferences.FDEngineAdapter;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.cashflow.FixedDividend;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import static org.junit.Assert.assertTrue;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

//TODO: Figure out why tests for options with both continuous and discrete dividends fail.
//TODO: Make the known value test work.  It is slightly off from the answer in Hull probably due to date conventions.
public class DividendOptionTest {

    @Test
    public void testEuropeanValues() {

        QL.info("Testing dividend European option values with no dividends...");

        /* @Real */ final double tolerance = 1.0e-5;

        final Option.Type types[] = { Option.Type.Call, Option.Type.Put };
        /* @Real */ final double strikes[] = { 50.0, 99.5, 100.0, 100.5, 150.0 };
        /* @Real */ final double underlyings[] = { 100.0 };
        /* @Rate */ final double qRates[] = { 0.00, 0.10, 0.30 };
        /* @Rate */ final double rRates[] = { 0.01, 0.05, 0.15 };
        /* @Integer */ final int lengths[] = { 1, 2 };
        /* @Volatility */ final double vols[] = { 0.05, 0.20, 0.70 };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(Utilities.flatRate(qRate, dc));
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(Utilities.flatRate(rRate, dc));
        final SimpleQuote vol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(Utilities.flatVol(vol, dc));

        for (final Type type : types)
            for (final double strike : strikes)
                for (final int length : lengths) {
                  final Date exDate = today.add(new Period(length, TimeUnit.Years));
                  final Exercise exercise = new EuropeanExercise(exDate);

                  final List<Date> dividendDates = new ArrayList<>();
                  final List</* @Real */ Double> dividends = new ArrayList<>();
                  for (final Date d = today.add(new Period(3, TimeUnit.Months));
                             d.lt(exercise.lastDate());
                             d.addAssign(new Period(6, TimeUnit.Months))) {
                      dividendDates.add(d.clone());
                      dividends.add(0.0);
                  }

                  final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);
                  final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(new Handle<Quote>(spot), qTS, rTS, volTS);
                  final PricingEngine ref_engine = new AnalyticEuropeanEngine(stochProcess);
                  final PricingEngine engine = new AnalyticDividendEuropeanEngine(stochProcess);

                  final DividendVanillaOption option = new DividendVanillaOption(payoff, exercise, dividendDates, dividends);
                  option.setPricingEngine(engine);

                  final VanillaOption ref_option = new VanillaOption(payoff, exercise);
                  ref_option.setPricingEngine(ref_engine);

                  for (final double u : underlyings)
                    for (final double q : qRates)
                        for (final double r : rRates)
                            for (final double v : vols) {
                                spot.setValue(u);
                                qRate.setValue(q);
                                rRate.setValue(r);
                                vol.setValue(v);

                                /* @Real */ final double calculated = option.NPV();
                                /* @Real */ final double expected = ref_option.NPV();
                                /* @Real */ final double error = Math.abs(calculated-expected);
                                if (error > tolerance)
                                    REPORT_FAILURE("value start limit",
                                                   payoff, exercise,
                                                   u, q, r, today, v,
                                                   expected, calculated,
                                                   error, tolerance);
                            }
                }
    }

    // Reference pg. 253 - Hull - Options, Futures, and Other Derivatives 5th ed
    // Exercise 12.8

    @Test
    public void testEuropeanKnownValue() {

        QL.info("Testing dividend European option values with known value...");

        /* @Real */ final double tolerance = 1.0e-2;
        /* @Real */ final double expected = 3.67;

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(Utilities.flatRate(qRate, dc));
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(Utilities.flatRate(rRate, dc));
        final SimpleQuote vol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(Utilities.flatVol(vol, dc));

        final Date exDate = today.add(new Period(6, TimeUnit.Months));
        final Exercise exercise = new EuropeanExercise(exDate);

        final List<Date> dividendDates = new ArrayList<>();
        final List</* @Real */ Double> dividends = new ArrayList<>();
        dividendDates.add(today.add(new Period(2, TimeUnit.Months)));
        dividends.add(0.50);
        dividendDates.add(today.add(new Period(5, TimeUnit.Months)));
        dividends.add(0.50);

        final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 40.0);
        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(new Handle<Quote>(spot), qTS, rTS, volTS);
        final PricingEngine engine = new AnalyticDividendEuropeanEngine(stochProcess);

        final DividendVanillaOption option = new DividendVanillaOption(payoff, exercise, dividendDates, dividends);
        option.setPricingEngine(engine);

        /* @Real */ final double u = 40.0;
        /* @Rate */ final double q = 0.0, r = 0.09;
        /* @Volatility */ final double v = 0.30;
        spot.setValue(u);
        qRate.setValue(q);
        rRate.setValue(r);
        vol.setValue(v);

        /* @Real */ final double calculated = option.NPV();
        /* @Real */ final double error = Math.abs(calculated-expected);
        if (error > tolerance)
            REPORT_FAILURE("value start limit",
                           payoff, exercise,
                           u, q, r, today, v,
                           expected, calculated,
                           error, tolerance);
    }


    @Test
    public void testEuropeanStartLimit() {

        QL.info("Testing dividend European option with a dividend on today's date...");

        /* @Real */ final double tolerance = 1.0e-5;
        /* @Real */ final double dividendValue = 10.0;

        final Option.Type types[] = { Option.Type.Call, Option.Type.Put };
        /* @Real */ final double strikes[] = { 50.0, 99.5, 100.0, 100.5, 150.0 };
        /* @Real */ final double underlyings[] = { 100.0 };
        /* @Rate */ final double qRates[] = { 0.00, 0.10, 0.30 };
        /* @Rate */ final double rRates[] = { 0.01, 0.05, 0.15 };
        /* @Integer */ final int lengths[] = { 1, 2 };
        /* @Volatility */ final double vols[] = { 0.05, 0.20, 0.70 };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(Utilities.flatRate(qRate, dc));
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(Utilities.flatRate(rRate, dc));
        final SimpleQuote vol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(Utilities.flatVol(vol, dc));

        for (final Type type : types)
            for (final double strike : strikes)
                for (final int length : lengths) {
                  final Date exDate = today.add(new Period(length, TimeUnit.Months));
                  final Exercise exercise = new EuropeanExercise(exDate);

                  final List<Date> dividendDates = new ArrayList<>();
                  final List</* @Real */ Double> dividends = new ArrayList<>();
                  dividendDates.add(today);
                  dividends.add(dividendValue);

                  final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);
                  final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(new Handle<Quote>(spot), qTS, rTS, volTS);
                  final PricingEngine engine = new AnalyticDividendEuropeanEngine(stochProcess);
                  final PricingEngine ref_engine = new AnalyticEuropeanEngine(stochProcess);

                  final DividendVanillaOption option = new DividendVanillaOption(payoff, exercise, dividendDates, dividends);
                  option.setPricingEngine(engine);

                  final VanillaOption ref_option = new VanillaOption(payoff, exercise);
                  ref_option.setPricingEngine(ref_engine);

                  for (final double u : underlyings)
                    for (final double q : qRates)
                        for (final double r : rRates)
                            for (final double v : vols) {
                                spot.setValue(u);
                                qRate.setValue(q);
                                rRate.setValue(r);
                                vol.setValue(v);

                                /* @Real */ final double calculated = option.NPV();
                                spot.setValue(u-dividendValue);
                                /* @Real */ final double expected = ref_option.NPV();
                                /* @Real */ final double error = Math.abs(calculated-expected);
                                if (error > tolerance)
                                    REPORT_FAILURE("value", payoff, exercise,
                                                   u, q, r, today, v,
                                                   expected, calculated,
                                                   error, tolerance);
                            }
                }
    }

    @Test
    public void testEuropeanEndLimit() {

        QL.info("Testing dividend European option values with end limits...");

        /* @Real */ final double tolerance = 1.0e-5;
        /* @Real */ final double dividendValue = 10.0;

        final Option.Type types[] = { Option.Type.Call, Option.Type.Put };
        /* @Real */ final double strikes[] = { 50.0, 99.5, 100.0, 100.5, 150.0 };
        /* @Real */ final double underlyings[] = { 100.0 };
        /* @Rate */ final double qRates[] = { 0.00, 0.10, 0.30 };
        /* @Rate */ final double rRates[] = { 0.01, 0.05, 0.15 };
        /* @Integer */ final int lengths[] = { 1, 2 };
        /* @Volatility */ final double vols[] = { 0.05, 0.20, 0.70 };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(Utilities.flatRate(qRate, dc));
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(Utilities.flatRate(rRate, dc));
        final SimpleQuote vol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(Utilities.flatVol(vol, dc));

        for (final Type type : types)
            for (final double strike : strikes)
                for (final int length : lengths) {
                  final Date exDate = today.add(new Period(length, TimeUnit.Years));
                  final Exercise exercise = new EuropeanExercise(exDate);

                  final List<Date> dividendDates = new ArrayList<>();
                  final List</* @Real */ Double> dividends = new ArrayList<>();
                  dividendDates.add(exercise.lastDate());
                  dividends.add(dividendValue);

                  final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);
                  final StrikedTypePayoff refPayoff = new PlainVanillaPayoff(type, strike + dividendValue);
                  final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(new Handle<Quote>(spot), qTS, rTS, volTS);
                  final PricingEngine engine = new AnalyticDividendEuropeanEngine(stochProcess);
                  final PricingEngine ref_engine = new AnalyticEuropeanEngine(stochProcess);

                  final DividendVanillaOption option = new DividendVanillaOption(payoff, exercise,dividendDates, dividends);
                  option.setPricingEngine(engine);

                  final VanillaOption ref_option = new VanillaOption(refPayoff, exercise);
                  ref_option.setPricingEngine(ref_engine);

                  for (final double u : underlyings)
                    for (final double q : qRates)
                        for (final double r : rRates)
                            for (final double v : vols) {
                                /* @Volatility */ spot.setValue(u);
                                qRate.setValue(q);
                                rRate.setValue(r);
                                vol.setValue(v);

                                /* @Real */ final double calculated = option.NPV();
                                /* @Real */ final double expected = ref_option.NPV();
                                /* @Real */ final double error = Math.abs(calculated-expected);
                                if (error > tolerance)
                                    REPORT_FAILURE("value", payoff, exercise,
                                                   u, q, r, today, v,
                                                   expected, calculated,
                                                   error, tolerance);
                            }
                }
    }


    @Test
    public void testEuropeanGreeks() {

        QL.info("Testing dividend European option greeks...");

        final Map<String, /* @Real */ Double> calculated = new HashMap<>();
        final Map<String, /* @Real */ Double> expected = new HashMap<>();
        final Map<String, /* @Real */ Double> tolerance = new HashMap<>();
        tolerance.put("delta", 1.0e-5);
        tolerance.put("gamma", 1.0e-5);
        tolerance.put("theta", 1.0e-5);
        tolerance.put("rho",   1.0e-5);
        tolerance.put("vega",  1.0e-5);

        final Option.Type types[] = { Option.Type.Call, Option.Type.Put };
        /* @Real */ final double strikes[] = { 50.0, 99.5, 100.0, 100.5, 150.0 };
        /* @Real */ final double underlyings[] = { 100.0 };
        /* @Rate */ final double qRates[] = { 0.00, 0.10, 0.30 };
        /* @Rate */ final double rRates[] = { 0.01, 0.05, 0.15 };
        /* @Integer */ final int lengths[] = { 1, 2 };
        /* @Volatility */ final double vols[] = { 0.05, 0.20, 0.40 };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(Utilities.flatRate(qRate, dc));
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(Utilities.flatRate(rRate, dc));
        final SimpleQuote vol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(Utilities.flatVol(vol, dc));

        for (final Type type : types)
            for (final double strike : strikes)
                for (final int length : lengths) {
                  final Date exDate = today.add(new Period(length, TimeUnit.Years));
                  final Exercise exercise = new EuropeanExercise(exDate);

                  final List<Date> dividendDates = new ArrayList<>();
                  final List</* @Real */ Double> dividends = new ArrayList<>();
                  for (final Date d = today.add(new Period(3, TimeUnit.Months));
                             d.lt(exercise.lastDate());
                             d.addAssign(new Period(6, TimeUnit.Months))) {
                      dividendDates.add(d.clone());
                      dividends.add(5.0);
                  }

                  final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);
                  final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(new Handle<Quote>(spot), qTS, rTS, volTS);
                  final PricingEngine engine = new AnalyticDividendEuropeanEngine(stochProcess);

                  final DividendVanillaOption option = new DividendVanillaOption(payoff, exercise, dividendDates, dividends);
                  option.setPricingEngine(engine);

                  for (final double u : underlyings)
                    for (final double q : qRates)
                        for (final double r : rRates)
                            for (final double v : vols) {
                                spot.setValue(u);
                                qRate.setValue(q);
                                rRate.setValue(r);
                                vol.setValue(v);

                                /* @Real */ final double value = option.NPV();
                                calculated.put("delta", option.delta());
                                calculated.put("gamma", option.gamma());
                                calculated.put("theta", option.theta());
                                calculated.put("rho",   option.rho());
                                calculated.put("vega",  option.vega());

                                if (value > spot.value()*1.0e-5) {
                                    // perturb spot and get delta and gamma
                                    /* @Real */ final double du = u*1.0e-4;
                                    spot.setValue(u+du);
                                    /* @Real */ double value_p = option.NPV();
                                    final double delta_p = option.delta();
                                    spot.setValue(u-du);
                                    /* @Real */ double value_m = option.NPV();
                                    final double delta_m = option.delta();
                                    spot.setValue(u);
                                    expected.put("delta", (value_p - value_m)/(2*du) );
                                    expected.put("gamma", (delta_p - delta_m)/(2*du) );

                                    // perturb risk-free /* @Rate */ double and get rho
                                    final /* @Spread */ double dr = r*1.0e-4;
                                    rRate.setValue(r+dr);
                                    value_p = option.NPV();
                                    rRate.setValue(r-dr);
                                    value_m = option.NPV();
                                    rRate.setValue(r);
                                    expected.put("rho", (value_p - value_m)/(2*dr) );

                                    // perturb /* @Volatility */ double and get vega
                                    final /* @Spread */ double dv = v*1.0e-4;
                                    vol.setValue(v+dv);
                                    value_p = option.NPV();
                                    vol.setValue(v-dv);
                                    value_m = option.NPV();
                                    vol.setValue(v);
                                    expected.put("vega", (value_p - value_m)/(2*dv) );

                                    // perturb date and get theta
                                    final /*@Time*/ double dT = dc.yearFraction(today.sub(1), today.add(1));
                                    new Settings().setEvaluationDate(today.sub(1));
                                    value_m = option.NPV();
                                    new Settings().setEvaluationDate(today.add(1));
                                    value_p = option.NPV();
                                    new Settings().setEvaluationDate(today);
                                    expected.put("theta", (value_p - value_m)/dT );

                                    // compare
                                    for (final Map.Entry<String, Double> it : calculated.entrySet()) {

                                        final String greek = it.getKey();
                                        /* @Real */final double expct = expected.get(greek);
                                        /* @Real */final double calcl = it.getValue();
                                        /* @Real */final double tol = tolerance.get(greek);
                                        /* @Real */final double error = Utilities.relativeError(expct, calcl, u);
                                        if (error > tol)
                                            REPORT_FAILURE(greek, payoff, exercise, u, q, r, today, v, expct, calcl, error, tol);
                                    }
                                }
                            }
                }
    }


    @Test
    public void testFdEuropeanValues() {

        QL.info("Testing finite-difference dividend European option values...");

        /* @Real */ final double tolerance = 1.0e-2;
        final /* @Size */ int gridPoints = 300;
        final /* @Size */ int timeSteps = 40;

        final Option.Type types[] = { Option.Type.Call, Option.Type.Put };
        /* @Real */ final double strikes[] = { 50.0, 99.5, 100.0, 100.5, 150.0 };
        /* @Real */ final double underlyings[] = { 100.0 };
        // /* @Rate */ double qRates[] = { 0.00, 0.10, 0.30 };
        // Analytic dividend may not be handling q correctly
        /* @Rate */ final double qRates[] = { 0.00 };
        /* @Rate */ final double rRates[] = { 0.01, 0.05, 0.15 };
        /* @Integer */ final int lengths[] = { 1, 2 };
        /* @Volatility */ final double vols[] = { 0.05, 0.20, 0.40 };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(Utilities.flatRate(qRate, dc));
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(Utilities.flatRate(rRate, dc));
        final SimpleQuote vol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(Utilities.flatVol(vol, dc));

        for (final Type type : types)
            for (final double strike : strikes)
                for (final int length : lengths) {
                  final Date exDate = today.add(new Period(length, TimeUnit.Years));
                  final Exercise exercise = new EuropeanExercise(exDate);

                  final List<Date> dividendDates = new ArrayList<>();
                  final List</* @Real */ Double> dividends = new ArrayList<>();
                  for (final Date d = today.add(new Period(3, TimeUnit.Months));
                             d.lt(exercise.lastDate());
                             d.addAssign(new Period(6, TimeUnit.Months))) {
                      dividendDates.add(d.clone());
                      dividends.add(5.0);
                  }

                  final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);
                  final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(new Handle<Quote>(spot), qTS, rTS, volTS);
                  final PricingEngine engine = new FDDividendEuropeanEngine(stochProcess, timeSteps, gridPoints);
                  final PricingEngine ref_engine = new AnalyticDividendEuropeanEngine(stochProcess);

                  final DividendVanillaOption option = new DividendVanillaOption(payoff, exercise, dividendDates, dividends);
                  option.setPricingEngine(engine);

                  final DividendVanillaOption ref_option = new DividendVanillaOption(payoff, exercise, dividendDates, dividends);
                  ref_option.setPricingEngine(ref_engine);

                  for (final double u : underlyings)
                    for (final double q : qRates)
                        for (final double r : rRates)
                            for (final double v : vols) {
                                spot.setValue(u);
                                qRate.setValue(q);
                                rRate.setValue(r);
                                vol.setValue(v);
                                // FLOATING_POINT_EXCEPTION
                                /* @Real */ final double calculated = option.NPV();
                                if (calculated > spot.value()*1.0e-5) {
                                    /* @Real */ final double expected = ref_option.NPV();
                                    /* @Real */ final double error = Math.abs(calculated-expected);
                                    if (error > tolerance)
                                        REPORT_FAILURE("value", payoff, exercise,
                                                       u, q, r, today, v,
                                                       expected, calculated,
                                                       error, tolerance);
                                }
                            }
                }
    }


    @Test
    public void testFdEuropeanGreeks() {

        QL.info("Testing finite-differences dividend European option greeks...");
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        /* @Integer */ final int lengths[] = { 1, 2 };

        for (final int length : lengths) {
            final Date exDate = today.add(new Period(length, TimeUnit.Years));
            final Exercise exercise = new EuropeanExercise(exDate);
            testFdGreeks(FDDividendEuropeanEngine.class, today, exercise);
        }
    }

    @Test
    public void testFdAmericanGreeks() {
        QL.info("Testing finite-differences dividend American option greeks...");
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        /* @Integer */ final int lengths[] = { 1, 2 };

        for (final int length : lengths) {
            final Date exDate = today.add(new Period(length, TimeUnit.Years));
            final Exercise exercise = new AmericanExercise(today, exDate);
            testFdGreeks(FDDividendAmericanEngine.class, today, exercise);
        }
    }


    @Test
    public void testFdEuropeanDegenerate() {

        QL.info("Testing degenerate finite-differences dividend European option...");

        final Date today = new Date(27, Month.February, 2005);
        new Settings().setEvaluationDate(today);
        final Date exDate = new Date(13, Month.April, 2005);
        final Exercise exercise = new EuropeanExercise(exDate);
        testFdDegenerate(FDDividendEuropeanEngine.class, today, exercise);
    }

    @Test
    public void testFdAmericanDegenerate() {

        QL.info("Testing degenerate finite-differences dividend American option...");

        final Date today = new Date(27, Month.February,2005);
        new Settings().setEvaluationDate(today);
        final Date exDate = new Date(13, Month.April, 2005);
        final Exercise exercise = new AmericanExercise(today, exDate);
        testFdDegenerate(FDDividendAmericanEngine.class, today, exercise);
    }


    private <T extends FDEngineAdapter> void testFdGreeks(final Class<T> engineClass, final Date today, final Exercise exercise) {

        final Map<String, /* @Real */ Double> calculated = new HashMap<>();
        final Map<String, /* @Real */ Double> expected = new HashMap<>();
        final Map<String, /* @Real */ Double> tolerance = new HashMap<>();
        tolerance.put("delta", 5.0e-3);
        tolerance.put("gamma", 7.0e-3);
        // tolerance.put("theta", 1.0e-2);

        final Option.Type types[] = { Option.Type.Call, Option.Type.Put };
        /* @Real */ final double strikes[] = { 50.0, 99.5, 100.0, 100.5, 150.0 };
        /* @Real */ final double underlyings[] = { 100.0 };
        /* @Rate */ final double qRates[] = { 0.00, 0.10, 0.20 };
        /* @Rate */ final double rRates[] = { 0.01, 0.05, 0.15 };
        /* @Volatility */ final double vols[] = { 0.05, 0.20, 0.50 };

        final DayCounter dc = new Actual360();

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(Utilities.flatRate(qRate, dc));
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(Utilities.flatRate(rRate, dc));
        final SimpleQuote vol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(Utilities.flatVol(vol, dc));

        for (final Type type : types)
            for (final double strike : strikes) {

                final List<Date> dividendDates = new ArrayList<>();
                final List</* @Real */ Double> dividends = new ArrayList<>();
                for (final Date d = today.add(new Period(3, TimeUnit.Months));
                           d.lt(exercise.lastDate());
                           d.addAssign(new Period(6, TimeUnit.Months))) {
                    dividendDates.add(d.clone());
                    dividends.add(5.0);
                }

                final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(new Handle<Quote>(spot), qTS, rTS, volTS);
                final PricingEngine engine;
                try {
                    final Constructor<T> baseConstructor = engineClass.getConstructor(GeneralizedBlackScholesProcess.class);
                    engine = baseConstructor.newInstance(stochProcess);
                } catch (final Exception e) {
                    throw new LibraryException(e);
                }
                final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);

                final DividendVanillaOption option = new DividendVanillaOption(payoff, exercise, dividendDates, dividends);
                option.setPricingEngine(engine);

                for (final double u : underlyings)
                    for (final double q : qRates)
                        for (final double r : rRates)
                            for (final double v : vols) {
                                spot.setValue(u);
                                qRate.setValue(q);
                                rRate.setValue(r);
                                vol.setValue(v);

                                // FLOATING_POINT_EXCEPTION
                                /* @Real */ final double value = option.NPV();
                                calculated.put("delta", option.delta() );
                                calculated.put("gamma", option.gamma() );
                                // calculated.put("theta", option.theta() );

                                if (value > spot.value()*1.0e-5) {
                                  // perturb spot and get delta and gamma
                                  /* @Real */ final double du = u*1.0e-4;
                                  spot.setValue(u+du);
                                  /* @Real */ final double value_p = option.NPV(),
                                       delta_p = option.delta();
                                  spot.setValue(u-du);
                                  /* @Real */ final double value_m = option.NPV(),
                                       delta_m = option.delta();
                                  spot.setValue(u);
                                  expected.put("delta", (value_p - value_m)/(2*du) );
                                  expected.put("gamma", (delta_p - delta_m)/(2*du) );

                                  // perturb date and get theta
                                  /*
                                    Time dT = dc.yearFraction(today-1, today+1);
                                    new Settings().setEvaluationDate(today.sub(1));
                                    value_m = option.NPV();
                                    new Settings().setEvaluationDate(today.add(1));
                                    value_p = option.NPV();
                                    new Settings().setEvaluationDate(today);
                                    expected.put("theta", (value_p - value_m)/dT );
                                  */

                                  // compare
                                  for (final Map.Entry<String, Double> it : calculated.entrySet()) {

                                      final String greek = it.getKey();
                                      /* @Real */final double expct = expected.get(greek);
                                      /* @Real */final double calcl = it.getValue();
                                      /* @Real */final double tol = tolerance.get(greek);
                                      /* @Real */final double error = Utilities.relativeError(expct, calcl, u);
                                      if (error > tol)
                                        REPORT_FAILURE(greek, payoff, exercise, u, q, r, today, v, expct, calcl, error, tol);
                                  }
                                }
                              }
            }
    }


    private <T extends FDEngineAdapter> void testFdDegenerate(final Class<T> engineClass, final Date today, final Exercise exercise) {
        final DayCounter dc = new Actual360();
        final SimpleQuote spot = new SimpleQuote(54.625);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(Utilities.flatRate(0.052706, dc));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(Utilities.flatRate(0.0, dc));
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(Utilities.flatVol(0.282922, dc));
        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(new Handle<Quote>(spot), qTS, rTS, volTS);

        final /* @Size */ int timeSteps = 40;
        final /* @Size */ int gridPoints = 300;

        final PricingEngine engine;
        try {
            final Constructor<T> baseConstructor = engineClass.getConstructor(GeneralizedBlackScholesProcess.class, int.class, int.class);
            engine = baseConstructor.newInstance(process, timeSteps, gridPoints);
        } catch (final Exception e) {
            throw new LibraryException(e);
        }

        final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 55.0);
        final /* @Real */ double tolerance = 3.0e-3;

        final List<Date> dividendDates = new ArrayList<>();
        final List</* @Real */ Double> dividends = new ArrayList<>();

        final DividendVanillaOption option1 = new DividendVanillaOption(payoff, exercise, dividendDates, dividends);
        option1.setPricingEngine(engine);

        // FLOATING_POINT_EXCEPTION
        final /* @Real */ double refValue = option1.NPV();

        for (/* @Size */ int i=0; i<=6; i++) {
            dividends.add(0.0);
            dividendDates.add(today.add(i));

            final DividendVanillaOption option = new DividendVanillaOption(payoff, exercise, dividendDates, dividends);
            option.setPricingEngine(engine);
            final /* @Real */ double value = option.NPV();

            if (Math.abs(refValue-value) > tolerance) {
                final StringBuilder sb = new StringBuilder();
                sb.append("NPV changed by null dividend :\n");
                sb.append("    previous value: ").append(refValue).append('\n');
                sb.append("    current value:  ").append(value).append('\n');
                sb.append("    change:         ").append(value-refValue);
                fail(sb.toString());
            }
        }
    }


    private void REPORT_FAILURE(
            final String greekName,
            final StrikedTypePayoff payoff,
            final Exercise exercise,
            final double s, final double q, final double r,
            final Date today, final double v,
            final double expected, final double calculated,
            final double error, final double tolerance) {

        final StringBuilder sb = new StringBuilder();

        sb.append(exercise).append(" ");
        sb.append(payoff.optionType()).append(" option with ");
        sb.append(payoff).append(" payoff:\n");
        sb.append("    spot value:       ").append(s).append("\n");
        sb.append("    strike:           ").append(payoff.strike()).append("\n");
        sb.append("    dividend yield:   ").append(q).append("\n");
        sb.append("    risk-free rate:   ").append(r).append("\n");
        sb.append("    reference date:   ").append(today).append("\n");
        sb.append("    maturity:         ").append(exercise.lastDate()).append("\n");
        sb.append("    volatility:       ").append(v).append("\n\n");
        sb.append("    expected ").append(greekName).append(":   ").append(expected).append("\n");
        sb.append("    calculated ").append(greekName).append(": ").append(calculated).append("\n");
        sb.append("    error:            ").append(error).append("\n");
        sb.append("    tolerance:        ").append(tolerance);
    }

    /**
     * Java port of v1.42.1 {@code test-suite/dividendoption.cpp:testEscrowedDividendModel} (line 951).
     * <p>
     * Cross-validates the FdBlackScholesVanillaEngine Escrowed dividend model against the closed-form
     * AnalyticDividendEuropeanEngine on a European put with two cash dividends. The Escrowed-PDE NPV
     * and delta must agree with the analytic reference to tolerance 0.0025.
     */
    @Test
    public void testEscrowedDividendModel() {
        QL.info("Testing finite-difference European engine with the escrowed dividend model...");

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(12, Month.October, 2019);
        new Settings().setEvaluationDate(today);

        final Handle< Quote > spot = new Handle< Quote >(new SimpleQuote(100.0));
        final Handle< YieldTermStructure > qTS = new Handle< YieldTermStructure >(Utilities.flatRate(today, 0.063, dc));
        final Handle< YieldTermStructure > rTS = new Handle< YieldTermStructure >(Utilities.flatRate(today, 0.094, dc));
        final Handle< BlackVolTermStructure > volTS = new Handle< BlackVolTermStructure >(
                Utilities.flatVol(today, 0.3, dc));

        final Date maturity = today.add(new Period(1, TimeUnit.Years));

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(spot, qTS, rTS, volTS);

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Put, spot.currentLink().value());
        final Exercise exercise = new EuropeanExercise(maturity);

        // 2 dividends: 8.3 @ 3 months, 6.8 @ 9 months
        final List< Date > divDates = new ArrayList<>();
        divDates.add(today.add(new Period(3, TimeUnit.Months)));
        divDates.add(today.add(new Period(9, TimeUnit.Months)));
        final List< Double > divAmounts = new ArrayList<>();
        divAmounts.add(8.3);
        divAmounts.add(6.8);

        // Analytic reference (closed-form discounted-spot dividend model).
        final DividendVanillaOption refOption = new DividendVanillaOption(payoff, exercise, divDates, divAmounts);
        refOption.setPricingEngine(new AnalyticDividendEuropeanEngine(process));

        final double analyticNPV = refOption.NPV();
        final double analyticDelta = refOption.delta();

        // FD Escrowed engine (under test).
        final DividendSchedule divs = new DividendSchedule();
        for ( int i = 0; i < divDates.size(); i++ ) {
            divs.add(new FixedDividend(divAmounts.get(i), divDates.get(i)));
        }
        final VanillaOption option = new VanillaOption(payoff, exercise);
        option.setPricingEngine(
                new FdBlackScholesVanillaEngine(process, divs, null, 50, 200, 1, FdmSchemeDesc.Douglas(),
                        FdBlackScholesVanillaEngine.CashDividendModel.Escrowed, false, Double.NaN));

        final double pdeNPV = option.NPV();
        final double pdeDelta = option.delta();

        final double tol = 0.0025;

        assertTrue("NPV escrowed PDE=" + pdeNPV + " vs analytic=" + analyticNPV + " diff=" + Math.abs(pdeNPV - analyticNPV)
                + " tol=" + tol, Math.abs(pdeNPV - analyticNPV) <= tol);

        assertTrue("Delta escrowed PDE=" + pdeDelta + " vs analytic=" + analyticDelta + " diff=" + Math.abs(
                pdeDelta - analyticDelta) + " tol=" + tol, Math.abs(pdeDelta - analyticDelta) <= tol);
    }

    /**
     * Java helper mirroring {@code test-suite/dividendoption.cpp:testFdDividendAtTZero} (line 857).
     * <p>
     * Builds an FD-BS vanilla engine with a single cash dividend at {@code today} and verifies the FD
     * NPV agrees with the closed-form {@link AnalyticDividendEuropeanEngine} reference to {@code 1e-4}.
     * Theta is checked per the {@code model} switch — Spot is expected to misbehave on T==0 (C++
     * throws, Java returns NaN, see callsite notes), while Escrowed should yield a finite value.
     */
    private void testFdDividendAtTZero(final Date today, final Exercise exercise,
            final FdBlackScholesVanillaEngine.CashDividendModel model) {
        final DayCounter dc = new Actual360();
        final SimpleQuote spot = new SimpleQuote(54.625);
        final Handle< YieldTermStructure > rTS = new Handle< YieldTermStructure >(Utilities.flatRate(0.0, dc));
        final Handle< BlackVolTermStructure > volTS = new Handle< BlackVolTermStructure >(
                Utilities.flatVol(0.282922, dc));

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(new Handle< Quote >(spot), rTS, rTS,
                volTS);

        final int timeSteps = 50;
        final int gridPoints = 400;

        final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 55.0);

        // today's dividend must be taken into account
        final List< Date > dividendDates = new ArrayList<>();
        dividendDates.add(today);
        final List< Double > dividendAmounts = new ArrayList<>();
        dividendAmounts.add(1.0);

        // Build the FD engine via the full constructor (no MakeFdBlackScholesVanillaEngine builder in Java).
        final DividendSchedule divs = new DividendSchedule();
        divs.add(new FixedDividend(dividendAmounts.get(0), dividendDates.get(0)));
        final VanillaOption option = new VanillaOption(payoff, exercise);
        option.setPricingEngine(
                new FdBlackScholesVanillaEngine(process, divs, null, timeSteps, gridPoints, 0, FdmSchemeDesc.Douglas(),
                        model, false, Double.NaN));
        final double calculated = option.NPV();

        // Theta behaviour — C++ throws for Spot (dividend-at-T0 hits a stopping-time = 0 / theta-rollback edge)
        // and is expected to return a finite value for Escrowed (PV-discounted dividends don't pin a grid node).
        // Java now mirrors C++ as of Phase 1.2-C (Fdm1DimSolver T=0 NaN → NULL_REAL fix, commit f89df992):
        // option.theta() on Spot throws "theta not provided" (was: returned NaN). Escrowed remains finite.
        switch ( model ) {
            case Spot:
                try {
                    final double t = option.theta();
                    fail("Spot model theta() at T=0 should throw, got " + t);
                } catch (final RuntimeException expected) {
                    // expected — see comment above; mirrors C++ QL_REQUIRE(theta_ != Null<Real>())
                }
                break;
            case Escrowed:
                final double thetaValue = option.theta();
                assertTrue("Escrowed theta should be finite (non-NaN), got " + thetaValue, !Double.isNaN(thetaValue));
                break;
            default:
                fail("unknown dividend model type");
        }

        // Closed-form reference uses DividendVanillaOption (Java's AnalyticDividendEuropeanEngine reads
        // cashFlow from the instrument arguments, not from a separate DividendVector parameter).
        final Exercise europeanExercise = new EuropeanExercise(exercise.lastDate());
        final DividendVanillaOption europeanOption = new DividendVanillaOption(payoff, europeanExercise, dividendDates,
                dividendAmounts);
        europeanOption.setPricingEngine(new AnalyticDividendEuropeanEngine(process));

        final double expected = europeanOption.NPV();

        final double tol = 1e-4;
        if ( Math.abs(calculated - expected) > tol ) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Can not reproduce reference values from analytic dividend engine :\n");
            sb.append("    calculated: ").append(calculated).append('\n');
            sb.append("    expected  : ").append(expected).append('\n');
            sb.append("    diff:       ").append(Math.abs(calculated - expected)).append('\n');
            sb.append("    tol:        ").append(tol);
            fail(sb.toString());
        }
    }

    /**
     * Faithful port of {@code test-suite/dividendoption.cpp:922} {@code BOOST_AUTO_TEST_CASE(testFdEuropeanWithDividendToday)}.
     * <p>
     * Exercises both {@code Spot} and {@code Escrowed} cash-dividend models via the
     * {@link FdBlackScholesVanillaEngine} on a European call where the only dividend is scheduled on
     * {@code today}. The FD NPV must match the closed-form analytic reference. Theta semantics differ
     * from C++ on the {@code Spot} branch — see {@link #testFdDividendAtTZero}.
     */
    @Test
    public void testFdEuropeanWithDividendToday() {
        QL.info("Testing finite-differences dividend European option with dividend on today's date...");

        final Date today = new Date(27, Month.February, 2005);
        new Settings().setEvaluationDate(today);
        final Date exDate = new Date(13, Month.April, 2005);

        final Exercise exercise = new EuropeanExercise(exDate);

        testFdDividendAtTZero(today, exercise, FdBlackScholesVanillaEngine.CashDividendModel.Spot);
        testFdDividendAtTZero(today, exercise, FdBlackScholesVanillaEngine.CashDividendModel.Escrowed);
    }

    /**
     * Faithful port of {@code test-suite/dividendoption.cpp:937} {@code BOOST_AUTO_TEST_CASE(testFdAmericanWithDividendToday)}.
     * <p>
     * Same shape as {@link #testFdEuropeanWithDividendToday} but on an {@link AmericanExercise} and only
     * the {@code Spot} cash-dividend model (Escrowed is not exercised on the American branch in C++).
     */
    @Test
    public void testFdAmericanWithDividendToday() {
        QL.info("Testing finite-differences dividend American option with dividend on today's date...");

        final Date today = new Date(27, Month.February, 2005);
        new Settings().setEvaluationDate(today);
        final Date exDate = new Date(13, Month.April, 2005);

        final Exercise exercise = new AmericanExercise(today, exDate);

        testFdDividendAtTZero(today, exercise, FdBlackScholesVanillaEngine.CashDividendModel.Spot);
    }

    // ------------------------------------------------------------------
    // EXISTING_EQUIVALENT carry-over note (Phase1-D5-B-R3 retained)
    // ------------------------------------------------------------------
    // testFdEuropeanGreeks (cpp:722, gated by *precondition(if_speed(Fast)))
    //   EXISTING_EQUIVALENT: covered by {@link #testFdEuropeanGreeks} above.
    //   The Java existing test uses FDDividendEuropeanEngine (the legacy
    //   Java engine) rather than FdBlackScholesVanillaEngine with the
    //   Spot/Escrowed cash-dividend-model switch from v1.42.1 — the Spot
    //   path is functionally equivalent.

    /**
     * Faithful port of v1.42.1 {@code test-suite/dividendoption.cpp:testCashDividendEuropeanEngine} (line 1024).
     * <p>
     * Cross-validates the new {@link org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine}
     * against the FD-PDE {@link FdBlackScholesVanillaEngine}, for both {@code Spot} and
     * {@code Escrowed} cash-dividend models, on a grid of (call/put) x (two maturities) x
     * (four strikes). Tolerance: 0.005 absolute.
     */
    @Test
    public void testCashDividendEuropeanEngine() {
        QL.info("Testing cash-dividend European engine with finite-difference European engine...");

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(1, Month.January, 2024);
        new Settings().setEvaluationDate(today);

        final Date[] rDates = new Date[] {
                today, new Date(1, Month.May, 2024), new Date(1, Month.November, 2024), new Date(1, Month.January, 2027)
        };
        final double[] rRates = new double[] { 0.3, 0.15, 0.1, 0.15 };
        final Handle< YieldTermStructure > rTS = new Handle< YieldTermStructure >(
                new org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve<
                        org.jquantlib.math.interpolations.factories.Linear >(
                        org.jquantlib.math.interpolations.factories.Linear.class, rDates, rRates, dc));

        final Date[] qDates = new Date[] {
                today, new Date(1, Month.May, 2024), new Date(1, Month.November, 2025), new Date(1, Month.January, 2027)
        };
        final double[] qRates = new double[] { 0.05, 0.03, 0.1, 0.05 };
        final Handle< YieldTermStructure > qTS = new Handle< YieldTermStructure >(
                new org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve<
                        org.jquantlib.math.interpolations.factories.Linear >(
                        org.jquantlib.math.interpolations.factories.Linear.class, qDates, qRates, dc));

        final Date[] vDates = new Date[] {
                new Date(2, Month.January, 2024), new Date(1, Month.July, 2024),
                new Date(1, Month.August, 2024), new Date(1, Month.January, 2027)
        };
        final double[] vVols = new double[] { 0.3, 0.4, 0.42, 0.5 };
        final org.jquantlib.termstructures.volatilities.BlackVarianceCurve vCurve =
                new org.jquantlib.termstructures.volatilities.BlackVarianceCurve(today, vDates, vVols, dc);
        vCurve.setInterpolation();
        final Handle< BlackVolTermStructure > vTS = new Handle< BlackVolTermStructure >(vCurve);

        final Handle< Quote > spot = new Handle< Quote >(new SimpleQuote(100.0));
        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(spot, qTS, rTS, vTS);

        final List< Date > dividendDates = new ArrayList<>();
        dividendDates.add(new Date(1, Month.April, 2024));
        dividendDates.add(new Date(1, Month.November, 2024));
        dividendDates.add(new Date(1, Month.October, 2024));
        dividendDates.add(new Date(1, Month.April, 2026));
        dividendDates.add(new Date(27, Month.March, 2028));
        dividendDates.add(new Date(1, Month.October, 2023));
        final List< Double > dividendAmounts = new ArrayList<>();
        dividendAmounts.add(4.0);
        dividendAmounts.add(10.0);
        dividendAmounts.add(2.0);
        dividendAmounts.add(5.0);
        dividendAmounts.add(25.0);
        dividendAmounts.add(15.0);

        final DividendSchedule dividendSchedule = new DividendSchedule();
        for ( int i = 0; i < dividendDates.size(); i++ ) {
            dividendSchedule.add(new org.jquantlib.cashflow.FixedDividend(dividendAmounts.get(i), dividendDates.get(i)));
        }

        final double tol = 0.005;
        for ( final org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine.CashDividendModel cashDivModel
                : new org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine.CashDividendModel[] {
                        org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine.CashDividendModel.Spot,
                        org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine.CashDividendModel.Escrowed
                } ) {

            final FdBlackScholesVanillaEngine.CashDividendModel fdModel =
                    (cashDivModel == org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine.CashDividendModel.Spot)
                            ? FdBlackScholesVanillaEngine.CashDividendModel.Spot
                            : FdBlackScholesVanillaEngine.CashDividendModel.Escrowed;

            final PricingEngine cashDivEngine = new org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine(
                    process, dividendSchedule, cashDivModel);

            for ( final Option.Type optionType : new Option.Type[] { Option.Type.Call, Option.Type.Put } ) {
                for ( final Date maturityDate : new Date[] {
                        new Date(1, Month.April, 2026), new Date(1, Month.January, 2027) } ) {
                    // The Java FdBlackScholesVanillaEngine throws on dividends past maturity, so build
                    // a per-maturity filtered schedule (C++ engine filters internally).
                    final DividendSchedule fdDivs = new DividendSchedule();
                    for ( final org.jquantlib.cashflow.Dividend d : dividendSchedule ) {
                        if ( d.date().ge(today) && d.date().le(maturityDate) ) {
                            fdDivs.add(d);
                        }
                    }
                    final PricingEngine fdEngine = new FdBlackScholesVanillaEngine(process, fdDivs, null, 100, 800, 0,
                            FdmSchemeDesc.Douglas(), fdModel, false, Double.NaN);

                    for ( final double strike : new double[] { 50, 100, 125, 175 } ) {
                        final VanillaOption option = new VanillaOption(new PlainVanillaPayoff(optionType, strike),
                                new EuropeanExercise(maturityDate));

                        option.setPricingEngine(fdEngine);
                        final double fdNPV = option.NPV();

                        option.setPricingEngine(cashDivEngine);
                        final double cdNPV = option.NPV();

                        final double diff = Math.abs(fdNPV - cdNPV);
                        if ( diff > tol ) {
                            fail("Failed to compare European option prices with CashDividendEuropeanEngine and "
                                    + "FdBlackScholesVanillaEngine\n  Strike: " + strike + "\n  Type: " + optionType
                                    + "\n  Maturity: " + maturityDate + "\n  Model: " + cashDivModel + "\n  FDM NPV: "
                                    + fdNPV + "\n  CD NPV: " + cdNPV + "\n  diff: " + diff + "\n  tol: " + tol);
                        }
                    }
                }
            }
        }
    }

    /**
     * Faithful port of v1.42.1 {@code test-suite/dividendoption.cpp:testCashDividendEuropeanEngineWithManyDividends}
     * (line 1124). Cross-validates {@link org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine}
     * against the FD-PDE engine on a heavy schedule (~250 dividends 1-5 days apart with random amounts).
     * Tolerance: 0.04 absolute.
     */
    @Test
    public void testCashDividendEuropeanEngineWithManyDividends() {
        QL.info("Testing cash-dividend European engine with many dividends...");

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(9, Month.November, 2025);
        new Settings().setEvaluationDate(today);

        final Date maturityDate = today.add(new Period(2, TimeUnit.Years));
        final Handle< Quote > spot = new Handle< Quote >(new SimpleQuote(100.0));

        final Handle< YieldTermStructure > qTS = new Handle< YieldTermStructure >(Utilities.flatRate(today, 0.05, dc));

        final Date[] rDates = new Date[] { today, new Date(1, Month.May, 2026), new Date(1, Month.November, 2027),
                new Date(1, Month.January, 2032) };
        final double[] rRates = new double[] { 0.05, 0.075, 0.04, 0.06 };
        final Handle< YieldTermStructure > rTS = new Handle< YieldTermStructure >(
                new org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve<
                        org.jquantlib.math.interpolations.factories.Linear >(
                        org.jquantlib.math.interpolations.factories.Linear.class, rDates, rRates, dc));

        final Date[] vDates = new Date[] { new Date(2, Month.January, 2026), new Date(1, Month.July, 2026),
                new Date(1, Month.August, 2027), new Date(1, Month.January, 2032) };
        final double[] vVols = new double[] { 0.3, 0.4, 0.42, 0.5 };
        final org.jquantlib.termstructures.volatilities.BlackVarianceCurve vCurve =
                new org.jquantlib.termstructures.volatilities.BlackVarianceCurve(today, vDates, vVols, dc);
        vCurve.setInterpolation();
        final Handle< BlackVolTermStructure > vTS = new Handle< BlackVolTermStructure >(vCurve);

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle< Quote >(new SimpleQuote(100.0)), qTS, rTS, vTS);

        final VanillaOption option = new VanillaOption(new PlainVanillaPayoff(Option.Type.Call, 110.0),
                new EuropeanExercise(maturityDate));

        final org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng rng =
                new org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng(1234L);
        final List< Date > dividendDates = new ArrayList<>();
        final List< Double > dividendAmounts = new ArrayList<>();
        // Start with a single dividend at today - 1 month (sentinel; outside the [settlement,maturity] window).
        dividendDates.add(today.sub(new Period(1, TimeUnit.Months)));
        dividendAmounts.add(1.0);

        final Date hardCap = maturityDate.add(new Period(1, TimeUnit.Months));
        while ( dividendDates.get(dividendDates.size() - 1).lt(hardCap) ) {
            final long step = (rng.nextInt32() % 5L) + 1L;
            dividendDates.add(dividendDates.get(dividendDates.size() - 1).add(new Period((int) step, TimeUnit.Days)));
            dividendAmounts.add(0.1 * rng.next().value());
        }

        final DividendSchedule divs = new DividendSchedule();
        for ( int i = 0; i < dividendDates.size(); i++ ) {
            divs.add(new org.jquantlib.cashflow.FixedDividend(dividendAmounts.get(i), dividendDates.get(i)));
        }

        option.setPricingEngine(new FdBlackScholesVanillaEngine(process, divs, null, 10, 500, 0,
                FdmSchemeDesc.Douglas(), FdBlackScholesVanillaEngine.CashDividendModel.Spot, false, Double.NaN));
        final double expected = option.NPV();

        option.setPricingEngine(new org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine(process, divs,
                org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine.CashDividendModel.Spot));
        final double calculated = option.NPV();

        final double tol = 0.04;
        final double diff = Math.abs(expected - calculated);
        if ( diff > tol ) {
            fail("Failed to compare European option prices with many dividends, CashDividendEuropeanEngine and "
                    + "FdBlackScholesVanillaEngine\n  FDM: " + expected + "\n  CD: " + calculated + "\n  diff: " + diff
                    + "\n  tol: " + tol);
        }
    }

    /**
     * Faithful port of v1.42.1
     * {@code test-suite/dividendoption.cpp:testCashDividendEuropeanEngineWithSingleDividends} (line 1215).
     * Cross-validates {@link org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine} against the
     * FD-PDE engine for a single dividend on 6 dates (around settlement and around maturity) and both
     * {@code Spot}/{@code Escrowed} models. Tolerance: 0.001 absolute.
     */
    @Test
    public void testCashDividendEuropeanEngineWithSingleDividends() {
        QL.info("Testing cash-dividend European engine with single dividend...");

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(11, Month.November, 2025);
        new Settings().setEvaluationDate(today);

        final Date maturityDate = today.add(new Period(18, TimeUnit.Months));

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle< Quote >(new SimpleQuote(100.0)),
                new Handle< YieldTermStructure >(Utilities.flatRate(today, 0.05, dc)),
                new Handle< YieldTermStructure >(Utilities.flatRate(today, 0.025, dc)),
                new Handle< BlackVolTermStructure >(Utilities.flatVol(today, 0.3, dc)));

        final Exercise exercise = new EuropeanExercise(maturityDate);
        final double divAmount = 5.0;

        for ( final Date divDate : new Date[] {
                today.sub(new Period(1, TimeUnit.Days)),
                today,
                today.add(new Period(1, TimeUnit.Days)),
                today.add(new Period(6, TimeUnit.Months)),
                maturityDate,
                maturityDate.add(new Period(1, TimeUnit.Days)) } ) {

            for ( final org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine.CashDividendModel cashDivModel
                    : new org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine.CashDividendModel[] {
                            org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine.CashDividendModel.Spot,
                            org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine.CashDividendModel.Escrowed
                    } ) {

                final FdBlackScholesVanillaEngine.CashDividendModel fdModel =
                        (cashDivModel == org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine.CashDividendModel.Spot)
                                ? FdBlackScholesVanillaEngine.CashDividendModel.Spot
                                : FdBlackScholesVanillaEngine.CashDividendModel.Escrowed;

                final DividendSchedule divs = new DividendSchedule();
                divs.add(new org.jquantlib.cashflow.FixedDividend(divAmount, divDate));

                // The C++ FdBlackScholesVanillaEngine filters dividends to [settlement,maturity]; the Java
                // engine throws on a dividend past maturity, so pass a filtered schedule here.
                // CashDividendEuropeanEngine filters internally and accepts the full schedule.
                final DividendSchedule fdDivs = new DividendSchedule();
                if ( divDate.ge(today) && divDate.le(maturityDate) ) {
                    fdDivs.add(new org.jquantlib.cashflow.FixedDividend(divAmount, divDate));
                }

                final PricingEngine fdEngine = new FdBlackScholesVanillaEngine(process, fdDivs, null, 200, 400, 0,
                        FdmSchemeDesc.Douglas(), fdModel, false, Double.NaN);

                final PricingEngine cashDivEngine = new org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine(
                        process, divs, cashDivModel);

                for ( final Option.Type optionType : new Option.Type[] { Option.Type.Call, Option.Type.Put } ) {
                    final VanillaOption option = new VanillaOption(new PlainVanillaPayoff(optionType, 95.0), exercise);

                    option.setPricingEngine(fdEngine);
                    final double expected = option.NPV();

                    option.setPricingEngine(cashDivEngine);
                    final double calculated = option.NPV();

                    final double tol = 0.001;
                    final double diff = Math.abs(expected - calculated);
                    if ( diff > tol ) {
                        fail("Failed to compare European option prices with CashDividendEuropeanEngine and "
                                + "FdBlackScholesVanillaEngine\n  Type: " + optionType + "\n  DivDate: " + divDate
                                + "\n  Model: " + cashDivModel + "\n  FDM: " + expected + "\n  CD: " + calculated
                                + "\n  diff: " + diff + "\n  tol: " + tol);
                    }
                }
            }
        }
    }

    /**
     * Faithful port of v1.42.1 {@code test-suite/dividendoption.cpp:testZeroStrikeCallWithCashDividends} (line 1292).
     * <p>
     * Validates that a zero-strike European call priced with the FD Escrowed model and the
     * {@link org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine} (Spot default) both
     * recover the closed-form value {@code spot * Dq - dividend * Dr(divDate)/Dq(divDate) * Dq(T)},
     * and that an American counterpart equals {@code spot} exactly (no optimal early-exercise).
     * Tolerance: 1e-3 absolute.
     */
    @Test
    public void testZeroStrikeCallWithCashDividends() {
        QL.info("Testing zero strike call with cash dividend model...");

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(26, Month.October, 2025);
        new Settings().setEvaluationDate(today);

        final Date maturityDate = today.add(new Period(1, TimeUnit.Years));
        final Handle< Quote > spot = new Handle< Quote >(new SimpleQuote(100.0));

        final Handle< YieldTermStructure > qTS = new Handle< YieldTermStructure >(Utilities.flatRate(today, 0.063, dc));
        final Handle< YieldTermStructure > rTS = new Handle< YieldTermStructure >(Utilities.flatRate(today, 0.094, dc));

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle< Quote >(new SimpleQuote(100.0)), qTS, rTS,
                new Handle< BlackVolTermStructure >(Utilities.flatVol(today, 0.3, dc)));

        final VanillaOption europeanOption = new VanillaOption(new PlainVanillaPayoff(Option.Type.Call, 0.0),
                new EuropeanExercise(maturityDate));

        final double dividend = 5.0;

        for ( final Date dividendDate : new Date[] { today, new Date(1, Month.January, 2026), maturityDate } ) {
            final DividendSchedule divs = new DividendSchedule();
            divs.add(new org.jquantlib.cashflow.FixedDividend(dividend, dividendDate));

            final PricingEngine fdEngine = new FdBlackScholesVanillaEngine(process, divs, null, 100, 400, 0,
                    FdmSchemeDesc.Douglas(), FdBlackScholesVanillaEngine.CashDividendModel.Escrowed, false, Double.NaN);

            europeanOption.setPricingEngine(fdEngine);
            final double europeanCalculated = europeanOption.NPV();
            final double europeanExpected = process.x0() * qTS.currentLink().discount(maturityDate)
                    - dividend * rTS.currentLink().discount(dividendDate)
                            / qTS.currentLink().discount(dividendDate) * qTS.currentLink().discount(maturityDate);

            final double tol = 1e-3;
            final double europeanFdmDiff = Math.abs(europeanCalculated - europeanExpected);
            if ( europeanFdmDiff > tol ) {
                fail("Failed to calculate zero strike European call price with escrowed dividend model\n  FDM: "
                        + europeanCalculated + "\n  expected: " + europeanExpected + "\n  diff: " + europeanFdmDiff
                        + "\n  tol: " + tol);
            }

            europeanOption.setPricingEngine(
                    new org.jquantlib.pricingengines.vanilla.CashDividendEuropeanEngine(process, divs));
            final double europeanCdCalculated = europeanOption.NPV();
            final double europeanCdDiff = Math.abs(europeanCdCalculated - europeanExpected);

            if ( europeanCdDiff > tol ) {
                fail("Failed to calculate zero strike European call price with spot dividend model\n  semi-analytic: "
                        + europeanCdCalculated + "\n  expected: " + europeanExpected + "\n  diff: " + europeanCdDiff
                        + "\n  tol: " + tol);
            }

            final VanillaOption americanOption = new VanillaOption(new PlainVanillaPayoff(Option.Type.Call, 0.0),
                    new AmericanExercise(today, maturityDate));
            americanOption.setPricingEngine(fdEngine);

            final double americanCalculated = americanOption.NPV();
            final double americanExpected = process.x0();
            final double americanDiff = Math.abs(americanCalculated - americanExpected);

            if ( americanDiff > tol ) {
                fail("Failed to calculate zero strike American call price with escrowed dividend model\n  FDM: "
                        + americanCalculated + "\n  expected: " + americanExpected + "\n  diff: " + americanDiff
                        + "\n  tol: " + tol);
            }
        }
    }

    /**
     * Faithful port of v1.42.1 {@code test-suite/dividendoption.cpp:testAmericanOptionsWithEscrowedDividends}
     * (line 1385).
     * <p>
     * Cross-validates the Escrowed-dividend FD American option price against the Spot-dividend
     * FD American option price using an adjusted volatility designed to match the two PV-perspectives.
     * Tolerance: 0.1 absolute, sweep over (call/put) x (4 dividend dates).
     */
    @Test
    public void testAmericanOptionsWithEscrowedDividends() {
        QL.info("Testing American option with escrowed dividend model...");

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(26, Month.October, 2025);
        new Settings().setEvaluationDate(today);

        final Date maturityDate = today.add(new Period(18, TimeUnit.Months));
        final double maturityTime = dc.yearFraction(today, maturityDate);

        final Handle< Quote > spot = new Handle< Quote >(new SimpleQuote(100.0));

        final Handle< YieldTermStructure > qTS = new Handle< YieldTermStructure >(Utilities.flatRate(today, 0.05, dc));
        final Handle< YieldTermStructure > rTS = new Handle< YieldTermStructure >(Utilities.flatRate(today, 0.15, dc));

        final double v = 0.3;
        final SimpleQuote vol = new SimpleQuote(v);
        final Handle< BlackVolTermStructure > volTS = new Handle< BlackVolTermStructure >(Utilities.flatVol(vol, dc));

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle< Quote >(new SimpleQuote(100.0)), qTS, rTS, volTS);

        final double dividend = 5.0;

        for ( final Option.Type optionType : new Option.Type[] { Option.Type.Call, Option.Type.Put } ) {
            for ( final Date dividendDate : new Date[] {
                    today, new Date(1, Month.January, 2026), new Date(1, Month.January, 2027), maturityDate } ) {

                final DividendSchedule divs = new DividendSchedule();
                divs.add(new org.jquantlib.cashflow.FixedDividend(dividend, dividendDate));

                final PricingEngine escrowedEngine = new FdBlackScholesVanillaEngine(process, divs, null, 100, 400, 0,
                        FdmSchemeDesc.Douglas(), FdBlackScholesVanillaEngine.CashDividendModel.Escrowed, false,
                        Double.NaN);
                final PricingEngine spotEngine = new FdBlackScholesVanillaEngine(process, divs, null, 100, 400, 0,
                        FdmSchemeDesc.Douglas(), FdBlackScholesVanillaEngine.CashDividendModel.Spot, false,
                        Double.NaN);

                final VanillaOption option = new VanillaOption(new PlainVanillaPayoff(optionType, 95.0),
                        new AmericanExercise(today, maturityDate));

                vol.setValue(v);
                option.setPricingEngine(escrowedEngine);
                final double escrowedNPV = option.NPV();

                final double s0 = 100.0;
                final double adjustedVar = (v * v * dc.yearFraction(today, dividendDate)
                        * Math.pow((s0 - dividend) / s0, 2.0)
                        + v * v * dc.yearFraction(dividendDate, maturityDate)) / maturityTime;
                vol.setValue(Math.sqrt(adjustedVar));
                option.setPricingEngine(spotEngine);
                final double spotNPV = option.NPV();

                final double tol = 0.1;
                final double diff = Math.abs(spotNPV - escrowedNPV);
                if ( diff > tol ) {
                    fail("Failed to compare American option prices with cash- and escrowed dividend model\n  Type: "
                            + optionType + "\n  div date: " + dividendDate + "\n  escrowed NPV: " + escrowedNPV
                            + "\n  cash NPV: " + spotNPV + "\n  diff: " + diff + "\n  tol: " + tol);
                }
            }
        }
    }

}
