/*
 Copyright (C) 2017, 2018 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license. You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the license for more details.
*/

package org.jquantlib.termstructures.volatilities.equityfx;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.interpolations.BackwardFlatInterpolation;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.math.interpolations.MonotonicNaturalCubicInterpolation;
import org.jquantlib.math.interpolations.NaturalCubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.*;
import org.jquantlib.methods.finitedifferences.meshers.Concentrating1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FirstDerivativeOp;
import org.jquantlib.methods.finitedifferences.operators.SecondDerivativeOp;
import org.jquantlib.methods.finitedifferences.operators.TripleBandLinearOp;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.util.LazyObject;
import org.jquantlib.util.Observable;

import java.util.*;

/**
 * Andreasen-Huge local volatility calibration and interpolation.
 *
 * <p>References: Andreasen J., Huge B., 2010. Volatility Interpolation
 * https://ssrn.com/abstract=1694972
 *
 * <p>Java port of v1.42.1
 * ql/termstructures/volatility/equityfx/andreasenhugevolatilityinterpl.{hpp,cpp}
 *
 * @author Phase 2m Track D port
 */
public class AndreasenHugeVolatilityInterpl extends LazyObject {

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------
    private final List< CalibrationEntry > calibrationSet;
    private final Handle< Quote > spot;
    private final Handle< YieldTermStructure > rTS;
    private final Handle< YieldTermStructure > qTS;
    private final InterpolationType interpolationType;
    private final CalibrationType calibrationType;
    private final int nGridPoints;
    private final double minStrikeVal;
    private final double maxStrikeVal;
    private final OptimizationMethod optimizationMethod;
    private final EndCriteria endCriteria;
    private final List< Double > strikes = new ArrayList<>();
    private final List< Date > expiries = new ArrayList<>();
    private final double[] expiryTimes;
    private final double[] dT;
    private final int[][] calibrationMatrix; // [iExpiry][iStrike] -> set-index or -1
    private final Map< Double, CacheEntry > localVolCache_ = new HashMap<>();
    private final Map< Double, CacheEntry > priceCache_ = new HashMap<>();
    private double avgError, minError, maxError;
    // mutable calibration state
    private FdmMesherComposite mesher;
    private double[] gridPoints; // log-forward coords, all grid nodes
    private double[] gridInFwd;  // spot-price values at grid nodes
    private final List< StepResult > calibrationResults = new ArrayList<>();

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------
    public AndreasenHugeVolatilityInterpl(final List< CalibrationEntry > calibrationSet, final Handle< Quote > spot,
            final Handle< YieldTermStructure > rTS, final Handle< YieldTermStructure > qTS,
            final InterpolationType interpolationType, final CalibrationType calibrationType, final int nGridPoints,
            final double minStrike, final double maxStrike, final OptimizationMethod optimizationMethod,
            final EndCriteria endCriteria) {

        QL.require(nGridPoints > 2 && !calibrationSet.isEmpty(), "undefined grid or calibration set");

        this.spot = spot;
        this.rTS = rTS;
        this.qTS = qTS;
        this.interpolationType = interpolationType;
        this.calibrationType = calibrationType;
        this.nGridPoints = nGridPoints;
        this.minStrikeVal = minStrike;
        this.maxStrikeVal = maxStrike;
        this.optimizationMethod = optimizationMethod;
        this.endCriteria = endCriteria;

        final TreeSet< Double > strikeSet = new TreeSet<>();
        final TreeSet< Date > expirySet = new TreeSet<>();

        this.calibrationSet = new ArrayList<>(calibrationSet.size());

        for ( final CalibrationEntry entry : calibrationSet ) {
            final Exercise ex = entry.option.exercise();
            QL.require(ex.type() == Exercise.Type.European, "European option required");
            expirySet.add(ex.lastDate());

            final PlainVanillaPayoff payoff = (PlainVanillaPayoff) entry.option.payoff();
            QL.require(payoff != null, "plain vanilla payoff required");
            strikeSet.add(payoff.strike());

            // Reconstruct the option to own a clean copy
            this.calibrationSet.add(new CalibrationEntry(new VanillaOption(payoff, ex), entry.vol));
        }

        strikes.addAll(strikeSet);
        expiries.addAll(expirySet);

        expiryTimes = new double[expiries.size()];
        dT = new double[expiries.size()];

        // calibrationMatrix[l][k] = index in calibrationSet, or -1
        calibrationMatrix = new int[expiries.size()][strikes.size()];
        for ( final int[] row : calibrationMatrix ) {
            java.util.Arrays.fill(row, -1);
        }

        for ( int i = 0; i < calibrationSet.size(); ++i ) {
            final CalibrationEntry entry = calibrationSet.get(i);
            final Date expiry = entry.option.exercise().lastDate();
            final int l = expiries.indexOf(expiry);
            final double strike = ((PlainVanillaPayoff) entry.option.payoff()).strike();
            int k = -1;
            for ( int j = 0; j < strikes.size(); ++j ) {
                if ( Math.abs(strikes.get(j) - strike) < 1e-10 * strike ) {
                    k = j;
                    break;
                }
            }
            if ( l >= 0 && k >= 0 )
                calibrationMatrix[l][k] = i;
        }

        // Register for change notifications
        // (LazyObject.registerWith is commented out in Java; observe via notifyObservers pattern)
        spot.addObserver(this);
        rTS.addObserver(this);
        qTS.addObserver(this);
        for ( final CalibrationEntry entry : calibrationSet ) {
            if ( entry.vol instanceof Observable ) {
                entry.vol.addObserver(this);
            }
        }
    }

    /** Default-parameter convenience constructor (matches C++ default args). */
    public AndreasenHugeVolatilityInterpl(final List< CalibrationEntry > calibrationSet, final Handle< Quote > spot,
            final Handle< YieldTermStructure > rTS, final Handle< YieldTermStructure > qTS) {
        this(calibrationSet, spot, rTS, qTS, InterpolationType.CubicSpline, CalibrationType.Call, 500, Double.NaN,
                Double.NaN, new LevenbergMarquardt(), new EndCriteria(500, 100, 1e-12, 1e-10, 1e-10));
    }

    // ------------------------------------------------------------------
    // Public interface
    // ------------------------------------------------------------------
    public Date maxDate() {
        return expiries.get(expiries.size() - 1);
    }

    public double minStrike() {
        return Double.isNaN(minStrikeVal) ? strikes.get(0) / 8.0 : minStrikeVal;
    }

    public double maxStrike() {
        return Double.isNaN(maxStrikeVal) ? 8.0 * strikes.get(strikes.size() - 1) : maxStrikeVal;
    }

    public double fwd(final double t) {
        return spot.currentLink().value() * qTS.currentLink().discount(t) / rTS.currentLink().discount(t);
    }

    public Handle< YieldTermStructure > riskFreeRate() {
        return rTS;
    }

    /** Returns {minError, maxError, avgError} (all in vol units). */
    public double[] calibrationError() {
        calculate();
        return new double[] { minError, maxError, avgError };
    }

    /**
     * Returns the (undiscounted/fwd-normalized) option price for the given time and strike. Caches results by time. In
     * case of CallPut, always returns the call-normalised price.
     */
    public double optionPrice(final double t, final double strike, final Option.Type optionType) {
        final CacheEntry cached = priceCache_.get(t);
        if ( cached != null ) {
            return resolvePrice(cached, t, strike, optionType);
        }

        calculate();

        final double[] prices;
        switch ( calibrationType ) {
        case Put:
            prices = toDoubleArray(getPriceSlice(t, Option.Type.Put));
            break;
        case Call:
        case CallPut:
            prices = toDoubleArray(getPriceSlice(t, Option.Type.Call));
            break;
        default:
            throw new IllegalStateException("unknown calibration type");
        }

        final double fwdVal =
                spot.currentLink().value() * qTS.currentLink().discount(t) / rTS.currentLink().discount(t);

        // Cubic interpolation over interior nodes [1 .. N-2]
        final double[] gp = gridPoints;
        final int n = gp.length;
        final double[] subX = java.util.Arrays.copyOfRange(gp, 1, n - 1);
        final double[] subY = java.util.Arrays.copyOfRange(prices, 1, n - 1);
        final NaturalCubicInterpolation interpl = new NaturalCubicInterpolation(new Array(subX), new Array(subY));
        interpl.enableExtrapolation();

        priceCache_.put(t, new CacheEntry(fwdVal, prices, interpl));
        return optionPrice(t, strike, optionType);
    }

    public double localVol(final double t, final double strike) {
        final CacheEntry cached = localVolCache_.get(t);
        if ( cached != null ) {
            return getCacheValue(cached, t, strike);
        }

        calculate();

        final double[] lv;
        switch ( calibrationType ) {
        case CallPut: {
            final double[] putLV = toDoubleArray(getLocalVolSlice(t, Option.Type.Put));
            final double[] callLV = toDoubleArray(getLocalVolSlice(t, Option.Type.Call));
            lv = new double[gridPoints.length];
            for ( int i = 0; i < lv.length; ++i ) {
                lv[i] = (gridPoints[i] > 0.0) ? callLV[i] : putLV[i];
            }
            break;
        }
        case Put:
            lv = toDoubleArray(getLocalVolSlice(t, Option.Type.Put));
            break;
        case Call:
            lv = toDoubleArray(getLocalVolSlice(t, Option.Type.Call));
            break;
        default:
            throw new IllegalStateException("unknown calibration type");
        }

        final double fwdVal =
                spot.currentLink().value() * qTS.currentLink().discount(t) / rTS.currentLink().discount(t);

        final int n = gridPoints.length;
        final double[] subX = java.util.Arrays.copyOfRange(gridPoints, 1, n - 1);
        final double[] subY = java.util.Arrays.copyOfRange(lv, 1, n - 1);
        final LinearInterpolation linInterpl = new LinearInterpolation(new Array(subX), new Array(subY));
        linInterpl.enableExtrapolation();

        localVolCache_.put(t, new CacheEntry(fwdVal, lv, linInterpl));
        return localVol(t, strike);
    }

    // ------------------------------------------------------------------
    // LazyObject
    // ------------------------------------------------------------------
    @Override
    protected void performCalculations() throws ArithmeticException {
        QL.require(maxStrike() > minStrike(), "max strike must be greater than min strike");

        final org.jquantlib.daycounters.DayCounter dc = rTS.currentLink().dayCounter();
        final Date refDate = rTS.currentLink().referenceDate();

        for ( int i = 0; i < expiries.size(); ++i ) {
            expiryTimes[i] = dc.yearFraction(refDate, expiries.get(i));
            dT[i] = expiryTimes[i] - (i == 0 ? 0.0 : expiryTimes[i - 1]);
        }

        final double spotVal = spot.currentLink().value();

        mesher = new FdmMesherComposite(
                new Concentrating1dMesher(Math.log(minStrike() / spotVal), Math.log(maxStrike() / spotVal), nGridPoints,
                        0.0,   // cPoint = 0 (ATM in log-fwd space)
                        0.025));// density (Track A constructor: no requireCPoint param)

        final Array gridPointsArr = mesher.locations(0);
        gridPoints = toDoubleArray(gridPointsArr);
        gridInFwd = new double[gridPoints.length];
        for ( int i = 0; i < gridPoints.length; ++i ) {
            gridInFwd[i] = Math.exp(gridPoints[i]) * spotVal;
        }

        localVolCache_.clear();
        priceCache_.clear();
        calibrationResults.clear();

        avgError = 0.0;
        minError = Double.MAX_VALUE;
        maxError = 0.0;

        // Initial boundary payoffs in log-fwd coordinates, normalized by spot.
        // Mirrors C++ v1.42.1: npvPuts[i] = PlainVanillaPayoff(Put, strike)(1.0)
        //                                = max(strike - 1.0, 0.0)
        //                     npvCalls[i] = PlainVanillaPayoff(Call, strike)(1.0)
        //                                 = max(1.0 - strike, 0.0)
        // where strike = exp(gridPoint) is the fwd-normalized strike and the
        // "underlying" is 1.0 (because everything is normalized by the forward).
        Array npvPuts = new Array(nGridPoints);
        Array npvCalls = new Array(nGridPoints);
        for ( int i = 0; i < nGridPoints; ++i ) {
            final double strike = Math.exp(gridPoints[i]); // log-fwd strike
            npvPuts.set(i, Math.max(strike - 1.0, 0.0)); // Put payoff at S=1
            npvCalls.set(i, Math.max(1.0 - strike, 0.0)); // Call payoff at S=1
        }

        for ( int i = 0; i < expiries.size(); ++i ) {
            final AHCostFunction putCostFct = buildCostFunction(i, Option.Type.Put, npvPuts);
            final AHCostFunction callCostFct = buildCostFunction(i, Option.Type.Call, npvCalls);

            final CombinedCostFunction costFunction = new CombinedCostFunction(putCostFct, callCostFct);

            final Array initVals = costFunction.initialValues();
            final PositiveConstraint constraint = new PositiveConstraint();
            final Problem problem = new Problem(costFunction, constraint, initVals);
            optimizationMethod.minimize(problem, endCriteria);

            final Array sig = problem.currentValue();

            final StepResult result = new StepResult();
            result.putNPVs = npvPuts.clone();
            result.callNPVs = npvCalls.clone();
            result.sigmas = sig.clone();
            result.costFunction = (calibrationType == CalibrationType.Call) ? callCostFct : putCostFct;
            calibrationResults.add(result);

            // Compute calibration errors (in vol units, vega-normalized)
            final Array vegaDiffs;
            switch ( calibrationType ) {
            case CallPut: {
                final Array vegaPutDiffs = putCostFct.vegaCalibrationError(sig);
                final Array vegaCallDiffs = callCostFct.vegaCalibrationError(sig);
                vegaDiffs = new Array(vegaPutDiffs.size());
                final double fwdVal =
                        spot.currentLink().value() * qTS.currentLink().discount(expiryTimes[i]) / rTS.currentLink()
                                .discount(expiryTimes[i]);
                for ( int j = 0; j < vegaDiffs.size(); ++j ) {
                    vegaDiffs.set(j, Math.abs((fwdVal > gridInFwd[j]) ? vegaPutDiffs.get(j) : vegaCallDiffs.get(j)));
                }
                break;
            }
            case Put:
                vegaDiffs = putCostFct.vegaCalibrationError(sig).abs();
                break;
            case Call:
                vegaDiffs = callCostFct.vegaCalibrationError(sig).abs();
                break;
            default:
                throw new IllegalStateException("unknown calibration type");
            }

            for ( int j = 0; j < vegaDiffs.size(); ++j ) {
                final double vd = vegaDiffs.get(j);
                avgError += vd;
                if ( vd < minError )
                    minError = vd;
                if ( vd > maxError )
                    maxError = vd;
            }

            // Advance the NPV slices
            if ( putCostFct != null ) {
                npvPuts = putCostFct.solveFor(dT[i], sig, npvPuts);
            }
            if ( callCostFct != null ) {
                npvCalls = callCostFct.solveFor(dT[i], sig, npvCalls);
            }
        }

        avgError /= calibrationSet.size();
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------
    private AHCostFunction buildCostFunction(final int iExpiry, final Option.Type optionType,
            final Array previousNPVs) {

        if ( calibrationType != CalibrationType.CallPut && (
                (calibrationType == CalibrationType.Call && optionType == Option.Type.Put) || (
                        calibrationType == CalibrationType.Put && optionType == Option.Type.Call)) ) {
            return null;
        }

        final double expiryTime = expiryTimes[iExpiry];
        final double discount = rTS.currentLink().discount(expiryTime);
        final double fwdVal = spot.currentLink().value() * qTS.currentLink().discount(expiryTime) / discount;

        // Count options at this expiry
        int nOptions = 0;
        for ( final int idx : calibrationMatrix[iExpiry] ) {
            if ( idx >= 0 )
                ++nOptions;
        }

        final Array lnMarketStrikes = new Array(nOptions);
        final Array marketNPVs = new Array(nOptions);
        final Array marketVegas = new Array(nOptions);

        for ( int j = 0, k = 0; j < strikes.size(); ++j ) {
            final int idx = calibrationMatrix[iExpiry][j];
            if ( idx < 0 )
                continue;

            final double vol = calibrationSet.get(idx).vol.value();
            final double stdDev = vol * Math.sqrt(expiryTime);

            final BlackCalculator calc = new BlackCalculator(new PlainVanillaPayoff(optionType, strikes.get(j)), fwdVal,
                    stdDev, discount);

            final double npv = calc.value();
            final double vega = calc.vega(expiryTime);

            marketNPVs.set(k, npv / (discount * fwdVal));
            marketVegas.set(k, vega / (discount * fwdVal));
            lnMarketStrikes.set(k, Math.log(strikes.get(j) / fwdVal));
            ++k;
        }

        return new AHCostFunction(marketNPVs, marketVegas, lnMarketStrikes, previousNPVs, mesher, dT[iExpiry],
                interpolationType);
    }

    private int getExerciseTimeIdx(final double t) {
        // upper_bound(expiryTimes, t) then clamped to [0, N-1]
        int idx = 0;
        while ( idx < expiryTimes.length && expiryTimes[idx] <= t )
            ++idx;
        return Math.min(expiryTimes.length - 1, idx);
    }

    private double getCacheValue(final CacheEntry e, final double t, final double strike) {
        final double k = Math.log(strike / e.fwd);
        final int n = gridPoints.length;
        final double s = Math.max(gridPoints[1], Math.min(gridPoints[n - 2], k));
        if ( e.isLinear ) {
            return e.linInterpl.op(s);
        } else {
            return e.interpl.op(s);
        }
    }

    private Array getPriceSlice(final double t, final Option.Type optionType) {
        final int iu = getExerciseTimeIdx(t);
        final StepResult r = calibrationResults.get(iu);
        return r.costFunction.solveFor((iu == 0) ? t : t - expiryTimes[iu - 1], r.sigmas,
                (optionType == Option.Type.Call) ? r.callNPVs : r.putNPVs);
    }

    private Array getLocalVolSlice(final double t, final Option.Type optionType) {
        final int iu = getExerciseTimeIdx(t);
        final StepResult r = calibrationResults.get(iu);

        final Array previousNPVs = (optionType == Option.Type.Call) ? r.callNPVs : r.putNPVs;
        final AHCostFunction cf = r.costFunction;

        final double dt = (iu == 0) ? t : t - expiryTimes[iu - 1];
        final Array sig = r.sigmas;

        final Array cAtJ = cf.solveFor(dt, sig, previousNPVs);
        final Array dCdT = cf.solveFor(dt, sig, cf.apply(cf.solveFor(dt, sig, previousNPVs)));
        final Array d2CdK2 = cf.d2CdK2(cAtJ);

        // localVol = sqrt(2 * dCdT / d2CdK2)
        final Array localVol = new Array(cAtJ.size());
        for ( int i = 0; i < localVol.size(); ++i ) {
            final double ratio = 2.0 * dCdT.get(i) / d2CdK2.get(i);
            localVol.set(i, Math.sqrt(Math.max(0.0, ratio)));
        }

        // Sanitize boundaries and NaN/negative interior
        for ( int i = 1; i < localVol.size() - 1; ++i ) {
            final double v = localVol.get(i);
            if ( !Double.isFinite(v) || v < 0.0 ) {
                localVol.set(i, 0.25);
            }
        }

        return localVol;
    }

    private double resolvePrice(final CacheEntry cached, final double t, final double strike,
            final Option.Type optionType) {
        final double df = rTS.currentLink().discount(t);
        final double fwdVal = cached.fwd;

        double price = getCacheValue(cached, t, strike);

        if ( optionType == Option.Type.Put && (calibrationType == CalibrationType.Call
                || calibrationType == CalibrationType.CallPut) ) {
            price = price + strike / fwdVal - 1.0;
        } else if ( optionType == Option.Type.Call && calibrationType == CalibrationType.Put ) {
            price = 1.0 - strike / fwdVal + price;
        }

        return price * df * fwdVal;
    }

    private double[] toDoubleArray(final Array a) {
        final double[] r = new double[a.size()];
        for ( int i = 0; i < a.size(); ++i )
            r[i] = a.get(i);
        return r;
    }

    public enum InterpolationType {PiecewiseConstant, Linear, CubicSpline}

    public enum CalibrationType {Call, Put, CallPut}

    /** One (VanillaOption, implied-vol Quote) pair in the calibration set. */
    public static final class CalibrationEntry {
        public final VanillaOption option;
        public final Quote vol;

        public CalibrationEntry(final VanillaOption option, final Quote vol) {
            this.option = option;
            this.vol = vol;
        }
    }

    // ------------------------------------------------------------------
    // Per-step calibration result container
    // ------------------------------------------------------------------
    private static final class StepResult {
        Array putNPVs;
        Array callNPVs;
        Array sigmas;
        AHCostFunction costFunction; // the one used for price lookup (call or put)
    }

    // Holds a (fwd, prices, interpolation) cache entry
    private static final class CacheEntry {
        final double fwd;
        final double[] prices; // values on the full grid
        final NaturalCubicInterpolation interpl;
        final LinearInterpolation linInterpl; // only for localVolCache_
        final boolean isLinear;

        // price cache entry (cubic)
        CacheEntry(final double fwd, final double[] prices, final NaturalCubicInterpolation interpl) {
            this.fwd = fwd;
            this.prices = prices;
            this.interpl = interpl;
            this.linInterpl = null;
            this.isLinear = false;
        }

        // local vol cache entry (linear)
        CacheEntry(final double fwd, final double[] lv, final LinearInterpolation linInterpl) {
            this.fwd = fwd;
            this.prices = lv;
            this.interpl = null;
            this.linInterpl = linInterpl;
            this.isLinear = true;
        }
    }

    // ------------------------------------------------------------------
    // Inner helper: the "AndreasenHugeCostFunction" from the C++ source
    // ------------------------------------------------------------------
    private final class AHCostFunction extends CostFunction {

        private final Array marketNPVs;
        private final Array marketVegas;
        private final Array lnMarketStrikes;
        private final Array previousNPVs;
        private final FdmMesherComposite mesher;
        private final int nGridPoints;
        private final double dT;
        private final InterpolationType interpType;

        private final FirstDerivativeOp dxMap;
        private final TripleBandLinearOp dxxMap;
        private final TripleBandLinearOp d2CdK2op;
        private final TripleBandLinearOp mapT;

        AHCostFunction(final Array marketNPVs, final Array marketVegas, final Array lnMarketStrikes,
                final Array previousNPVs, final FdmMesherComposite mesher, final double dT,
                final InterpolationType interpType) {

            this.marketNPVs = marketNPVs.clone();
            this.marketVegas = marketVegas.clone();
            this.lnMarketStrikes = lnMarketStrikes.clone();
            this.previousNPVs = previousNPVs.clone();
            this.mesher = mesher;
            this.nGridPoints = mesher.layout().size();
            this.dT = dT;
            // If only one market strike, fall back to PiecewiseConstant
            this.interpType = (lnMarketStrikes.size() > 1) ? interpType : InterpolationType.PiecewiseConstant;

            this.dxMap = new FirstDerivativeOp(0, mesher);
            this.dxxMap = new SecondDerivativeOp(0, mesher);
            // d2CdK2 = dxMap * (-1) + dxxMap  (i.e. dxMap.mult(-1).add(dxxMap))
            final double[] minusOne = new double[nGridPoints];
            java.util.Arrays.fill(minusOne, -1.0);
            this.d2CdK2op = dxMap.mult(new Array(minusOne)).add(dxxMap);
            this.mapT = new TripleBandLinearOp(0, mesher);
        }

        Array d2CdK2(final Array c) {
            return d2CdK2op.apply(c);
        }

        Array solveFor(final double dt, final Array sig, final Array b) {
            // Build sigma interpolation on [lnMarketStrikes]
            final double[] lnK = toDoubleArray(lnMarketStrikes);

            // Build z: variance * 0.5 at each grid node
            final double[] z = new double[nGridPoints];
            int idx = 0;
            for ( final FdmLinearOpIterator iter : mesher.layout() ) {
                final double lnStrike = mesher.location(iter, 0);
                final double clampedLn = Math.max(lnK[0], Math.min(lnK[lnK.length - 1], lnStrike));

                final double vol = sigmaAt(sig, lnK, clampedLn);
                z[idx++] = 0.5 * vol * vol;
            }

            final Array zArr = new Array(z);
            // mapT = zArr * dxMap  -  dxxMap * zArr  (axpyb: a*x + y*b)
            // C++ call: mapT_.axpyb(z, dxMap_, dxxMap_.mult(-z), Array())
            // axpyb(a, x, y, b) sets this = a*x + y + b  where x,y are TripleBandLinearOp
            mapT.axpyb(zArr, dxMap, dxxMap.mult(zArr.mul(-1.0)), new Array());

            // solve: (I + dt * mapT) u = b
            // Array(size, fill) does not exist — build from filled double[]
            final double[] dtArr = new double[z.length];
            java.util.Arrays.fill(dtArr, dt);
            return mapT.mult(new Array(dtArr)).solveSplitting(b, 1.0);
        }

        Array apply(final Array c) {
            return mapT.apply(c).mul(-1.0);
        }

        @Override
        public Array values(final Array sig) {
            final Array newNPVs = solveFor(dT, sig, previousNPVs);

            final double[] gridPoints = mesher.getFdm1dMeshers().get(0).locations();
            final MonotonicNaturalCubicInterpolation interpl = new MonotonicNaturalCubicInterpolation(
                    new Array(gridPoints), new Array(toDoubleArray(newNPVs)));

            final Array retVal = new Array(lnMarketStrikes.size());
            for ( int i = 0; i < retVal.size(); ++i ) {
                retVal.set(i, interpl.op(lnMarketStrikes.get(i)) - marketNPVs.get(i));
            }
            return retVal;
        }

        Array vegaCalibrationError(final Array sig) {
            // element-wise division: values(sig) / marketVegas
            final Array v = values(sig);
            final Array result = new Array(v.size());
            for ( int i = 0; i < v.size(); ++i ) {
                result.set(i, v.get(i) / marketVegas.get(i));
            }
            return result;
        }

        Array initialValues() {
            final double[] v = new double[lnMarketStrikes.size()];
            java.util.Arrays.fill(v, 0.25);
            return new Array(v);
        }

        @Override
        public double value(final Array x) {
            final Array v = values(x);
            double sum = 0.0;
            for ( int i = 0; i < v.size(); ++i ) {
                final double vi = v.get(i);
                sum += vi * vi;
            }
            return sum;
        }

        // ---- helpers -------------------------------------------------------

        private double sigmaAt(final Array sig, final double[] lnK, final double lnStrike) {
            final double[] sigArr = toDoubleArray(sig);
            switch ( interpType ) {
            case CubicSpline: {
                final NaturalCubicInterpolation interpl = new NaturalCubicInterpolation(new Array(lnK),
                        new Array(sigArr));
                interpl.enableExtrapolation();
                return interpl.op(lnStrike);
            }
            case Linear: {
                final LinearInterpolation interpl = new LinearInterpolation(new Array(lnK), new Array(sigArr));
                interpl.enableExtrapolation();
                return interpl.op(lnStrike);
            }
            case PiecewiseConstant: {
                // midpoints between strikes, last = last strike
                final double[] x = new double[lnK.length];
                for ( int i = 0; i < lnK.length - 1; ++i ) {
                    x[i] = 0.5 * (lnK[i] + lnK[i + 1]);
                }
                x[lnK.length - 1] = lnK[lnK.length - 1];
                final BackwardFlatInterpolation interpl = new BackwardFlatInterpolation(new Array(x),
                        new Array(sigArr));
                interpl.enableExtrapolation();
                return interpl.op(lnStrike);
            }
            default:
                throw new IllegalArgumentException("unknown interpolation type");
            }
        }

        private double[] toDoubleArray(final Array a) {
            final double[] r = new double[a.size()];
            for ( int i = 0; i < a.size(); ++i )
                r[i] = a.get(i);
            return r;
        }
    }

    // ------------------------------------------------------------------
    // Inner helper: CombinedCostFunction
    // ------------------------------------------------------------------
    private final class CombinedCostFunction extends CostFunction {
        private final AHCostFunction putCostFct;
        private final AHCostFunction callCostFct;

        CombinedCostFunction(final AHCostFunction put, final AHCostFunction call) {
            this.putCostFct = put;
            this.callCostFct = call;
        }

        @Override
        public Array values(final Array sig) {
            if ( putCostFct != null && callCostFct != null ) {
                final Array pv = putCostFct.values(sig);
                final Array cv = callCostFct.values(sig);
                final Array retVal = new Array(pv.size() + cv.size());
                for ( int i = 0; i < pv.size(); ++i )
                    retVal.set(i, pv.get(i));
                for ( int i = 0; i < cv.size(); ++i )
                    retVal.set(pv.size() + i, cv.get(i));
                return retVal;
            } else if ( putCostFct != null ) {
                return putCostFct.values(sig);
            } else if ( callCostFct != null ) {
                return callCostFct.values(sig);
            } else {
                throw new IllegalStateException("cost function not set");
            }
        }

        Array initialValues() {
            if ( putCostFct != null && callCostFct != null ) {
                final Array p = putCostFct.initialValues();
                final Array c = callCostFct.initialValues();
                final Array result = new Array(p.size());
                for ( int i = 0; i < p.size(); ++i )
                    result.set(i, 0.5 * (p.get(i) + c.get(i)));
                return result;
            } else if ( putCostFct != null ) {
                return putCostFct.initialValues();
            } else if ( callCostFct != null ) {
                return callCostFct.initialValues();
            } else {
                throw new IllegalStateException("cost function not set");
            }
        }

        @Override
        public double value(final Array x) {
            final Array v = values(x);
            double sum = 0.0;
            for ( int i = 0; i < v.size(); ++i ) {
                final double vi = v.get(i);
                sum += vi * vi;
            }
            return sum;
        }
    }
}
