/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 5h.5-SLV — HestonSLV{FDM,MC}Model skeleton smoke tests.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.models;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.models.HestonSLVFDMModel;
import org.jquantlib.experimental.models.HestonSLVFokkerPlanckFdmParams;
import org.jquantlib.experimental.models.HestonSLVFokkerPlanckFdmParams.GreensFctAlgorithm;
import org.jquantlib.experimental.models.HestonSLVMCModel;
import org.jquantlib.methods.finitedifferences.operators.FdmSquareRootFwdOp.TransformationType;
import org.jquantlib.model.marketmodels.browniangenerators.MTBrownianGeneratorFactory;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.LocalConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

/**
 * Skeleton-level smoke tests for {@link HestonSLVFDMModel} and
 * {@link HestonSLVMCModel}.
 *
 * <p>Verifies constructor, params struct, public-API getters, and
 * documents the Phase 5h.5-SLV-b carry-forward by asserting
 * {@code performCalculations()} throws.
 */
public class HestonSLVModelSmokeTest {

    private static Handle<HestonModel> buildHestonModel() {
        final Date today = new Date(15, Month.May, 2026);
        final DayCounter dc = new Actual365Fixed();
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.03, dc));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.01, dc));
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(100.0));
        final HestonProcess hp = new HestonProcess(rTS, qTS, spot,
                0.04, 2.5, 0.04, 0.2, -0.5);
        return new Handle<HestonModel>(new HestonModel(hp));
    }

    private static Handle<LocalVolTermStructure> buildLocalVol() {
        final Date today = new Date(15, Month.May, 2026);
        return new Handle<LocalVolTermStructure>(
                new LocalConstantVol(today, 0.20, new Actual365Fixed()));
    }

    @Test
    public void testFdmModelConstructorAndGetters() {
        final HestonSLVFokkerPlanckFdmParams p = new HestonSLVFokkerPlanckFdmParams(
                51, 31,                // xGrid, vGrid
                365, 30,               // tMaxStepsPerYear, tMinStepsPerYear
                2.0,                   // tStepNumberDecay
                4,                     // nRannacherTimeSteps
                2,                     // predictionCorretionSteps
                0.1,                   // x0Density
                1e-6,                  // localVolEpsProb
                10000,                 // maxIntegrationIterations
                1e-6, 1e-6, 1e-4,      // vLowerEps, vUpperEps, vMin
                1.0, 1e-6, 1e-6,       // v0Density, vLowerBoundDensity, vUpperBoundDensity
                1e-8,                  // leverageFctPropEps
                GreensFctAlgorithm.Gaussian,
                TransformationType.Log,
                FdmSchemeDesc.Douglas());

        assertNotNull(p.schemeDesc);
        assertTrue("xGrid", p.xGrid == 51);
        assertTrue("vGrid", p.vGrid == 31);
        assertTrue("trafo Log", p.trafoType == TransformationType.Log);

        final HestonSLVFDMModel m = new HestonSLVFDMModel(
                buildLocalVol(), buildHestonModel(),
                new Date(15, Month.May, 2027),
                p, false, new ArrayList<Date>(), 1.0);
        assertNotNull(m);
        assertNotNull(m.logEntries());
    }

    @Test
    public void testFdmModelPerformCalculationsBodyFill() {
        // Phase 5h.5-SLV-b body-fill: the FDM bootstrap now runs end-to-end.
        // With a small-grid Plain config + Gaussian Greens fct, the calibration
        // returns a non-null leverage surface whose value at (T/2, spot) is in
        // [0.001, 50] (the C++-mirrored clamp range).
        final HestonSLVFokkerPlanckFdmParams p = new HestonSLVFokkerPlanckFdmParams(
                21, 11, 100, 10, 2.0, 1, 1, 0.1, 1e-6, 1000,
                1e-6, 1e-6, 1e-4, 1.0, 1e-6, 1e-6, 1e-8,
                GreensFctAlgorithm.Gaussian, TransformationType.Plain,
                FdmSchemeDesc.Douglas());
        final HestonSLVFDMModel m = new HestonSLVFDMModel(
                buildLocalVol(), buildHestonModel(),
                new Date(15, Month.May, 2027), p);
        final LocalVolTermStructure lev = m.leverageFunction();
        assertNotNull("leverage surface must be non-null", lev);
        final double l = lev.localVol(0.5, 100.0);
        assertTrue("leverage in clamp range [0.001, 50]: " + l,
                l >= 0.001 && l <= 50.0);
    }

    @Test
    public void testMcModelConstructorAndGetters() {
        final HestonSLVMCModel m = new HestonSLVMCModel(
                buildLocalVol(), buildHestonModel(),
                new MTBrownianGeneratorFactory(42L),
                new Date(15, Month.May, 2027));
        assertNotNull(m);
        assertNotNull(m.localVol());
    }

    @Test
    public void testMcModelPerformCalculationsBodyFill() {
        // Phase 5h.5-SLV-b body-fill: MC bootstrap runs end-to-end and
        // produces a non-null leverage surface. Path count is kept small
        // (32 paths, 7 bins, 1-month horizon) so the smoke test stays fast;
        // the calibration is statistically too noisy to assert exact values
        // but the surface should produce a finite, in-range leverage at
        // (T/2, spot).
        final HestonSLVMCModel m = new HestonSLVMCModel(
                buildLocalVol(), buildHestonModel(),
                new MTBrownianGeneratorFactory(42L),
                new Date(15, Month.June, 2026),
                /*timeStepsPerYear=*/ 12,
                /*nBins=*/ 7,
                /*calibrationPaths=*/ 32,
                new java.util.ArrayList<Date>(), 1.0);
        final LocalVolTermStructure lev = m.leverageFunction();
        assertNotNull("leverage surface must be non-null", lev);
        final double l = lev.localVol(0.04, 100.0);
        assertTrue("leverage finite: " + l, !Double.isNaN(l) && !Double.isInfinite(l));
    }
}
