/*
 Copyright (C) 2015 Johannes Goettker-Schnetmann
 Copyright (C) 2015 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.processes.HestonStochasticLocalVolProcess;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.model.marketmodels.BrownianGenerator;
import org.jquantlib.model.marketmodels.BrownianGeneratorFactory;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.volatilities.equityfx.FixedLocalVolSurface;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;
import org.jquantlib.util.LazyObject;

/**
 * MC-based calibration of a Heston Stochastic-Local-Volatility model.
 * <p>
 * Java port of v1.42.1 {@code ql/models/equity/hestonslvmcmodel.{hpp,cpp}}
 * (Phase 5h.5-SLV-b body-fill).
 * <p>
 * References:
 * <pre>
 *   Anthonie W. van der Stoep, Lech A. Grzelak, Cornelis W. Oosterlee, 2013,
 *   "The Heston Stochastic-Local Volatility Model: Efficient Monte Carlo Simulation"
 *   http://papers.ssrn.com/sol3/papers.cfm?abstract_id=2278122
 * </pre>
 *
 * @author Phase 5h.5-SLV-b port
 */
public class HestonSLVMCModel extends LazyObject {

    private final Handle<LocalVolTermStructure> localVol;
    private final Handle<HestonModel> hestonModel;
    private final BrownianGeneratorFactory brownianGeneratorFactory;
    private final Date endDate;
    @SuppressWarnings("unused")
    private final int timeStepsPerYear;
    private final int nBins;
    private final int calibrationPaths;
    private final List<Date> mandatoryDates;
    private final double mixingFactor;
    private final TimeGrid timeGrid;

    private FixedLocalVolSurface leverageFunction;

    public HestonSLVMCModel(final Handle<LocalVolTermStructure> localVol,
                            final Handle<HestonModel> hestonModel,
                            final BrownianGeneratorFactory brownianGeneratorFactory,
                            final Date endDate) {
        this(localVol, hestonModel, brownianGeneratorFactory, endDate,
             365, 201, 1 << 15, new ArrayList<Date>(), 1.0);
    }

    public HestonSLVMCModel(final Handle<LocalVolTermStructure> localVol,
                            final Handle<HestonModel> hestonModel,
                            final BrownianGeneratorFactory brownianGeneratorFactory,
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

        // Build the time grid covering all mandatory dates and the end date.
        final HestonProcess proc = hestonModel.empty() ? null : hestonModel.currentLink().process();
        QL.require(proc != null, "Heston model is empty");
        final DayCounter dc = proc.riskFreeRate().currentLink().dayCounter();
        final Date refDate = proc.riskFreeRate().currentLink().referenceDate();

        final List<Double> gridTimes = new ArrayList<Double>(this.mandatoryDates.size() + 1);
        for (final Date d : this.mandatoryDates) {
            gridTimes.add(Double.valueOf(dc.yearFraction(refDate, d)));
        }
        gridTimes.add(Double.valueOf(dc.yearFraction(refDate, endDate)));

        final int nSteps = Math.max(2, (int) (gridTimes.get(gridTimes.size() - 1).doubleValue()
                                              * timeStepsPerYear));
        this.timeGrid = new TimeGrid(gridTimes, nSteps);
    }

    public HestonProcess hestonProcess() {
        return hestonModel.empty() ? null : hestonModel.currentLink().process();
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
        QL.require(brownianGeneratorFactory != null,
                "BrownianGeneratorFactory required for MC calibration");

        final HestonProcess hestonProcess = hestonModel.currentLink().process();
        final SimpleQuote spot = (SimpleQuote) hestonProcess.s0().currentLink();

        final double v0          = hestonProcess.v0().currentLink().value();
        final DayCounter dc      = hestonProcess.riskFreeRate().currentLink().dayCounter();
        final Date refDate       = hestonProcess.riskFreeRate().currentLink().referenceDate();

        final double lv0 = localVol.currentLink().localVol(0.0, spot.value()) / Math.sqrt(v0);

        // Allocate leverage matrix and per-time strike vectors.
        final Matrix L = new Matrix(nBins, timeGrid.size());
        final List<double[]> vStrikes = new ArrayList<double[]>(timeGrid.size());

        // Strikes initially: spot ± dx*nBins/2 with dx = spot * sqrt(eps).
        for (int i = 0; i < timeGrid.size(); ++i) {
            final int u = nBins / 2;
            final double dx = spot.value() * Math.sqrt(Constants.QL_EPSILON);
            final double[] s = new double[nBins];
            for (int j = 0; j < nBins; ++j) {
                s[j] = spot.value() + (j - u) * dx;
            }
            vStrikes.add(s);
        }

        // Initial column: leverage = lv0 across all strikes at t=0.
        for (int j = 0; j < nBins; ++j) {
            L.set(j, 0, lv0);
        }

        // Convert TimeGrid → double[] for FixedLocalVolSurface.
        final double[] tArray = new double[timeGrid.size()];
        for (int i = 0; i < timeGrid.size(); ++i) tArray[i] = timeGrid.at(i);

        leverageFunction = new FixedLocalVolSurface(refDate, tArray, vStrikes, L, dc,
                FixedLocalVolSurface.Extrapolation.ConstantExtrapolation,
                FixedLocalVolSurface.Extrapolation.ConstantExtrapolation);

        final HestonStochasticLocalVolProcess slvProcess =
                new HestonStochasticLocalVolProcess(hestonProcess, leverageFunction, mixingFactor);

        // Pair (S, v) per path.
        final double[][] pairs = new double[calibrationPaths][2];
        for (int i = 0; i < calibrationPaths; ++i) {
            pairs[i][0] = spot.value();
            pairs[i][1] = v0;
        }

        final int k = calibrationPaths / nBins;
        final int m = calibrationPaths % nBins;

        final int timeSteps = timeGrid.size() - 1;

        // Pre-generate Brownian increments for all paths and steps.
        final double[][][] paths = new double[calibrationPaths][timeSteps][2];

        final BrownianGenerator brownianGenerator = brownianGeneratorFactory.create(2, timeSteps);
        for (int i = 0; i < calibrationPaths; ++i) {
            brownianGenerator.nextPath();
            final double[] tmp = new double[2];
            for (int j = 0; j < timeSteps; ++j) {
                brownianGenerator.nextStep(tmp);
                paths[i][j][0] = tmp[0];
                paths[i][j][1] = tmp[1];
            }
        }

        // Sweep time, evolve all paths, then bin & estimate leverage.
        final Array x0 = new Array(2);
        final Array dw = new Array(2);
        for (int n = 1; n < timeGrid.size(); ++n) {
            final double t = timeGrid.at(n - 1);
            final double dt = timeGrid.dt(n - 1);

            for (int i = 0; i < calibrationPaths; ++i) {
                x0.set(0, pairs[i][0]);
                x0.set(1, pairs[i][1]);
                dw.set(0, paths[i][n - 1][0]);
                dw.set(1, paths[i][n - 1][1]);

                final Array next = slvProcess.evolve(t, x0, dt, dw);
                pairs[i][0] = next.get(0);
                pairs[i][1] = next.get(1);
            }

            // Sort by S (first component).
            Arrays.sort(pairs, new Comparator<double[]>() {
                @Override
                public int compare(final double[] a, final double[] b) {
                    return Double.compare(a[0], b[0]);
                }
            });

            // Bin into nBins equally-sized buckets; estimate leverage per bin.
            int s = 0;
            for (int i = 0; i < nBins; ++i) {
                final int inc = k + ((i < m) ? 1 : 0);
                final int e = s + inc;
                double sum = 0.0;
                for (int j = s; j < e; ++j) sum += pairs[j][1];
                sum /= inc;

                final double midStrike = 0.5 * (pairs[e - 1][0] + pairs[s][0]);
                vStrikes.get(n)[i] = midStrike;
                final double lv = localVol.currentLink().localVol(t, midStrike, true);
                // Per-bin leverage estimate L = sqrt(lv^2 / E[v|bin]).
                // C++ v1.42.1 (hestonslvmcmodel.cpp:181) does not guard the
                // denominator; the C++ paths happen to keep the mean variance
                // strictly positive in every bin under their Sobol+QE
                // realisation. The Java Sobol+QE realisation occasionally
                // produces a bin whose paths all hit the absorbing v=0 branch
                // (psi >= 1.5, u <= p), driving E[v|bin] to zero and the raw
                // estimator to +Inf/NaN. The companion FDM calibrator clamps
                // the leverage cell to [1e-3, 50.0] (hestonslvfdmmodel.cpp:484)
                // for the same robustness reason; we apply the same clamp here
                // so that downstream FdmHestonOp solvers see a finite L on
                // every interior cell.
                final double lRaw = Math.sqrt(lv * lv / sum);
                final double lClamped;
                if (Double.isNaN(lRaw) || Double.isInfinite(lRaw)) {
                    lClamped = 50.0;
                } else {
                    lClamped = Math.min(50.0, Math.max(1.0e-3, lRaw));
                }
                L.set(i, n, lClamped);

                s = e;
            }
            leverageFunction.setLinearInterpolation();
        }
    }
}
