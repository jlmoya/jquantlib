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

package org.jquantlib.testsuite.methods.montecarlo;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.MultiPathGenerator;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathGenerator;
import org.jquantlib.methods.montecarlo.Sample;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeometricBrownianMotionProcess;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.jquantlib.processes.SquareRootProcess;
import org.jquantlib.processes.StochasticProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.TimeGrid;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/pathgenerator.cpp (Phase 5e.5b-CFC-d-85).
 *
 * <p>2 BOOST_AUTO_TEST_CASE methods, both body-filled with the cached
 * MT-seed path values from the C++ source. The Java
 * {@code PseudoRandom.makeSequenceGenerator} factory is currently broken
 * (it builds a {@code null} {@code RandomSequenceGenerator} class via
 * reflection — see {@code GenericPseudoRandom.makeSequenceGenerator}),
 * so we compose the equivalent stack by hand:
 * {@code MersenneTwisterUniformRng -> RandomSequenceGenerator -> InverseCumulativeRsg<,InverseCumulativeNormal>}.
 * That mirrors {@code typedef GenericPseudoRandom<MersenneTwisterUniformRng,
 * InverseCumulativeNormal> PseudoRandom} from
 * {@code ql/math/randomnumbers/rngtraits.hpp}, the same construction used
 * by {@link org.jquantlib.testsuite.math.randomnumbers.RngTraitsTest}.
 *
 * <p>Tolerances: TIGHT bit-exact MT-seed tolerances from the C++ source
 * ({@code 2.0e-8} for forward sample, {@code 2.0e-7} for antithetic
 * sample). Loosening these to force green is prohibited by the
 * project's quality-gate rules.
 */
public class PathGeneratorTest {

    public PathGeneratorTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Mirrors C++ {@code testSingle(process, tag, brownianBridge, expected, antithetic)}
     * from pathgenerator.cpp.
     */
    private static void testSingle(final StochasticProcess1D process,
                                   final String tag,
                                   final boolean brownianBridge,
                                   final double expected,
                                   final double antithetic) {
        final long seed = 42L;
        final double length = 10.0;
        final int timeSteps = 12;

        final InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                   InverseCumulativeNormal> rsg = makePseudoRandomRsg(timeSteps, seed);
        final PathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                                 InverseCumulativeNormal>> generator =
                new PathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                                       InverseCumulativeNormal>>(
                        process, length, timeSteps, rsg, brownianBridge);

        for (int i = 0; i < 100; i++) {
            generator.next();
        }

        Sample<Path> sample = generator.next();
        double calculated = sample.value().back();
        double error = Math.abs(calculated - expected);
        double tolerance = 2.0e-8;
        if (error > tolerance) {
            fail("using " + tag + " process "
                    + (brownianBridge ? "with " : "without ") + "brownian bridge:\n"
                    + "    calculated: " + calculated + "\n"
                    + "    expected:   " + expected + "\n"
                    + "    error:      " + error + "\n"
                    + "    tolerance:  " + tolerance);
        }

        sample = generator.antithetic();
        calculated = sample.value().back();
        error = Math.abs(calculated - antithetic);
        tolerance = 2.0e-7;
        if (error > tolerance) {
            fail("using " + tag + " process "
                    + (brownianBridge ? "with " : "without ") + "brownian bridge:\n"
                    + "antithetic sample:\n"
                    + "    calculated: " + calculated + "\n"
                    + "    expected:   " + antithetic + "\n"
                    + "    error:      " + error + "\n"
                    + "    tolerance:  " + tolerance);
        }
    }

    /**
     * Mirrors C++ {@code testMultiple(process, tag, expected[], antithetic[])}
     * from pathgenerator.cpp.
     */
    private static void testMultiple(final StochasticProcess process,
                                     final String tag,
                                     final double[] expected,
                                     final double[] antithetic) {
        final long seed = 42L;
        final double length = 10.0;
        final int timeSteps = 12;
        final int assets = process.size();

        final InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                   InverseCumulativeNormal> rsg =
                makePseudoRandomRsg(timeSteps * assets, seed);
        final MultiPathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                                      InverseCumulativeNormal>> generator =
                new MultiPathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                                            InverseCumulativeNormal>>(
                        process, new TimeGrid(length, timeSteps), rsg, false);

        for (int i = 0; i < 100; i++) {
            generator.next();
        }

        Sample<MultiPath> sample = generator.next();
        final double[] calculated = new double[assets];
        final double tolerance = 2.0e-7;
        for (int j = 0; j < assets; j++) {
            calculated[j] = sample.value().get(j).back();
        }
        for (int j = 0; j < assets; j++) {
            final double error = Math.abs(calculated[j] - expected[j]);
            if (error > tolerance) {
                fail("using " + tag + " process (asset " + (j + 1) + "):\n"
                        + "    calculated: " + calculated[j] + "\n"
                        + "    expected:   " + expected[j] + "\n"
                        + "    error:      " + error + "\n"
                        + "    tolerance:  " + tolerance);
            }
        }

        sample = generator.antithetic();
        for (int j = 0; j < assets; j++) {
            calculated[j] = sample.value().get(j).back();
        }
        for (int j = 0; j < assets; j++) {
            final double error = Math.abs(calculated[j] - antithetic[j]);
            if (error > tolerance) {
                fail("using " + tag + " process (asset " + (j + 1) + "):\n"
                        + "antithetic sample:\n"
                        + "    calculated: " + calculated[j] + "\n"
                        + "    expected:   " + antithetic[j] + "\n"
                        + "    error:      " + error + "\n"
                        + "    tolerance:  " + tolerance);
            }
        }
    }

    /**
     * Mirrors C++ {@code PseudoRandom::make_sequence_generator(dimension, seed)}
     * by composing the canonical MT + InverseCumulativeNormal stack.
     */
    private static InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                        InverseCumulativeNormal>
            makePseudoRandomRsg(final int dimension, final long seed) {
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(seed);
        final RandomSequenceGenerator<MersenneTwisterUniformRng> rsg =
                new RandomSequenceGenerator<MersenneTwisterUniformRng>(
                        MersenneTwisterUniformRng.class, dimension, rng);
        return new InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                        InverseCumulativeNormal>(rsg, new InverseCumulativeNormal());
    }

    @Test
    public void testPathGenerator() {
        QL.info("Testing 1-D path generation against cached values...");

        new Settings().setEvaluationDate(new Date(26, Month.April, 2005));

        final DayCounter dc = new Actual360();
        final Handle<? extends Quote> x0 = new Handle<Quote>(new SimpleQuote(100.0));
        final YieldTermStructure rTS = Utilities.flatRate(0.05, dc);
        final YieldTermStructure qTS = Utilities.flatRate(0.02, dc);
        final BlackVolTermStructure volTS = Utilities.flatVol(0.20, dc);
        final Handle<YieldTermStructure> r = new Handle<YieldTermStructure>(rTS);
        final Handle<YieldTermStructure> q = new Handle<YieldTermStructure>(qTS);
        final Handle<BlackVolTermStructure> sigma = new Handle<BlackVolTermStructure>(volTS);

        // commented values must be used when Halley's correction is enabled
        testSingle(new BlackScholesMertonProcess(x0, q, r, sigma),
                   "Black-Scholes", false, 26.13784357783, 467.2928561411);
        testSingle(new BlackScholesMertonProcess(x0, q, r, sigma),
                   "Black-Scholes", true, 60.28215549393, 202.6143139999);

        testSingle(new GeometricBrownianMotionProcess(100.0, 0.03, 0.20),
                   "geometric Brownian", false, 27.62223714065, 483.6026514084);

        testSingle(new OrnsteinUhlenbeckProcess(0.1, 0.20),
                   "Ornstein-Uhlenbeck", false, -0.8372003433557, 0.8372003433557);

        testSingle(new SquareRootProcess(0.1, 0.1, 0.20, 10.0),
                   "square-root", false, 1.70608664108, 6.024200546031);
    }

    @Test
    public void testMultiPathGenerator() {
        QL.info("Testing n-D path generation against cached values...");

        new Settings().setEvaluationDate(new Date(26, Month.April, 2005));

        final DayCounter dc = new Actual360();
        final Handle<? extends Quote> x0 = new Handle<Quote>(new SimpleQuote(100.0));
        final Handle<YieldTermStructure> r = new Handle<YieldTermStructure>(Utilities.flatRate(0.05, dc));
        final Handle<YieldTermStructure> q = new Handle<YieldTermStructure>(Utilities.flatRate(0.02, dc));
        final Handle<BlackVolTermStructure> sigma = new Handle<BlackVolTermStructure>(Utilities.flatVol(0.20, dc));

        final Matrix correlation = new Matrix(3, 3);
        correlation.set(0, 0, 1.0); correlation.set(0, 1, 0.9); correlation.set(0, 2, 0.7);
        correlation.set(1, 0, 0.9); correlation.set(1, 1, 1.0); correlation.set(1, 2, 0.4);
        correlation.set(2, 0, 0.7); correlation.set(2, 1, 0.4); correlation.set(2, 2, 1.0);

        // Black-Scholes
        {
            final List<StochasticProcess1D> processes = new ArrayList<StochasticProcess1D>(3);
            processes.add(new BlackScholesMertonProcess(x0, q, r, sigma));
            processes.add(new BlackScholesMertonProcess(x0, q, r, sigma));
            processes.add(new BlackScholesMertonProcess(x0, q, r, sigma));
            final StochasticProcess process = new StochasticProcessArray(processes, correlation);
            // commented values must be used when Halley's correction is enabled
            final double[] result1 = {
                    188.2235868185,
                    270.6713069569,
                    113.0431145652};
            final double[] result1a = {
                    64.89105742957,
                    45.12494404804,
                    108.0475146914};
            testMultiple(process, "Black-Scholes", result1, result1a);
        }

        // geometric Brownian
        {
            final List<StochasticProcess1D> processes = new ArrayList<StochasticProcess1D>(3);
            processes.add(new GeometricBrownianMotionProcess(100.0, 0.03, 0.20));
            processes.add(new GeometricBrownianMotionProcess(100.0, 0.03, 0.20));
            processes.add(new GeometricBrownianMotionProcess(100.0, 0.03, 0.20));
            final StochasticProcess process = new StochasticProcessArray(processes, correlation);
            final double[] result2 = {
                    174.8266131680,
                    237.2692443633,
                    119.1168555440};
            final double[] result2a = {
                    57.69082393020,
                    38.50016862915,
                    116.4056510107};
            testMultiple(process, "geometric Brownian", result2, result2a);
        }

        // Ornstein-Uhlenbeck
        {
            final List<StochasticProcess1D> processes = new ArrayList<StochasticProcess1D>(3);
            processes.add(new OrnsteinUhlenbeckProcess(0.1, 0.20));
            processes.add(new OrnsteinUhlenbeckProcess(0.1, 0.20));
            processes.add(new OrnsteinUhlenbeckProcess(0.1, 0.20));
            final StochasticProcess process = new StochasticProcessArray(processes, correlation);
            final double[] result3 = {
                    0.2942058437284,
                    0.5525006418386,
                    0.02650931054575};
            final double[] result3a = {
                    -0.2942058437284,
                    -0.5525006418386,
                    -0.02650931054575};
            testMultiple(process, "Ornstein-Uhlenbeck", result3, result3a);
        }

        // square-root
        {
            final List<StochasticProcess1D> processes = new ArrayList<StochasticProcess1D>(3);
            processes.add(new SquareRootProcess(0.1, 0.1, 0.20, 10.0));
            processes.add(new SquareRootProcess(0.1, 0.1, 0.20, 10.0));
            processes.add(new SquareRootProcess(0.1, 0.1, 0.20, 10.0));
            final StochasticProcess process = new StochasticProcessArray(processes, correlation);
            final double[] result4 = {
                    4.279510844897,
                    4.943783503533,
                    3.590930385958};
            final double[] result4a = {
                    2.763967737724,
                    2.226487196647,
                    3.503859264341};
            testMultiple(process, "square-root", result4, result4a);
        }
    }
}
