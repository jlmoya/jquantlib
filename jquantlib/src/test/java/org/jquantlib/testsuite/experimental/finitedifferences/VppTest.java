/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.finitedifferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.BermudanExercise;
import org.jquantlib.experimental.finitedifferences.FdSimpleExtOUStorageEngine;
import org.jquantlib.experimental.finitedifferences.FdmExtOUJumpOp;
import org.jquantlib.experimental.processes.ExtOUWithJumpsProcess;
import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess;
import org.jquantlib.instruments.VanillaStorageOption;
import org.jquantlib.math.Ops;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.ExponentialJump1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5j skeleton port of {@code test-suite/vpp.cpp} v1.42.1.
 *
 * <p><strong>Phase 5e.5b-CFC-d-215 update:</strong>
 * {@link org.jquantlib.instruments.VanillaStorageOption},
 * {@link org.jquantlib.methods.finitedifferences.stepconditions.FdmSimpleStorageCondition},
 * {@link org.jquantlib.experimental.finitedifferences.FdmSimple2dExtOUSolver},
 * and the {@link FdSimpleExtOUStorageEngine#calculate()} body are now
 * ported. {@link #testSimpleExtOUStorageEngine()} reproduces the cached
 * C++ NPV of 69.5755 within the same 5e-2 tolerance the C++ test uses.
 *
 * <p>Source: {@code test-suite/vpp.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class VppTest {

    private static final String REASON_VPP_ENGINE =
            "Phase 5j.5 — requires FdSimpleKlugeExtOUVPPEngine "
          + "(full VPP pricing path; FdmKlugeExtOUOp + FdmVPPStepCondition* "
          + "not yet ported)";

    private static final String REASON_DP_ENGINE =
            "Phase 5j.5 — VanillaVPPOption + SwingExercise are ported "
          + "(Phase 5e.5b-CFC-d-164); still requires DynProgVPPIntrinsicValueEngine "
          + "(needs FdmVPPStepCondition + FdmVPPStepConditionFactory)";

    private static final String REASON_SPREAD_ENGINE =
            "Phase 5j.5 — FdKlugeExtOUSpreadEngine + FdmSpreadPayoffInnerValue are "
          + "ported as a skeleton (Phase 5e.5b-CFC-d-164); the calculate() body "
          + "still needs FdmKlugeExtOUSolver<3> (which needs FdmNdimSolver "
          + "+ FdmKlugeExtOUOp), plus AverageBasketPayoff + BasketOption + "
          + "MultiPathGenerator harness for the MC reference value";

    private static final String REASON_PROCESS =
            "Phase 5j.5 — requires GemanRoncoroniProcess MC harness wiring "
          + "(process exists; calibration loop not yet exercised)";

    @Ignore(REASON_PROCESS)
    @Test
    public void testGemanRoncoroniProcess() { fail("not implemented"); }

    /**
     * Java port of C++ {@code testSimpleExtOUStorageEngine} (vpp.cpp:359-402).
     *
     * <p>Builds a Bermudan working-gas storage option (capacity 50, load 0,
     * change rate 1, daily exercises over 12 months) driven by an extended
     * Ornstein-Uhlenbeck process with {@code speed = 1, vol = 0.5, x0 = 3,
     * b(t) = 3}, prices it on a 25-x-grid / elevator y-mesh / 1-time-step
     * FD scheme, and checks the NPV against the cached C++ value of
     * 69.5755 within 5e-2 absolute (same tolerance as C++).</p>
     */
    @Test
    public void testSimpleExtOUStorageEngine() {
        final Date settlementDate = new Date(18, Month.December, 2011);
        new Settings().setEvaluationDate(settlementDate);
        final DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISDA);
        final Date maturityDate =
                settlementDate.add(new Period(12, TimeUnit.Months));

        // Daily exercise dates from settlement+1 until maturityDate.
        final List<Date> exerciseDatesList = new ArrayList<Date>();
        exerciseDatesList.add(settlementDate.add(new Period(1, TimeUnit.Days)));
        while (exerciseDatesList.get(exerciseDatesList.size() - 1).lt(maturityDate)) {
            final Date last = exerciseDatesList.get(exerciseDatesList.size() - 1);
            exerciseDatesList.add(last.add(new Period(1, TimeUnit.Days)));
        }
        final Date[] exerciseDates =
                exerciseDatesList.toArray(new Date[exerciseDatesList.size()]);
        final BermudanExercise bermudanExercise = new BermudanExercise(exerciseDates);

        final double x0 = 3.0;
        final double speed = 1.0;
        final double volatility = 0.5;
        final double irRate = 0.1;
        final double constantLevel = x0;

        final ExtendedOrnsteinUhlenbeckProcess ouProcess =
                new ExtendedOrnsteinUhlenbeckProcess(speed, volatility, x0,
                        new Ops.DoubleOp() {
                            @Override public double op(final double t) {
                                return constantLevel;
                            }
                        });

        final YieldTermStructure rTS = new FlatForward(settlementDate,
                irRate, dayCounter);

        // tGrid = 1, xGrid = 25, yGrid = null (elevator), no shape,
        // Douglas scheme (engine default).
        final PricingEngine storageEngine = new FdSimpleExtOUStorageEngine(
                ouProcess, rTS, 1, 25);

        final VanillaStorageOption storageOption = new VanillaStorageOption(
                bermudanExercise, 50.0, 0.0, 1.0);
        storageOption.setPricingEngine(storageEngine);

        final double expected = 69.5755;
        final double calculated = storageOption.NPV();

        // C++ tolerance: 5e-2 absolute. Same here.
        assertEquals("Failed to reproduce cached values "
                   + "(calculated=" + calculated + ", expected=" + expected + ")",
                   expected, calculated, 5.0e-2);
    }

    @Ignore(REASON_SPREAD_ENGINE)
    @Test
    public void testKlugeExtOUSpreadOption() { fail("not implemented"); }

    @Ignore(REASON_DP_ENGINE)
    @Test
    public void testVPPIntrinsicValue() { fail("not implemented"); }

    @Ignore(REASON_VPP_ENGINE)
    @Test
    public void testVPPPricing() { fail("not implemented"); }

    @Ignore("Phase 5j.5 — requires Kluge ExtOU matrix decomposition utility "
          + "(used by Bermudan VPP MC engine)")
    @Test
    public void testKlugeExtOUMatrixDecomposition() { fail("not implemented"); }

    @Test
    public void testFdmExtOUJumpOpSmoke() {
        final int xGrid = 9;
        final int yGrid = 7;

        final double speed = 1.0;
        final double vol = 0.4;
        final double x0 = 3.0;
        final ExtendedOrnsteinUhlenbeckProcess ouProcess =
                new ExtendedOrnsteinUhlenbeckProcess(speed, vol, x0,
                        new Ops.DoubleOp() {
                            @Override public double op(final double t) { return x0; }
                        });

        final double beta = 5.0;
        final double jumpIntensity = 1.0;
        final double eta = 1.0 / 0.4;
        final ExtOUWithJumpsProcess process =
                new ExtOUWithJumpsProcess(ouProcess, 0.0, beta, jumpIntensity, eta);

        final Fdm1dMesher xMesher = new Uniform1dMesher(0.5, 5.5, xGrid);
        final Fdm1dMesher yMesher =
                new ExponentialJump1dMesher(yGrid, beta, jumpIntensity, eta);
        final FdmMesher mesher = new FdmMesherComposite(xMesher, yMesher);

        final Date refDate = new Date(18, Month.December, 2011);
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final YieldTermStructure rTS = new FlatForward(refDate, 0.1, dc);

        final FdmExtOUJumpOp op = new FdmExtOUJumpOp(
                mesher, process, rTS, new FdmBoundaryConditionSet(), 16);
        op.setTime(0.1, 0.2);

        final int n = mesher.layout().size();
        assertEquals(xGrid * yGrid, n);
        final Array r = new Array(n);
        for (int i = 0; i < n; ++i) {
            r.set(i, Math.sin(0.5 * i) + 1.7);
        }

        final Array full = op.apply(r);
        final Array d0   = op.applyDirection(0, r);
        final Array d1   = op.applyDirection(1, r);
        final Array mix  = op.applyMixed(r);

        assertEquals(n, full.size());
        assertNotNull(d0);
        assertNotNull(d1);
        assertNotNull(mix);

        final double tol = 1.0e-12;
        for (int i = 0; i < n; ++i) {
            final double expected = d0.get(i) + d1.get(i) + mix.get(i);
            assertEquals("mismatch at i=" + i,
                    expected, full.get(i), tol);
        }

        final Array splitOther = op.solveSplitting(2, r, 0.1);
        for (int i = 0; i < n; ++i) {
            assertEquals(r.get(i), splitOther.get(i), 0.0);
        }

        final Array pre = op.preconditioner(r, 0.01);
        assertEquals(n, pre.size());

        assertTrue("size() should be 2", op.size() == 2);
    }
}
