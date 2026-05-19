/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

/*
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels.models;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.Utilities;

import java.util.ArrayList;
import java.util.List;

/**
 * MarketModel facade over a list of pseudo-root matrices.
 *
 * <p>Java port of {@code ql/models/marketmodels/models/pseudorootfacade.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Phase 3j Track B forward-declared: the raw-matrix constructor is fully
 * ported here so that {@link CTSMMCapletCalibration#calibrate} can use it. Track A may extend with the
 * {@link CTSMMCapletCalibration}-based constructor (lands as A.6b in Phase 3j Track A).
 */
public class PseudoRootFacade extends MarketModel {

    private final int numberOfFactors_;
    private final int numberOfRates_;
    private final int numberOfSteps_;
    private final double[] initialRates_;
    private final double[] displacements_;
    private final EvolutionDescription evolution_;
    private final List< Matrix > covariancePseudoRoots_;

    /**
     * Calibrator constructor (Phase 3j A.6b): builds a PseudoRootFacade from a completed
     * {@link CTSMMCapletCalibration}. Extracts the calibrator's swap pseudo-roots, coterminal swap rates, and
     * displacements as the facade's per-step volatility structure.
     *
     * <p>Mirrors {@code PseudoRootFacade(const ext::shared_ptr<CTSMMCapletCalibration>&)}
     * in {@code ql/models/marketmodels/models/pseudorootfacade.cpp:28-33} (QuantLib v1.42.1).
     *
     * @param calibrator a successfully-calibrated {@link CTSMMCapletCalibration}
     */
    public PseudoRootFacade(final CTSMMCapletCalibration calibrator) {
        final List< Matrix > roots = calibrator.swapPseudoRoots();
        this.covariancePseudoRoots_ = new ArrayList<>(roots);
        this.numberOfFactors_ = roots.get(0).columns();
        this.numberOfRates_ = roots.get(0).rows();
        this.numberOfSteps_ = roots.size();
        this.initialRates_ = calibrator.curveState().coterminalSwapRates();
        this.displacements_ = calibrator.displacements();
        this.evolution_ = new EvolutionDescription(calibrator.curveState().rateTimes());
    }

    /**
     * Raw-matrix constructor.
     *
     * @param covariancePseudoRoots one matrix per evolution step
     * @param rateTimes             rate-time grid
     * @param initialRates          initial rates
     * @param displacements         per-rate displacements
     */
    public PseudoRootFacade(final List< Matrix > covariancePseudoRoots, final double[] rateTimes,
            final double[] initialRates, final double[] displacements) {
        Utilities.checkIncreasingTimes(rateTimes);
        QL.require(rateTimes.length > 1, "Rate times must contain at least two values");

        this.covariancePseudoRoots_ = new ArrayList<>(covariancePseudoRoots);
        this.numberOfFactors_ = covariancePseudoRoots.get(0).columns();
        this.numberOfRates_ = covariancePseudoRoots.get(0).rows();
        this.numberOfSteps_ = covariancePseudoRoots.size();
        this.initialRates_ = initialRates.clone();
        this.displacements_ = displacements.clone();
        this.evolution_ = new EvolutionDescription(rateTimes);

        QL.require(numberOfRates_ == rateTimes.length - 1,
                "mismatch between number of rates (" + numberOfRates_ + ") and rate times");
        QL.require(numberOfRates_ == displacements.length,
                "mismatch between number of rates (" + numberOfRates_ + ") and displacements (" + displacements.length
                        + ")");
        QL.require(numberOfRates_ <= numberOfFactors_ * numberOfSteps_,
                "number of rates (" + numberOfRates_ + ") greater than number of factors (" + numberOfFactors_
                        + ") times number of steps (" + numberOfSteps_ + ")");
        QL.require(numberOfRates_ == covariancePseudoRoots.size(),
                "number of rates (" + numberOfRates_ + ") must equal covariancePseudoRoots size ("
                        + covariancePseudoRoots.size() + ")");

        for ( int k = 0; k < numberOfSteps_; ++k ) {
            final Matrix m = covariancePseudoRoots_.get(k);
            QL.require(m.rows() == numberOfRates_,
                    "step " + k + ": pseudoRoot has wrong number of rows: " + m.rows() + " instead of "
                            + numberOfRates_);
            QL.require(m.columns() == numberOfFactors_,
                    "step " + k + ": pseudoRoot has wrong number of columns: " + m.columns() + " instead of "
                            + numberOfFactors_);
        }
    }

    @Override
    public double[] initialRates() {
        return initialRates_;
    }

    @Override
    public double[] displacements() {
        return displacements_;
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
    }

    @Override
    public int numberOfRates() {
        return numberOfRates_;
    }

    @Override
    public int numberOfFactors() {
        return numberOfFactors_;
    }

    @Override
    public int numberOfSteps() {
        return numberOfSteps_;
    }

    @Override
    public Matrix pseudoRoot(final int i) {
        QL.require(i < numberOfSteps_,
                "the index " + i + " is invalid: it must be less than number of steps (" + numberOfSteps_ + ")");
        return covariancePseudoRoots_.get(i);
    }
}
