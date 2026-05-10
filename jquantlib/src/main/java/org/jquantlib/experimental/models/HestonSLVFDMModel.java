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
import java.util.Collections;
import java.util.List;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.util.LazyObject;

/**
 * FDM-based calibration of a Heston Stochastic-Local-Volatility model.
 * <p>
 * Java port of v1.42.1 {@code ql/models/equity/hestonslvfdmmodel.{hpp,cpp}}
 * (Phase 5h.5-SLV WI-4 — <strong>SKELETON</strong>).
 * <p>
 * The full FDM bootstrap requires several supporting classes that are not
 * yet in JQuantLib (Phase 5h.5-SLV-b carry-forwards):
 * <ul>
 *   <li>{@code FdmHestonGreensFct} — Green's-function initial condition.</li>
 *   <li>{@code FixedLocalVolSurface} — output leverage container.</li>
 *   <li>{@code SquareRootProcessRNDCalculator},
 *       {@code LocalVolRNDCalculator} — risk-neutral density calculators.</li>
 *   <li>{@code FdmMesherIntegral} — joint-density integration over the mesh.</li>
 *   <li>Concentrating1dMesher (present) usage with mandatory date pivots.</li>
 * </ul>
 * Until those land this class only stores the calibration inputs and
 * exposes the public-API surface that the C++ class advertises.
 *
 * @author Phase 5h.5-SLV port
 */
public class HestonSLVFDMModel extends LazyObject {

    private final Handle<LocalVolTermStructure> localVol;
    private final Handle<HestonModel> hestonModel;
    private final Date endDate;
    private final HestonSLVFokkerPlanckFdmParams params;
    private final List<Date> mandatoryDates;
    private final double mixingFactor;
    private final boolean logging;

    private LocalVolTermStructure leverageFunction;
    private final List<LogEntry> logEntries = new ArrayList<LogEntry>();

    public HestonSLVFDMModel(final Handle<LocalVolTermStructure> localVol,
                             final Handle<HestonModel> hestonModel,
                             final Date endDate,
                             final HestonSLVFokkerPlanckFdmParams params) {
        this(localVol, hestonModel, endDate, params, false,
             new ArrayList<Date>(), 1.0);
    }

    public HestonSLVFDMModel(final Handle<LocalVolTermStructure> localVol,
                             final Handle<HestonModel> hestonModel,
                             final Date endDate,
                             final HestonSLVFokkerPlanckFdmParams params,
                             final boolean logging) {
        this(localVol, hestonModel, endDate, params, logging,
             new ArrayList<Date>(), 1.0);
    }

    public HestonSLVFDMModel(final Handle<LocalVolTermStructure> localVol,
                             final Handle<HestonModel> hestonModel,
                             final Date endDate,
                             final HestonSLVFokkerPlanckFdmParams params,
                             final boolean logging,
                             final List<Date> mandatoryDates,
                             final double mixingFactor) {
        this.localVol = localVol;
        this.hestonModel = hestonModel;
        this.endDate = endDate;
        this.params = params;
        this.logging = logging;
        this.mandatoryDates = (mandatoryDates != null)
                ? new ArrayList<Date>(mandatoryDates)
                : new ArrayList<Date>();
        this.mixingFactor = mixingFactor;

        // C++: registerWith(hestonModel_); registerWith(localVol_);
        if (!hestonModel.empty()) hestonModel.addObserver(this);
        if (!localVol.empty())    localVol   .addObserver(this);
    }

    /**
     * Returns the Heston-model {@link HestonProcess} sourced from the
     * model handle. Mirrors C++ {@code hestonProcess()}.
     * <p>
     * Returns {@code null} if the underlying handle is empty — the C++
     * version dereferences blindly so Java callers should follow the same
     * usage discipline.
     */
    public HestonProcess hestonProcess() {
        if (hestonModel.empty()) return null;
        // JQuantLib's HestonModel does not currently expose process(); for
        // the skeleton we return null and document the dependency.
        // Phase 5h.5-SLV-b will introduce HestonModel.process() if needed.
        return null;
    }

    public LocalVolTermStructure localVol() {
        return localVol.empty() ? null : localVol.currentLink();
    }

    /**
     * Returns the calibrated leverage function. Triggers
     * {@link #performCalculations()} via {@link LazyObject#calculate()}
     * if not yet computed.
     */
    public LocalVolTermStructure leverageFunction() {
        calculate();
        return leverageFunction;
    }

    /**
     * Per-step diagnostic record retained when {@code logging=true}.
     * Ports the C++ {@code LogEntry} POD-struct verbatim.
     */
    public static final class LogEntry {
        public final double t;
        public final Array prob;
        public final FdmMesherComposite mesher;

        public LogEntry(final double t, final Array prob,
                        final FdmMesherComposite mesher) {
            this.t = t;
            this.prob = prob;
            this.mesher = mesher;
        }
    }

    public List<LogEntry> logEntries() {
        return Collections.unmodifiableList(logEntries);
    }

    @Override
    protected void performCalculations() {
        // Phase 5h.5-SLV-b carry-forward: the full forward bootstrap requires
        //   - FdmHestonGreensFct (initial condition)
        //   - LocalVolRNDCalculator + SquareRootProcessRNDCalculator
        //   - FixedLocalVolSurface (output container)
        //   - FdmMesherIntegral
        //   - per-step Concentrating1dMesher build
        //   - one of {Hundsdorfer, CraigSneyd, ImplicitEuler} schemes against
        //     FdmHestonFwdOp with a leverage feedback loop.
        // None of those are ported yet; throw so callers get a clear signal.
        throw new UnsupportedOperationException(
                "HestonSLVFDMModel.performCalculations() — Phase 5h.5-SLV-b "
                + "carry-forward (needs FdmHestonGreensFct, FixedLocalVolSurface, "
                + "LocalVolRNDCalculator, SquareRootProcessRNDCalculator, "
                + "FdmMesherIntegral). endDate=" + endDate
                + ", xGrid=" + params.xGrid + ", vGrid=" + params.vGrid
                + ", mixing=" + mixingFactor + ", mandatory=" + mandatoryDates.size()
                + ", logging=" + logging);
    }
}
