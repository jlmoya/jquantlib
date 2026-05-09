/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

package org.jquantlib.testsuite.model.marketmodels.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.models.CTSMMCapletCalibration;
import org.jquantlib.model.marketmodels.models.PiecewiseConstantAbcdVariance;
import org.jquantlib.model.marketmodels.models.PiecewiseConstantVariance;
import org.jquantlib.model.marketmodels.models.PseudoRootFacade;
import org.junit.Test;

/**
 * Smoke tests for {@link PseudoRootFacade} — Phase 3j A.6.
 *
 * <p>The raw-matrix constructor was forward-declared by Track B (commit
 * {@code 9d07ca8}); the {@link CTSMMCapletCalibration}-based constructor lands
 * here as A.6b.
 */
public class PseudoRootFacadeTest {

    public PseudoRootFacadeTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** Trivial subclass of CTSMMCapletCalibration producing identity-style pseudoroots. */
    private static final class TrivialCalibration extends CTSMMCapletCalibration {
        TrivialCalibration(final EvolutionDescription evolution,
                           final PiecewiseConstantCorrelation corr,
                           final List<PiecewiseConstantVariance> variances,
                           final double[] mktCapletVols,
                           final CurveState cs,
                           final double displacement) {
            super(evolution, corr, variances, mktCapletVols, cs, displacement);
        }
        @Override
        protected int calibrationImpl(final int numberOfFactors,
                                      final int innerMaxIterations,
                                      final double innerTolerance) {
            final int n = numberOfRates_;
            this.swapCovariancePseudoRoots_ = new ArrayList<>();
            for (int k = 0; k < n; ++k) {
                final Matrix m = new Matrix(n, numberOfFactors);
                for (int i = 0; i < n; ++i) {
                    final double vol = Math.sqrt(displacedSwapVariances_.get(i).variances()[k]
                            / Math.max(1, numberOfFactors));
                    for (int j = 0; j < numberOfFactors; ++j) m.set(i, j, vol);
                }
                this.swapCovariancePseudoRoots_.add(m);
            }
            return 0;
        }
    }

    private static PiecewiseConstantCorrelation constCorr(final int numRates,
                                                           final double[] times,
                                                           final double rho) {
        final Matrix m = new Matrix(numRates, numRates);
        for (int i = 0; i < numRates; ++i) {
            for (int j = 0; j < numRates; ++j) {
                m.set(i, j, i == j ? 1.0 : rho);
            }
        }
        return new PiecewiseConstantCorrelation() {
            @Override public List<Double> times() {
                final List<Double> t = new ArrayList<>();
                for (final double v : times) t.add(v);
                return t;
            }
            @Override public List<Double> rateTimes() { return times(); }
            @Override public List<Matrix> correlations() {
                final List<Matrix> out = new ArrayList<>();
                for (int k = 0; k < times.length; ++k) out.add(m);
                return out;
            }
            @Override public int numberOfRates() { return numRates; }
        };
    }

    /**
     * Raw-matrix constructor: builds 2-rate facade from 2 hand-crafted pseudo-roots.
     */
    @Test
    public void testRawMatrixConstructor() {
        final double[] rateTimes = {1.0, 2.0, 3.0};
        final List<Matrix> roots = new ArrayList<>();
        for (int k = 0; k < 2; ++k) {
            final Matrix m = new Matrix(2, 2);
            m.set(0, 0, 0.10); m.set(0, 1, 0.0);
            m.set(1, 0, 0.0);  m.set(1, 1, 0.10);
            roots.add(m);
        }
        final double[] initialRates = {0.04, 0.04};
        final double[] displacements = {0.0, 0.0};
        final PseudoRootFacade f = new PseudoRootFacade(roots, rateTimes, initialRates, displacements);

        assertEquals(2, f.numberOfRates());
        assertEquals(2, f.numberOfFactors());
        assertEquals(2, f.numberOfSteps());
        assertEquals(0.10, f.pseudoRoot(0).get(0, 0), 0.0);
        assertEquals(0.10, f.pseudoRoot(1).get(1, 1), 0.0);
    }

    /**
     * Calibrator constructor (A.6b): build PseudoRootFacade from a calibrated
     * CTSMMCapletCalibration; verify that pseudoRoot(i), numberOfRates, factors,
     * and steps mirror the calibrator's outputs.
     */
    @Test
    public void testCalibratorConstructor() {
        final double[] rateTimes = {1.0, 2.0, 3.0};
        final EvolutionDescription ev = new EvolutionDescription(rateTimes);
        final PiecewiseConstantCorrelation corr = constCorr(2, ev.evolutionTimes(), 0.5);
        final List<PiecewiseConstantVariance> vars = new ArrayList<>();
        for (int i = 0; i < 2; ++i) {
            vars.add(new PiecewiseConstantAbcdVariance(0.05, 0.1, 0.5, 0.05, i, rateTimes));
        }
        final double lastSwaptionVol = vars.get(1).totalVolatility(1);
        final double[] mktCapletVols = {0.20, lastSwaptionVol};

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[] {0.04, 0.04});

        final TrivialCalibration calib = new TrivialCalibration(
                ev, corr, vars, mktCapletVols, cs, 0.0);
        final boolean ok = calib.calibrate(2, 100, 1e-6, 100, 1e-6);
        org.junit.Assert.assertTrue("calibration must succeed", ok);

        final PseudoRootFacade facade = new PseudoRootFacade(calib);

        assertEquals("nRates", calib.swapPseudoRoot(0).rows(), facade.numberOfRates());
        assertEquals("nFactors", calib.swapPseudoRoot(0).columns(), facade.numberOfFactors());
        assertEquals("nSteps", calib.swapPseudoRoots().size(), facade.numberOfSteps());

        // pseudo-roots should be the same matrices
        for (int k = 0; k < facade.numberOfSteps(); ++k) {
            final Matrix orig = calib.swapPseudoRoot(k);
            final Matrix mirror = facade.pseudoRoot(k);
            for (int i = 0; i < orig.rows(); ++i) {
                for (int j = 0; j < orig.columns(); ++j) {
                    assertEquals("step " + k + " [" + i + "][" + j + "]",
                            orig.get(i, j), mirror.get(i, j), 0.0);
                }
            }
        }

        // initialRates should be coterminalSwapRates from the curve state
        assertNotNull(facade.initialRates());
        assertEquals(facade.numberOfRates(), facade.initialRates().length);
        // displacements should equal calibrator's [0.0, 0.0]
        assertEquals(0.0, facade.displacements()[0], 0.0);
    }
}
