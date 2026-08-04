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

package org.jquantlib.testsuite.math.optimization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LBFGSB;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.NonhomogeneousBoundaryConstraint;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validates {@link LBFGSB} — new in C++ QuantLib v1.43 — against the {@code math/v143_lbfgsb} probe.
 * <p>
 * The probe's cases are self-describing: each records the objective, start point, bounds, memory, tolerances and
 * end-criteria it used, so this test reconstructs every case from the reference rather than duplicating the setup by
 * hand. That keeps the two in step: adding a probe case automatically adds a Java case.
 * <p>
 * L-BFGS-B's inner machinery (the generalized Cauchy point, the subspace minimization, the compact representation and
 * the Wolfe line search) is unreachable from outside, so correctness is pinned three ways: many bound and start-point
 * configurations that exercise distinct code paths; a truncated-iteration sweep that exposes the iterate sequence step
 * by step; and a full trace of every objective evaluation in order.
 *
 * @author Jose Moya
 */
public class LBFGSBTest {

    /**
     * LOOSE tier. Iterative optimization accumulates rounding through the line search and the compact-representation
     * matrix inverse, so the converged point matches C++ to roughly 1e-9 relative rather than to the last bits. The
     * discriminating assertions here are the discrete ones — end-criteria type, evaluation counts and the iterate
     * trajectory — which are exact.
     */
    private static final double REL_TOL = 1.0e-8;
    private static final double ABS_TOL = 1.0e-8;

    private static ReferenceReader ref() {
        return ReferenceReader.load("math/v143_lbfgsb");
    }

    private static void assertClose(final String what, final double expected, final double actual) {
        assertEquals(what, expected, actual, Math.max(ABS_TOL, REL_TOL * Math.abs(expected)));
    }

    private static Array toArray(final JSONArray a) {
        final double[] v = new double[a.length()];
        for ( int i = 0; i < v.length; ++i ) {
            v[i] = a.getDouble(i);
        }
        return new Array(v);
    }

    //
    // objectives — mirror the probe's cost functions exactly
    //

    private static final class RosenbrockFunction extends CostFunction {
        @Override
        public double value(final Array x) {
            double f = 0.0;
            for ( int i = 0; i + 1 < x.size(); ++i ) {
                f += 100.0 * Math.pow(x.get(i + 1) - x.get(i) * x.get(i), 2) + Math.pow(1.0 - x.get(i), 2);
            }
            return f;
        }

        @Override
        public Array values(final Array x) {
            return new Array(new double[] { value(x) });
        }

        @Override
        public void gradient(final Array grad, final Array x) {
            grad.fill(0.0);
            for ( int i = 0; i + 1 < x.size(); ++i ) {
                grad.set(i, grad.get(i)
                        - 400.0 * x.get(i) * (x.get(i + 1) - x.get(i) * x.get(i)) - 2.0 * (1.0 - x.get(i)));
                grad.set(i + 1, grad.get(i + 1) + 200.0 * (x.get(i + 1) - x.get(i) * x.get(i)));
            }
        }

        @Override
        public double valueAndGradient(final Array grad, final Array x) {
            gradient(grad, x);
            return value(x);
        }
    }

    private static class WeightedQuadratic extends CostFunction {
        protected final Array center;
        protected final Array weight;

        WeightedQuadratic(final Array center, final Array weight) {
            this.center = center;
            this.weight = weight;
        }

        @Override
        public double value(final Array x) {
            double f = 0.0;
            for ( int i = 0; i < x.size(); ++i ) {
                f += weight.get(i) * Math.pow(x.get(i) - center.get(i), 2);
            }
            return f;
        }

        @Override
        public Array values(final Array x) {
            return new Array(new double[] { value(x) });
        }

        @Override
        public void gradient(final Array grad, final Array x) {
            for ( int i = 0; i < x.size(); ++i ) {
                grad.set(i, 2.0 * weight.get(i) * (x.get(i) - center.get(i)));
            }
        }

        @Override
        public double valueAndGradient(final Array grad, final Array x) {
            gradient(grad, x);
            return value(x);
        }
    }

    /**
     * Same objective, but declaring only {@code value} — so {@link CostFunction}'s central-difference fallback
     * supplies the gradient, exactly as the C++ {@code WeightedQuadraticValueOnly} does. It is the one case where the
     * gradient LBFGSB sees is not exact, and where the finite differencing happens inside the cost function and so
     * does not bump the problem's evaluation counters.
     */
    private static final class WeightedQuadraticValueOnly extends CostFunction {
        private final Array center;
        private final Array weight;

        WeightedQuadraticValueOnly(final Array center, final Array weight) {
            this.center = center;
            this.weight = weight;
        }

        @Override
        public double value(final Array x) {
            double f = 0.0;
            for ( int i = 0; i < x.size(); ++i ) {
                f += weight.get(i) * Math.pow(x.get(i) - center.get(i), 2);
            }
            return f;
        }

        @Override
        public Array values(final Array x) {
            return new Array(new double[] { value(x) });
        }
    }

    private static CostFunction objectiveFor(final JSONObject inputs) {
        final String name = inputs.getString("objective");
        switch ( name ) {
        case "rosenbrock":
            return new RosenbrockFunction();
        case "weighted_quadratic":
            return new WeightedQuadratic(toArray(inputs.getJSONArray("center")),
                    toArray(inputs.getJSONArray("weight")));
        case "weighted_quadratic_value_only":
            return new WeightedQuadraticValueOnly(toArray(inputs.getJSONArray("center")),
                    toArray(inputs.getJSONArray("weight")));
        default:
            throw new IllegalArgumentException("unknown objective: " + name);
        }
    }

    private static EndCriteria endCriteriaFor(final JSONObject ec) {
        return new EndCriteria(ec.getInt("maxIterations"), ec.getInt("maxStationaryStateIterations"),
                ec.getDouble("rootEpsilon"), ec.getDouble("functionEpsilon"), ec.getDouble("gradientNormEpsilon"));
    }

    /**
     * Rebuilds the problem's constraint. When the case used the explicit-bounds constructor the arrays under
     * {@code lowerBound}/{@code upperBound} are the constructor's bounds, and the constraint's own bounds — which
     * L-BFGS-B must ignore — are recorded separately.
     */
    private static Constraint constraintFor(final JSONObject inputs) {
        final String kind = inputs.optString("constraint", "");
        if ( kind.startsWith("NoConstraint") ) {
            return new NoConstraint();
        }
        if ( inputs.has("constraintLowerBound") ) {
            return new NonhomogeneousBoundaryConstraint(toArray(inputs.getJSONArray("constraintLowerBound")),
                    toArray(inputs.getJSONArray("constraintUpperBound")));
        }
        return new NonhomogeneousBoundaryConstraint(toArray(inputs.getJSONArray("lowerBound")),
                toArray(inputs.getJSONArray("upperBound")));
    }

    /** True when the case used {@code LBFGSB(lowerBound, upperBound, ...)} rather than the plain constructor. */
    private static boolean usesExplicitBounds(final JSONObject inputs) {
        return inputs.has("lbfgsbConstructor");
    }

    //
    // the driver
    //

    /**
     * Runs one probe case and checks the converged point, the objective value, the projected-gradient norm, the
     * end-criteria type and the evaluation counters.
     */
    private static void checkCase(final String caseName) {
        final ReferenceReader.Case c = ref().getCase(caseName);
        final JSONObject in = c.inputs();
        final JSONObject out = (JSONObject) c.expectedRaw();

        final JSONObject opt = in.getJSONObject("lbfgsb");
        final CostFunction f = objectiveFor(in);
        final Array x0 = toArray(in.getJSONArray("x0"));
        final Problem problem = new Problem(f, constraintFor(in), x0);
        final EndCriteria ec = endCriteriaFor(in.getJSONObject("endCriteria"));

        final LBFGSB optimizer;
        if ( usesExplicitBounds(in) ) {
            optimizer = new LBFGSB(toArray(in.getJSONArray("lowerBound")), toArray(in.getJSONArray("upperBound")),
                    opt.getInt("memory"), opt.getDouble("pgTol"), opt.getDouble("fTol"));
        } else {
            optimizer = new LBFGSB(opt.getInt("memory"), opt.getDouble("pgTol"), opt.getDouble("fTol"));
        }

        final EndCriteria.Type type = optimizer.minimize(problem, ec);

        assertEquals(caseName + ": end criteria", out.getString("endCriteriaName"), type.name());

        final JSONArray xExpected = out.getJSONArray("x");
        final Array x = problem.currentValue();
        assertEquals(caseName + ": solution size", xExpected.length(), x.size());
        for ( int i = 0; i < x.size(); ++i ) {
            assertClose(caseName + ": x[" + i + "]", xExpected.getDouble(i), x.get(i));
        }

        assertClose(caseName + ": function value", out.getDouble("functionValue"), problem.functionValue());
        assertClose(caseName + ": gradient norm value", out.getDouble("gradientNormValue"),
                problem.gradientNormValue());
        assertEquals(caseName + ": function evaluations", out.getInt("functionEvaluations"),
                problem.functionEvaluation());
        assertEquals(caseName + ": gradient evaluations", out.getInt("gradientEvaluations"),
                problem.gradientEvaluation());
    }

    //
    // tests
    //

    /**
     * Every ordinary probe case — one assertion sweep. Cases that need bespoke handling (the trajectory sweep, the
     * evaluation traces and the constructor guards) have their own tests below and are skipped here.
     */
    @Test
    public void testAllProbeCases() {
        QL.info("Testing LBFGSB against every C++ v1.43 probe case...");
        int checked = 0;
        for ( final String name : ref().caseNames() ) {
            if ( name.endsWith("_eval_trace") || name.endsWith("_iterate_trajectory")
                    || name.equals("constructor_argument_validation") ) {
                continue;
            }
            checkCase(name);
            ++checked;
        }
        assertTrue("expected the probe to carry a substantial number of cases, got " + checked, checked >= 20);
    }

    /**
     * The iterate trajectory. Truncating at successive iteration counts exposes the sequence of points L-BFGS-B
     * actually visits — the closest observable proxy for its otherwise-private inner loop, and the assertion that
     * would catch a Cauchy-point or subspace-minimization error that still happens to converge.
     */
    @Test
    public void testIterateTrajectory() {
        QL.info("Testing the LBFGSB iterate trajectory against C++ v1.43...");
        final ReferenceReader.Case c = ref().getCase("rosenbrock_2d_bounded_iterate_trajectory");
        final JSONObject in = c.inputs();
        final JSONObject out = (JSONObject) c.expectedRaw();
        final JSONObject opt = in.getJSONObject("lbfgsb");

        final JSONArray steps = out.getJSONArray("steps");
        for ( int s = 0; s < steps.length(); ++s ) {
            final JSONObject step = steps.getJSONObject(s);
            final int k = step.getInt("maxIterations");

            final Problem problem = new Problem(objectiveFor(in), constraintFor(in), toArray(in.getJSONArray("x0")));
            final EndCriteria ec = new EndCriteria(k, 2, 1.0e-12, 1.0e-12, 1.0e-10);
            final LBFGSB optimizer = new LBFGSB(opt.getInt("memory"), opt.getDouble("pgTol"), opt.getDouble("fTol"));
            final EndCriteria.Type type = optimizer.minimize(problem, ec);

            final String tag = "trajectory k=" + k;
            assertEquals(tag + ": end criteria", step.getString("endCriteriaName"), type.name());
            final JSONArray xe = step.getJSONArray("x");
            for ( int i = 0; i < xe.length(); ++i ) {
                assertClose(tag + ": x[" + i + "]", xe.getDouble(i), problem.currentValue().get(i));
            }
            assertClose(tag + ": function value", step.getDouble("functionValue"), problem.functionValue());
            assertEquals(tag + ": function evaluations", step.getInt("functionEvaluations"),
                    problem.functionEvaluation());
        }
    }

    /**
     * The objective-evaluation trace: every point at which L-BFGS-B asked for a value and gradient, in order. This
     * catches a line search that lands on the right answer by a different route — a difference that would otherwise
     * only show up much later, on a harder problem.
     */
    @Test
    public void testEvaluationTraces() {
        QL.info("Testing the LBFGSB objective-evaluation trace against C++ v1.43...");
        for ( final String name : ref().caseNames() ) {
            if ( !name.endsWith("_eval_trace") ) {
                continue;
            }
            final ReferenceReader.Case c = ref().getCase(name);
            final JSONObject in = c.inputs();
            final JSONObject out = (JSONObject) c.expectedRaw();
            final JSONObject opt = in.getJSONObject("lbfgsb");

            final CostFunction inner = objectiveFor(in);
            final JSONArray trace = out.getJSONArray("evaluations");
            final int[] seen = { 0 };

            final CostFunction recording = new CostFunction() {
                @Override
                public double value(final Array x) {
                    return inner.value(x);
                }

                @Override
                public Array values(final Array x) {
                    return inner.values(x);
                }

                @Override
                public void gradient(final Array grad, final Array x) {
                    inner.gradient(grad, x);
                }

                @Override
                public double valueAndGradient(final Array grad, final Array x) {
                    final double f = inner.valueAndGradient(grad, x);
                    final int k = seen[0]++;
                    if ( k < trace.length() ) {
                        final JSONObject e = trace.getJSONObject(k);
                        final JSONArray xe = e.getJSONArray("x");
                        for ( int i = 0; i < xe.length(); ++i ) {
                            assertClose(name + " eval " + k + ": x[" + i + "]", xe.getDouble(i), x.get(i));
                        }
                        assertClose(name + " eval " + k + ": f", e.getDouble("f"), f);
                    }
                    return f;
                }
            };

            final Problem problem = new Problem(recording, constraintFor(in), toArray(in.getJSONArray("x0")));
            final LBFGSB optimizer = new LBFGSB(opt.getInt("memory"), opt.getDouble("pgTol"), opt.getDouble("fTol"));
            optimizer.minimize(problem, endCriteriaFor(in.getJSONObject("endCriteria")));

            assertEquals(name + ": evaluation count", out.getInt("evaluationCount"), seen[0]);
        }
    }

    /**
     * Constructor and dimension guards. The probe records that C++ rejects each of these; a port that silently
     * accepts them would run on garbage rather than fail loudly.
     */
    @Test
    public void testArgumentValidation() {
        QL.info("Testing LBFGSB argument validation against C++ v1.43...");
        final JSONObject out = (JSONObject) ref().getCase("constructor_argument_validation").expectedRaw();

        assertEquals("zero memory throws", out.getBoolean("zeroMemoryThrows"),
                throwsFor(() -> new LBFGSB(0, 1.0e-8, 1.0e-10)));

        assertEquals("mismatched bounds throw", out.getBoolean("mismatchedBoundsThrows"),
                throwsFor(() -> new LBFGSB(new Array(2), new Array(3), 10, 1.0e-8, 1.0e-10)));

        assertEquals("wrong bound dimension throws", out.getBoolean("wrongDimensionThrows"), throwsFor(() -> {
            final Array lo = new Array(new double[] { -1.0, -1.0, -1.0 });
            final Array hi = new Array(new double[] { 1.0, 1.0, 1.0 });
            final Problem p = new Problem(new RosenbrockFunction(), new NoConstraint(),
                    new Array(new double[] { 0.0, 0.0 }));
            new LBFGSB(lo, hi).minimize(p, new EndCriteria(100, 10, 1.0e-12, 1.0e-12, 1.0e-10));
        }));
    }

    private static boolean throwsFor(final Runnable r) {
        try {
            r.run();
            return false;
        } catch ( final RuntimeException expected ) {
            return true;
        }
    }
}
