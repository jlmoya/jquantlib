/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
 Copyright (C) 2013 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/
package org.jquantlib.pricingengines.capfloor.gaussian1d;

import org.jquantlib.QL;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.InterestRateIndex;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gaussian1dModel;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * One-factor Gaussian1d-model cap/floor engine.
 * <p>
 * Java port of C++ QuantLib v1.42.1 {@code ql/pricingengines/capfloor/gaussian1dcapfloorengine.{hpp,cpp}} (commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}). Phase 2j WI-2.2.
 *
 * <p>Prices each caplet/floorlet independently by integrating its payoff over
 * the Gaussian1d state-variable distribution at the fixing date. For each optionlet the payoff-over-numeraire array is
 * fitted with a natural cubic spline (Lagrange end conditions) and convolved analytically via
 * {@link Gaussian1dModel#gaussianShiftedPolynomialIntegral}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>The C++ engine extends
 *     {@code GenericModelEngine<Gaussian1dModel, CapFloor::arguments,
 *     CapFloor::results>}. Java extends {@link CapFloor.Engine} (which extends
 *     {@link org.jquantlib.pricingengines.GenericEngine}) and stores model +
 *     discount-curve handles directly (same pattern as
 *     {@link org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dSwaptionEngine}).
 * <li>The C++ {@code arguments_.indexes[i]} cast to {@code IborIndex} is
 *     replicated via {@link CapFloor.ArgumentsImpl#indexes} (added by the
 *     Phase 2j WI-2.2 align commit). When the index is {@code null} or not an
 *     {@code IborIndex}, the engine falls back to the model-curve
 *     zero-bond difference: {@code zerobond(valueDate) − zerobond(paymentDate)},
 *     exactly mirroring the C++ {@code iborIndex == nullptr} branch.
 * <li>OpenMP parallelism around the inner state-grid loop is omitted (no
 *     analogue in JQuantLib). The serial loop is semantically identical.
 * <li>The additional-results map entries {@code "optionletsPrice"} and
 *     {@code "optionletsAtmForward"} are populated on
 *     {@link CapFloor.ResultsImpl} via
 *     {@link org.jquantlib.pricingengines.PricingEngine.Results}, using the
 *     same keys as the C++ engine. Consuming code may retrieve them from
 *     {@code capFloor.additionalResults()} after pricing.
 * </ul>
 *
 * @see Gaussian1dModel
 * @see CapFloor
 */
public class Gaussian1dCapFloorEngine extends CapFloor.Engine {

    private final Gaussian1dModel model_;
    private final int integrationPoints_;
    private final double stddevs_;
    private final boolean extrapolatePayoff_;
    private final boolean flatPayoffExtrapolation_;
    private final Handle< YieldTermStructure > discountCurve_;

    // ──────────────────────────────────────────────────────────────────────
    //   Constructors (mirrors gaussian1dcapfloorengine.hpp, defaults
    //   collapsed via overloads)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Defaults: 64 integration points, 7.0 stddevs, extrapolate=true, flatExtrapolation=false, empty discountCurve.
     */
    public Gaussian1dCapFloorEngine(final Gaussian1dModel model) {
        this(model, 64, 7.0, true, false, new Handle< YieldTermStructure >());
    }

    /** Override integration density / std-dev cap; other defaults apply. */
    public Gaussian1dCapFloorEngine(final Gaussian1dModel model, final int integrationPoints, final double stddevs) {
        this(model, integrationPoints, stddevs, true, false, new Handle< YieldTermStructure >());
    }

    /** Five-arg form: explicit extrapolation flags; discountCurve defaults empty. */
    public Gaussian1dCapFloorEngine(final Gaussian1dModel model, final int integrationPoints, final double stddevs,
            final boolean extrapolatePayoff, final boolean flatPayoffExtrapolation) {
        this(model, integrationPoints, stddevs, extrapolatePayoff, flatPayoffExtrapolation,
                new Handle< YieldTermStructure >());
    }

    /** Full ctor — mirrors C++ shared_ptr constructor. */
    public Gaussian1dCapFloorEngine(final Gaussian1dModel model, final int integrationPoints, final double stddevs,
            final boolean extrapolatePayoff, final boolean flatPayoffExtrapolation,
            final Handle< YieldTermStructure > discountCurve) {
        super();
        QL.require(model != null, "no model specified");
        this.model_ = model;
        this.integrationPoints_ = integrationPoints;
        this.stddevs_ = stddevs;
        this.extrapolatePayoff_ = extrapolatePayoff;
        this.flatPayoffExtrapolation_ = flatPayoffExtrapolation;
        this.discountCurve_ = (discountCurve != null) ? discountCurve : new Handle< YieldTermStructure >();

        this.model_.addObserver(this);
        if ( !this.discountCurve_.empty() ) {
            this.discountCurve_.addObserver(this);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //   Accessors
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Tries to resolve the {@code i}-th index as an {@link IborIndex}. Returns {@code null} if the array is
     * {@code null}, if the element is {@code null}, or if the element is not an {@code IborIndex}. Mirrors C++
     * {@code ext::dynamic_pointer_cast<IborIndex>(arguments_.indexes[i])}.
     */
    private static IborIndex resolveIborIndex(final InterestRateIndex[] indexes, final int i) {
        if ( indexes == null || indexes[i] == null ) {
            return null;
        }
        return (indexes[i] instanceof IborIndex) ? (IborIndex) indexes[i] : null;
    }

    /** Snapshots a primitive double[] into a new Array. */
    private static Array doublesToArray(final double[] src, final int n) {
        final Array a = new Array(n);
        for ( int i = 0; i < n; i++ ) {
            a.set(i, src[i]);
        }
        return a;
    }

    /** Snapshots an Array into a primitive double[] for fast indexing. */
    private static double[] arrayToDoubles(final Array a) {
        final int n = a.size();
        final double[] r = new double[n];
        for ( int i = 0; i < n; i++ ) {
            r[i] = a.get(i);
        }
        return r;
    }

    public Gaussian1dModel model() {
        return model_;
    }

    public int integrationPoints() {
        return integrationPoints_;
    }

    public double stddevs() {
        return stddevs_;
    }

    // ──────────────────────────────────────────────────────────────────────
    //   Engine: calculate()
    // ──────────────────────────────────────────────────────────────────────

    public boolean extrapolatePayoff() {
        return extrapolatePayoff_;
    }

    // ──────────────────────────────────────────────────────────────────────
    //   Helpers
    // ──────────────────────────────────────────────────────────────────────

    public boolean flatPayoffExtrapolation() {
        return flatPayoffExtrapolation_;
    }

    public Handle< YieldTermStructure > discountCurve() {
        return discountCurve_;
    }

    /**
     * Mirrors C++ {@code Gaussian1dCapFloorEngine::calculate()} verbatim ({@code gaussian1dcapfloorengine.cpp}).
     */
    @Override
    public void calculate() {
        final CapFloor.ArgumentsImpl args = (CapFloor.ArgumentsImpl) arguments_;
        final CapFloor.ResultsImpl results = (CapFloor.ResultsImpl) results_;

        // C++: QL_REQUIRE spread == 0.0 for all spreads
        for ( final double spread : args.spreads ) {
            QL.require(spread == 0.0, "Non zero spreads (" + spread + ") are not allowed.");
        }

        final int optionlets = args.startDates.length;
        final double[] values = new double[optionlets];
        final double[] forwards = new double[optionlets];
        double value = 0.0;

        final Date settlement = model_.termStructure().currentLink().referenceDate();

        final CapFloor.Type type = args.type;

        // State-variable grid (same for all optionlets — mirrors C++ scope)
        final Array zArray = model_.yGrid(stddevs_, integrationPoints_);
        final double[] z = arrayToDoubles(zArray);
        final int gridSize = z.length;
        final double[] p = new double[gridSize];

        for ( int i = 0; i < optionlets; i++ ) {

            final Date valueDate = args.startDates[i];
            final Date paymentDate = args.endDates[i];

            // Resolve IborIndex (may be null if coupon carries no ibor index).
            final IborIndex iborIndex = resolveIborIndex(args.indexes, i);

            if ( paymentDate.gt(settlement) ) {

                final double f = args.nominals[i] * args.gearings[i];
                final Date fixingDate = args.fixingDates[i];
                final double fixingTime = model_.termStructure().currentLink().timeFromReference(fixingDate);

                double strike;

                // ── Cap / Collar ──────────────────────────────────────────
                if ( type == CapFloor.Type.Cap || type == CapFloor.Type.Collar ) {
                    strike = args.capRates[i];

                    if ( !fixingDate.gt(settlement) ) {
                        // In-the-past: immediate payoff (no integration needed).
                        values[i] = Math.max(args.forwards[i] - strike, 0.0) * f * args.accrualTimes[i];
                    } else {
                        // Compute payoff/numeraire at each grid point.
                        for ( int j = 0; j < gridSize; j++ ) {
                            final double floatingLegNpv;
                            if ( iborIndex != null ) {
                                floatingLegNpv = args.accrualTimes[i] * model_.forwardRate(fixingDate, fixingDate, z[j],
                                        iborIndex) * model_.zerobond(paymentDate, fixingDate, z[j], discountCurve_);
                            } else {
                                floatingLegNpv =
                                        model_.zerobond(valueDate, fixingDate, z[j]) - model_.zerobond(paymentDate,
                                                fixingDate, z[j]);
                            }
                            final double fixedLegNpv =
                                    strike * args.accrualTimes[i] * model_.zerobond(paymentDate, fixingDate, z[j]);
                            p[j] = Math.max(floatingLegNpv - fixedLegNpv, 0.0) / model_.numeraire(fixingTime, z[j],
                                    discountCurve_);
                        }

                        final double price = integratePayoff(z, p, gridSize,
                                /* capExtrapolation= */ true);
                        values[i] = price * model_.numeraire(0.0, 0.0, discountCurve_) * f;
                    }
                }

                // ── Floor / Collar ────────────────────────────────────────
                if ( type == CapFloor.Type.Floor || type == CapFloor.Type.Collar ) {
                    strike = args.floorRates[i];
                    final double floorlet;

                    if ( !fixingDate.gt(settlement) ) {
                        floorlet = Math.max(-(args.forwards[i] - strike), 0.0) * f * args.accrualTimes[i];
                    } else {
                        for ( int j = 0; j < gridSize; j++ ) {
                            final double floatingLegNpv;
                            if ( iborIndex != null ) {
                                floatingLegNpv = args.accrualTimes[i] * model_.forwardRate(fixingDate, fixingDate, z[j],
                                        iborIndex) * model_.zerobond(paymentDate, fixingDate, z[j], discountCurve_);
                            } else {
                                floatingLegNpv =
                                        model_.zerobond(valueDate, fixingDate, z[j]) - model_.zerobond(paymentDate,
                                                fixingDate, z[j]);
                            }
                            final double fixedLegNpv =
                                    strike * args.accrualTimes[i] * model_.zerobond(paymentDate, fixingDate, z[j]);
                            p[j] = Math.max(-(floatingLegNpv - fixedLegNpv), 0.0) / model_.numeraire(fixingTime, z[j],
                                    discountCurve_);
                        }

                        final double price = integratePayoff(z, p, gridSize,
                                /* capExtrapolation= */ false);
                        floorlet = price * model_.numeraire(0.0, 0.0, discountCurve_) * f;
                    }

                    if ( type == CapFloor.Type.Floor ) {
                        values[i] = floorlet;
                    } else {
                        // collar = long cap, short floor
                        values[i] -= floorlet;
                    }
                }

                value += values[i];
            }
        }

        results.value = value;
        // additional results mirrors C++ — optionletsPrice + optionletsAtmForward
        results.additionalResults().put("optionletsPrice", values);
        results.additionalResults().put("optionletsAtmForward", forwards);
    }

    /**
     * Builds a natural cubic spline with Lagrange end conditions, integrates the payoff/numeraire array {@code p} over
     * the grid {@code z}, and adds the tail extrapolation terms per the {@code extrapolatePayoff_} /
     * {@code flatPayoffExtrapolation_} flags.
     *
     * <p>The C++ engine constructs the interpolation inside each optionlet's
     * cap/floor block; this helper factors out the shared logic. The {@code capExtrapolation} parameter selects which
     * tail gets the cubic extension: cap uses the upper tail; floor uses the lower tail.
     *
     * @param z                state-variable grid
     * @param p                payoff/numeraire values at each grid point
     * @param gridSize         length of {@code z} and {@code p}
     * @param capExtrapolation {@code true} for cap (right-tail cubic), {@code false} for floor (left-tail cubic)
     * @return integrated price (not yet multiplied by numeraire(0,0) * f)
     */
    private double integratePayoff(final double[] z, final double[] p, final int gridSize,
            final boolean capExtrapolation) {

        // Wrap p into Array for CubicInterpolation
        final Array zArr = doublesToArray(z, gridSize);
        final Array pArr = doublesToArray(p, gridSize);

        final CubicInterpolation payoff = new CubicInterpolation(zArr, pArr, CubicInterpolation.DerivativeApprox.Spline,
                true, CubicInterpolation.BoundaryCondition.Lagrange, 0.0, CubicInterpolation.BoundaryCondition.Lagrange,
                0.0);

        final Array aCoef = payoff.aCoefficients();
        final Array bCoef = payoff.bCoefficients();
        final Array cCoef = payoff.cCoefficients();

        double price = 0.0;

        // Interior segments
        for ( int j = 0; j < gridSize - 1; j++ ) {
            price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cCoef.get(j), bCoef.get(j), aCoef.get(j),
                    p[j], z[j], z[j], z[j + 1]);
        }

        // Tail extrapolation
        if ( extrapolatePayoff_ ) {
            if ( flatPayoffExtrapolation_ ) {
                // Both tails: flat extension at the penultimate / first values.
                price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, 0.0, 0.0, 0.0, p[gridSize - 2],
                        z[gridSize - 2], z[gridSize - 1], 100.0);
                price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, 0.0, 0.0, 0.0, p[0], z[0], -100.0,
                        z[0]);
            } else {
                if ( capExtrapolation ) {
                    // Cap: extend upper tail with cubic from last segment.
                    price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cCoef.get(gridSize - 2),
                            bCoef.get(gridSize - 2), aCoef.get(gridSize - 2), p[gridSize - 2], z[gridSize - 2],
                            z[gridSize - 1], 100.0);
                } else {
                    // Floor: extend lower tail with cubic from first segment.
                    price += Gaussian1dModel.gaussianShiftedPolynomialIntegral(0.0, cCoef.get(0), bCoef.get(0),
                            aCoef.get(0), p[0], z[0], -100.0, z[0]);
                }
            }
        }

        return price;
    }
}
