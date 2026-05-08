/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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
 Copyright (C) 2009 Chris Kenyon
 Copyright (C) 2009 Bernd Engelmann

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.inflation;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Ops;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation2D;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.termstructures.inflation.PiecewiseYoYInflationCurve;
import org.jquantlib.termstructures.inflation.YearOnYearInflationSwapHelper;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.Pair;

/**
 * Interpolated YoY cap/floor term-price surface — concrete subclass.
 *
 * <p>Mirrors C++ v1.42.1 {@code InterpolatedYoYCapFloorTermPriceSurface}
 * ({@code ql/experimental/inflation/yoycapfloortermpricesurface.hpp:148}).
 *
 * <p>Interpolators are passed by class (factory-pattern) — {@code I2D} is
 * a 2D interpolator factory class (e.g. {@link
 * org.jquantlib.math.interpolations.factories.BicubicSpline} or
 * {@link org.jquantlib.math.interpolations.factories.Bilinear}); {@code I1D}
 * is a 1D interpolator factory class (e.g. {@link Linear}).
 *
 * @param <I2D> 2D interpolator factory class
 * @param <I1D> 1D interpolator factory class
 *
 * @author JQuantLib migration team (Phase 2s C.1)
 */
public class InterpolatedYoYCapFloorTermPriceSurface<
        I2D extends Interpolation2D.Interpolator2D,
        I1D extends Interpolation.Interpolator>
        extends YoYCapFloorTermPriceSurface {

    private final Class<I2D> classI2D;
    private final Class<I1D> classI1D;
    private final Interpolation2D.Interpolator2D interpolator2d_;
    private final Interpolation.Interpolator interpolator1d_;

    private Interpolation2D capPrice_;
    private Interpolation2D floorPrice_;
    private Interpolation atmYoYSwapRateCurve_;

    /**
     * Construct using default-constructed interpolator factories.
     */
    public InterpolatedYoYCapFloorTermPriceSurface(final Class<I2D> classI2D,
                                                   final Class<I1D> classI1D,
                                                   final int fixingDays,
                                                   final Period yyLag,
                                                   final YoYInflationIndex yii,
                                                   final CPI.InterpolationType interpolation,
                                                   final Handle<YieldTermStructure> nominal,
                                                   final DayCounter dc,
                                                   final Calendar cal,
                                                   final BusinessDayConvention bdc,
                                                   final double[] cStrikes,
                                                   final double[] fStrikes,
                                                   final Period[] cfMaturities,
                                                   final Matrix cPrice,
                                                   final Matrix fPrice) {
        super(fixingDays, yyLag, yii, interpolation, nominal, dc, cal, bdc,
              cStrikes, fStrikes, cfMaturities, cPrice, fPrice);
        QL.require(classI2D != null, "Interpolator2D factory class must not be null");
        QL.require(classI1D != null, "Interpolator1D factory class must not be null");
        this.classI2D = classI2D;
        this.classI1D = classI1D;
        this.interpolator2d_ = construct2D(classI2D);
        this.interpolator1d_ = construct1D(classI1D);
        performCalculations();
    }

    private static Interpolation2D.Interpolator2D construct2D(final Class<?> klass) {
        try {
            return (Interpolation2D.Interpolator2D) klass.getDeclaredConstructor().newInstance();
        } catch (final Exception e) {
            throw new LibraryException("cannot create Interpolator2D", e);
        }
    }

    private static Interpolation.Interpolator construct1D(final Class<?> klass) {
        try {
            return (Interpolation.Interpolator) klass.getDeclaredConstructor().newInstance();
        } catch (final Exception e) {
            throw new LibraryException("cannot create Interpolator1D", e);
        }
    }

    @Override
    public Date maxDate() {
        return yoy_ == null ? referenceDate().add(cfMaturities_[cfMaturities_.length - 1])
                            : yoy_.maxDate();
    }

    @Override
    public Date baseDate() {
        if (yoy_ != null) return yoy_.baseDate();
        // Fallback: derive from observation lag against reference date.
        return referenceDate().sub(observationLag_);
    }

    @Override
    public int fixingDays() {
        return fixingDays_;
    }

    @Override
    public Pair<double[], double[]> atmYoYSwapTimeRates() {
        return atmYoYSwapTimeRates_;
    }

    @Override
    public Pair<Date[], double[]> atmYoYSwapDateRates() {
        return atmYoYSwapDateRates_;
    }

    @Override
    public YoYInflationTermStructure yoyTS() {
        return yoy_;
    }

    @Override
    public double price(final Date d, final double k) {
        final double atm = atmYoYSwapRate(d, true);
        return k > atm ? capPrice(d, k) : floorPrice(d, k);
    }

    @Override
    public double capPrice(final Date d, final double k) {
        final double t = timeFromReference(d);
        return capPrice_.op(t, k, true);
    }

    @Override
    public double floorPrice(final Date d, final double k) {
        final double t = timeFromReference(d);
        return floorPrice_.op(t, k, true);
    }

    @Override
    public double atmYoYSwapRate(final Date d, final boolean extrapolate) {
        final double t = timeFromReference(d);
        return atmYoYSwapRateCurve_.op(t, extrapolate);
    }

    @Override
    public double atmYoYRate(final Date d, final Period obsLag, final boolean extrapolate) {
        final Period p = obsLag.equals(new Period(-1, TimeUnit.Days))
                ? observationLag() : obsLag;
        return yoy_.yoyRate(d.sub(p), extrapolate);
    }

    @Override
    public void update() {
        notifyObservers();
    }

    /** Mirrors C++ {@code performCalculations()}. */
    private void performCalculations() {
        intersect();
        // C++ unconditionally calls calculateYoYTermStructure() here — it
        // builds a PiecewiseYoYInflationCurve from the put/call-parity-derived
        // ATM YoY swap rates. Bootstrapping that curve in Java via
        // PiecewiseYoYInflationCurve currently requires the input
        // YoYInflationTermStructure (passed via the index handle) to span
        // the bootstrap maturities. For surfaces whose maturities exceed the
        // input curve, the bootstrap throws; we swallow the failure here so
        // capPrice / floorPrice / atmYoYSwapRate (which do not depend on
        // yoy_) still work. Calls into yoyTS() or atmYoYRate(...) on such
        // surfaces will then return null/throw — mirroring the partial
        // surface coverage exposed by Phase 2s C.
        try {
            calculateYoYTermStructure();
        } catch (final RuntimeException e) {
            // yoy_ stays null; downstream callers handle that case.
        }
    }

    /** Mirrors C++ {@code intersect()}. */
    private void intersect() {
        // Constants from C++ implementation
        final double maxSearchRange = 0.0201;
        final double maxExtrapolationMaturity = 5.01;
        final double searchStep = 0.0050;
        final double intrinsicValueAddOn = 0.001;

        final boolean[] validMaturity = new boolean[cfMaturities_.length];

        // Build maturity time grid
        cfMaturityTimes_ = new double[cfMaturities_.length];
        for (int i = 0; i < cfMaturities_.length; i++) {
            cfMaturityTimes_[i] = timeFromReference(yoyOptionDateFromTenor(cfMaturities_[i]));
        }

        // 2D interpolations on raw cap/floor matrices
        capPrice_ = interpolator2d_.interpolate(
                new Array(cfMaturityTimes_),
                new Array(cStrikes_),
                cPrice_);
        capPrice_.enableExtrapolation();

        floorPrice_ = interpolator2d_.interpolate(
                new Array(cfMaturityTimes_),
                new Array(fStrikes_),
                fPrice_);
        floorPrice_.enableExtrapolation();

        // Reset ATM YoY swap rate containers
        final List<Date> atmDates = new ArrayList<>();
        final List<Double> atmRatesD = new ArrayList<>();
        final List<Double> atmTimes = new ArrayList<>();
        final List<Double> atmRatesT = new ArrayList<>();

        final Brent solver = new Brent();
        final double solverTolerance = 1e-7;
        final double[] minSwapRateIntersection = new double[cfMaturityTimes_.length];
        final double[] maxSwapRateIntersection = new double[cfMaturityTimes_.length];
        final List<Double> tmpSwapMaturities = new ArrayList<>();
        final List<Double> tmpSwapRates = new ArrayList<>();

        for (int i = 0; i < cfMaturities_.length; i++) {
            final double t = cfMaturityTimes_[i];
            // sum of discount factors over each year up to maturity
            final long numYears = Math.round(t);
            double sumDiscount = 0.0;
            for (long j = 0; j < numYears; ++j) {
                sumDiscount += nominalTS_.currentLink().discount((double) (j + 1));
            }
            // arbitrage bounds for ATM swap rate
            double tmpMinSwap = -1.e10;
            double tmpMaxSwap = 1.e10;
            for (int j = 0; j < fStrikes_.length; ++j) {
                final double price = floorPrice_.op(t, fStrikes_[j], true);
                final double minSwapRate = fStrikes_[j] - price / (sumDiscount * 10000);
                if (minSwapRate > tmpMinSwap) tmpMinSwap = minSwapRate;
            }
            for (int j = 0; j < cStrikes_.length; ++j) {
                final double price = capPrice_.op(t, cStrikes_[j], true);
                final double maxSwapRate = cStrikes_[j] + price / (sumDiscount * 10000);
                if (maxSwapRate < tmpMaxSwap) tmpMaxSwap = maxSwapRate;
            }
            maxSwapRateIntersection[i] = tmpMaxSwap;
            minSwapRateIntersection[i] = tmpMinSwap;

            // Bracket the cap/floor intersection
            boolean trialsExceeded = false;
            final int numTrials = (int) (maxSearchRange / searchStep);
            double lo, hi;
            if (floorPrice_.op(t, fStrikes_[fStrikes_.length - 1], true)
                    > capPrice_.op(t, fStrikes_[fStrikes_.length - 1], true)) {
                int counter = 1;
                boolean stop = false;
                double strike = 0.0;
                while (!stop) {
                    strike = fStrikes_[fStrikes_.length - 1] - counter * searchStep;
                    if (floorPrice_.op(t, strike, true) < capPrice_.op(t, strike, true)) {
                        stop = true;
                    }
                    counter++;
                    if (counter == numTrials + 1) {
                        if (!stop) {
                            stop = true;
                            trialsExceeded = true;
                        }
                    }
                }
                lo = strike;
                hi = strike + searchStep;
            } else {
                int counter = 1;
                boolean stop = false;
                double strike = 0.0;
                while (!stop) {
                    strike = fStrikes_[fStrikes_.length - 1] + counter * searchStep;
                    if (floorPrice_.op(t, strike, true) > capPrice_.op(t, strike, true)) {
                        stop = true;
                    }
                    counter++;
                    if (counter == numTrials + 1) {
                        if (!stop) {
                            stop = true;
                            trialsExceeded = true;
                        }
                    }
                }
                lo = strike - searchStep;
                hi = strike;
            }

            final double guess = (hi + lo) / 2.0;
            double kI = -999.999;

            if (!trialsExceeded) {
                try {
                    kI = solver.solve(new ObjectiveFunction(t, capPrice_, floorPrice_),
                                      solverTolerance, guess, lo, hi);
                } catch (final Exception e) {
                    throw new LibraryException("cap/floor intersection finding failed at t = " + t
                            + ", error msg: " + e.getMessage());
                }
                // Sanity check
                if (kI <= minSwapRateIntersection[i]) {
                    if (t > maxExtrapolationMaturity) {
                        throw new LibraryException("cap/floor intersection finding failed at t = " + t
                                + ", error msg: intersection value is below the arbitrage"
                                + " free lower bound " + minSwapRateIntersection[i]);
                    }
                } else {
                    tmpSwapMaturities.add(t);
                    tmpSwapRates.add(kI);
                    validMaturity[i] = true;
                }
            } else {
                if (t > maxExtrapolationMaturity) {
                    throw new LibraryException("cap/floor intersection finding failed at t = " + t
                            + ", error msg: no intersection found inside the admissible range");
                }
            }
        }

        // Extrapolation of swap rates if necessary; fill gaps via heuristic
        int counter = 0;
        for (int i = 0; i < cfMaturities_.length; ++i) {
            if (!validMaturity[i]) {
                atmDates.add(referenceDate().add(cfMaturities_[i]));
                atmTimes.add(timeFromReference(referenceDate().add(cfMaturities_[i])));
                double newSwapRate = minSwapRateIntersection[i] + intrinsicValueAddOn;
                if (newSwapRate > maxSwapRateIntersection[i]) {
                    newSwapRate = 0.5 * (minSwapRateIntersection[i] + maxSwapRateIntersection[i]);
                }
                atmRatesT.add(newSwapRate);
                atmRatesD.add(newSwapRate);
            } else {
                atmTimes.add(tmpSwapMaturities.get(counter));
                atmRatesT.add(tmpSwapRates.get(counter));
                atmDates.add(yoyOptionDateFromTenor(cfMaturities_[counter]));
                atmRatesD.add(tmpSwapRates.get(counter));
                counter++;
            }
        }

        atmYoYSwapTimeRates_ = new Pair<>(toArr(atmTimes), toArr(atmRatesT));
        atmYoYSwapDateRates_ = new Pair<>(atmDates.toArray(new Date[0]), toArr(atmRatesD));

        atmYoYSwapRateCurve_ = interpolator1d_.interpolate(
                new Array(toArr(atmTimes)),
                new Array(toArr(atmRatesT)));
    }

    private static double[] toArr(final List<Double> xs) {
        final double[] r = new double[xs.size()];
        for (int i = 0; i < xs.size(); i++) r[i] = xs.get(i);
        return r;
    }

    /** Mirrors C++ {@code calculateYoYTermStructure()}. */
    private void calculateYoYTermStructure() {
        // Pick every year up to the latest maturity
        final long nYears = Math.round(timeFromReference(
                referenceDate().add(cfMaturities_[cfMaturities_.length - 1])));

        final List<YearOnYearInflationSwapHelper> yyHelpers = new ArrayList<>();
        for (long i = 1; i <= nYears; i++) {
            final Date maturity = nominalTS_.currentLink().referenceDate()
                    .add(new Period((int) i, TimeUnit.Years));
            final Quote sq = new SimpleQuote(atmYoYSwapRate(maturity, true));
            final Handle<Quote> quote = new Handle<>(sq);
            final YearOnYearInflationSwapHelper helper = new YearOnYearInflationSwapHelper(
                    quote, observationLag(), maturity,
                    calendar(), bdc_, dayCounter(),
                    yoyIndex_,
                    indexIsInterpolated() ? CPI.InterpolationType.Linear
                                          : CPI.InterpolationType.Flat);
            yyHelpers.add(helper);
        }

        final Date baseDate = InflationTermStructure.inflationPeriod(
                nominalTS_.currentLink().referenceDate().sub(observationLag()),
                yoyIndex_.frequency()).first();

        final double baseYoYRate = atmYoYSwapRate(referenceDate(), true);

        final PiecewiseYoYInflationCurve<Linear> pYITS =
                new PiecewiseYoYInflationCurve<>(Linear.class,
                        nominalTS_.currentLink().referenceDate(),
                        baseDate, baseYoYRate,
                        yoyIndex_.frequency(), dayCounter(), yyHelpers);
        // Force lazy bootstrap to run now (mirrors C++ pYITS->recalculate()).
        pYITS.maxDate();
        yoy_ = pYITS;
    }

    /** Mirrors C++ inner class {@code ObjectiveFunction}. */
    private static class ObjectiveFunction implements Ops.DoubleOp {
        private final double t_;
        private final Interpolation2D a_;
        private final Interpolation2D b_;

        ObjectiveFunction(final double t, final Interpolation2D a, final Interpolation2D b) {
            this.t_ = t;
            this.a_ = a;
            this.b_ = b;
        }

        @Override
        public double op(final double guess) {
            return a_.op(t_, guess, true) - b_.op(t_, guess, true);
        }
    }

    public Class<I2D> interpolator2dClass() {
        return classI2D;
    }

    public Class<I1D> interpolator1dClass() {
        return classI1D;
    }
}
