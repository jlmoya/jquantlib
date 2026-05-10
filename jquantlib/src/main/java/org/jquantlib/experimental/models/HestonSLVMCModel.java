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

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.util.LazyObject;

/**
 * MC-based calibration of a Heston Stochastic-Local-Volatility model.
 * <p>
 * Java port of v1.42.1 {@code ql/models/equity/hestonslvmcmodel.{hpp,cpp}}
 * (Phase 5h.5-SLV WI-5 — <strong>SKELETON</strong>).
 * <p>
 * References:
 * <pre>
 *   Anthonie W. van der Stoep, Lech A. Grzelak, Cornelis W. Oosterlee, 2013,
 *   "The Heston Stochastic-Local Volatility Model: Efficient Monte Carlo Simulation"
 *   http://papers.ssrn.com/sol3/papers.cfm?abstract_id=2278122
 * </pre>
 * <p>
 * The full MC bootstrap requires the following classes that are not yet
 * in JQuantLib (Phase 5h.5-SLV-b carry-forwards):
 * <ul>
 *   <li>{@code BrownianGeneratorFactory} (market-models infra) —
 *       MF-Sobol prerequisite.</li>
 *   <li>{@code FixedLocalVolSurface} — output leverage container.</li>
 *   <li>Constant-extrapolation kernel for the per-time-step bin estimator.</li>
 * </ul>
 * Until those land this class only stores the calibration inputs and
 * exposes the public-API surface that the C++ class advertises.
 *
 * @author Phase 5h.5-SLV port
 */
public class HestonSLVMCModel extends LazyObject {

    private final Handle<LocalVolTermStructure> localVol;
    private final Handle<HestonModel> hestonModel;
    private final Object brownianGeneratorFactory; // BrownianGeneratorFactory placeholder
    private final Date endDate;
    private final int timeStepsPerYear;
    private final int nBins;
    private final int calibrationPaths;
    private final List<Date> mandatoryDates;
    private final double mixingFactor;

    private LocalVolTermStructure leverageFunction;

    public HestonSLVMCModel(final Handle<LocalVolTermStructure> localVol,
                            final Handle<HestonModel> hestonModel,
                            final Object brownianGeneratorFactory,
                            final Date endDate) {
        this(localVol, hestonModel, brownianGeneratorFactory, endDate,
             365, 201, 1 << 15, new ArrayList<Date>(), 1.0);
    }

    public HestonSLVMCModel(final Handle<LocalVolTermStructure> localVol,
                            final Handle<HestonModel> hestonModel,
                            final Object brownianGeneratorFactory,
                            final Date endDate,
                            final int timeStepsPerYear,
                            final int nBins,
                            final int calibrationPaths,
                            final List<Date> mandatoryDates,
                            final double mixingFactor) {
        this.localVol = localVol;
        this.hestonModel = hestonModel;
        this.brownianGeneratorFactory = brownianGeneratorFactory;
        this.endDate = endDate;
        this.timeStepsPerYear = timeStepsPerYear;
        this.nBins = nBins;
        this.calibrationPaths = calibrationPaths;
        this.mandatoryDates = (mandatoryDates != null)
                ? new ArrayList<Date>(mandatoryDates)
                : new ArrayList<Date>();
        this.mixingFactor = mixingFactor;

        if (!hestonModel.empty()) hestonModel.addObserver(this);
        if (!localVol.empty())    localVol   .addObserver(this);
    }

    /**
     * Returns the Heston-model {@link HestonProcess}. Mirrors C++
     * {@code hestonProcess()}.
     * <p>
     * Returns {@code null} if the underlying handle is empty — JQuantLib's
     * {@link HestonModel} does not yet expose its process. Phase 5h.5-SLV-b
     * carry-forward.
     */
    public HestonProcess hestonProcess() {
        if (hestonModel.empty()) return null;
        return null; // TODO: HestonModel.process() port (Phase 5h.5-SLV-b)
    }

    public LocalVolTermStructure localVol() {
        return localVol.empty() ? null : localVol.currentLink();
    }

    public LocalVolTermStructure leverageFunction() {
        calculate();
        return leverageFunction;
    }

    @Override
    protected void performCalculations() {
        // Phase 5h.5-SLV-b carry-forward: full MC bootstrap requires
        //   - BrownianGeneratorFactory (market-models infra)
        //   - FixedLocalVolSurface output container
        //   - per-step bin estimator with constant extrapolation
        //   - HestonStochasticLocalVolProcess.evolve in MC loop
        //   - bilinear interpolation across (t, x) grid with smoothing.
        throw new UnsupportedOperationException(
                "HestonSLVMCModel.performCalculations() — Phase 5h.5-SLV-b "
                + "carry-forward (needs BrownianGeneratorFactory, "
                + "FixedLocalVolSurface, bin estimator). endDate=" + endDate
                + ", paths=" + calibrationPaths + ", bins=" + nBins
                + ", stepsPerYear=" + timeStepsPerYear + ", mixing=" + mixingFactor
                + ", mandatory=" + mandatoryDates.size()
                + ", brownianFactory=" + brownianGeneratorFactory);
    }
}
