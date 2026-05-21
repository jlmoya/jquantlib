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
 Copyright (C) 2025 Eugene Toder, Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.yieldcurves;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.util.Observer;

/**
 * Builds a set of curves that form a dependency cycle by driving them through a single global bootstrap.
 *
 * <p>Java port of QuantLib v1.42.1 {@code MultiCurve} ({@code ql/termstructures/multicurve.{hpp,cpp}}).
 *
 * <p>Cycle-build recipe:
 * <ol>
 *   <li>Create empty {@link RelinkableHandle}s to {@link YieldTermStructure} for each cycle member ("internal"
 *       handles used inside the cycle only).</li>
 *   <li>Construct each member curve (typically via {@link PiecewiseYieldCurve} with a {@link GlobalBootstrap}, or a
 *       spreaded curve like {@code ZeroSpreadedTermStructure}). Rate helpers / base curves should reference the
 *       internal handles from step 1.</li>
 *   <li>Construct a {@code MultiCurve} instance and add cycle members via
 *       {@link #addBootstrappedCurve(RelinkableHandle, YieldTermStructure)} (for curves with their own bootstrap) or
 *       {@link #addNonBootstrappedCurve(RelinkableHandle, YieldTermStructure)} (for derived / spreaded curves).
 *       Each call links the caller's internal handle to the curve without registering as observer of it, breaking
 *       the notification cycle, and returns an external {@link Handle} for use outside the cycle.</li>
 * </ol>
 *
 * <h3>Java-specific notes</h3>
 * <p>C++ uses the null-deleter aliasing-{@code shared_ptr} pattern for two purposes:
 * <ul>
 *   <li>To detach the {@code RelinkableHandle} from the curve's <i>ownership</i> graph (avoiding shared-pointer
 *       cycles). In Java this is moot — GC handles cycles natively, so we simply call
 *       {@link RelinkableHandle#linkTo(org.jquantlib.util.Observable, boolean)} with {@code isObserver=false}.</li>
 *   <li>To keep the curve alive as long as either the curve handle or the {@code MultiCurve} is alive. The Java
 *       port achieves this by retaining a strong reference to each curve in {@link #curves}.</li>
 * </ul>
 */
public class MultiCurve implements Observer {

    private final MultiCurveBootstrap multiCurveBootstrap;
    private final List< YieldTermStructure > curves = new ArrayList<>();

    /** Accuracy-only ctor; mirrors C++ {@code MultiCurve(Real accuracy)}. */
    public MultiCurve(final double accuracy) {
        this.multiCurveBootstrap = new MultiCurveBootstrap(accuracy);
    }

    /** Optimiser ctor; mirrors C++ {@code MultiCurve(optimizer, endCriteria)}. */
    public MultiCurve(final OptimizationMethod optimizer, final EndCriteria endCriteria) {
        this.multiCurveBootstrap = new MultiCurveBootstrap(optimizer, endCriteria);
    }

    /**
     * Add a curve that has its own bootstrap (must implement {@link MultiCurveBootstrapProvider}; e.g. a
     * {@link PiecewiseYieldCurve} wired with {@link GlobalBootstrap}).
     *
     * @param internalHandle the empty {@link RelinkableHandle} used to construct the rate helpers / index for this
     *     curve. Must be empty.
     * @param curve the curve to add.
     * @return an external {@link Handle} for use outside the cycle.
     */
    public Handle< YieldTermStructure > addBootstrappedCurve(
            final RelinkableHandle< YieldTermStructure > internalHandle, final YieldTermStructure curve) {
        QL.require(internalHandle.empty(),
                "internal handle must be empty; was the curve added already?");
        QL.require(curve instanceof MultiCurveBootstrapProvider,
                "curve must be a MultiCurveBootstrapProvider");
        final MultiCurveBootstrapContributor bootstrap =
                ((MultiCurveBootstrapProvider) curve).multiCurveBootstrapContributor();
        QL.require(bootstrap != null,
                "curve does not provide a valid multi curve bootstrap contributor");
        multiCurveBootstrap.add(bootstrap);
        return addCurve(internalHandle, curve);
    }

    /**
     * Add a curve that derives from / observes others (e.g. {@code ZeroSpreadedTermStructure}). The curve is
     * registered as a per-step observer of the {@link MultiCurveBootstrap}, so it sees fresh data on every LM step.
     *
     * @param internalHandle the empty {@link RelinkableHandle} used inside the cycle. Must be empty.
     * @param curve the curve to add. Must not be null.
     * @return an external {@link Handle} for use outside the cycle.
     */
    public Handle< YieldTermStructure > addNonBootstrappedCurve(
            final RelinkableHandle< YieldTermStructure > internalHandle, final YieldTermStructure curve) {
        QL.require(internalHandle.empty(),
                "internal handle must be empty; was the curve added already?");
        QL.require(curve != null, "curve must not be null");
        multiCurveBootstrap.addObserver(curve);
        return addCurve(internalHandle, curve);
    }

    private Handle< YieldTermStructure > addCurve(
            final RelinkableHandle< YieldTermStructure > internalHandle, final YieldTermStructure curve) {
        // Link the internal handle to the curve without registering as observer — Java equivalent of the C++
        // null-deleter aliasing shared_ptr (we just need the non-owning, non-observing reference).
        internalHandle.linkTo(curve, false);
        // External handle: in C++ this is an aliasing shared_ptr that keeps the MultiCurve (and hence every
        // member curve) alive. In Java the strong reference to `curve` we keep in `curves` already ensures
        // liveness, so a plain Handle suffices.
        final Handle< YieldTermStructure > externalHandle = new Handle< YieldTermStructure >(curve);
        // Observe the curve so MultiCurve.update() propagates notifications to all members (matches C++
        // registerWithObservables(curve) + Observer base).
        curve.addObserver(this);
        curves.add(curve);
        return externalHandle;
    }

    @Override
    public void update() {
        // mirror C++ MultiCurve::update — push update to every member curve
        for ( final YieldTermStructure c : curves ) {
            c.update();
        }
    }
}
