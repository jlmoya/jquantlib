/*
 Copyright (C) 2005, 2006 Klaus Spanderen (C++ original).
 Copyright (C) 2026 JQuantLib migration contributors (Java port).

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
*/

package org.jquantlib.legacy.libormarkets;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.AffineModel;
import org.jquantlib.model.CalibratedModel;
import org.jquantlib.model.Parameter;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.processes.LiborForwardModelProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.volatilities.swaption.SwaptionVolatilityMatrix;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Libor forward model — exact-cap pricing + Rebonato swaption approximation.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code legacy/libormarketmodels/liborforwardmodel.{hpp,cpp}}.
 *
 * <p>References:
 * <ul>
 *   <li>Stefan Weber, 2005, <em>Efficient Calibration for Libor Market
 *       Models</em>.</li>
 *   <li>Damiano Brigo, Fabio Mercurio, Massimo Morini, 2003, <em>Different
 *       Covariance Parameterizations of Libor Market Model and Joint
 *       Caps/Swaptions Calibration</em>.</li>
 * </ul>
 */
public class LiborForwardModel extends CalibratedModel implements AffineModel {

    private final List<Double> f_;
    private final List<Double> accrualPeriod_;

    private final LfmCovarianceProxy covarProxy_;
    private final LiborForwardModelProcess process_;

    /** Cached swaption-volatility matrix (computed lazily; invalidated by
     *  {@link #setParams(Array)}). Mirrors C++ {@code mutable shared_ptr<SwaptionVolatilityMatrix> swaptionVola}. */
    private SwaptionVolatilityMatrix swaptionVola_;

    public LiborForwardModel(final LiborForwardModelProcess process,
                             final LmVolatilityModel volaModel,
                             final LmCorrelationModel corrModel) {
        super(paramCount(volaModel) + paramCount(corrModel));
        this.process_ = process;
        this.covarProxy_ = new LfmCovarianceProxy(volaModel, corrModel);
        this.f_ = new ArrayList<Double>(process.size());
        this.accrualPeriod_ = new ArrayList<Double>(process.size());

        // Mirror C++ ctor body: copy the volatility and correlation
        // parameters into the CalibratedModel arguments_ list.
        final List<Parameter> volaParams = volaModel.params();
        final List<Parameter> corrParams = corrModel.params();
        final int k = volaParams.size();
        for (int i = 0; i < k; ++i) {
            arguments_.set(i, volaParams.get(i));
        }
        for (int i = 0; i < corrParams.size(); ++i) {
            arguments_.set(k + i, corrParams.get(i));
        }

        // Pre-compute the forward-rate decoupling factors f_i = 1/(1 + delta_i * L_i(0))
        // (Brigo-Mercurio decoupling change-of-measure factors).
        final Array initialValues = process.initialValues();
        for (int i = 0; i < process.size(); ++i) {
            final double dt = process.accrualEndTimes().get(i)
                    - process.accrualStartTimes().get(i);
            accrualPeriod_.add(dt);
            f_.add(1.0 / (1.0 + dt * initialValues.get(i)));
        }
    }

    private static int paramCount(final LmVolatilityModel m) {
        int n = 0;
        for (final Parameter p : m.params()) {
            n += p.size();
        }
        return n;
    }

    private static int paramCount(final LmCorrelationModel m) {
        int n = 0;
        for (final Parameter p : m.params()) {
            n += p.size();
        }
        return n;
    }

    @Override
    public void setParams(final Array params) {
        super.setParams(params);

        // Splice the flat parameter array back into vola/corr param lists.
        // Mirror C++: copy [begin, begin+k) -> vola.setParams, [k, end) -> corr.setParams.
        final int k = paramCount(covarProxy_.volatilityModel());
        final List<Parameter> volaParams =
                new ArrayList<Parameter>(arguments_.subList(0, k));
        final List<Parameter> corrParams =
                new ArrayList<Parameter>(arguments_.subList(k, arguments_.size()));
        covarProxy_.volatilityModel().setParams(volaParams);
        covarProxy_.correlationModel().setParams(corrParams);

        // Invalidate the cached swaption-vol matrix.
        swaptionVola_ = null;
    }

    /**
     * Discount-bond option price under the LFM. Mirrors C++
     * {@code LiborForwardModel::discountBondOption(type, strike, maturity,
     * bondMaturity)} (liborforwardmodel.cpp:64-103).
     */
    @Override
    public double discountBondOption(final Option.Type type,
                                     final double strike,
                                     final double maturity,
                                     final double bondMaturity) {
        final List<Double> accrualStartTimes = process_.accrualStartTimes();
        final List<Double> accrualEndTimes = process_.accrualEndTimes();

        QL.require(accrualStartTimes.get(0) <= maturity
                        && accrualStartTimes.get(accrualStartTimes.size() - 1) >= maturity,
                "capet maturity does not fit to the process");

        // std::lower_bound on accrualStartTimes for maturity.
        int i;
        {
            int lo = 0;
            int hi = accrualStartTimes.size();
            while (lo < hi) {
                final int mid = (lo + hi) >>> 1;
                if (accrualStartTimes.get(mid) < maturity) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            i = lo;
        }

        final double eps = 100.0 * Math.ulp(1.0);
        QL.require(i < process_.size()
                        && Math.abs(maturity - accrualStartTimes.get(i)) < eps
                        && Math.abs(bondMaturity - accrualEndTimes.get(i)) < eps,
                "irregular fixings are not (yet) supported");

        final double tenor = accrualEndTimes.get(i) - accrualStartTimes.get(i);
        final double forward = process_.initialValues().get(i);
        final double capRate = (1.0 / strike - 1.0) / tenor;
        final double var = covarProxy_.integratedCovariance(
                i, i, process_.fixingTimes().get(i));
        final double dis = process_.index()
                .termStructure().currentLink().discount(bondMaturity);

        final Option.Type flipped =
                (type == Option.Type.Put) ? Option.Type.Call : Option.Type.Put;
        final double black = BlackFormula.blackFormula(flipped, capRate, forward, Math.sqrt(var));

        final double npv = dis * tenor * black;
        return npv / (1.0 + capRate * tenor);
    }

    /** Weight vector w_alpha..beta(0) from Brigo-Mercurio swap-rate decomp. */
    protected Array w_0(final int alpha, final int beta) {
        final Array omega = new Array(beta + 1);
        QL.require(alpha < beta, "alpha needs to be smaller than beta");

        double s = 0.0;
        for (int k = alpha + 1; k <= beta; ++k) {
            double b = accrualPeriod_.get(k);
            for (int j = alpha + 1; j <= k; ++j) {
                b *= f_.get(j);
            }
            s += b;
        }

        for (int ii = alpha + 1; ii <= beta; ++ii) {
            double a = accrualPeriod_.get(ii);
            for (int j = alpha + 1; j <= ii; ++j) {
                a *= f_.get(j);
            }
            omega.set(ii, a / s);
        }
        return omega;
    }

    /** Initial forward swap rate S_0(alpha, beta). */
    public double S_0(final int alpha, final int beta) {
        final Array w = w_0(alpha, beta);
        final Array f = process_.initialValues();
        double fwdRate = 0.0;
        for (int i = alpha + 1; i <= beta; ++i) {
            fwdRate += w.get(i) * f.get(i);
        }
        return fwdRate;
    }

    /**
     * Swaption-volatility matrix via Rebonato's approximation. Mirrors C++
     * {@code LiborForwardModel::getSwaptionVolatilityMatrix()}
     * (liborforwardmodel.cpp:147-199).
     */
    public SwaptionVolatilityMatrix getSwaptionVolatilityMatrix() {
        if (swaptionVola_ != null) {
            return swaptionVola_;
        }

        final IborIndex index = process_.index();
        final Date today = process_.fixingDates().get(0);

        final int size = process_.size() / 2;
        final Matrix volatilities = new Matrix(size, size);

        final List<Date> exercises = new ArrayList<Date>(size);
        for (int i = 1; i <= size; ++i) {
            exercises.add(process_.fixingDates().get(i));
        }

        final List<Period> lengths = new ArrayList<Period>(size);
        for (int i = 0; i < size; ++i) {
            lengths.add(new Period((i + 1) * index.tenor().length(),
                    index.tenor().units()));
        }

        final Array f = process_.initialValues();
        for (int k = 0; k < size; ++k) {
            final int alpha = k;
            final double t_alpha = process_.fixingTimes().get(alpha + 1);

            final Matrix var = new Matrix(size, size);
            for (int i = alpha + 1; i <= k + size; ++i) {
                for (int j = i; j <= k + size; ++j) {
                    final double v = covarProxy_.integratedCovariance(i, j, t_alpha);
                    var.set(i - alpha - 1, j - alpha - 1, v);
                    var.set(j - alpha - 1, i - alpha - 1, v);
                }
            }

            for (int l = 1; l <= size; ++l) {
                final int beta = l + k;
                final Array w = w_0(alpha, beta);

                double sum = 0.0;
                for (int i = alpha + 1; i <= beta; ++i) {
                    for (int j = alpha + 1; j <= beta; ++j) {
                        sum += w.get(i) * w.get(j) * f.get(i) * f.get(j)
                                * var.get(i - alpha - 1, j - alpha - 1);
                    }
                }
                volatilities.set(k, l - 1, Math.sqrt(sum / t_alpha) / S_0(alpha, beta));
            }
        }

        // Wrap the Matrix into the {@link SwaptionVolatilityMatrix} ctor that
        // accepts a list of exercise Dates + a list of swap-tenor Periods.
        swaptionVola_ = new SwaptionVolatilityMatrix(
                today, new NullCalendar(),
                BusinessDayConvention.Following,
                exercises,
                org.jquantlib.termstructures.volatilities.swaption.SwaptionVolatilityDiscrete.FromDates.Marker,
                lengths, volatilities, index.dayCounter(),
                /* flatExtrapolation */ false,
                org.jquantlib.model.VolatilityType.ShiftedLognormal,
                /* shifts */ new Matrix(0, 0));
        return swaptionVola_;
    }

    /** The next two methods are meaningless within this context but required
     *  by the {@link AffineModel} interface. */
    @Override
    public double discount(final double t) {
        return process_.index().termStructure().currentLink().discount(t);
    }

    @Override
    public double discountBond(final double now, final double maturity, final Array factors) {
        return discount(maturity);
    }
}
