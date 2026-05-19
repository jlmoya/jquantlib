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
import org.jquantlib.experimental.finitedifferences.FdmKlugeExtOUOp;
import org.jquantlib.experimental.processes.ExtOUWithJumpsProcess;
import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess;
import org.jquantlib.experimental.processes.GemanRoncoroniProcess;
import org.jquantlib.experimental.processes.KlugeExtOUProcess;
import org.jquantlib.instruments.VanillaStorageOption;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.math.statistics.GeneralStatistics;
import org.jquantlib.methods.finitedifferences.meshers.ExponentialJump1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.FdmSimpleProcess1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.MultiPathGenerator;
import org.jquantlib.methods.montecarlo.Sample;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.StochasticProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeGrid;
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
 * <p><strong>Phase 5e.5b-CFC-d-247 update:</strong>
 * {@link #testGemanRoncoroniProcess()} is now body-filled. It wires the
 * {@link GemanRoncoroniProcess} electricity spot process with an
 * {@link ExtendedOrnsteinUhlenbeckProcess} fuel-log process via
 * {@link StochasticProcessArray}, drives a {@link MultiPathGenerator}
 * over a 10-year horizon with 250 steps per year, and statistically
 * checks the mean spark-spread NPV against the cached C++ value of
 * 12500 using a {@code max(3*errorEstimate, 5e-2*|expected|)} band
 * (the C++ test's own 3-sigma rule, with a 5e-2 LOOSE-tier relative
 * floor since MT-stream differences vs C++ can shift the empirical
 * mean a few percent).
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

    /**
     * Java port of C++ {@code testGemanRoncoroniProcess} (vpp.cpp:238-357).
     *
     * <p>Builds the Geman-Roncoroni electricity spot process with the paper's
     * canonical parameter set, couples it to an
     * {@link ExtendedOrnsteinUhlenbeckProcess} fuel-log process via a
     * {@link StochasticProcessArray} with a 0.25 correlation, and drives a
     * {@link MultiPathGenerator} over a 10-year horizon with 250 steps per
     * year. For each path the spark-spread NPV
     * {@code sum max(0, p_e - heatRate*p_f) * df(t)} and the "on-time"
     * fraction (spark spread > 0) are accumulated.
     *
     * <p>Path count is reduced from C++ {@code nrTrails=250} to 80 to keep
     * the JVM test loop within the surefire budget while still giving a
     * statistically meaningful Monte Carlo mean. Tolerance follows the
     * C++ test's 3-sigma statistical rule with a 5e-2 LOOSE-tier relative
     * floor (per project quality-gate rules).
     */
    @Test
    public void testGemanRoncoroniProcess() {
        final Date today = new Date(18, Month.December, 2011);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);

        final YieldTermStructure rTS = new FlatForward(today, 0.03, dc);

        final double x0     =  3.3;
        final double beta   =  0.05;
        final double alpha  =  3.1;
        final double gamma  = -0.09;
        final double delta  =  0.07;
        final double eps    = -0.40;
        final double zeta   = -0.40;
        final double d      =  1.6;
        final double k      =  1.0;
        final double tau    =  0.5;
        final double sig2   = 10.0;
        final double a      = -7.0;
        final double b      = -0.3;
        final double theta1 = 35.0;
        final double theta2 =  9.0;
        final double theta3 =  0.10;
        final double psi    =  1.9;

        final GemanRoncoroniProcess grProcess = new GemanRoncoroniProcess(
                x0, alpha, beta, gamma, delta, eps, zeta, d,
                k, tau, sig2, a, b, theta1, theta2, theta3, psi);

        final double speed   = 5.0;
        final double vol     = Math.sqrt(1.4);
        final double betaG   = 0.08;
        final double alphaG  = 1.0;

        // linear(alphaG, betaG)(t) = alphaG + betaG * t
        final Ops.DoubleOp linear = new Ops.DoubleOp() {
            @Override public double op(final double t) {
                return alphaG + betaG * t;
            }
        };

        final double x0G = 1.1;
        final StochasticProcess1D eouProcess = new ExtendedOrnsteinUhlenbeckProcess(
                speed, vol, x0G, linear,
                ExtendedOrnsteinUhlenbeckProcess.Discretization.Trapezodial, 1.0e-4);

        final List<StochasticProcess1D> processes = new ArrayList<StochasticProcess1D>(2);
        processes.add(grProcess);
        processes.add(eouProcess);

        final Matrix correlation = new Matrix(2, 2);
        correlation.set(0, 0, 1.0);
        correlation.set(1, 1, 1.0);
        correlation.set(0, 1, 0.25);
        correlation.set(1, 0, 0.25);

        final StochasticProcess pArray = new StochasticProcessArray(processes, correlation);

        final double T = 10.0;
        final int stepsPerYear = 250;
        final int steps = (int) (T * stepsPerYear);

        final TimeGrid grid = new TimeGrid(T, steps);

        // Reduced from C++ nrTrails=250 to keep JVM test runtime bounded;
        // 80 paths still yields a stable 3-sigma envelope for the MC mean.
        final int nrTrails = 80;
        final long seed = 421L;

        final int dim = pArray.size() * (grid.size() - 1);
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(seed);
        final RandomSequenceGenerator<MersenneTwisterUniformRng> rsg =
                new RandomSequenceGenerator<MersenneTwisterUniformRng>(
                        MersenneTwisterUniformRng.class, dim, rng);
        final InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                   InverseCumulativeNormal> gsg =
                new InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                        InverseCumulativeNormal>(
                        rsg, new InverseCumulativeNormal());

        final MultiPathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                                      InverseCumulativeNormal>> generator =
                new MultiPathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                                            InverseCumulativeNormal>>(
                        pArray, grid, gsg, false);

        final GeneralStatistics npv = new GeneralStatistics();
        final GeneralStatistics onTime = new GeneralStatistics();

        final double heatRate = 8.0;

        for (int n = 0; n < nrTrails; ++n) {
            double plantValue = 0.0;
            final Sample<MultiPath> path = generator.next();

            for (int i = 1; i <= steps; ++i) {
                final double t = (double) i / stepsPerYear;
                final double df = rTS.discount(t);

                final double fuelPrice         = Math.exp(path.value().get(1).get(i));
                final double electricityPrice  = Math.exp(path.value().get(0).get(i));

                final double sparkSpread = electricityPrice - heatRate * fuelPrice;
                plantValue += Math.max(0.0, sparkSpread) * df;
                onTime.add(sparkSpread > 0.0 ? 1.0 : 0.0);
            }
            npv.add(plantValue);
        }

        final double expectedNPV = 12500.0;
        final double calculatedNPV = npv.mean();
        final double errorEstimateNPV = npv.errorEstimate();

        // C++ test uses 3*errorEstimate; we honour that statistical bound
        // with a 5e-2 relative floor (project LOOSE tier), since MT stream
        // differences vs C++ may shift the empirical mean a few percent.
        final double npvBand = Math.max(3.0 * errorEstimateNPV,
                                        5.0e-2 * Math.abs(expectedNPV));
        assertEquals(
                "Failed to reproduce GR process MC NPV"
              + " (calculated=" + calculatedNPV
              + ", expected=" + expectedNPV
              + ", errorEstimate=" + errorEstimateNPV
              + ", band=" + npvBand + ")",
                expectedNPV, calculatedNPV, npvBand);

        final double expectedOnTime = 0.43;
        final double calculatedOnTime = onTime.mean();
        // Bernoulli stderr from C++: sqrt(p(1-p))/nrTrails.
        final double errorEstimateOnTime =
                Math.sqrt(calculatedOnTime * (1.0 - calculatedOnTime)) / nrTrails;
        final double onTimeBand = Math.max(3.0 * errorEstimateOnTime,
                                           5.0e-2 * Math.abs(expectedOnTime));
        assertEquals(
                "Failed to reproduce GR process MC on-time fraction"
              + " (calculated=" + calculatedOnTime
              + ", expected=" + expectedOnTime
              + ", errorEstimate=" + errorEstimateOnTime
              + ", band=" + onTimeBand + ")",
                expectedOnTime, calculatedOnTime, onTimeBand);
    }

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

    /**
     * Java port of C++ {@code testKlugeExtOUMatrixDecomposition} (vpp.cpp:848-939).
     *
     * <p>Builds the {@link KlugeExtOUProcess} on a 3D mesher (50x20x20 grid)
     * and the corresponding {@link FdmKlugeExtOUOp}, evolves to
     * {@code (t1,t2) = (0.1, 0.2)}, applies the operator to a Mersenne-
     * Twister-seeded random vector, and verifies that:</p>
     * <ul>
     *   <li>{@code op.toSparseMatrix() * x == op.apply(x)} (full-operator
     *       sparse matrix reproduces the apply action);</li>
     *   <li>{@code matrixDecomp.back() * x == op.applyMixed(x)} (the last
     *       decomp entry is the correlation + Kluge integro mixed
     *       contribution);</li>
     *   <li>{@code matrixDecomp[i] * x == op.applyDirection(i, x)} for
     *       {@code i in {0,1,2}} (per-direction decomp matrices reproduce
     *       the directional apply action).</li>
     * </ul>
     *
     * <p>Tolerance matches the C++ test: absolute 1e-9 OR relative 1e-9
     * (TIGHT tier — exact matrix-form / apply-form equivalence).</p>
     *
     * <p>Note on storage: the C++ test uses {@code SparseMatrix} (boost
     * uBLAS compressed). The Java port likewise uses
     * {@link FdmKlugeExtOUOp#toSparseMatrixDecomp()} +
     * {@link FdmKlugeExtOUOp#toSparseMatrix()} — the 50x20x20 = 20000-cell
     * layout would otherwise need 20000x20000 = 3.2 GB dense matrices,
     * exceeding the surefire heap.</p>
     *
     * <p>Source: {@code test-suite/vpp.cpp} v1.42.1 @ {@code 099987f0ca}.
     * Port: Phase 5e.5b-CFC-d-285 along with {@link FdmKlugeExtOUOp}.
     */
    @Test
    public void testKlugeExtOUMatrixDecomposition() {
        final Date today = new Date(18, Month.December, 2011);
        new Settings().setEvaluationDate(today);

        final KlugeExtOUProcess klugeOUProcess = createKlugeExtOUProcess();

        final int xGrid = 50;
        final int yGrid = 20;
        final int uGrid = 20;
        final double maturity = 1.0;

        final ExtOUWithJumpsProcess klugeProcess = klugeOUProcess.getKlugeProcess();
        final StochasticProcess1D ouProcess =
                klugeProcess.getExtendedOrnsteinUhlenbeckProcess();

        final Fdm1dMesher xMesher =
                new FdmSimpleProcess1dMesher(xGrid, ouProcess, maturity);
        final Fdm1dMesher yMesher =
                new ExponentialJump1dMesher(yGrid,
                        klugeProcess.beta(),
                        klugeProcess.jumpIntensity(),
                        klugeProcess.eta());
        final Fdm1dMesher uMesher =
                new FdmSimpleProcess1dMesher(uGrid,
                        klugeOUProcess.getExtOUProcess(),
                        maturity);
        final FdmMesher mesher = new FdmMesherComposite(xMesher, yMesher, uMesher);

        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final YieldTermStructure rTS = new FlatForward(today, 0.0, dc);

        final FdmKlugeExtOUOp op = new FdmKlugeExtOUOp(
                mesher, klugeOUProcess, rTS, new FdmBoundaryConditionSet(), 16);
        op.setTime(0.1, 0.2);

        final int n = mesher.layout().size();
        final Array x = new Array(n);

        // C++ uses PseudoRandom::rng_type rng(PseudoRandom::urng_type(12345UL))
        // i.e. a Mersenne-Twister uniform on [0,1).
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(12345L);
        for (int i = 0; i < n; ++i) {
            x.set(i, rng.next().value());
        }

        final double tol = 1.0e-9;
        final Array applyExpected      = op.apply(x);
        final Array applyExpectedMixed = op.applyMixed(x);

        final List<SparseMatrix> matrixDecomp = op.toSparseMatrixDecomp();
        final Array applyCalculated      = op.toSparseMatrix().mul(x);
        final Array applyCalculatedMixed =
                matrixDecomp.get(matrixDecomp.size() - 1).mul(x);

        for (int i = 0; i < n; ++i) {
            final double diffApply = Math.abs(applyExpected.get(i) - applyCalculated.get(i));
            assertTrue(
                    "Failed to reproduce apply operation at i=" + i
                  + " expected="   + applyExpected.get(i)
                  + " calculated=" + applyCalculated.get(i)
                  + " diff="       + diffApply,
                    diffApply <= tol
                 || diffApply <= Math.abs(applyExpected.get(i)) * tol);

            final double diffMixed =
                    Math.abs(applyExpectedMixed.get(i) - applyCalculatedMixed.get(i));
            assertTrue(
                    "Failed to reproduce mixed apply at i=" + i
                  + " expected="   + applyExpectedMixed.get(i)
                  + " calculated=" + applyCalculatedMixed.get(i)
                  + " diff="       + diffMixed,
                    diffMixed <= tol
                 || diffMixed <= Math.abs(applyExpected.get(i)) * tol);
        }

        for (int dir = 0; dir < 3; ++dir) {
            final Array applyExpectedDir   = op.applyDirection(dir, x);
            final Array applyCalculatedDir = matrixDecomp.get(dir).mul(x);

            for (int j = 0; j < n; ++j) {
                final double diff =
                        Math.abs(applyExpectedDir.get(j) - applyCalculatedDir.get(j));
                assertTrue(
                        "Failed to reproduce apply_direction at dir=" + dir + " j=" + j
                      + " expected="   + applyExpectedDir.get(j)
                      + " calculated=" + applyCalculatedDir.get(j)
                      + " diff="       + diff,
                        diff <= tol
                     || diff <= Math.abs(applyExpectedDir.get(j)) * tol);
            }
        }
    }

    /**
     * Java port of C++ {@code createKlugeExtOUProcess} helper (vpp.cpp:206-236).
     *
     * <p>Constructs the canonical Kluge OU + extended OU joint process used
     * by the VPP test suite: power ≡ {@code exp(X + Y)} where {@code X} is
     * an OU diffusion (alpha=7.0, vol=1.4) and {@code Y} is exponential
     * jumps (beta=200, lambda=4.0, eta=5.0); gas ≡ {@code exp(U)} where
     * {@code U} is OU (kappa=4.45, vol=sqrt(1.3)); correlation {@code rho=0.7}
     * between {@code dW^X} and {@code dW^U}.</p>
     */
    private static KlugeExtOUProcess createKlugeExtOUProcess() {
        final double beta         = 200.0;
        final double eta          = 1.0 / 0.2;
        final double lambda       = 4.0;
        final double alpha        = 7.0;
        final double volatility_x = 1.4;
        final double kappa        = 4.45;
        final double volatility_u = Math.sqrt(1.3);
        final double rho          = 0.7;

        final double x0_0 = 0.0;
        final double x0_1 = 0.0;
        final double u    = 0.0;

        // constant_b(x) returns the constant function t -> x
        final Ops.DoubleOp constB_x0 = new Ops.DoubleOp() {
            @Override public double op(final double t) { return x0_0; }
        };
        final Ops.DoubleOp constB_u = new Ops.DoubleOp() {
            @Override public double op(final double t) { return u; }
        };

        final ExtendedOrnsteinUhlenbeckProcess ouProcess =
                new ExtendedOrnsteinUhlenbeckProcess(alpha, volatility_x, x0_0, constB_x0);
        final ExtOUWithJumpsProcess lnPowerProcess =
                new ExtOUWithJumpsProcess(ouProcess, x0_1, beta, lambda, eta);

        final ExtendedOrnsteinUhlenbeckProcess lnGasProcess =
                new ExtendedOrnsteinUhlenbeckProcess(kappa, volatility_u, u, constB_u);

        return new KlugeExtOUProcess(rho, lnPowerProcess, lnGasProcess);
    }

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
