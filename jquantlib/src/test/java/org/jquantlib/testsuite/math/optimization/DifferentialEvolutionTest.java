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

package org.jquantlib.testsuite.math.optimization;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.BoundaryConstraint;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.DifferentialEvolution;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/optimizers.cpp::testDifferentialEvolution.
 *
 * <p>Five benchmark cost functions (FirstDeJong / SecondDeJong / ModThirdDeJong /
 * ModFourthDeJong / Griewangk) are minimized using two
 * {@link DifferentialEvolution.Configuration} variants. Tolerances mirror the
 * C++ source verbatim: four problems require {@code |fmin| <= 1e-8}; the noisy
 * ModFourthDeJong problem only requires {@code fmin <= 15} due to RNG injection
 * into its cost function.
 *
 * <p>Phase 5e.5b-CFC-d-84: production class
 * {@link DifferentialEvolution} is in place; the test is body-filled here.
 */
public class DifferentialEvolutionTest {

    public DifferentialEvolutionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    //
    // Benchmark cost functions (faithful Java ports of the C++ types).
    //

    private static final class FirstDeJong extends CostFunction {
        @Override
        public Array values(final Array x) {
            final double v = value(x);
            final double[] data = new double[x.size()];
            for (int i = 0; i < data.length; ++i) {
                data[i] = v;
            }
            return new Array(data);
        }
        @Override
        public double value(final Array x) {
            // DotProduct(x, x)
            double s = 0.0;
            for (int i = 0; i < x.size(); ++i) {
                s += x.get(i) * x.get(i);
            }
            return s;
        }
    }

    private static final class SecondDeJong extends CostFunction {
        @Override
        public Array values(final Array x) {
            final double v = value(x);
            final double[] data = new double[x.size()];
            for (int i = 0; i < data.length; ++i) {
                data[i] = v;
            }
            return new Array(data);
        }
        @Override
        public double value(final Array x) {
            final double a = x.get(0) * x.get(0) - x.get(1);
            final double b = 1.0 - x.get(0);
            return 100.0 * a * a + b * b;
        }
    }

    private static final class ModThirdDeJong extends CostFunction {
        @Override
        public Array values(final Array x) {
            final double v = value(x);
            final double[] data = new double[x.size()];
            for (int i = 0; i < data.length; ++i) {
                data[i] = v;
            }
            return new Array(data);
        }
        @Override
        public double value(final Array x) {
            double fx = 0.0;
            for (int i = 0; i < x.size(); ++i) {
                final double fl = Math.floor(x.get(i));
                fx += fl * fl;
            }
            return fx;
        }
    }

    private static final class ModFourthDeJong extends CostFunction {
        private final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(4711L);
        @Override
        public Array values(final Array x) {
            final double v = value(x);
            final double[] data = new double[x.size()];
            for (int i = 0; i < data.length; ++i) {
                data[i] = v;
            }
            return new Array(data);
        }
        @Override
        public double value(final Array x) {
            double fx = 0.0;
            for (int i = 0; i < x.size(); ++i) {
                final double xi = x.get(i);
                fx += (i + 1.0) * Math.pow(xi, 4.0)
                        + ((rng.nextInt32() + 0.5) / 4294967296.0);
            }
            return fx;
        }
    }

    private static final class Griewangk extends CostFunction {
        @Override
        public Array values(final Array x) {
            final double v = value(x);
            final double[] data = new double[x.size()];
            for (int i = 0; i < data.length; ++i) {
                data[i] = v;
            }
            return new Array(data);
        }
        @Override
        public double value(final Array x) {
            double fx = 0.0;
            for (int i = 0; i < x.size(); ++i) {
                final double xi = x.get(i);
                fx += xi * xi / 4000.0;
            }
            double p = 1.0;
            for (int i = 0; i < x.size(); ++i) {
                p *= Math.cos(x.get(i) / Math.sqrt(i + 1.0));
            }
            return fx - p + 1.0;
        }
    }

    private static Array filled(final int n, final double v) {
        final double[] data = new double[n];
        for (int i = 0; i < n; ++i) {
            data[i] = v;
        }
        return new Array(data);
    }

    @Test
    public void testDifferentialEvolution() {
        // C++ test-suite/optimizers.cpp:434

        final DifferentialEvolution.Configuration conf =
                new DifferentialEvolution.Configuration()
                        .withStepsizeWeight(0.4)
                        .withBounds()
                        .withCrossoverProbability(0.35)
                        .withPopulationMembers(500)
                        .withStrategy(DifferentialEvolution.Strategy.BestMemberWithJitter)
                        .withCrossoverType(DifferentialEvolution.CrossoverType.Normal)
                        .withAdaptiveCrossover()
                        .withSeed(3242L);

        final DifferentialEvolution.Configuration conf2 =
                new DifferentialEvolution.Configuration()
                        .withStepsizeWeight(1.8)
                        .withBounds()
                        .withCrossoverProbability(0.9)
                        .withPopulationMembers(1000)
                        .withStrategy(DifferentialEvolution.Strategy.Rand1SelfadaptiveWithRotation)
                        .withCrossoverType(DifferentialEvolution.CrossoverType.Normal)
                        .withAdaptiveCrossover()
                        .withSeed(3242L);

        // Five independent optimizer instances: first four use {@code conf},
        // the last uses {@code conf2} (matching the C++ vector layout).
        final DifferentialEvolution[] optimizers = {
                new DifferentialEvolution(conf),
                new DifferentialEvolution(conf),
                new DifferentialEvolution(conf),
                new DifferentialEvolution(conf),
                new DifferentialEvolution(conf2)
        };

        final CostFunction[] costFunctions = {
                new FirstDeJong(),
                new SecondDeJong(),
                new ModThirdDeJong(),
                new ModFourthDeJong(),
                new Griewangk()
        };

        final BoundaryConstraint[] constraints = {
                new BoundaryConstraint(-10.0, 10.0),
                new BoundaryConstraint(-10.0, 10.0),
                new BoundaryConstraint(-10.0, 10.0),
                new BoundaryConstraint(-10.0, 10.0),
                new BoundaryConstraint(-600.0, 600.0)
        };

        final Array[] initialValues = {
                filled(3, 5.0),
                filled(2, 5.0),
                filled(5, 5.0),
                filled(30, 5.0),
                filled(10, 100.0)
        };

        final EndCriteria[] endCriteria = {
                new EndCriteria(100, 10, 1e-10, 1e-8, Double.NaN),
                new EndCriteria(100, 10, 1e-10, 1e-8, Double.NaN),
                new EndCriteria(100, 10, 1e-10, 1e-8, Double.NaN),
                new EndCriteria(500, 100, 1e-10, 1e-8, Double.NaN),
                new EndCriteria(1000, 800, 1e-12, 1e-10, Double.NaN)
        };

        final double[] minima = {0.0, 0.0, 0.0, 10.9639796558, 0.0};

        for (int i = 0; i < costFunctions.length; ++i) {
            final Problem problem = new Problem(costFunctions[i], constraints[i], initialValues[i]);
            optimizers[i].minimize(problem, endCriteria[i]);

            if (i != 3) {
                if (Math.abs(problem.functionValue() - minima[i]) > 1e-8) {
                    fail("costFunction # " + i
                            + "\ncalculated: " + problem.functionValue()
                            + "\nexpected:   " + minima[i]);
                }
            } else {
                // ModFourthDeJong is unstable due to its RNG-injected noise;
                // the C++ test accepts any minimum value < 15.
                if (problem.functionValue() > 15) {
                    fail("costFunction # " + i
                            + "\ncalculated: " + problem.functionValue()
                            + "\nexpected:   less than 15");
                }
            }
        }
    }
}
