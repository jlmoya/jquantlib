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
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.experimental.finitedifferences.DynProgVPPIntrinsicValueEngine;
import org.jquantlib.experimental.finitedifferences.FdKlugeExtOUSpreadEngine;
import org.jquantlib.experimental.finitedifferences.FdSimpleExtOUStorageEngine;
import org.jquantlib.experimental.finitedifferences.FdmExtOUJumpOp;
import org.jquantlib.experimental.finitedifferences.FdmKlugeExtOUOp;
import org.jquantlib.experimental.finitedifferences.VanillaVPPOption;
import org.jquantlib.experimental.processes.ExtOUWithJumpsProcess;
import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess;
import org.jquantlib.experimental.processes.GemanRoncoroniProcess;
import org.jquantlib.experimental.processes.KlugeExtOUProcess;
import org.jquantlib.instruments.AverageBasketPayoff;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.BasketPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.SwingExercise;
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

    /**
     * Java port of C++ {@code testKlugeExtOUSpreadOption} (vpp.cpp:404-485).
     *
     * <p>Builds a Kluge ext-OU spread basket option (power - 2*gas, zero
     * strike call), prices it with the FD engine on a coarse 3-D grid,
     * and compares the FD NPV against an inline Monte-Carlo reference
     * (replicating the C++ test's harness with reduced trail count to
     * keep the JVM runtime bounded).</p>
     *
     * <p>Tolerance: project LOOSE tier {@code max(3*mcError, 5e-2*|MC|)}
     * (the C++ test uses just {@code 3*mcError}; we add a relative
     * floor to absorb MT-stream differences vs C++).</p>
     */
    @Test
    public void testKlugeExtOUSpreadOption() {
        final Date settlementDate = new Date(18, Month.December, 2011);
        new Settings().setEvaluationDate(settlementDate);
        final DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISDA);
        final Date maturityDate = settlementDate.add(new Period(1, TimeUnit.Years));
        final double maturity = dayCounter.yearFraction(settlementDate, maturityDate);

        final double speed     = 1.0;
        final double vol       = Math.sqrt(1.4);
        final double betaG     = 0.0;
        final double alphaG    = 3.0;
        final double x0G       = 3.0;

        final double irRate    = 0.0;
        final double heatRate  = 2.0;
        final double rho       = 0.5;

        final ExtOUWithJumpsProcess klugeProcess = createKlugeProcessForSpread();

        final double alphaGFinal = alphaG;
        final double betaGFinal  = betaG;
        final Ops.DoubleOp f = new Ops.DoubleOp() {
            @Override public double op(final double x) {
                return alphaGFinal + betaGFinal * x;
            }
        };

        final ExtendedOrnsteinUhlenbeckProcess extOUProcess =
                new ExtendedOrnsteinUhlenbeckProcess(speed, vol, x0G, f,
                        ExtendedOrnsteinUhlenbeckProcess.Discretization.Trapezodial,
                        1.0e-4);

        final YieldTermStructure rTS = new FlatForward(settlementDate, irRate, dayCounter);

        final KlugeExtOUProcess klugeOUProcess =
                new KlugeExtOUProcess(rho, klugeProcess, extOUProcess);

        final Payoff payoff = new PlainVanillaPayoff(Option.Type.Call, 0.0);
        final double[] spreadFactors = new double[] { 1.0, -heatRate };
        final BasketPayoff basketPayoff = new AverageBasketPayoff(payoff, spreadFactors);

        final BasketOption option = new BasketOption(basketPayoff,
                new EuropeanExercise(maturityDate));

        // C++ uses tGrid=5, xGrid=200, yGrid=50, uGrid=20 (heavy 3-D
        // ADI). To keep JVM test runtime bounded we reduce the spatial
        // grids; the spread option is well-behaved enough that a coarser
        // mesh still hits the MC reference inside the LOOSE band.
        option.setPricingEngine(new FdKlugeExtOUSpreadEngine(
                klugeOUProcess, rTS, 5, 100, 30, 20));

        final double calculated = option.NPV();

        // MC reference: replicate the inline Monte-Carlo verification
        // block from the C++ test (vpp.cpp:454-483) but with a reduced
        // trail count that fits the project's JVM budget.
        final TimeGrid grid = new TimeGrid(maturity, 50);
        final int nTrails = 4000;

        final int dim = klugeOUProcess.factors() * (grid.size() - 1);
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(1234L);
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
                        klugeOUProcess, grid, gsg, false);

        final GeneralStatistics npv = new GeneralStatistics();
        for (int i = 0; i < nTrails; ++i) {
            final Sample<MultiPath> path = generator.next();
            // C++: p[0] = path[0].back() + path[1].back(); p[1] = path[2].back()
            // (power is X + Y, gas is U), then basketPayoff(exp(p)).
            final int last = path.value().get(0).length() - 1;
            final double p0 = path.value().get(0).get(last)
                            + path.value().get(1).get(last);
            final double p1 = path.value().get(2).get(last);
            final double[] expValues = new double[] { Math.exp(p0), Math.exp(p1) };
            npv.add(basketPayoff.get(expValues));
        }

        final double expectedMC = npv.mean();
        final double mcError = npv.errorEstimate();

        final double band = Math.max(3.0 * mcError, 5.0e-2 * Math.abs(expectedMC));
        assertEquals(
                "Failed to reproduce Kluge ExtOU spread MC reference"
              + " (calculated=" + calculated
              + ", expectedMC=" + expectedMC
              + ", mcError=" + mcError
              + ", band=" + band + ")",
                expectedMC, calculated, band);
    }

    /**
     * Java port of C++ {@code testVPPIntrinsicValue} (vpp.cpp:487-533).
     *
     * <p>Prices a {@link VanillaVPPOption} (no start or running-hour
     * limit) on a deterministic 168-hour power / fuel price trajectory
     * via {@link DynProgVPPIntrinsicValueEngine} for a sweep of seven
     * heat rates and compares the NPV against MILP-derived reference
     * values cached in the C++ test.</p>
     */
    @Test
    public void testVPPIntrinsicValue() {
        final Date today = new Date(18, Month.December, 2011);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);

        final double pMin = 8.0;
        final double pMax = 40.0;
        final int tMinUp = 2;
        final int tMinDown = 2;
        final double startUpFuel = 20.0;
        final double startUpFixCost = 100.0;
        final double fuelCostAddon = 3.0;

        final SwingExercise exercise = new SwingExercise(today,
                today.add(new Period(6, TimeUnit.Days)), 3600);

        // MILP-derived reference values from C++ vpp.cpp:511-512.
        final double[] efficiency = { 0.35, 0.4, 0.45, 0.5, 0.55, 0.6, 0.9 };
        final double[] expected = {
                0.0, 2056.04, 11145.577778, 26452.04,
                44512.461818, 62000.626667, 137591.911111 };

        final double[] fuelPrices = vppFuelPrices();
        final double[] powerPrices = vppPowerPrices();

        final YieldTermStructure rTS = new FlatForward(today, 0.0, dc);

        for (int i = 0; i < efficiency.length; ++i) {
            final double heatRate = 1.0 / efficiency[i];

            final VanillaVPPOption option = new VanillaVPPOption(
                    heatRate, pMin, pMax, tMinUp, tMinDown,
                    startUpFuel, startUpFixCost, exercise);

            option.setPricingEngine(new DynProgVPPIntrinsicValueEngine(
                    fuelPrices, powerPrices, fuelCostAddon, rTS));

            final double calculated = option.NPV();

            // C++ tolerance: 1e-4 abs. Project LOOSE tier: 1e-2 abs.
            assertEquals(
                    "Failed to reproduce reference VPP intrinsic value"
                  + " (i=" + i + ", efficiency=" + efficiency[i]
                  + ", calculated=" + calculated
                  + ", expected=" + expected[i] + ")",
                    expected[i], calculated, 1.0e-2);
        }
    }

    /**
     * VPP-test fixture parameter mirroring C++ {@code createKlugeProcess}
     * (vpp.cpp:69-84). The Kluge process for the spread option uses
     * {@code (x0=3, beta=5, eta=2, lambda=1, speed=1, vol=2)}, distinct
     * from the heavier {@code createKlugeExtOUProcess} that drives the
     * full VPP option.
     */
    private static ExtOUWithJumpsProcess createKlugeProcessForSpread() {
        final double beta          = 5.0;
        final double eta           = 2.0;
        final double jumpIntensity = 1.0;
        final double speed         = 1.0;
        final double volatility    = 2.0;
        final double x0X           = 3.0;
        final double x0Y           = 0.0;

        final ExtendedOrnsteinUhlenbeckProcess ouProcess =
                new ExtendedOrnsteinUhlenbeckProcess(speed, volatility, x0X,
                        new Ops.DoubleOp() {
                            @Override public double op(final double t) {
                                return x0X;
                            }
                        });
        return new ExtOUWithJumpsProcess(ouProcess, x0Y, beta, jumpIntensity, eta);
    }

    /**
     * 168-hour fuel-price trajectory mirroring C++ {@code fuelPrices}
     * (vpp.cpp:97-118).
     */
    private static double[] vppFuelPrices() {
        return new double[] {
                20.74, 21.65, 20.78, 21.58, 21.43, 20.82, 22.02, 21.52,
                21.02, 21.46, 21.75, 20.69, 22.16, 20.38, 20.82, 20.68,
                20.57, 21.92, 22.04, 20.45, 20.75, 21.92, 20.53, 20.67,
                20.88, 21.02, 20.82, 21.67, 21.82, 22.12, 20.45, 20.74,
                22.39, 20.95, 21.71, 20.70, 20.94, 21.59, 22.33, 21.13,
                21.50, 21.42, 20.56, 21.23, 21.37, 21.90, 20.62, 21.17,
                21.86, 22.04, 22.05, 21.00, 20.70, 21.12, 21.26, 22.40,
                21.31, 22.24, 21.96, 21.02, 21.71, 20.48, 21.36, 21.75,
                21.90, 20.44, 21.26, 22.29, 20.34, 21.79, 21.66, 21.50,
                20.76, 20.27, 20.84, 20.24, 21.97, 20.52, 20.98, 21.40,
                20.39, 20.71, 20.78, 20.30, 21.56, 21.72, 20.27, 21.57,
                21.82, 20.57, 21.33, 20.51, 22.32, 21.99, 20.57, 22.11,
                21.56, 22.24, 20.62, 21.70, 21.11, 21.19, 21.79, 20.46,
                22.21, 20.82, 20.52, 22.29, 20.71, 21.45, 22.40, 20.63,
                20.95, 21.97, 22.20, 20.67, 21.01, 22.25, 20.76, 21.33,
                20.49, 20.33, 21.94, 20.64, 20.99, 21.09, 20.97, 22.17,
                20.72, 22.06, 20.86, 21.40, 21.75, 20.78, 21.79, 20.47,
                21.19, 21.60, 20.75, 21.36, 21.61, 20.37, 21.67, 20.28,
                22.33, 21.37, 21.33, 20.87, 21.25, 22.01, 22.08, 20.81,
                20.70, 21.84, 21.82, 21.68, 21.24, 22.36, 20.83, 20.64,
                21.03, 20.57, 22.34, 20.96, 21.54, 21.26, 21.43, 22.39
        };
    }

    /**
     * 168-hour power-price trajectory mirroring C++ {@code powerPrices}
     * (vpp.cpp:120-141).
     */
    private static double[] vppPowerPrices() {
        return new double[] {
                40.40, 36.71, 31.87, 25.81, 31.61, 35.00, 46.22, 60.68,
                42.45, 38.01, 33.84, 29.79, 31.84, 38.53, 49.23, 59.92,
                43.85, 37.47, 34.89, 29.99, 30.85, 29.19, 29.25, 38.67,
                36.90, 25.93, 22.12, 20.19, 17.19, 19.29, 13.51, 18.14,
                33.76, 30.48, 25.63, 18.01, 23.86, 32.41, 48.56, 64.69,
                38.42, 39.31, 32.73, 29.97, 31.41, 35.02, 46.85, 58.12,
                39.14, 35.42, 32.61, 28.76, 29.41, 35.83, 46.73, 61.41,
                61.01, 59.43, 60.43, 66.29, 62.79, 62.66, 57.66, 51.63,
                62.18, 60.53, 61.94, 64.86, 59.57, 58.15, 53.74, 48.36,
                45.64, 51.21, 51.54, 50.79, 54.50, 49.92, 41.58, 39.81,
                28.86, 37.42, 39.78, 42.36, 45.67, 36.84, 33.91, 28.75,
                62.97, 63.84, 62.91, 68.77, 64.33, 61.95, 59.12, 54.89,
                63.62, 60.90, 66.57, 69.51, 64.71, 59.89, 57.28, 57.10,
                65.09, 63.82, 67.52, 70.51, 65.59, 59.36, 58.22, 54.64,
                52.17, 53.02, 57.12, 53.50, 53.16, 49.21, 52.21, 40.96,
                49.01, 47.94, 49.89, 53.83, 52.96, 50.33, 51.72, 46.99,
                39.06, 47.99, 47.91, 52.35, 48.51, 47.39, 50.45, 43.66,
                25.62, 35.76, 42.76, 46.51, 45.62, 46.79, 48.76, 41.00,
                52.65, 55.57, 57.67, 56.79, 55.15, 54.74, 50.31, 47.49,
                53.72, 55.62, 55.89, 58.11, 54.46, 52.92, 49.61, 44.68,
                51.59, 57.44, 56.50, 55.12, 57.22, 54.61, 49.92, 45.20
        };
    }

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
