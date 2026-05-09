/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

package org.jquantlib.testsuite.model.marketmodels.models;

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.models.CTSMMCapletMaxHomogeneityCalibration;
import org.jquantlib.model.marketmodels.models.PiecewiseConstantAbcdVariance;
import org.jquantlib.model.marketmodels.models.PiecewiseConstantVariance;
import org.junit.Test;

/**
 * Smoke tests for {@link CTSMMCapletMaxHomogeneityCalibration} — Phase 3j B.6.
 */
public class CTSMMCapletMaxHomogeneityCalibrationTest {

    public CTSMMCapletMaxHomogeneityCalibrationTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
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

    /** Smoke: instantiate. */
    @Test
    public void testConstruct() {
        final double[] rateTimes = {1.0, 2.0, 3.0, 4.0};
        final EvolutionDescription ev = new EvolutionDescription(rateTimes);
        final PiecewiseConstantCorrelation corr = constCorr(3, ev.evolutionTimes(), 0.7);

        final List<PiecewiseConstantVariance> vars = new ArrayList<>();
        for (int i = 0; i < 3; ++i) {
            vars.add(new PiecewiseConstantAbcdVariance(0.05, 0.1, 0.5, 0.05, i, rateTimes));
        }
        final double lastSwaptionVol = vars.get(2).totalVolatility(2);
        final double[] mktCapletVols = {0.20, 0.18, lastSwaptionVol};

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.04, 0.04, 0.04});

        final CTSMMCapletMaxHomogeneityCalibration calib =
                new CTSMMCapletMaxHomogeneityCalibration(ev, corr, vars, mktCapletVols, cs, 0.0, 0.5);
        assertNotNull(calib);
    }
}
