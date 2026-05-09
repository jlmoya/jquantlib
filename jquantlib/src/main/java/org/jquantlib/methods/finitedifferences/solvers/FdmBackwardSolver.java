/*
 Copyright (C) 2009 Andreas Gaida
 Copyright (C) 2009 Ralph Schreyer
 Copyright (C) 2009 Klaus Spanderen

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.methods.finitedifferences.solvers;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.jquantlib.QL;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.StepCondition;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.schemes.CraigSneydScheme;
import org.jquantlib.methods.finitedifferences.schemes.CrankNicolsonScheme;
import org.jquantlib.methods.finitedifferences.schemes.DouglasScheme;
import org.jquantlib.methods.finitedifferences.schemes.ExplicitEulerScheme;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.schemes.HundsdorferScheme;
import org.jquantlib.methods.finitedifferences.schemes.ImplicitEulerScheme;
import org.jquantlib.methods.finitedifferences.schemes.MethodOfLinesScheme;
import org.jquantlib.methods.finitedifferences.schemes.ModifiedCraigSneydScheme;
import org.jquantlib.methods.finitedifferences.schemes.TrBDF2Scheme;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

/**
 * Time-stepping rollback driver for the Fdm framework.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/solvers/fdmbackwardsolver.{hpp,cpp}}.
 *
 * <p>The driver accepts a scheme descriptor and dispatches to the matching
 * scheme implementation, then rolls the state from {@code from} down to
 * {@code to} in {@code steps} (+ optional {@code dampingSteps}) increments.
 *
 * <h2>Scheme coverage</h2>
 *
 * Phase 2h WI-1 dispatched only Hundsdorfer / Douglas / ImplicitEuler.
 * Phase 5j.5 wires the remaining six scheme variants now that all the
 * ported scheme classes exist (CrankNicolson, CraigSneyd, ModifiedCraigSneyd,
 * ExplicitEuler, MethodOfLines, TrBDF2).
 *
 * @author Phase 2h WI-1 port; Phase 5j.5 — full scheme dispatch
 */
public class FdmBackwardSolver {

    /**
     * Erased evolver interface so the rollback loop can be inlined once
     * (Java has no template specialization). Each scheme provides this
     * adapter via lambdas in {@link #rollback}.
     */
    private interface Evolver {
        void setStep(double dt);
        void step(Array a, double t);
    }

    private final FdmLinearOpComposite map;
    private final FdmBoundaryConditionSet bcSet;
    private final FdmStepConditionComposite condition;
    private final FdmSchemeDesc schemeDesc;

    public FdmBackwardSolver(final FdmLinearOpComposite map,
                             final FdmBoundaryConditionSet bcSet,
                             final FdmStepConditionComposite condition,
                             final FdmSchemeDesc schemeDesc) {
        this.map = map;
        this.bcSet = bcSet;
        // Mirrors C++ default-construction when condition is null:
        // empty stoppingTimes, empty Conditions list.
        this.condition = (condition != null)
                ? condition
                : new FdmStepConditionComposite(
                        new ArrayList<List<Double>>(),
                        new FdmStepConditionComposite.Conditions());
        this.schemeDesc = schemeDesc;
    }

    /**
     * Rollback {@code rhs} from time {@code from} down to {@code to} in
     * {@code steps + dampingSteps} increments. The first
     * {@code dampingSteps} steps use implicit Euler (skipped if the main
     * scheme is itself implicit Euler).
     */
    public void rollback(final Array rhs,
                         final double from, final double to,
                         final int steps, final int dampingSteps) {
        final double deltaT = from - to;
        final int allSteps = steps + dampingSteps;
        final double dampingTo = from - (deltaT * dampingSteps) / allSteps;

        if (dampingSteps != 0
                && schemeDesc.type != FdmSchemeDesc.FdmSchemeType.ImplicitEulerType) {
            final ImplicitEulerScheme implicitEvolver =
                    new ImplicitEulerScheme(map, bcSet);
            rollbackImpl(rhs, from, dampingTo, dampingSteps,
                    new Evolver() {
                        @Override public void setStep(final double dt) { implicitEvolver.setStep(dt); }
                        @Override public void step(final Array a, final double t) { implicitEvolver.step(a, t); }
                    },
                    condition.stoppingTimes(), condition);
        }

        switch (schemeDesc.type) {
        case HundsdorferType: {
            final HundsdorferScheme hsEvolver =
                    new HundsdorferScheme(schemeDesc.theta, schemeDesc.mu, map, bcSet);
            rollbackImpl(rhs, dampingTo, to, steps,
                    new Evolver() {
                        @Override public void setStep(final double dt) { hsEvolver.setStep(dt); }
                        @Override public void step(final Array a, final double t) { hsEvolver.step(a, t); }
                    },
                    condition.stoppingTimes(), condition);
            break;
        }
        case DouglasType: {
            final DouglasScheme dsEvolver =
                    new DouglasScheme(schemeDesc.theta, map, bcSet);
            rollbackImpl(rhs, dampingTo, to, steps,
                    new Evolver() {
                        @Override public void setStep(final double dt) { dsEvolver.setStep(dt); }
                        @Override public void step(final Array a, final double t) { dsEvolver.step(a, t); }
                    },
                    condition.stoppingTimes(), condition);
            break;
        }
        case ImplicitEulerType: {
            final ImplicitEulerScheme implicitEvolver =
                    new ImplicitEulerScheme(map, bcSet);
            rollbackImpl(rhs, from, to, allSteps,
                    new Evolver() {
                        @Override public void setStep(final double dt) { implicitEvolver.setStep(dt); }
                        @Override public void step(final Array a, final double t) { implicitEvolver.step(a, t); }
                    },
                    condition.stoppingTimes(), condition);
            break;
        }
        case CrankNicolsonType: {
            final CrankNicolsonScheme cnEvolver =
                    new CrankNicolsonScheme(schemeDesc.theta, map, bcSet);
            rollbackImpl(rhs, dampingTo, to, steps,
                    new Evolver() {
                        @Override public void setStep(final double dt) { cnEvolver.setStep(dt); }
                        @Override public void step(final Array a, final double t) { cnEvolver.step(a, t); }
                    },
                    condition.stoppingTimes(), condition);
            break;
        }
        case CraigSneydType: {
            final CraigSneydScheme csEvolver =
                    new CraigSneydScheme(schemeDesc.theta, schemeDesc.mu, map, bcSet);
            rollbackImpl(rhs, dampingTo, to, steps,
                    new Evolver() {
                        @Override public void setStep(final double dt) { csEvolver.setStep(dt); }
                        @Override public void step(final Array a, final double t) { csEvolver.step(a, t); }
                    },
                    condition.stoppingTimes(), condition);
            break;
        }
        case ModifiedCraigSneydType: {
            final ModifiedCraigSneydScheme mcsEvolver =
                    new ModifiedCraigSneydScheme(
                            schemeDesc.theta, schemeDesc.mu, map, bcSet);
            rollbackImpl(rhs, dampingTo, to, steps,
                    new Evolver() {
                        @Override public void setStep(final double dt) { mcsEvolver.setStep(dt); }
                        @Override public void step(final Array a, final double t) { mcsEvolver.step(a, t); }
                    },
                    condition.stoppingTimes(), condition);
            break;
        }
        case ExplicitEulerType: {
            final ExplicitEulerScheme expEvolver =
                    new ExplicitEulerScheme(map, bcSet);
            rollbackImpl(rhs, dampingTo, to, steps,
                    new Evolver() {
                        @Override public void setStep(final double dt) { expEvolver.setStep(dt); }
                        @Override public void step(final Array a, final double t) { expEvolver.step(a, t); }
                    },
                    condition.stoppingTimes(), condition);
            break;
        }
        case MethodOfLinesType: {
            // FdmSchemeDesc repurposes (theta, mu) as (eps, relInitStepSize).
            final MethodOfLinesScheme molEvolver =
                    new MethodOfLinesScheme(
                            schemeDesc.theta, schemeDesc.mu, map, bcSet);
            rollbackImpl(rhs, dampingTo, to, steps,
                    new Evolver() {
                        @Override public void setStep(final double dt) { molEvolver.setStep(dt); }
                        @Override public void step(final Array a, final double t) { molEvolver.step(a, t); }
                    },
                    condition.stoppingTimes(), condition);
            break;
        }
        case TrBDF2Type: {
            // C++ uses CraigSneyd as the trapezoidal sub-scheme and
            // schemeDesc.mu as the iterative-solver relative tolerance.
            final FdmSchemeDesc trDesc = FdmSchemeDesc.CraigSneyd();
            final CraigSneydScheme trapezoidal = new CraigSneydScheme(
                    trDesc.theta, trDesc.mu, map, bcSet);
            final TrBDF2Scheme trEvolver = new TrBDF2Scheme(
                    schemeDesc.theta, map,
                    TrBDF2Scheme.adapt(trapezoidal),
                    bcSet, schemeDesc.mu, TrBDF2Scheme.SolverType.BiCGstab);
            rollbackImpl(rhs, dampingTo, to, steps,
                    new Evolver() {
                        @Override public void setStep(final double dt) { trEvolver.setStep(dt); }
                        @Override public void step(final Array a, final double t) { trEvolver.step(a, t); }
                    },
                    condition.stoppingTimes(), condition);
            break;
        }
        default:
            throw new LibraryException("Unknown scheme type: " + schemeDesc.type);
        }
    }

    /**
     * Generic rollback loop, mirrors C++
     * {@code FiniteDifferenceModel<Evolver>::rollbackImpl}.
     * <p>
     * The {@code stoppingTimes} input is the per-condition list from the
     * composite; we deduplicate and sort it inline (matches C++
     * constructor's std::sort + std::unique).
     */
    private void rollbackImpl(Array a,
                              final double from, final double to,
                              final int steps,
                              final Evolver evolver,
                              final List<Double> rawStoppingTimes,
                              final StepCondition<Array> stepCondition) {
        QL.require(from >= to, "trying to roll back from " + from + " to " + to);

        // Sort + dedup, matching C++ constructor body.
        final List<Double> stoppingTimes =
                new ArrayList<Double>(new TreeSet<Double>(rawStoppingTimes));

        final double dt = (from - to) / steps;
        double t = from;
        evolver.setStep(dt);

        // Mirrors C++ "if last stoppingTime == from, apply condition before stepping".
        if (!stoppingTimes.isEmpty()
                && stoppingTimes.get(stoppingTimes.size() - 1) == from) {
            if (stepCondition != null) {
                stepCondition.applyTo(a, from);
            }
        }

        for (int i = 0; i < steps; ++i, t -= dt) {
            double now = t;
            // make sure last step ends exactly on "to" (matches C++)
            double next = (i < steps - 1) ? t - dt : to;
            if (Math.abs(to - next) < Math.sqrt(QL_EPSILON)) {
                next = to;
            }
            boolean hit = false;
            for (int j = stoppingTimes.size() - 1; j >= 0; --j) {
                final double sj = stoppingTimes.get(j);
                if (next <= sj && sj < now) {
                    hit = true;
                    evolver.setStep(now - sj);
                    evolver.step(a, now);
                    if (stepCondition != null) {
                        stepCondition.applyTo(a, sj);
                    }
                    now = sj;
                }
            }
            if (hit) {
                if (now > next) {
                    evolver.setStep(now - next);
                    evolver.step(a, now);
                    if (stepCondition != null) {
                        stepCondition.applyTo(a, next);
                    }
                }
                evolver.setStep(dt);
            } else {
                evolver.step(a, now);
                if (stepCondition != null) {
                    stepCondition.applyTo(a, next);
                }
            }
        }
    }

    /** Approximate machine epsilon for double, matching C++ {@code QL_EPSILON}. */
    private static final double QL_EPSILON = 2.2204460492503131e-16;
}
