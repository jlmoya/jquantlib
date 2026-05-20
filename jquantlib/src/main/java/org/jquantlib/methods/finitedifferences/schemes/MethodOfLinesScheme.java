/*
 Copyright (C) 2018 Klaus Spanderen

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
package org.jquantlib.methods.finitedifferences.schemes;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.ode.AdaptiveRungeKutta;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

/**
 * Method-of-lines time-stepping scheme.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/schemes/methodoflinesscheme.{hpp,cpp}}.
 * <p>
 * Converts the spatial discretisation into a system of ODEs and integrates with an adaptive Runge-Kutta solver
 * ({@link AdaptiveRungeKutta}). This avoids choosing a fixed time step but may be expensive for large systems.
 *
 * @author Phase 2l Track C.5 port
 */
public class MethodOfLinesScheme {

    protected final FdmLinearOpComposite map;
    protected final BoundaryConditionSchemeHelper bcSet;
    private final double eps;
    private final double relInitStepSize;
    /** Time step (NaN until {@link #setStep} is called). */
    protected double dt;

    /** Constructor with empty boundary-condition set (mirrors C++ default arg). */
    public MethodOfLinesScheme(final double eps, final double relInitStepSize, final FdmLinearOpComposite map) {
        this(eps, relInitStepSize, map, new FdmBoundaryConditionSet());
    }

    public MethodOfLinesScheme(final double eps, final double relInitStepSize, final FdmLinearOpComposite map,
            final FdmBoundaryConditionSet bcSet) {
        this.dt = Double.NaN;
        this.eps = eps;
        this.relInitStepSize = relInitStepSize;
        this.map = map;
        this.bcSet = new BoundaryConditionSchemeHelper(bcSet);
    }

    private static double[] toDoubleArray(final Array a) {
        final double[] result = new double[a.size()];
        for ( int i = 0; i < a.size(); i++ ) {
            result[i] = a.get(i);
        }
        return result;
    }

    private static Array fromDoubleArray(final double[] v) {
        final Array a = new Array(v.length);
        for ( int i = 0; i < v.length; i++ ) {
            a.set(i, v[i]);
        }
        return a;
    }

    /** Set the rollback step size. */
    public void setStep(final double dt) {
        this.dt = dt;
    }

    /**
     * Advance {@code a} from time {@code t} to {@code t-dt} in-place.
     * <p>
     * Mirrors C++ {@code MethodOfLinesScheme::step}. Integrates the ODE {@code dy/dt = -L(y)} from {@code t} to
     * {@code max(0, t-dt)} using an adaptive Cash-Karp Runge-Kutta stepper.
     *
     * <h3>Java/C++ divergence note</h3>
     * C++ {@code MethodOfLinesScheme::step} never calls {@code bcSet_.setTime(t)} (it relies on its callers
     * having constructed {@code FdmDirichletBoundary} instances whose value is fixed at construction time and
     * whose {@code setTime} is a no-op). In Java we lack a constant-Dirichlet variant — all engines (e.g.,
     * {@link org.jquantlib.pricingengines.barrier.FdBlackScholesBarrierEngine},
     * {@link org.jquantlib.pricingengines.barrier.FdHestonBarrierEngine}) construct
     * {@link org.jquantlib.methods.finitedifferences.utilities.FdmTimeDepDirichletBoundary} with a constant
     * lambda. That class only populates its {@code values_} buffer inside {@code setTime(t)}; without an
     * explicit {@code setTime} call the buffer remains zero-initialised and {@code applyAfterSolving}
     * silently writes zero into the boundary cells (e.g. drops the rebate to ~3.5e-5 on the discrete-dividend
     * barrier test). We therefore inject {@code bcSet.setTime(...)} calls here — once inside the ODE RHS so
     * any time-dependent lambdas (e.g. discount-factor boundaries) see the right argument at each substep,
     * and once before the final {@code applyAfterSolving} so the boundary cells carry the destination-time
     * value. For constant lambdas this is a no-op; for time-dependent ones it matches the semantics other
     * schemes (Hundsdorfer/Douglas/Crank-Nicolson/CraigSneyd) already obtain through their explicit
     * {@code bcSet.setTime(tPrev)} call.
     */
    public void step(final Array a, final double t) {
        QL.require(t - dt > -1e-8, "a step towards negative time given");

        final double tFrom = t;
        final double tTo = Math.max(0.0, t - dt);

        // ODE: dy/ds = -(map applied to y at time s)
        // Note: C++ integrates backward in time (from t to t-dt),
        // the RHS is the negative of the forward spatial operator.
        final AdaptiveRungeKutta rk = new AdaptiveRungeKutta(eps, relInitStepSize * dt);

        final double[] u0 = toDoubleArray(a);

        final double[] v = rk.solve((s, u) -> applyOde(s, u), u0, tFrom, tTo);

        final Array y = fromDoubleArray(v);
        // Java carve-out vs C++ (see step()'s doc-comment): refresh the boundary buffer at the destination
        // time before writing it into the result. C++ relies on constant Dirichlet whose values are baked in;
        // Java's FdmTimeDepDirichletBoundary needs setTime to populate values_ — otherwise applyAfterSolving
        // overwrites the boundary cells with zero.
        bcSet.setTime(tTo);
        bcSet.applyAfterSolving(y);

        a.fill(y);
    }

    /**
     * ODE right-hand-side for the adaptive integrator: {@code dy/dt = -L(y)}. Mirrors C++
     * {@code MethodOfLinesScheme::apply}.
     */
    private double[] applyOde(final double t, final double[] u) {
        map.setTime(t, t + 0.0001);
        // Java carve-out vs C++ (see step()'s doc-comment): refresh the boundary buffer at the current ODE
        // sub-step time so any time-dependent boundary lambda (discount-factor / yield-curve based) sees
        // the right argument. For constant lambdas this is a no-op.
        bcSet.setTime(t);
        bcSet.applyBeforeApplying(map);

        final Array arr = fromDoubleArray(u);
        final Array dxdt = map.apply(arr).mulAssign(-1.0);

        return toDoubleArray(dxdt);
    }
}
