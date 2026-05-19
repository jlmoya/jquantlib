/*
 Copyright (C) 2015 Johannes Goettker-Schnetmann
 Copyright (C) 2015 Klaus Spanderen

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
package org.jquantlib.experimental.models;

import org.jquantlib.methods.finitedifferences.operators.FdmSquareRootFwdOp.TransformationType;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;

/**
 * Calibration parameters for {@link HestonSLVFDMModel}.
 * <p>
 * Java port of the {@code HestonSLVFokkerPlanckFdmParams} struct in v1.42.1
 * {@code ql/models/equity/hestonslvfdmmodel.hpp}.
 *
 * @author Phase 5h.5-SLV port
 */
public final class HestonSLVFokkerPlanckFdmParams {

    public final int xGrid, vGrid;
    public final int tMaxStepsPerYear, tMinStepsPerYear;
    public final double tStepNumberDecay;

    /** Rannacher smoothing steps at the beginning. */
    public final int nRannacherTimeSteps;

    public final int predictionCorretionSteps;

    /** Local volatility forward equation parameters. */
    public final double x0Density;
    public final double localVolEpsProb;
    public final int maxIntegrationIterations;

    /** Variance mesher definition. */
    public final double vLowerEps, vUpperEps, vMin;
    public final double v0Density, vLowerBoundDensity, vUpperBoundDensity;

    /** Do not calculate leverage function if prob is smaller than eps. */
    public final double leverageFctPropEps;

    /** Algorithm to get to the start configuration at time point one. */
    public final GreensFctAlgorithm greensAlgorithm;
    public final TransformationType trafoType;

    /** Finite-difference scheme. */
    public final FdmSchemeDesc schemeDesc;

    public HestonSLVFokkerPlanckFdmParams(final int xGrid, final int vGrid, final int tMaxStepsPerYear,
            final int tMinStepsPerYear, final double tStepNumberDecay, final int nRannacherTimeSteps,
            final int predictionCorretionSteps, final double x0Density, final double localVolEpsProb,
            final int maxIntegrationIterations, final double vLowerEps, final double vUpperEps, final double vMin,
            final double v0Density, final double vLowerBoundDensity, final double vUpperBoundDensity,
            final double leverageFctPropEps, final GreensFctAlgorithm greensAlgorithm,
            final TransformationType trafoType, final FdmSchemeDesc schemeDesc) {
        this.xGrid = xGrid;
        this.vGrid = vGrid;
        this.tMaxStepsPerYear = tMaxStepsPerYear;
        this.tMinStepsPerYear = tMinStepsPerYear;
        this.tStepNumberDecay = tStepNumberDecay;
        this.nRannacherTimeSteps = nRannacherTimeSteps;
        this.predictionCorretionSteps = predictionCorretionSteps;
        this.x0Density = x0Density;
        this.localVolEpsProb = localVolEpsProb;
        this.maxIntegrationIterations = maxIntegrationIterations;
        this.vLowerEps = vLowerEps;
        this.vUpperEps = vUpperEps;
        this.vMin = vMin;
        this.v0Density = v0Density;
        this.vLowerBoundDensity = vLowerBoundDensity;
        this.vUpperBoundDensity = vUpperBoundDensity;
        this.leverageFctPropEps = leverageFctPropEps;
        this.greensAlgorithm = greensAlgorithm;
        this.trafoType = trafoType;
        this.schemeDesc = schemeDesc;
    }

    /** Mirrors C++ {@code FdmHestonGreensFct::Algorithm} enum. */
    public enum GreensFctAlgorithm {
        ZeroCorrelation, Gaussian, SemiAnalytical
    }
}
