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
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.models.HestonSLVFokkerPlanckFdmParams.GreensFctAlgorithm;
import org.jquantlib.math.integrals.DiscreteSimpsonIntegral;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.Concentrating1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Predefined1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmHestonFwdOp;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmSquareRootFwdOp.TransformationType;
import org.jquantlib.methods.finitedifferences.schemes.CraigSneydScheme;
import org.jquantlib.methods.finitedifferences.schemes.DouglasScheme;
import org.jquantlib.methods.finitedifferences.schemes.ExplicitEulerScheme;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.schemes.HundsdorferScheme;
import org.jquantlib.methods.finitedifferences.schemes.ImplicitEulerScheme;
import org.jquantlib.methods.finitedifferences.schemes.ModifiedCraigSneydScheme;
import org.jquantlib.methods.finitedifferences.utilities.FdmHestonGreensFct;
import org.jquantlib.methods.finitedifferences.utilities.FdmMesherIntegral;
import org.jquantlib.methods.finitedifferences.utilities.LocalVolRNDCalculator;
import org.jquantlib.methods.finitedifferences.utilities.SquareRootProcessRNDCalculator;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.equityfx.FixedLocalVolSurface;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;
import org.jquantlib.util.LazyObject;

/**
 * FDM-based calibration of a Heston Stochastic-Local-Volatility model.
 * <p>
 * Java port of v1.42.1 {@code ql/models/equity/hestonslvfdmmodel.{hpp,cpp}}
 * (Phase 5h.5-SLV-b body-fill).
 *
 * <p>Calibrates the leverage function {@code L(S, t)} so that the Heston
 * stochastic-volatility model with extra leverage matches the Dupire local
 * volatility surface exactly. The forward Fokker-Planck PDE is rolled
 * forward in time on a 2D (log-spot, variance) grid; at each step the
 * leverage column is updated from the conditional second-moment integral
 * of the joint density.
 *
 * @author Phase 5h.5-SLV-b port
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

        if (!hestonModel.empty()) hestonModel.addObserver(this);
        if (!localVol.empty())    localVol   .addObserver(this);
    }

    /** Returns the Heston model's underlying process. */
    public HestonProcess hestonProcess() {
        return hestonModel.empty() ? null : hestonModel.currentLink().process();
    }

    public LocalVolTermStructure localVol() {
        return localVol.empty() ? null : localVol.currentLink();
    }

    /** Returns the calibrated leverage function (triggers calibration if needed). */
    public LocalVolTermStructure leverageFunction() {
        calculate();
        return leverageFunction;
    }

    /** Diagnostic log entry retained when {@code logging=true}. */
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
        logEntries.clear();

        final HestonProcess hestonProcess = hestonModel.currentLink().process();
        final SimpleQuote spot = (SimpleQuote) hestonProcess.s0().currentLink();
        final YieldTermStructure rTS = hestonProcess.riskFreeRate().currentLink();
        final YieldTermStructure qTS = hestonProcess.dividendYield().currentLink();

        final double v0    = hestonProcess.v0().currentLink().value();
        final double kappa = hestonProcess.kappa().currentLink().value();
        final double theta = hestonProcess.theta().currentLink().value();
        final double sigma = hestonProcess.sigma().currentLink().value();
        final double mixedSigma = mixingFactor * sigma;
        final double alphaPow = 2.0 * kappa * theta / (mixedSigma * mixedSigma);

        final int xGrid = params.xGrid;
        final int vGrid = params.vGrid;

        final DayCounter dc = rTS.dayCounter();
        final Date refDate = rTS.referenceDate();

        final double T = dc.yearFraction(refDate, endDate);
        QL.require(refDate.lt(endDate), "reference date must be smaller than final calibration date");
        QL.require(localVol.currentLink().maxTime() >= T,
                "final calibration maturity exceeds local volatility surface");

        // Build the exponential time-step grid.
        final double maxDt = 1.0 / params.tMaxStepsPerYear;
        final double minDt = 1.0 / params.tMinStepsPerYear;

        double tIdx = 0.0;
        final List<Double> times = new ArrayList<Double>();
        times.add(Double.valueOf(tIdx));
        while (tIdx < T) {
            final double decayFactor = Math.exp(-params.tStepNumberDecay * tIdx);
            final double dt = maxDt * decayFactor + minDt * (1.0 - decayFactor);
            tIdx = Math.min(T, tIdx + dt);
            times.add(Double.valueOf(tIdx));
        }
        for (final Date d : mandatoryDates) {
            times.add(Double.valueOf(dc.yearFraction(refDate, d)));
        }
        final TimeGrid timeGrid = new TimeGrid(times);

        // Build the local-volatility RND calculator on the same time grid.
        final LocalVolRNDCalculator localVolRND = new LocalVolRNDCalculator(
                spot, rTS, qTS, localVol.currentLink(),
                timeGrid, xGrid,
                params.x0Density, params.localVolEpsProb, params.maxIntegrationIterations);

        final List<Integer> rescaleSteps = localVolRND.rescaleTimeSteps();

        final SquareRootProcessRNDCalculator squareRootRnd =
                new SquareRootProcessRNDCalculator(v0, kappa, theta, mixedSigma);

        final TransformationType trafoType = params.trafoType;

        // Per-time-step 1d meshers along x and v.
        final List<Fdm1dMesher> xMesher = new ArrayList<Fdm1dMesher>(timeGrid.size());
        final List<Fdm1dMesher> vMesher = new ArrayList<Fdm1dMesher>(timeGrid.size());
        xMesher.add(localVolRND.mesher(0.0));
        final double[] vSeed = new double[vGrid];
        Arrays.fill(vSeed, v0);
        vMesher.add(new Predefined1dMesher(vSeed));

        int rescaleIdx = 0;
        for (int i = 1; i < timeGrid.size(); ++i) {
            xMesher.add(localVolRND.mesher(timeGrid.at(i)));
            if (rescaleIdx < rescaleSteps.size() && i == rescaleSteps.get(rescaleIdx).intValue()) {
                ++rescaleIdx;
                final double t0 = timeGrid.at(rescaleSteps.get(rescaleIdx - 1).intValue());
                final double t1 = (rescaleIdx < rescaleSteps.size())
                        ? timeGrid.at(rescaleSteps.get(rescaleIdx).intValue())
                        : timeGrid.back();
                vMesher.add(buildVarianceMesher(squareRootRnd, t0, t1, vGrid, v0, params));
            } else {
                vMesher.add(vMesher.get(vMesher.size() - 1));
            }
        }

        // Initial probability distribution from the Green's function.
        FdmMesherComposite mesher = new FdmMesherComposite(xMesher.get(1), vMesher.get(1));

        final double lv0 = localVol.currentLink().localVol(0.0, spot.value()) / Math.sqrt(v0);

        // Leverage matrix L: rows = xGrid (strikes), cols = timeGrid.size() (times).
        final Matrix L = new Matrix(xGrid, timeGrid.size());
        for (int j = 0; j < xGrid; ++j) {
            L.set(j, 0, lv0);
            L.set(j, 1, lv0);
        }

        // Per-time strike vectors: vStrikes[i][j] = exp(xMesher[i].locations()[j]).
        final List<double[]> vStrikes = new ArrayList<double[]>(timeGrid.size());
        for (int i = 0; i < timeGrid.size(); ++i) {
            final double[] xs = xMesher.get(i).locations();
            final double[] strikes = new double[xGrid];
            if (xs[0] == xs[xs.length - 1]) {
                Arrays.fill(strikes, Math.exp(xs[0]));
            } else {
                for (int j = 0; j < xGrid; ++j) strikes[j] = Math.exp(xs[j]);
            }
            vStrikes.add(strikes);
        }

        // Convert TimeGrid → double[] for FixedLocalVolSurface.
        final double[] tArray = new double[timeGrid.size()];
        for (int i = 0; i < timeGrid.size(); ++i) tArray[i] = timeGrid.at(i);

        final FixedLocalVolSurface leverageFct = new FixedLocalVolSurface(refDate,
                tArray, vStrikes, L, dc,
                FixedLocalVolSurface.Extrapolation.ConstantExtrapolation,
                FixedLocalVolSurface.Extrapolation.ConstantExtrapolation);

        FdmLinearOpComposite hestonFwdOp = new FdmHestonFwdOp(mesher, hestonProcess,
                trafoType, leverageFct, mixingFactor);

        Array p = new FdmHestonGreensFct(mesher, hestonProcess, trafoType, lv0)
                .get(timeGrid.at(1), greensFctAlgorithm(params.greensAlgorithm));

        if (logging) {
            logEntries.add(new LogEntry(timeGrid.at(1), p.clone(), mesher));
        }

        for (int i = 2; i < times.size(); ++i) {
            final double t = timeGrid.at(i);
            final double dt = t - timeGrid.at(i - 1);

            final List<Fdm1dMesher> currentMs = mesher.getFdm1dMeshers();
            if (currentMs.get(0) != xMesher.get(i) || currentMs.get(1) != vMesher.get(i)) {
                final FdmMesherComposite newMesher = new FdmMesherComposite(
                        xMesher.get(i), vMesher.get(i));
                p = reshapePDF(p, mesher, newMesher);
                mesher = newMesher;
                p = rescalePDF(p, mesher, trafoType, alphaPow);
                hestonFwdOp = new FdmHestonFwdOp(mesher, hestonProcess,
                        trafoType, leverageFct, mixingFactor);
            }

            Array pn = p.clone();
            final List<Fdm1dMesher> ms = mesher.getFdm1dMeshers();
            final Array x = arrayOf(ms.get(0).locations()).exp();
            final Array v = arrayOf(ms.get(1).locations());

            // Predictor-corrector inner loop.
            for (int r = 0; r < params.predictionCorretionSteps; ++r) {
                final FdmSchemeDesc fdmSchemeDesc =
                        (i < params.nRannacherTimeSteps + 2)
                                ? FdmSchemeDesc.ImplicitEuler()
                                : params.schemeDesc;
                final FdmScheme fdmScheme = fdmSchemeFactory(fdmSchemeDesc, hestonFwdOp);

                for (int j = 0; j < x.size(); ++j) {
                    final Array pSlice = new Array(vGrid);
                    for (int k = 0; k < vGrid; ++k) {
                        pSlice.set(k, pn.get(j + k * xGrid));
                    }

                    final double pInt;
                    if (trafoType == TransformationType.Power) {
                        final Array w = pow(v, alphaPow - 1.0).mul(pSlice);
                        pInt = new DiscreteSimpsonIntegral().op(v, w);
                    } else {
                        pInt = new DiscreteSimpsonIntegral().op(v, pSlice);
                    }

                    final double vpInt;
                    if (trafoType == TransformationType.Log) {
                        final Array w = v.exp().mul(pSlice);
                        vpInt = new DiscreteSimpsonIntegral().op(v, w);
                    } else if (trafoType == TransformationType.Power) {
                        final Array w = pow(v, alphaPow).mul(pSlice);
                        vpInt = new DiscreteSimpsonIntegral().op(v, w);
                    } else {
                        final Array w = v.mul(pSlice);
                        vpInt = new DiscreteSimpsonIntegral().op(v, w);
                    }

                    final double scale = pInt / vpInt;
                    final double localVolValue = localVol.currentLink().localVol(t, x.get(j));
                    final double l = (scale >= 0.0)
                            ? localVolValue * Math.sqrt(scale) : 1.0;
                    L.set(j, i, Math.min(50.0, Math.max(0.001, l)));
                }
                leverageFct.setLinearInterpolation();

                // Boundary extrapolation (constant clamp).
                final double sLowerBound = Math.max(x.get(0),
                        Math.exp(localVolRND.invcdf(params.leverageFctPropEps, t)));
                final double sUpperBound = Math.min(x.get(x.size() - 1),
                        Math.exp(localVolRND.invcdf(1.0 - params.leverageFctPropEps, t)));
                final double lowerL = leverageFct.localVol(t, sLowerBound);
                final double upperL = leverageFct.localVol(t, sUpperBound);
                for (int j = 0; j < x.size(); ++j) {
                    if (x.get(j) < sLowerBound) {
                        L.set(j, i, lowerL);
                    } else if (x.get(j) > sUpperBound) {
                        L.set(j, i, upperL);
                    }
                }
                leverageFct.setLinearInterpolation();

                pn = p.clone();
                fdmScheme.setStep(dt);
                fdmScheme.step(pn, t);
            }
            p = pn;
            p = rescalePDF(p, mesher, trafoType, alphaPow);

            if (logging) {
                logEntries.add(new LogEntry(t, p.clone(), mesher));
            }
        }

        leverageFunction = leverageFct;
    }

    /** Map params.greensAlgorithm enum to FdmHestonGreensFct.Algorithm. */
    private static FdmHestonGreensFct.Algorithm greensFctAlgorithm(final GreensFctAlgorithm a) {
        switch (a) {
            case ZeroCorrelation: return FdmHestonGreensFct.Algorithm.ZeroCorrelation;
            case Gaussian:        return FdmHestonGreensFct.Algorithm.Gaussian;
            case SemiAnalytical:  return FdmHestonGreensFct.Algorithm.SemiAnalytical;
            default: throw new IllegalArgumentException("unknown algorithm: " + a);
        }
    }

    private static Array arrayOf(final double[] xs) {
        final Array a = new Array(xs.length);
        for (int i = 0; i < xs.length; ++i) a.set(i, xs[i]);
        return a;
    }

    private static Array pow(final Array v, final double e) {
        final Array out = new Array(v.size());
        for (int i = 0; i < v.size(); ++i) out.set(i, Math.pow(v.get(i), e));
        return out;
    }

    /**
     * Build the variance mesher.
     *
     * <p>Approximate port: the C++ helper uses a multi-critical-point
     * {@code Concentrating1dMesher} (3 cPoints: lower, v0, upper) which
     * the Java {@link Concentrating1dMesher} does not yet expose
     * (single-point variant only). Until that overload lands we use the
     * single-point variant centred at {@code v0} — this anchors the grid
     * around the Heston long-run variance but loses fine boundary control.
     * Phase 5h.5-SLV-c carry-forward.
     */
    private static Fdm1dMesher buildVarianceMesher(
            final SquareRootProcessRNDCalculator rnd,
            final double t0, final double t1, final int vGrid, final double v0,
            final HestonSLVFokkerPlanckFdmParams params) {
        double lowerBound = Double.POSITIVE_INFINITY;
        double upperBound = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 10; ++i) {
            final double t = t0 + i / 10.0 * (t1 - t0);
            lowerBound = Math.min(lowerBound, rnd.invcdf(params.vLowerEps, t));
            upperBound = Math.max(upperBound, rnd.invcdf(1.0 - params.vUpperEps, t));
        }
        lowerBound = Math.max(lowerBound, params.vMin);

        final double lb, ub, v0Center;
        if (params.trafoType == TransformationType.Log) {
            lb = Math.log(lowerBound); ub = Math.log(upperBound); v0Center = Math.log(v0);
        } else {
            lb = lowerBound; ub = upperBound; v0Center = v0;
        }
        return new Concentrating1dMesher(lb, ub, vGrid, v0Center, params.v0Density);
    }

    /** Integrate the joint pdf over the mesher with a power-Jacobian for Power trafo. */
    private static double integratePDF(final Array p,
                                       final FdmMesherComposite mesher,
                                       final TransformationType trafoType,
                                       final double alphaPow) {
        if (trafoType != TransformationType.Power) {
            return new FdmMesherIntegral(mesher,
                    new FdmMesherIntegral.Integrator1d() {
                        @Override
                        public double op(final Array x, final Array f) {
                            return new DiscreteSimpsonIntegral().op(x, f);
                        }
                    }).integrate(p);
        }
        final Array tp = new Array(p.size());
        for (final FdmLinearOpIterator it : mesher.layout()) {
            final int idx = it.index();
            final double nu = mesher.location(it, 1);
            tp.set(idx, p.get(idx) * Math.pow(nu, alphaPow - 1.0));
        }
        return new FdmMesherIntegral(mesher,
                new FdmMesherIntegral.Integrator1d() {
                    @Override
                    public double op(final Array x, final Array f) {
                        return new DiscreteSimpsonIntegral().op(x, f);
                    }
                }).integrate(tp);
    }

    private static Array rescalePDF(final Array p,
                                    final FdmMesherComposite mesher,
                                    final TransformationType trafoType,
                                    final double alphaPow) {
        return p.div(integratePDF(p, mesher, trafoType, alphaPow));
    }

    /**
     * Reshape a probability density when the underlying mesher changes.
     * Bilinear interpolation in (x, v); zero outside the original domain.
     */
    private static Array reshapePDF(final Array p,
                                    final FdmMesherComposite oldMesher,
                                    final FdmMesherComposite newMesher) {
        QL.require(oldMesher.layout().size() == newMesher.layout().size()
                && oldMesher.layout().size() == p.size(),
                "inconsistent mesher or vector size");

        final List<Fdm1dMesher> oldMs = oldMesher.getFdm1dMeshers();
        final double[] xOld = oldMs.get(0).locations();
        final double[] vOld = oldMs.get(1).locations();
        final int nx = xOld.length;
        final int nv = vOld.length;

        final Array pNew = new Array(p.size());
        for (final FdmLinearOpIterator it : newMesher.layout()) {
            final double xq = newMesher.location(it, 0);
            final double vq = newMesher.location(it, 1);
            final int idx = it.index();
            if (xq > xOld[nx - 1] || xq < xOld[0]
                    || vq > vOld[nv - 1] || vq < vOld[0]) {
                pNew.set(idx, 0.0);
            } else {
                pNew.set(idx, bilinear(xOld, vOld, p, nx, xq, vq));
            }
        }
        return pNew;
    }

    /** Inline bilinear interpolation on a tensor-product grid (row-major in x). */
    private static double bilinear(final double[] xs, final double[] ys, final Array f,
                                   final int nx, final double x, final double y) {
        // Find x bracket.
        int ix = upperBound(xs, x) - 1;
        if (ix < 0) ix = 0; else if (ix >= xs.length - 1) ix = xs.length - 2;
        int iy = upperBound(ys, y) - 1;
        if (iy < 0) iy = 0; else if (iy >= ys.length - 1) iy = ys.length - 2;

        final double x0 = xs[ix], x1 = xs[ix + 1];
        final double y0 = ys[iy], y1 = ys[iy + 1];
        final double tx = (x - x0) / (x1 - x0);
        final double ty = (y - y0) / (y1 - y0);

        final double f00 = f.get(ix     + iy     * nx);
        final double f10 = f.get(ix + 1 + iy     * nx);
        final double f01 = f.get(ix     + (iy+1) * nx);
        final double f11 = f.get(ix + 1 + (iy+1) * nx);

        return (1 - tx) * (1 - ty) * f00 + tx * (1 - ty) * f10
                + (1 - tx) * ty * f01 + tx * ty * f11;
    }

    private static int upperBound(final double[] arr, final double v) {
        int lo = 0, hi = arr.length;
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (arr[mid] <= v) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** Internal scheme-wrapper interface, mirrors C++ anon-namespace FdmScheme. */
    private interface FdmScheme {
        void step(Array a, double t);
        void setStep(double dt);
    }

    /** Build a scheme wrapper from the descriptor. */
    private static FdmScheme fdmSchemeFactory(final FdmSchemeDesc desc,
                                              final FdmLinearOpComposite op) {
        switch (desc.type) {
            case HundsdorferType:
                return new FdmScheme() {
                    final HundsdorferScheme s = new HundsdorferScheme(desc.theta, desc.mu, op);
                    @Override public void step(final Array a, final double t) { s.step(a, t); }
                    @Override public void setStep(final double dt) { s.setStep(dt); }
                };
            case DouglasType:
                return new FdmScheme() {
                    final DouglasScheme s = new DouglasScheme(desc.theta, op);
                    @Override public void step(final Array a, final double t) { s.step(a, t); }
                    @Override public void setStep(final double dt) { s.setStep(dt); }
                };
            case CraigSneydType:
                return new FdmScheme() {
                    final CraigSneydScheme s = new CraigSneydScheme(desc.theta, desc.mu, op);
                    @Override public void step(final Array a, final double t) { s.step(a, t); }
                    @Override public void setStep(final double dt) { s.setStep(dt); }
                };
            case ModifiedCraigSneydType:
                return new FdmScheme() {
                    final ModifiedCraigSneydScheme s =
                            new ModifiedCraigSneydScheme(desc.theta, desc.mu, op);
                    @Override public void step(final Array a, final double t) { s.step(a, t); }
                    @Override public void setStep(final double dt) { s.setStep(dt); }
                };
            case ImplicitEulerType:
                return new FdmScheme() {
                    final ImplicitEulerScheme s = new ImplicitEulerScheme(op);
                    @Override public void step(final Array a, final double t) { s.step(a, t); }
                    @Override public void setStep(final double dt) { s.setStep(dt); }
                };
            case ExplicitEulerType:
                return new FdmScheme() {
                    final ExplicitEulerScheme s = new ExplicitEulerScheme(op);
                    @Override public void step(final Array a, final double t) { s.step(a, t); }
                    @Override public void setStep(final double dt) { s.setStep(dt); }
                };
            default:
                throw new IllegalArgumentException("Unknown scheme type: " + desc.type);
        }
    }
}
