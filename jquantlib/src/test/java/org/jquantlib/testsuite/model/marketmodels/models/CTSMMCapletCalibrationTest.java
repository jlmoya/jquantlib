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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.junit.Test;

/**
 * Smoke tests for {@link CTSMMCapletCalibration} — Phase 3j B.3.
 */
public class CTSMMCapletCalibrationTest {

    public CTSMMCapletCalibrationTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Trivial subclass producing identity pseudo-roots, used only to verify
     * that the abstract base wires up correctly.
     */
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
            // Build identity-style pseudoroots based on input variances
            final int n = numberOfRates_;
            this.swapCovariancePseudoRoots_ = new ArrayList<>();
            for (int k = 0; k < n; ++k) {
                final Matrix m = new Matrix(n, numberOfFactors);
                for (int i = 0; i < n; ++i) {
                    for (int j = 0; j < numberOfFactors; ++j) m.set(i, j, 0.0);
                    final double vol = Math.sqrt(displacedSwapVariances_.get(i).variances()[k]
                            / Math.max(1, numberOfFactors));
                    for (int j = 0; j < numberOfFactors; ++j) m.set(i, j, vol);
                }
                this.swapCovariancePseudoRoots_.add(m);
            }
            return 0;
        }
    }

    /** Constant-correlation helper (off-diagonal correlation = rho) for tests. */
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

    /** performChecks should accept a well-formed setup. */
    @Test
    public void testPerformChecksAccepts() {
        final double[] rateTimes = {1.0, 2.0, 3.0};
        final EvolutionDescription ev = new EvolutionDescription(rateTimes);
        final PiecewiseConstantCorrelation corr = constCorr(2, ev.evolutionTimes(), 0.7);

        // Build PiecewiseConstantAbcdVariance for each rate; resetIndex i means
        // variance 0..i populated.
        final List<PiecewiseConstantVariance> vars = new ArrayList<>();
        for (int i = 0; i < 2; ++i) {
            vars.add(new PiecewiseConstantAbcdVariance(0.05, 0.1, 0.5, 0.05, i, rateTimes));
        }

        // mktCapletVols: last must equal last swaption vol; force consistency
        final double lastSwaptionVol = vars.get(1).totalVolatility(1);
        final double[] mktCapletVols = {0.20, lastSwaptionVol};

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        final double[] forwards = {0.04, 0.04};
        cs.setOnForwardRates(forwards);

        // Should not throw
        CTSMMCapletCalibration.performChecks(ev, corr, vars, mktCapletVols, cs);
    }

    /** performChecks should reject mismatched mktCapletVols length. */
    @Test
    public void testPerformChecksRejects() {
        final double[] rateTimes = {1.0, 2.0, 3.0};
        final EvolutionDescription ev = new EvolutionDescription(rateTimes);
        final PiecewiseConstantCorrelation corr = constCorr(2, ev.evolutionTimes(), 0.7);
        final List<PiecewiseConstantVariance> vars = new ArrayList<>();
        for (int i = 0; i < 2; ++i) {
            vars.add(new PiecewiseConstantAbcdVariance(0.05, 0.1, 0.5, 0.05, i, rateTimes));
        }
        final double[] mktCapletVolsWrong = {0.20};  // wrong length
        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.04, 0.04});

        try {
            CTSMMCapletCalibration.performChecks(ev, corr, vars, mktCapletVolsWrong, cs);
            fail("performChecks should reject mismatched length");
        } catch (final RuntimeException ok) {
            // expected
        }
    }

    /**
     * Constructor smoke: verify the abstract base instantiates and pre-calibration
     * inspectors throw appropriately.
     */
    @Test
    public void testConstructAndPreCalibration() {
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
        cs.setOnForwardRates(new double[]{0.04, 0.04});

        final TrivialCalibration calib = new TrivialCalibration(ev, corr, vars, mktCapletVols, cs, 0.0);
        // mktCapletVols accessible without calibration
        assertEquals(2, calib.mktCapletVols().length);
        // Pre-calibration access to mdlCapletVols should throw
        try {
            calib.mdlCapletVols();
            fail("should throw before calibration");
        } catch (final RuntimeException ok) {
            // expected
        }
        assertEquals(2, calib.curveState().numberOfRates());
        assertTrue(Arrays.equals(new double[]{0.0, 0.0}, calib.displacements()));
    }
}
