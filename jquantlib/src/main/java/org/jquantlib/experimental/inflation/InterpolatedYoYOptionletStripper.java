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

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.experimental.inflation;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.inflation.GenericIndexes.YYGenericCPI;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.instruments.InflationCapFloor;
import org.jquantlib.instruments.MakeYoYInflationCapFloor;
import org.jquantlib.math.Ops;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.inflation.InflationCapFloorEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.termstructures.volatility.inflation.ConstantYoYOptionletVolatility;
import org.jquantlib.termstructures.volatility.inflation.YoYOptionletVolatilitySurface;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.util.Pair;

/**
 * Concrete YoY optionlet stripper that interpolates along each K (rather
 * than fitting a model).
 *
 * <p>Mirrors C++ v1.42.1
 * {@code QuantLib::InterpolatedYoYOptionletStripper}
 * ({@code ql/experimental/inflation/interpolatedyoyoptionletstripper.hpp}).
 *
 * <p>For each strike K in the source price surface, the stripper:
 * <ol>
 *   <li>Solves for an initial flat-vol value that reproduces the smallest-
 *       maturity cap or floor price (Brent on {@code priceToMatch - NPV}).</li>
 *   <li>Builds a list of {@link YoYOptionletHelper}s for each
 *       (maturity, K).</li>
 *   <li>Bootstraps a {@link PiecewiseYoYOptionletVolatility} curve from
 *       these helpers, with a constructed
 *       {@code baseYoYVolatility = found - slope * Tmin * found}.</li>
 * </ol>
 *
 * @param <I> Interpolator type used for the per-K curve (e.g. Linear)
 *
 * @author JQuantLib migration team (Phase 2s Track B)
 */
public class InterpolatedYoYOptionletStripper<I extends Interpolator>
        extends YoYOptionletStripper {

    /** Per-K stripped vol curves (one per strike). */
    protected final List<YoYOptionletVolatilitySurface> volCurves_ = new ArrayList<>();

    private final Class<I> classI;

    public InterpolatedYoYOptionletStripper(final Class<I> classI) {
        QL.require(classI != null, "Generic type for Interpolator is null");
        this.classI = classI;
    }

    //
    // YoYOptionletStripper interface
    //

    @Override
    public double minStrike() {
        return yoyCapFloorTermPriceSurface_.strikes().get(0);
    }

    @Override
    public double maxStrike() {
        final List<Double> ks = yoyCapFloorTermPriceSurface_.strikes();
        return ks.get(ks.size() - 1);
    }

    @Override
    public List<Double> strikes() {
        return yoyCapFloorTermPriceSurface_.strikes();
    }

    @Override
    public Pair<List<Double>, List<Double>> slice(final Date d) {
        final List<Double> ks = strikes();
        final int nK = ks.size();
        final List<Double> outKs = new ArrayList<>(nK);
        final List<Double> outVs = new ArrayList<>(nK);
        for (int i = 0; i < nK; ++i) {
            final double K = ks.get(i);
            final double v = volCurves_.get(i).volatility(d, K);
            outKs.add(K);
            outVs.add(v);
        }
        return new Pair<>(outKs, outVs);
    }

    /**
     * Mirrors C++ {@code initialize(...)}. Strips per-K vol curves out of
     * the price surface using the supplied cap/floor pricing engine.
     */
    @Override
    public void initialize(final YoYCapFloorTermPriceSurfaceLike s,
                           final InflationCapFloorEngine p,
                           final double slope) {
        this.yoyCapFloorTermPriceSurface_ = s;
        this.p_ = p;
        this.lag_ = s.observationLag();
        this.frequency_ = s.frequency();
        this.indexIsInterpolated_ = s.indexIsInterpolated();
        final int fixingDays_ = s.fixingDays();
        final int settlementDays = 0;  // always
        final Calendar cal = s.calendar();
        final BusinessDayConvention bdc = s.businessDayConvention();
        final DayCounter dc = s.dayCounter();

        // switch from caps to floors when out of floors
        final List<Double> floorStrikes = s.floorStrikes();
        final double maxFloor = floorStrikes.get(floorStrikes.size() - 1);
        InflationCapFloor.Type useType = InflationCapFloor.Type.Floor;
        final Period TPmin = s.maturities().get(0);

        // create a "fake index" based on Generic, this should work
        // provided that the lag and frequency are correct
        final Handle<YoYInflationTermStructure> hYoY = new Handle<>(s.YoYTS());
        final YoYInflationIndex anIndex = new YYGenericCPI(
                frequency_, false, lag_,
                new org.jquantlib.currencies.Currency(),
                hYoY);

        // strip each K separately
        final List<Double> Ks = s.strikes();
        for (int i = 0; i < Ks.size(); ++i) {
            final double K = Ks.get(i);
            if (K > maxFloor) {
                useType = InflationCapFloor.Type.Cap;
            }

            // solve for the initial point on the vol curve
            final Brent solver = new Brent();
            final double solverTolerance = 1e-7;
            // VOLATILITY guesses (always positive)
            final double lo = 0.00001;
            final double hi = 0.08;
            final double guess = (hi + lo) / 2.0;
            final double priceToMatch = (useType == InflationCapFloor.Type.Cap)
                    ? s.capPrice(TPmin, K)
                    : s.floorPrice(TPmin, K);

            final double found;
            try {
                final InflationCapFloor.Type useTypeFinal = useType;
                found = solver.solve(
                        new ObjectiveFunction(useTypeFinal, slope, K, lag_,
                                fixingDays_, anIndex, s, p_, priceToMatch),
                        solverTolerance, guess, lo, hi);
            } catch (final RuntimeException re) {
                throw new RuntimeException(
                        "failed to find solution because: " + re.getMessage(),
                        re);
            }

            // ***create helpers***
            final double notional = 10000;  // work in bps
            final List<YoYOptionletHelper> helpers = new ArrayList<>();
            final List<Period> mats = s.maturities();
            for (int j = 0; j < mats.size(); ++j) {
                final Period Tp = mats.get(j);
                final double nextPrice = (useType == InflationCapFloor.Type.Cap)
                        ? s.capPrice(Tp, K)
                        : s.floorPrice(Tp, K);

                final Handle<Quote> quote1 = new Handle<>(new SimpleQuote(nextPrice));

                // helper should be an integer number of periods away,
                // this is enforced by rounding
                final int nT = (int) Math.floor(
                        s.timeFromReference(s.yoyOptionDateFromTenor(Tp)) + 0.5);

                final YoYOptionletHelper helper = new YoYOptionletHelper(
                        quote1, notional, useType,
                        lag_, dc, cal, fixingDays_,
                        anIndex, CPI.InterpolationType.Flat,
                        K, nT, p_);

                final ConstantYoYOptionletVolatility yoyVolBLACK =
                        new ConstantYoYOptionletVolatility(
                                found, settlementDays,
                                cal, bdc, dc,
                                lag_, frequency_, false,
                                -1.0, 3.0,  // -100% to +300%
                                VolatilityType.ShiftedLognormal,
                                0.0);

                helper.setTermStructure(yoyVolBLACK);
                helpers.add(helper);
            }

            // ***bootstrap***
            // artificial vol at zero so that first section works
            final double Tmin = s.timeFromReference(s.yoyOptionDateFromTenor(TPmin));
            final double baseYoYVolatility = found - slope * Tmin * found;
            final double eps = Math.max(K, 0.02) / 1000.0;
            final double minStrike = K - eps;
            final double maxStrike = K + eps;

            final PiecewiseYoYOptionletVolatility<I> testPW =
                    new PiecewiseYoYOptionletVolatility<>(
                            classI, settlementDays, cal, bdc, dc, lag_,
                            frequency_, indexIsInterpolated_,
                            minStrike, maxStrike, baseYoYVolatility,
                            helpers);
            testPW.recalculate();
            volCurves_.add(testPW);
        }
    }

    //
    // ObjectiveFunction — inner class mirroring the C++ inner class.
    //

    /**
     * Mirrors C++ {@code ObjectiveFunction}: prices a small (2-pillar)
     * cap/floor under a guess vol curve and returns
     * {@code priceToMatch - NPV}.
     */
    private static final class ObjectiveFunction implements Ops.DoubleOp {

        private final double slope_;
        private final boolean indexIsInterpolated_;
        private final double[] tvec_ = new double[2];
        private final Date[] dvec_ = new Date[2];
        private final double[] vvec_ = new double[2];
        private final InflationCapFloor capfloor_;
        private final double priceToMatch_;
        private final YoYCapFloorTermPriceSurfaceLike surf_;
        private final InflationCapFloorEngine p_;

        @SuppressWarnings("unused")
        ObjectiveFunction(final InflationCapFloor.Type type,
                          final double slope,
                          final double K,
                          final Period lag,
                          final int fixingDays,
                          final YoYInflationIndex anIndex,
                          final YoYCapFloorTermPriceSurfaceLike surf,
                          final InflationCapFloorEngine p,
                          final double priceToMatch) {
            this.slope_ = slope;
            this.indexIsInterpolated_ = anIndex.interpolated();
            this.priceToMatch_ = priceToMatch;
            this.surf_ = surf;
            this.p_ = p;

            // C++: builds capfloor of length=floor(0.5+timeFromReference(minMaturity()))
            final int n = (int) Math.floor(
                    0.5 + surf.timeFromReference(surf.yoyOptionDateFromTenor(surf.minMaturity())));
            QL.require(n > 0, "first maturity in price surface not > 0: " + n);

            this.capfloor_ = new MakeYoYInflationCapFloor(type, anIndex,
                    n, surf.calendar(), surf.observationLag(),
                    CPI.InterpolationType.AsIndex)
                    .withNominal(10000.0)
                    .withStrike(K)
                    .build();

            // shortest time available from price surface
            // C++: dvec_[1] = surf_->minMaturity() + Period(7,Days)
            // surf->minMaturity() returns a Period; + Period yields Period;
            // assigning to Date implies the C++ has an implicit Date(...) cast.
            // We simulate the intent: minMaturity_DATE + 7 days.
            dvec_[0] = surf.baseDate();
            dvec_[1] = surf.yoyOptionDateFromTenor(surf.minMaturity())
                    .add(new Period(7, TimeUnit.Days));
            tvec_[0] = surf.dayCounter().yearFraction(surf.referenceDate(), dvec_[0]);
            tvec_[1] = surf.dayCounter().yearFraction(surf.referenceDate(), dvec_[1]);

            capfloor_.setPricingEngine(p_);
        }

        /**
         * Mirrors C++ {@code Real operator()(Volatility guess) const}.
         */
        @Override
        public double op(final double guess) {
            vvec_[1] = guess;
            vvec_[0] = guess - slope_ * (tvec_[1] - tvec_[0]) * guess;

            // could have Interpolator1D instead of Linear
            final InterpolatedYoYOptionletVolatilityCurve<Linear> vCurve =
                    new InterpolatedYoYOptionletVolatilityCurve<>(
                            Linear.class, 0,
                            new Target(),
                            BusinessDayConvention.ModifiedFollowing,
                            new Actual365Fixed(),
                            surf_.observationLag(),
                            surf_.frequency(),
                            indexIsInterpolated_,
                            dvec_, vvec_,
                            -1.0, 3.0);
            final Handle<YoYOptionletVolatilitySurface> hCurve =
                    new Handle<>(vCurve);
            p_.setVolatility(hCurve);
            // hopefully this gets to the pricer ... then
            return priceToMatch_ - capfloor_.NPV();
        }
    }

}
