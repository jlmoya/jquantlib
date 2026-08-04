/*
 Copyright (C) 2026 JQuantLib contributors

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
/*
 Copyright (C) 2013, 2018 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.model.shortrate.onefactormodels.gaussian1d;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussHermiteIntegration;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.FlatExtrapolator;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.model.*;
import org.jquantlib.processes.MfStateProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.*;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.time.*;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

import java.util.*;

/**
 * One-factor Markov Functional model.
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/models/shortrate/onefactormodels/markovfunctional.{hpp,cpp}} (commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}). Phase 2j.5 Track C.3.
 * <p>
 * <b>A20 discipline.</b> Calibration uses Brent root-finding inside an outer
 * iteration over y-grid nodes; iteration order matters for floating-point reproducibility. {@code calibrationPoints_}
 * therefore uses a {@link TreeMap} (= C++ {@code std::map<Date, CalibrationPoint>} sorted-by-key order, NOT insertion
 * order). We do not reorder loops, fold computations, or early-exit the inner Brent loop — every step matches the C++
 * control flow.
 * <p>
 * <b>Limitations vs C++ v1.42.1.</b>
 * <ul>
 *   <li>{@code SabrSmile} adjustment is now supported via
 *       {@link SabrInterpolatedSmileSection}. The SABR fit is superimposed with
 *       a {@link KahaleSmileSection} to guarantee arbitrage-freeness, matching
 *       C++ behaviour. Note: {@code SabrSmile} with Normal-type input volatilities
 *       is rejected (as in C++); shifted-lognormal surfaces require a shift ≥ 0
 *       for all raw strikes to satisfy Java {@code blackFormulaStdDevDerivative}
 *       constraints (surfaces with negative raw strikes should use
 *       {@code KahaleSmile} instead).</li>
 *   <li>{@code CustomSmile} adjustment is now supported via the
 *       {@link CustomSmileFactory} / {@link CustomSmileSection} inner classes
 *       (Phase 2k Track C.2). Callers must supply a {@code CustomSmileFactory}
 *       via {@link ModelSettings#withCustomSmileFactory}.</li>
 *   <li>{@code modelOutputs().marketVega_} is populated with {@code 0.0}
 *       (Java {@code SmileSection} does not yet expose
 *       {@code blackFormulaVolDerivative}).</li>
 * </ul>
 *
 * @author Peter Caspers (C++ original)
 * @author JQuantLib contributors (Java port)
 */
public class MarkovFunctional extends Gaussian1dModel {

    // ──────────────────────────────────────────────────────────────────────
    //   Adjustments bit-flags (mirror C++ ModelSettings::Adjustments enum)
    // ──────────────────────────────────────────────────────────────────────

    public static final int ADJUST_NONE = 0;
    public static final int ADJUST_DIGITALS = 1 << 0;
    public static final int ADJUST_YTS = 1 << 1;
    public static final int EXTRAPOLATE_PAYOFF_FLAT = 1 << 2;
    public static final int NO_PAYOFF_EXTRAPOLATION = 1 << 3;
    public static final int KAHALE_SMILE = 1 << 4;
    public static final int SMILE_EXPONENTIAL_EXTRAPOLATION = 1 << 5;
    public static final int KAHALE_INTERPOLATION = 1 << 6;
    public static final int SMILE_DELETE_ARBITRAGE_POINTS = 1 << 7;
    public static final int SABR_SMILE = 1 << 8;
    public static final int CUSTOM_SMILE = 1 << 9;

    // ──────────────────────────────────────────────────────────────────────
    //   CustomSmileSection / CustomSmileFactory (C++ markovfunctional.hpp:103-118)
    // ──────────────────────────────────────────────────────────────────────
    private final ModelSettings modelSettings_;
    private final ModelOutputs modelOutputs_ = new ModelOutputs();

    // ──────────────────────────────────────────────────────────────────────
    //   ModelSettings (mirrors C++ MarkovFunctional::ModelSettings)
    // ──────────────────────────────────────────────────────────────────────
    private final boolean capletCalibrated_;

    // ──────────────────────────────────────────────────────────────────────
    //   CalibrationPoint (mirrors C++ struct)
    // ──────────────────────────────────────────────────────────────────────
    private final List< Interpolation > numeraire_ = new ArrayList<>();

    // ──────────────────────────────────────────────────────────────────────
    //   ModelOutputs — diagnostics
    // ──────────────────────────────────────────────────────────────────────
    // Note: arguments_ is now owned by the CalibratedModel composition delegate
    // on the base {@link Gaussian1dModel}; subclasses register via {@link
    // Gaussian1dModel#addArgument} / {@link Gaussian1dModel#setArgument} (Phase 1
    // closure A8-B). The previous local {@code List<Parameter> arguments_}
    // shadow was removed when {@code calibrate(...)} was hoisted to Gaussian1dModel.

    // ──────────────────────────────────────────────────────────────────────
    //   Fields (mirror C++ private members)
    // ──────────────────────────────────────────────────────────────────────
    private final List< Date > volstepdates_;
    private final List< Double > volsteptimes_ = new ArrayList<>();
    private final double[] volatilities_;
    private final Handle< SwaptionVolatilityStructure > swaptionVol_;
    private final Handle< OptionletVolatilityStructure > capletVol_;
    private final List< Date > swaptionExpiries_;
    private final List< Date > capletExpiries_;
    private final List< Period > swaptionTenors_;
    private final SwapIndex swapIndexBase_;
    private final IborIndex iborIndex_;
    /**
     * C++ uses {@code std::map<Date, CalibrationPoint>} (sorted by Date key). {@link TreeMap} mirrors this exactly —
     * A20 discipline.
     */
    private final TreeMap< Date, CalibrationPoint > calibrationPoints_ = new TreeMap<>();
    private final List< Double > times_ = new ArrayList<>();
    private final List< int[] > arbitrageIndices_ = new ArrayList<>(); // pairs (left, right)
    private final List< int[] > forcedArbitrageIndices_ = new ArrayList<>();
    private Matrix discreteNumeraire_;
    private final Parameter reversion_;
    private Parameter sigma_;
    private double[] volsteptimesArray_;
    private Date numeraireDate_;
    private double numeraireTime_;
    private Array y_;
    private double[] normalIntegralX_;
    private double[] normalIntegralW_;
    /** Swaption-smile-calibrated MarkovFunctional. */
    public MarkovFunctional(final Handle< YieldTermStructure > termStructure, final double reversion,
            final List< Date > volstepdates, final double[] volatilities,
            final Handle< SwaptionVolatilityStructure > swaptionVol, final List< Date > swaptionExpiries,
            final List< Period > swaptionTenors, final SwapIndex swapIndexBase, final ModelSettings modelSettings) {
        super(termStructure);

        QL.require(swaptionExpiries.size() == swaptionTenors.size(),
                "swaption expiries (%d) differs from swaption tenors (%d)", swaptionExpiries.size(),
                swaptionTenors.size());
        QL.require(!swaptionExpiries.isEmpty(), "need at least one swaption expiry to calibrate numeraire");
        QL.require(termStructure != null && !termStructure.empty(), "yield term structure handle is empty");
        QL.require(swaptionVol != null && !swaptionVol.empty(), "swaption volatility structure is empty");

        this.modelSettings_ = modelSettings == null ? new ModelSettings() : modelSettings;
        this.modelSettings_.validate();
        this.capletCalibrated_ = false;
        // arguments_ slot for sigma_ is created on the Gaussian1dModel composition
        // delegate during initialize() (after sigma_ is built); seed a placeholder
        // here so the slot index 0 is reserved.
        addArgument(new NullParameter());
        this.volstepdates_ = new ArrayList<>(volstepdates);
        this.volatilities_ = volatilities.clone();
        this.swaptionVol_ = swaptionVol;
        this.capletVol_ = new Handle< OptionletVolatilityStructure >();
        this.swaptionExpiries_ = new ArrayList<>(swaptionExpiries);
        this.swaptionTenors_ = new ArrayList<>(swaptionTenors);
        this.capletExpiries_ = new ArrayList<>();
        this.swapIndexBase_ = swapIndexBase;
        this.iborIndex_ = swapIndexBase.iborIndex();
        this.reversion_ = new ConstantParameter(reversion, new NoConstraint());

        initialize();
    }

    /** Caplet-smile-calibrated MarkovFunctional. */
    public MarkovFunctional(final Handle< YieldTermStructure > termStructure, final double reversion,
            final List< Date > volstepdates, final double[] volatilities,
            final Handle< OptionletVolatilityStructure > capletVol, final List< Date > capletExpiries,
            final IborIndex iborIndex, final ModelSettings modelSettings) {
        super(termStructure);

        QL.require(!capletExpiries.isEmpty(), "need at least one caplet expiry to calibrate numeraire");
        QL.require(termStructure != null && !termStructure.empty(), "yield term structure handle is empty");
        QL.require(capletVol != null && !capletVol.empty(), "caplet volatility structure is empty");

        this.modelSettings_ = modelSettings == null ? new ModelSettings() : modelSettings;
        this.modelSettings_.validate();
        this.capletCalibrated_ = true;
        // arguments_ slot for sigma_ is created on the Gaussian1dModel composition
        // delegate during initialize() (after sigma_ is built); seed a placeholder
        // here so the slot index 0 is reserved.
        addArgument(new NullParameter());
        this.volstepdates_ = new ArrayList<>(volstepdates);
        this.volatilities_ = volatilities.clone();
        this.swaptionVol_ = new Handle< SwaptionVolatilityStructure >();
        this.capletVol_ = capletVol;
        this.swaptionExpiries_ = new ArrayList<>();
        this.swaptionTenors_ = new ArrayList<>();
        this.capletExpiries_ = new ArrayList<>(capletExpiries);
        this.swapIndexBase_ = null;
        this.iborIndex_ = iborIndex;
        this.reversion_ = new ConstantParameter(reversion, new NoConstraint());

        initialize();
    }

    private static double[] arrayCopy(final Array a) {
        final double[] out = new double[a.size()];
        for ( int i = 0; i < out.length; i++ )
            out[i] = a.get(i);
        return out;
    }

    private static double[] toDoubleArray(final List< Double > xs) {
        final double[] out = new double[xs.size()];
        for ( int i = 0; i < xs.size(); i++ )
            out[i] = xs.get(i);
        return out;
    }

    // ──────────────────────────────────────────────────────────────────────
    //   Constructors (2 — swaption-calibrated, caplet-calibrated)
    // ──────────────────────────────────────────────────────────────────────

    public ModelSettings modelSettings() {
        return modelSettings_;
    }

    public Date numeraireDate() {
        return numeraireDate_;
    }

    public double numeraireTime() {
        return numeraireTime_;
    }

    // ──────────────────────────────────────────────────────────────────────
    //   Public accessors
    // ──────────────────────────────────────────────────────────────────────

    /** Returns the sigma parameter values (mirrors C++ {@code volatility()}). */
    public Array volatility() {
        return sigma_.params();
    }

    /** Mirrors C++ {@code arbitrageIndices()} — list of (left, right) pairs as int[2]. */
    public List< int[] > arbitrageIndices() {
        calculate();
        return new ArrayList<>(arbitrageIndices_);
    }

    public void forceArbitrageIndices(final List< int[] > indices) {
        forcedArbitrageIndices_.clear();
        if ( indices != null ) {
            forcedArbitrageIndices_.addAll(indices);
        }
        update();
    }

    public ModelOutputs modelOutputs() {
        if ( modelOutputs_.dirty_ ) {
            calculate();

            modelOutputs_.marketZerorate_.clear();
            modelOutputs_.modelZerorate_.clear();
            // C++: for (Size i = 1; i < times_.size() - 1; i++)
            for ( int i = 1; i < times_.size() - 1; i++ ) {
                modelOutputs_.marketZerorate_.add(termStructure().currentLink()
                        .zeroRate(times_.get(i), org.jquantlib.termstructures.Compounding.Continuous,
                                org.jquantlib.time.Frequency.Annual, true).rate());
                modelOutputs_.modelZerorate_.add(-JQuantMath.log(zerobond(times_.get(i), 1.0e-10)) / times_.get(i));
            }

            modelOutputs_.smileStrikes_.clear();
            modelOutputs_.marketCallPremium_.clear();
            modelOutputs_.marketPutPremium_.clear();
            modelOutputs_.modelCallPremium_.clear();
            modelOutputs_.modelPutPremium_.clear();
            modelOutputs_.marketVega_.clear();
            modelOutputs_.marketRawCallPremium_.clear();
            modelOutputs_.marketRawPutPremium_.clear();

            for ( Map.Entry< Date, CalibrationPoint > e : calibrationPoints_.entrySet() ) {
                modelOutputs_.atm_.add(e.getValue().atm_);
                modelOutputs_.annuity_.add(e.getValue().annuity_);
                // marketVega population skipped (Java SmileSection.vega not yet ported);
                // placeholder empty list per case to keep shape consistent.
                modelOutputs_.marketVega_.add(new ArrayList<>());
                // smileStrikes / market*Premium population deferred — exposed as empty lists.
                modelOutputs_.smileStrikes_.add(new ArrayList<>());
                modelOutputs_.marketCallPremium_.add(new ArrayList<>());
                modelOutputs_.marketPutPremium_.add(new ArrayList<>());
                modelOutputs_.modelCallPremium_.add(new ArrayList<>());
                modelOutputs_.modelPutPremium_.add(new ArrayList<>());
                modelOutputs_.marketRawCallPremium_.add(new ArrayList<>());
                modelOutputs_.marketRawPutPremium_.add(new ArrayList<>());
            }

            modelOutputs_.dirty_ = false;
        }
        return modelOutputs_;
    }

    private void initialize() {
        modelOutputs_.dirty_ = true;
        modelOutputs_.settings_ = modelSettings_;

        final GaussHermiteIntegration gh = new GaussHermiteIntegration(modelSettings_.gaussHermitePoints_);
        // GaussianQuadrature exposes x(i) and weight(i) — Phase 2j.5 C.1 API.
        final int ghN = gh.order();
        normalIntegralX_ = new double[ghN];
        normalIntegralW_ = new double[ghN];
        for ( int i = 0; i < ghN; i++ ) {
            normalIntegralX_[i] = gh.x(i);
            normalIntegralW_[i] = gh.weight(i);
        }
        for ( int i = 0; i < normalIntegralX_.length; i++ ) {
            normalIntegralW_[i] *= JQuantMath.exp(-normalIntegralX_[i] * normalIntegralX_[i]) * Constants.M_1_SQRTPI;
            normalIntegralX_[i] *= Constants.M_SQRT2;
        }

        volsteptimesArray_ = new double[volstepdates_.size()];

        updateTimes1();

        if ( capletCalibrated_ ) {
            for ( final Date d : capletExpiries_ ) {
                makeCapletCalibrationPoint(d);
            }
        } else {
            for ( int i = 0; i < swaptionExpiries_.size(); i++ ) {
                makeSwaptionCalibrationPoint(swaptionExpiries_.get(i), swaptionTenors_.get(i));
            }
        }

        // Discover the global numeraire date as the latest payment date,
        // and pad the calibration map with intermediate payment dates as
        // required by the model. Mirrors C++ initialize() do{...}while
        // numeraire-completion loop.
        boolean done;
        numeraireDate_ = Date.minDate();
        do {
            Date numeraireKnown = numeraireDate_;
            done = true;

            // Iterate calibrationPoints_ in REVERSE order (C++ rbegin..rend).
            final NavigableMap< Date, CalibrationPoint > desc = calibrationPoints_.descendingMap();
            outer:
            for ( Map.Entry< Date, CalibrationPoint > e : desc.entrySet() ) {
                final Date expiry = e.getKey();
                final CalibrationPoint cp = e.getValue();
                if ( !done )
                    break;

                final Date lastPay = cp.paymentDates_.get(cp.paymentDates_.size() - 1);
                if ( lastPay.gt(numeraireDate_) ) {
                    numeraireDate_ = lastPay;
                    numeraireKnown = lastPay;
                    // C++: done = (i == calibrationPoints_.rbegin())
                    done = expiry.equals(calibrationPoints_.lastKey());
                }

                // Walk paymentDates in reverse
                for ( int j = cp.paymentDates_.size() - 1; j >= 0 && done; j-- ) {
                    final Date pd = cp.paymentDates_.get(j);
                    if ( pd.lt(numeraireKnown) ) {
                        if ( capletCalibrated_ ) {
                            makeCapletCalibrationPoint(pd);
                            done = false;
                            break outer;
                        } else {
                            int months = Math.max(1,
                                    (int) (((numeraireKnown.serialNumber() - pd.serialNumber()) / 365.25) * 12.0));
                            while ( underlyingSwap(swapIndexBase_, pd,
                                    new Period(months, TimeUnit.Months)).maturityDate().lt(numeraireKnown) ) {
                                ++months;
                            }
                            makeSwaptionCalibrationPoint(pd, new Period(months, TimeUnit.Months));
                            done = false;
                            break outer;
                        }
                    }
                }
                if ( done ) {
                    numeraireKnown = expiry;
                }
            }
        } while ( !done );

        updateTimes2();

        sigma_ = new PiecewiseConstantParameter(toDoubleArray(volsteptimes_));
        for ( int i = 0; i < sigma_.size(); i++ ) {
            sigma_.setParam(i, volatilities_[i]);
        }
        // Register sigma_ as argument slot 0 on the Gaussian1dModel CalibratedModel delegate.
        // Equivalent to C++ MarkovFunctional::initialize() lines that populate arguments_[0]
        // (CalibratedModel base inherited via multiple inheritance from MarkovFunctional in C++).
        setArgument(0, sigma_);

        stateProcess_ = new MfStateProcess(reversion_.get(0.0), volsteptimesArray_, arrayCopy(sigma_.params()));

        y_ = yGrid(modelSettings_.yStdDevs_, modelSettings_.yGridPoints_);

        discreteNumeraire_ = new Matrix(times_.size(), 2 * modelSettings_.yGridPoints_ + 1);
        // Initialize to 1.0 — mirrors C++ Matrix(rows, cols, 1.0) ctor.
        for ( int r = 0; r < discreteNumeraire_.rows(); r++ ) {
            for ( int c2 = 0; c2 < discreteNumeraire_.columns(); c2++ ) {
                discreteNumeraire_.set(r, c2, 1.0);
            }
        }
        numeraire_.clear();
        for ( int i = 0; i < times_.size(); i++ ) {
            final Array row = new Array(2 * modelSettings_.yGridPoints_ + 1);
            for ( int j = 0; j < row.size(); j++ ) {
                row.set(j, discreteNumeraire_.get(i, j));
            }
            final CubicInterpolation cubic = new CubicInterpolation(y_, row,
                    CubicInterpolation.DerivativeApprox.Spline, true, CubicInterpolation.BoundaryCondition.Lagrange,
                    0.0, CubicInterpolation.BoundaryCondition.Lagrange, 0.0);
            cubic.enableExtrapolation();
            // v1.43 folds the flat extrapolation into the interpolation object instead of clamping the abscissa at
            // every call site. Mirrors C++ markovfunctional.cpp; numerically identical, but the clamping now lives in
            // one place. The decorator needs its own extrapolation flag enabled — it does not inherit the
            // decorated object's.
            final FlatExtrapolator numInt = new FlatExtrapolator(cubic);
            numInt.enableExtrapolation();
            numeraire_.add(numInt);
        }

        // termStructure() observer registration is already done by Gaussian1dModel ctor.
        if ( !swaptionVol_.empty() ) {
            swaptionVol_.addObserver(this);
        }
        if ( !capletVol_.empty() ) {
            capletVol_.addObserver(this);
        }
    }

    private void updateTimes() {
        updateTimes1();
        updateTimes2();
    }

    private void updateTimes1() {
        volsteptimes_.clear();
        final YieldTermStructure ts = termStructure().currentLink();
        for ( int j = 0; j < volstepdates_.size(); j++ ) {
            volsteptimes_.add(ts.timeFromReference(volstepdates_.get(j)));
            volsteptimesArray_[j] = volsteptimes_.get(j);
            if ( j == 0 ) {
                QL.require(volsteptimes_.get(0) > 0.0, "volsteptimes must be positive (%f)", volsteptimes_.get(0));
            } else {
                QL.require(volsteptimes_.get(j) > volsteptimes_.get(j - 1),
                        "volsteptimes must be strictly increasing (%f@%d, %f@%d)", volsteptimes_.get(j - 1), j - 1,
                        volsteptimes_.get(j), j);
            }
        }
        if ( stateProcess_ != null ) {
            ((MfStateProcess) stateProcess_).setTimes(volsteptimesArray_.clone());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //   Initialization (mirror C++ initialize())
    // ──────────────────────────────────────────────────────────────────────

    private void updateTimes2() {
        numeraireTime_ = termStructure().currentLink().timeFromReference(numeraireDate_);
        times_.clear();
        times_.add(0.0);
        modelOutputs_.expiries_.clear();
        modelOutputs_.tenors_.clear();
        for ( Map.Entry< Date, CalibrationPoint > e : calibrationPoints_.entrySet() ) {
            times_.add(termStructure().currentLink().timeFromReference(e.getKey()));
            modelOutputs_.expiries_.add(e.getKey());
            modelOutputs_.tenors_.add(e.getValue().tenor_);
        }
        times_.add(numeraireTime_);
        QL.require(volatilities_.length == volsteptimes_.size() + 1,
                "there must be n+1 volatilities (%d) for n volatility step times (%d)", volatilities_.length,
                volsteptimes_.size());
    }

    private void makeSwaptionCalibrationPoint(final Date expiry, final Period tenor) {
        QL.require(!calibrationPoints_.containsKey(expiry),
                "swaption expiry (%s) occurs more than once in calibration set", expiry);

        final CalibrationPoint p = new CalibrationPoint();
        p.isCaplet_ = false;
        p.tenor_ = tenor;

        final VanillaSwap underlying = underlyingSwap(swapIndexBase_, expiry, tenor);
        final Schedule sched = underlying.fixedSchedule();
        final Calendar cal = sched.calendar();
        final BusinessDayConvention bdc = underlying.paymentConvention();

        for ( int k = 1; k < sched.size(); k++ ) {
            final Date prevDate = (k == 1) ? expiry : sched.date(k - 1);
            p.yearFractions_.add(swapIndexBase_.dayCounter().yearFraction(prevDate, sched.date(k)));
            p.paymentDates_.add(cal.adjust(sched.date(k), bdc));
        }
        calibrationPoints_.put(expiry, p);
    }

    private void makeCapletCalibrationPoint(final Date expiry) {
        QL.require(!calibrationPoints_.containsKey(expiry),
                "caplet expiry (%s) occurs more than once in calibration set", expiry);

        final CalibrationPoint p = new CalibrationPoint();
        p.isCaplet_ = true;
        p.tenor_ = iborIndex_.tenor();
        final Date valueDate = iborIndex_.valueDate(expiry);
        final Date endDate = iborIndex_.fixingCalendar()
                .advance(valueDate, iborIndex_.tenor(), iborIndex_.businessDayConvention(), iborIndex_.endOfMonth());
        p.paymentDates_.add(endDate);
        p.yearFractions_.add(iborIndex_.dayCounter().yearFraction(expiry, endDate));
        calibrationPoints_.put(expiry, p);
    }

    // ──────────────────────────────────────────────────────────────────────
    //   updateTimes / updateTimes1 / updateTimes2 (mirror C++)
    // ──────────────────────────────────────────────────────────────────────

    private void updateSmiles() {
        modelOutputs_.dirty_ = true;
        arbitrageIndices_.clear();

        int pointIndex = 0;
        for ( Map.Entry< Date, CalibrationPoint > e : calibrationPoints_.descendingMap().entrySet() ) {
            final Date expiry = e.getKey();
            final CalibrationPoint cp = e.getValue();

            SmileSection smileSection;
            if ( cp.isCaplet_ ) {
                cp.annuity_ = cp.yearFractions_.get(0) * termStructure().currentLink()
                        .discount(cp.paymentDates_.get(0), true);
                cp.atm_ = (termStructure().currentLink().discount(expiry, true) - termStructure().currentLink()
                        .discount(cp.paymentDates_.get(0), true)) / cp.annuity_;
                smileSection = capletVol_.currentLink().smileSection(expiry, true);
            } else {
                double annuity = 0.0;
                for ( int k = 0; k < cp.paymentDates_.size(); k++ ) {
                    annuity += cp.yearFractions_.get(k) * termStructure().currentLink()
                            .discount(cp.paymentDates_.get(k), true);
                }
                cp.annuity_ = annuity;
                cp.atm_ = (termStructure().currentLink().discount(expiry, true) - termStructure().currentLink()
                        .discount(cp.paymentDates_.get(cp.paymentDates_.size() - 1), true)) / annuity;
                smileSection = swaptionVol_.currentLink().smileSection(expiry, cp.tenor_, true);
            }

            cp.rawSmileSection_ = new AtmSmileSection(smileSection, cp.atm_);

            int forcedLeftIndex = -1;
            int forcedRightIndex = Integer.MAX_VALUE;
            if ( forcedArbitrageIndices_.size() > pointIndex ) {
                forcedLeftIndex = forcedArbitrageIndices_.get(pointIndex)[0];
                forcedRightIndex = forcedArbitrageIndices_.get(pointIndex)[1];
            }

            if ( (modelSettings_.adjustments_ & KAHALE_SMILE) != 0 ) {
                final KahaleSmileSection ks = new KahaleSmileSection(cp.rawSmileSection_, cp.atm_,
                        (modelSettings_.adjustments_ & KAHALE_INTERPOLATION) != 0,
                        (modelSettings_.adjustments_ & SMILE_EXPONENTIAL_EXTRAPOLATION) != 0,
                        (modelSettings_.adjustments_ & SMILE_DELETE_ARBITRAGE_POINTS) != 0,
                        modelSettings_.smileMoneynessCheckpoints_, modelSettings_.digitalGap_, forcedLeftIndex,
                        forcedRightIndex);
                cp.smileSection_ = ks;
                arbitrageIndices_.add(ks.coreIndices());
            } else if ( (modelSettings_.adjustments_ & SABR_SMILE) != 0 ) {
                // Mirror C++ markovfunctional.cpp lines 355-406:
                // 1) Build a strike grid from SmileSectionUtils (erase first zero-strike entry).
                // 2) Require ShiftedLognormal input (Normal not supported).
                // 3) Require >= 4 strikes for SABR calibration.
                // 4) Fit SabrInterpolatedSmileSection (initial params: α=0.03, β=0.80, ν=0.50, ρ=0.00).
                // 5) Superimpose KahaleSmileSection for arbitrage-freeness.
                final SmileSectionUtils ssutils = new SmileSectionUtils(cp.rawSmileSection_,
                        modelSettings_.smileMoneynessCheckpoints_, cp.atm_);
                double[] strikeGrid = ssutils.strikeGrid();
                // Erase first entry (zero / at-barrier strike not wanted in SABR calibration).
                final double[] k = new double[strikeGrid.length - 1];
                System.arraycopy(strikeGrid, 1, k, 0, k.length);

                QL.require(cp.rawSmileSection_.volatilityType() == VolatilityType.ShiftedLognormal,
                        "MarkovFunctional: SABR calibration to normal input volatilities is not supported");
                QL.require(k.length >= 4, "for sabr calibration at least 4 points are needed (is %d)", k.length);

                // Sample volatilities from rawSmileSection at the chosen strikes.
                final double[] v = new double[k.length];
                for ( int ki = 0; ki < k.length; ki++ ) {
                    v[ki] = cp.rawSmileSection_.volatility(k[ki]);
                }

                // Fit SABR — initial params match C++ (α=0.03, β=0.80, ν=0.50, ρ=0.00, all free).
                final SabrInterpolatedSmileSection sabrSection = new SabrInterpolatedSmileSection(expiry, cp.atm_, k,
                        false, cp.rawSmileSection_.volatility(cp.atm_), v, 0.03, 0.80, 0.50, 0.00, false, false, false,
                        false, true, null, null, new Actual365Fixed(), cp.rawSmileSection_.shift());

                // Superimpose Kahale for arbitrage-freeness (mirrors C++ lines 390-406).
                final KahaleSmileSection ks = new KahaleSmileSection(sabrSection, cp.atm_, false,
                        (modelSettings_.adjustments_ & SMILE_EXPONENTIAL_EXTRAPOLATION) != 0,
                        (modelSettings_.adjustments_ & SMILE_DELETE_ARBITRAGE_POINTS) != 0,
                        modelSettings_.smileMoneynessCheckpoints_, modelSettings_.digitalGap_, forcedLeftIndex,
                        forcedRightIndex);
                cp.smileSection_ = ks;
                arbitrageIndices_.add(ks.coreIndices());

            } else if ( (modelSettings_.adjustments_ & CUSTOM_SMILE) != 0 ) {
                // Custom smile is arbitrage-free by assumption (mirrors C++ markovfunctional.cpp:408-420).
                cp.smileSection_ = modelSettings_.customSmileFactory_.smileSection(cp.rawSmileSection_, cp.atm_);
                arbitrageIndices_.add(new int[] { -1, Integer.MAX_VALUE });
            } else {
                // No smile pretreatment (no Kahale / no SABR / no CustomSmile).
                cp.smileSection_ = cp.rawSmileSection_;
            }

            // Compute min/max digital prices — skipped for CustomSmile (it handles its own inversion).
            if ( (modelSettings_.adjustments_ & CUSTOM_SMILE) == 0 ) {
                cp.minRateDigital_ = cp.smileSection_.digitalOptionPrice(
                        modelSettings_.lowerRateBound_ - cp.smileSection_.shift(), Option.Type.Call, cp.annuity_,
                        modelSettings_.digitalGap_);
                cp.maxRateDigital_ = cp.smileSection_.digitalOptionPrice(
                        modelSettings_.upperRateBound_ - cp.smileSection_.shift(), Option.Type.Call, cp.annuity_,
                        modelSettings_.digitalGap_);
            }

            ++pointIndex;
        }
    }

    private void updateNumeraireTabulation() {
        modelOutputs_.dirty_ = true;
        modelOutputs_.adjustmentFactors_.clear();
        modelOutputs_.digitalsAdjustmentFactors_.clear();

        int idx = times_.size() - 2;

        // Reverse iteration over calibrationPoints_ — A20 discipline.
        for ( Map.Entry< Date, CalibrationPoint > e : calibrationPoints_.descendingMap().entrySet() ) {
            final CalibrationPoint cp = e.getValue();

            // For CustomSmile, cast the smile section to retrieve inverseDigitalCall.
            final CustomSmileSection customSec;
            if ( (modelSettings_.adjustments_ & CUSTOM_SMILE) != 0 ) {
                QL.require(cp.smileSection_ instanceof CustomSmileSection,
                        "no CustomSmileSection given, this is unexpected...");
                customSec = (CustomSmileSection) cp.smileSection_;
            } else {
                customSec = null;
            }

            final Array discreteDeflatedAnnuities = new Array(y_.size());
            for ( int j = 0; j < y_.size(); j++ )
                discreteDeflatedAnnuities.set(j, 0.0);
            Array deflatedFinalPayments = null;

            final double numeraire0 = termStructure().currentLink().discount(numeraireTime_, true);
            final double normalization = termStructure().currentLink().discount(times_.get(idx), true) / numeraire0;

            for ( int k = 0; k < cp.paymentDates_.size(); k++ ) {
                deflatedFinalPayments = deflatedZerobondArray(
                        termStructure().currentLink().timeFromReference(cp.paymentDates_.get(k)), times_.get(idx), y_);
                final double yf = cp.yearFractions_.get(k);
                for ( int j = 0; j < y_.size(); j++ ) {
                    discreteDeflatedAnnuities.set(j,
                            discreteDeflatedAnnuities.get(j) + deflatedFinalPayments.get(j) * yf);
                }
            }

            final CubicInterpolation deflatedAnnuities = new CubicInterpolation(y_, discreteDeflatedAnnuities,
                    CubicInterpolation.DerivativeApprox.Spline, true, CubicInterpolation.BoundaryCondition.Lagrange,
                    0.0, CubicInterpolation.BoundaryCondition.Lagrange, 0.0);

            double digitalsCorrectionFactor = 1.0;
            modelOutputs_.digitalsAdjustmentFactors_.add(0, digitalsCorrectionFactor);

            double digital = 0.0;
            double swapRate = 0.0;
            double swapRate0;

            // Outer loop: c=0 always; c=1 only if AdjustDigitals
            final boolean adjustDigitals = (modelSettings_.adjustments_ & ADJUST_DIGITALS) != 0;
            for ( int c = 0; c == 0 || (c == 1 && adjustDigitals); c++ ) {
                if ( c == 1 ) {
                    digitalsCorrectionFactor = cp.annuity_ / digital;
                    modelOutputs_.digitalsAdjustmentFactors_.set(0, digitalsCorrectionFactor);
                }

                digital = 0.0;
                swapRate0 = modelSettings_.upperRateBound_ / 2.0; // initial guess

                for ( int j = y_.size() - 1; j >= 0; j-- ) {
                    double integral = 0.0;

                    if ( j == y_.size() - 1 ) {
                        if ( (modelSettings_.adjustments_ & NO_PAYOFF_EXTRAPOLATION) == 0 ) {
                            if ( (modelSettings_.adjustments_ & EXTRAPOLATE_PAYOFF_FLAT) != 0 ) {
                                integral = Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, 0.0, 0.0, 0.0,
                                        discreteDeflatedAnnuities.get(j - 1), y_.get(j - 1), y_.get(j), 100.0);
                            } else {
                                final double ca = deflatedAnnuities.aCoefficients().get(j - 1);
                                final double cb = deflatedAnnuities.bCoefficients().get(j - 1);
                                final double cc = deflatedAnnuities.cCoefficients().get(j - 1);
                                integral = Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cc, cb, ca,
                                        discreteDeflatedAnnuities.get(j - 1), y_.get(j - 1), y_.get(j), 100.0);
                            }
                        }
                    } else {
                        final double ca = deflatedAnnuities.aCoefficients().get(j);
                        final double cb = deflatedAnnuities.bCoefficients().get(j);
                        final double cc = deflatedAnnuities.cCoefficients().get(j);
                        integral = Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cc, cb, ca,
                                discreteDeflatedAnnuities.get(j), y_.get(j), y_.get(j), y_.get(j + 1));
                    }

                    if ( integral < 0 ) {
                        modelOutputs_.messages_.add(
                                "WARNING: integral for digitalPrice negative for j=" + j + " (" + integral
                                        + ") --- reset it to zero.");
                        integral = 0.0;
                    }

                    digital += integral * numeraire0 * digitalsCorrectionFactor;

                    boolean check = true;
                    if ( customSec != null ) {
                        // CustomSmile handles its own inversion (mirrors C++ markovfunctional.cpp:551-553).
                        swapRate = customSec.inverseDigitalCall(digital, cp.annuity_);
                    } else if ( digital >= cp.minRateDigital_ ) {
                        swapRate = modelSettings_.lowerRateBound_ - cp.rawSmileSection_.shift();
                        check = false;
                    } else if ( digital <= cp.maxRateDigital_ ) {
                        swapRate = modelSettings_.upperRateBound_;
                        check = false;
                    } else {
                        swapRate = marketSwapRate(e.getKey(), cp, digital, swapRate0, cp.rawSmileSection_.shift());
                    }
                    if ( check && j < y_.size() - 1 && swapRate > swapRate0 ) {
                        modelOutputs_.messages_.add(
                                "WARNING: swap rate decreasing in y for t=" + times_.get(idx) + ", j=" + j
                                        + " — reset to " + swapRate0);
                        swapRate = swapRate0;
                    }
                    swapRate0 = swapRate;

                    final double numeraire =
                            1.0 / Math.max(swapRate * discreteDeflatedAnnuities.get(j) + deflatedFinalPayments.get(j),
                                    1e-6);
                    discreteNumeraire_.set(idx, j, numeraire * normalization);
                }
            }

            if ( (modelSettings_.adjustments_ & ADJUST_YTS) != 0 ) {
                refreshNumeraireRow(idx);
                final double modelDeflatedZerobond = deflatedZerobond(times_.get(idx), 0.0, 0.0);
                final double marketDeflatedZerobond =
                        termStructure().currentLink().discount(times_.get(idx), true) / termStructure().currentLink()
                                .discount(numeraireTime_, true);
                for ( int j = y_.size() - 1; j >= 0; j-- ) {
                    discreteNumeraire_.set(idx, j,
                            discreteNumeraire_.get(idx, j) * modelDeflatedZerobond / marketDeflatedZerobond);
                }
                modelOutputs_.adjustmentFactors_.add(0, modelDeflatedZerobond / marketDeflatedZerobond);
            } else {
                modelOutputs_.adjustmentFactors_.add(0, 1.0);
            }

            refreshNumeraireRow(idx);
            --idx;
        }
    }

    /**
     * Re-fits the {@code numeraire_[idx]} {@link CubicInterpolation} after the corresponding row of
     * {@code discreteNumeraire_} changed. Mirrors C++ {@code numeraire_[idx]->update()}.
     */
    private void refreshNumeraireRow(final int idx) {
        final Array row = new Array(2 * modelSettings_.yGridPoints_ + 1);
        for ( int j = 0; j < row.size(); j++ ) {
            row.set(j, discreteNumeraire_.get(idx, j));
        }
        final CubicInterpolation refitted = new CubicInterpolation(y_, row,
                CubicInterpolation.DerivativeApprox.Spline, true, CubicInterpolation.BoundaryCondition.Lagrange, 0.0,
                CubicInterpolation.BoundaryCondition.Lagrange, 0.0);
        refitted.enableExtrapolation();
        final FlatExtrapolator wrapped = new FlatExtrapolator(refitted);
        wrapped.enableExtrapolation();
        numeraire_.set(idx, wrapped);
    }

    // ──────────────────────────────────────────────────────────────────────
    //   makeSwaptionCalibrationPoint / makeCapletCalibrationPoint
    // ──────────────────────────────────────────────────────────────────────

    @Override
    protected double numeraireImpl(final double t, final double y, final Handle< YieldTermStructure > yts) {
        if ( t == 0.0 ) {
            return yts.empty()
                    ? termStructure().currentLink().discount(numeraireTime(), true)
                    : yts.currentLink().discount(numeraireTime());
        }
        final Array ya = new Array(1);
        ya.set(0, y);
        final double base = numeraireArray(t, ya).get(0);
        if ( yts.empty() )
            return base;
        final YieldTermStructure ytsLink = yts.currentLink();
        return base * (
                ytsLink.discount(numeraireTime()) / ytsLink.discount(t) * termStructure().currentLink().discount(t)
                        / termStructure().currentLink().discount(numeraireTime()));
    }

    @Override
    protected double zerobondImpl(final double T, final double t, final double y,
            final Handle< YieldTermStructure > yts) {
        if ( t == 0.0 ) {
            return yts.empty() ? termStructure().currentLink().discount(T, true) : yts.currentLink().discount(T, true);
        }
        final Array ya = new Array(1);
        ya.set(0, y);
        final double base = zerobondArray(T, t, ya).get(0);
        if ( yts.empty() )
            return base;
        final YieldTermStructure ytsLink = yts.currentLink();
        return base * (ytsLink.discount(T) / ytsLink.discount(t) * termStructure().currentLink().discount(t)
                / termStructure().currentLink().discount(T));
    }

    // ──────────────────────────────────────────────────────────────────────
    //   updateSmiles / updateNumeraireTabulation
    // ──────────────────────────────────────────────────────────────────────

    Array numeraireArray(final double t, final Array y) {
        calculate();
        final Array res = new Array(y.size());
        final double tsDisc = termStructure().currentLink().discount(numeraireTime_, true);
        if ( t < Constants.QL_EPSILON ) {
            for ( int j = 0; j < y.size(); j++ )
                res.set(j, tsDisc);
            return res;
        }
        final double inverseNormalization = tsDisc / termStructure().currentLink().discount(t, true);

        final double tz = Math.min(t, times_.get(times_.size() - 1));
        // upper_bound on times_ (excluding back), then min with size-1
        int i;
        {
            int lo = 0;
            int hi = times_.size() - 1;
            while ( lo < hi ) {
                final int mid = (lo + hi) >>> 1;
                if ( times_.get(mid) > t )
                    hi = mid;
                else
                    lo = mid + 1;
            }
            i = Math.min(lo, times_.size() - 1);
        }

        final double ta = times_.get(i - 1);
        final double tb = times_.get(i);
        final double dt = tb - ta;

        for ( int j = 0; j < y.size(); j++ ) {
            // No clamping here any more: since v1.43 the numeraire interpolations extrapolate flat themselves.
            final double na = numeraire_.get(i - 1).op(y.get(j));
            final double nb = numeraire_.get(i).op(y.get(j));
            res.set(j, inverseNormalization / ((tz - ta) / nb + (tb - tz) / na) * dt);
        }
        return res;
    }

    Array zerobondArray(final double T, final double t, final Array y) {
        final Array deflated = deflatedZerobondArray(T, t, y);
        final Array num = numeraireArray(t, y);
        final Array out = new Array(y.size());
        for ( int j = 0; j < y.size(); j++ )
            out.set(j, deflated.get(j) * num.get(j));
        return out;
    }

    /**
     * Gauss-Hermite integration of the deflated zerobond (mirrors C++ {@code deflatedZerobondArray}).
     */
    Array deflatedZerobondArray(final double T, final double t, final Array y) {
        calculate();
        final Array result = new Array(y.size());
        final double stdDev_0_t = stateProcess_.stdDeviation(0.0, 0.0, t);
        final double stdDev_0_T = stateProcess_.stdDeviation(0.0, 0.0, T);
        final double stdDev_t_T = stateProcess_.stdDeviation(t, 0.0, T - t);

        for ( int j = 0; j < y.size(); j++ ) {
            final Array ya = new Array(modelSettings_.gaussHermitePoints_);
            for ( int i = 0; i < modelSettings_.gaussHermitePoints_; i++ ) {
                ya.set(i, (y.get(j) * stdDev_0_t + stdDev_t_T * normalIntegralX_[i]) / stdDev_0_T);
            }
            final Array res = numeraireArray(T, ya);
            double acc = 0.0;
            for ( int i = 0; i < modelSettings_.gaussHermitePoints_; i++ ) {
                acc += normalIntegralW_[i] / res.get(i);
            }
            result.set(j, acc);
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────
    //   numeraireImpl / zerobondImpl (Gaussian1dModel hooks)
    // ──────────────────────────────────────────────────────────────────────

    double deflatedZerobond(final double T, final double t, final double y) {
        final Array ya = new Array(1);
        ya.set(0, y);
        return deflatedZerobondArray(T, t, ya).get(0);
    }

    double marketSwapRate(final Date expiry, final CalibrationPoint cp, final double digitalPrice, final double guess,
            final double shift) {
        final Brent b = new Brent();
        // Mirror C++: clamp guess to (lower-shift+eps, upper-eps)
        final double lo = modelSettings_.lowerRateBound_ - shift + 0.00001;
        final double hi = modelSettings_.upperRateBound_ - 0.00001;
        final double clamped = Math.max(Math.min(guess, hi), lo);

        final Ops.DoubleOp z = new Ops.DoubleOp() {
            @Override
            public double op(final double strike) {
                final double modelPrice = marketDigitalPrice(expiry, cp, Option.Type.Call, strike);
                return modelPrice - digitalPrice;
            }
        };

        return b.solve(z, modelSettings_.marketRateAccuracy_, clamped, modelSettings_.lowerRateBound_ - shift,
                modelSettings_.upperRateBound_);
    }

    // ──────────────────────────────────────────────────────────────────────
    //   numeraireArray / zerobondArray / deflatedZerobond(Array)
    // ──────────────────────────────────────────────────────────────────────

    double marketDigitalPrice(final Date expiry, final CalibrationPoint cp, final Option.Type type,
            final double strike) {
        return cp.smileSection_.digitalOptionPrice(strike, type, cp.annuity_, modelSettings_.digitalGap_);
    }

    @Override
    protected void performCalculations() {
        super.performCalculations();
        updateTimes();
        updateSmiles();
        updateNumeraireTabulation();
    }

    @Override
    protected void generateArguments() {
        ((MfStateProcess) stateProcess_).setVols(arrayCopy(sigma_.params()));
        if ( isModelCalibrated() ) {
            updateNumeraireTabulation();
        } else {
            calculate();
        }
        notifyObservers();
    }

    /**
     * Java equivalent of C++ CalibratedModel::isCalculated() — Phase 2j stub that always reports false (forcing
     * recomputation on every parameter change). Renamed from {@code isCalculated()} in Phase 5e.5b-CFC-d-62 because
     * LazyObject now exposes a {@code public isCalculated()} which cannot be shadowed (Java JLS §8.4.6.3 forbids
     * reducing access).
     */
    private boolean isModelCalibrated() {
        return false; // Conservative default — recompute on parameter change.
    }

    // ──────────────────────────────────────────────────────────────────────
    //   marketSwapRate / marketDigitalPrice (used by Brent helper)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates the sigma vector and re-runs the calibration tabulation. Mirrors C++ {@code CalibratedModel::setParams}
     * composed with {@code generateArguments}.
     */
    public void setParams(final Array params) {
        QL.require(params.size() == sigma_.size(), "param vector size (%d) doesn't match sigma_ size (%d)",
                params.size(), sigma_.size());
        for ( int i = 0; i < params.size(); i++ ) {
            sigma_.setParam(i, params.get(i));
        }
        generateArguments();
    }

    /**
     * Abstract smile section returned by {@link CustomSmileFactory}.
     * <p>
     * Mirrors C++ {@code MarkovFunctional::CustomSmileSection} (markovfunctional.hpp:103-106). Implementations must
     * provide an inverse-digital-call function so the numeraire tabulation can solve for the swap rate from a digital
     * price without calling Brent (the custom smile is assumed arbitrage-free by construction).
     *
     * <p>Constructors mirror the four {@link SmileSection} constructors that do not
     * require a specific DayCounter / VolatilityType — subclasses must forward to one.
     */
    public abstract static class CustomSmileSection extends SmileSection {

        /** Construct from expiry time and day-counter (ShiftedLognormal, shift=0). */
        protected CustomSmileSection(final double exerciseTime, final org.jquantlib.daycounters.DayCounter dc) {
            super(exerciseTime, dc);
        }

        /** Construct from expiry date, day-counter, and reference date (ShiftedLognormal, shift=0). */
        protected CustomSmileSection(final Date exerciseDate, final org.jquantlib.daycounters.DayCounter dc,
                final Date referenceDate) {
            super(exerciseDate, dc, referenceDate);
        }

        /** Construct with explicit volatility type and shift from expiry time. */
        protected CustomSmileSection(final double exerciseTime, final org.jquantlib.daycounters.DayCounter dc,
                final org.jquantlib.model.VolatilityType type, final double shift) {
            super(exerciseTime, dc, type, shift);
        }

        /** Construct with explicit volatility type and shift from expiry date. */
        protected CustomSmileSection(final Date exerciseDate, final org.jquantlib.daycounters.DayCounter dc,
                final Date referenceDate, final org.jquantlib.model.VolatilityType type, final double shift) {
            super(exerciseDate, dc, referenceDate, type, shift);
        }

        /**
         * Return the swap rate {@code r} such that {@code digitalOptionPrice(r - shift, Call, discount) == price}.
         *
         * @param price    target digital call price
         * @param discount annuity discount (equals {@code CalibrationPoint.annuity_})
         * @return swap rate inverse
         */
        public abstract double inverseDigitalCall(double price, double discount);
    }

    // ──────────────────────────────────────────────────────────────────────
    //   performCalculations / generateArguments — LazyObject hooks
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Abstract factory for user-supplied smile sections used in the {@link MarkovFunctional#CUSTOM_SMILE} adjustment
     * mode.
     * <p>
     * Mirrors C++ {@code MarkovFunctional::CustomSmileFactory} (markovfunctional.hpp:108-117). Implementations
     * construct a {@link CustomSmileSection} per-fixing from the raw (ATM-shifted) smile section and the ATM rate.
     */
    public abstract static class CustomSmileFactory {
        /**
         * Construct a {@link CustomSmileSection} for a given fixing.
         *
         * @param source raw (ATM-normalised) smile section built by {@code updateSmiles()}
         * @param atm    at-the-money rate for this fixing
         * @return user-supplied {@link CustomSmileSection} (non-null, arbitrage-free)
         */
        public abstract CustomSmileSection smileSection(SmileSection source, double atm);
    }

    /**
     * Builder-style settings holder. Mirrors C++ {@code ModelSettings} 1:1 (default values, withers, validate).
     * Adjustment-bits are integer flags — see {@link MarkovFunctional#KAHALE_SMILE} etc.
     */
    public static final class ModelSettings {
        public int yGridPoints_ = 64;
        public double yStdDevs_ = 7.0;
        public int gaussHermitePoints_ = 32;
        public double digitalGap_ = 1e-5;
        public double marketRateAccuracy_ = 1e-7;
        public double lowerRateBound_ = 0.0;
        public double upperRateBound_ = 2.0;
        public int adjustments_ = KAHALE_SMILE | SMILE_EXPONENTIAL_EXTRAPOLATION;
        public double[] smileMoneynessCheckpoints_ = new double[0];
        public CustomSmileFactory customSmileFactory_ = null;

        public ModelSettings() {
        }

        public ModelSettings withYGridPoints(final int n) {
            yGridPoints_ = n;
            return this;
        }

        public ModelSettings withYStdDevs(final double s) {
            yStdDevs_ = s;
            return this;
        }

        public ModelSettings withGaussHermitePoints(final int n) {
            gaussHermitePoints_ = n;
            return this;
        }

        public ModelSettings withDigitalGap(final double g) {
            digitalGap_ = g;
            return this;
        }

        public ModelSettings withMarketRateAccuracy(final double a) {
            marketRateAccuracy_ = a;
            return this;
        }

        public ModelSettings withLowerRateBound(final double l) {
            lowerRateBound_ = l;
            return this;
        }

        public ModelSettings withUpperRateBound(final double u) {
            upperRateBound_ = u;
            return this;
        }

        public ModelSettings withAdjustments(final int a) {
            adjustments_ = a;
            return this;
        }

        public ModelSettings addAdjustment(final int a) {
            adjustments_ |= a;
            return this;
        }

        public ModelSettings removeAdjustment(final int a) {
            adjustments_ &= ~a;
            return this;
        }

        public ModelSettings withSmileMoneynessCheckpoints(final double[] m) {
            smileMoneynessCheckpoints_ = m == null ? new double[0] : m.clone();
            return this;
        }

        public ModelSettings withCustomSmileFactory(final CustomSmileFactory f) {
            customSmileFactory_ = f;
            return this;
        }

        void validate() {
            if ( (adjustments_ & KAHALE_INTERPOLATION) != 0 ) {
                addAdjustment(KAHALE_SMILE);
            }
            if ( (adjustments_ & KAHALE_SMILE) != 0 && (adjustments_ & SMILE_DELETE_ARBITRAGE_POINTS) != 0 ) {
                addAdjustment(KAHALE_INTERPOLATION);
            }
            QL.require((adjustments_ & SABR_SMILE) == 0 || (adjustments_ & KAHALE_SMILE) == 0
                            || (adjustments_ & CUSTOM_SMILE) == 0,
                    "Only one of KahaleSmile, SabrSmile and CustomSmile can be specified at the same time");
            QL.require(yGridPoints_ > 0, "yGridPoints must be > 0 (%d)", yGridPoints_);
            QL.require(yStdDevs_ > 0.0, "yStdDevs must be > 0 (%f)", yStdDevs_);
            QL.require(gaussHermitePoints_ > 0, "gaussHermitePoints must be > 0 (%d)", gaussHermitePoints_);
            QL.require(digitalGap_ > 0.0, "digitalGap must be > 0 (%f)", digitalGap_);
            QL.require(marketRateAccuracy_ > 0.0, "marketRateAccuracy must be > 0 (%f)", marketRateAccuracy_);
            QL.require((adjustments_ & KAHALE_SMILE) == 0 || lowerRateBound_ == 0.0,
                    "If Kahale extrapolation is used, the lower rate bound must be zero (got %f)", lowerRateBound_);
            QL.require(lowerRateBound_ < upperRateBound_,
                    "Lower rate bound (%f) must be strictly less than upper rate bound (%f)", lowerRateBound_,
                    upperRateBound_);
            QL.require((adjustments_ & CUSTOM_SMILE) == 0 || customSmileFactory_ != null,
                    "CustomSmile mode requires a non-null CustomSmileFactory in ModelSettings");
        }
    }

    static final class CalibrationPoint {
        final List< Date > paymentDates_ = new ArrayList<>();
        final List< Double > yearFractions_ = new ArrayList<>();
        boolean isCaplet_;
        Period tenor_;
        double atm_ = Constants.NULL_REAL;
        double annuity_ = Constants.NULL_REAL;
        SmileSection smileSection_;
        SmileSection rawSmileSection_;
        double minRateDigital_ = Constants.NULL_REAL;
        double maxRateDigital_ = Constants.NULL_REAL;
    }

    // ──────────────────────────────────────────────────────────────────────
    //   Convenience: setParams pushes new sigma into model
    // ──────────────────────────────────────────────────────────────────────

    public static final class ModelOutputs {
        public final List< Date > expiries_ = new ArrayList<>();
        public final List< Period > tenors_ = new ArrayList<>();
        public final List< Double > atm_ = new ArrayList<>();
        public final List< Double > annuity_ = new ArrayList<>();
        public final List< Double > adjustmentFactors_ = new ArrayList<>();
        public final List< Double > digitalsAdjustmentFactors_ = new ArrayList<>();
        public final List< String > messages_ = new ArrayList<>();
        public final List< List< Double > > smileStrikes_ = new ArrayList<>();
        public final List< List< Double > > marketRawCallPremium_ = new ArrayList<>();
        public final List< List< Double > > marketRawPutPremium_ = new ArrayList<>();
        public final List< List< Double > > marketCallPremium_ = new ArrayList<>();
        public final List< List< Double > > marketPutPremium_ = new ArrayList<>();
        public final List< List< Double > > modelCallPremium_ = new ArrayList<>();
        public final List< List< Double > > modelPutPremium_ = new ArrayList<>();
        public final List< List< Double > > marketVega_ = new ArrayList<>();
        public final List< Double > marketZerorate_ = new ArrayList<>();
        public final List< Double > modelZerorate_ = new ArrayList<>();
        public boolean dirty_ = true;
        public ModelSettings settings_;
    }
}
