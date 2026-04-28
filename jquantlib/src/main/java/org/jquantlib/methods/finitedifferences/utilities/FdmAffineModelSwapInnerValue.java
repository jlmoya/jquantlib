/*
 Copyright (C) 2011 Klaus Spanderen

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
package org.jquantlib.methods.finitedifferences.utilities;

import java.util.Map;

import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.model.AffineModel;
import org.jquantlib.time.Date;

/**
 * FDM inner-value calculator for vanilla swaps under an affine short-rate
 * model.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/utilities/fdmaffinemodelswapinnervalue.hpp}
 * (header-only template {@code FdmAffineModelSwapInnerValue<ModelType>}
 * with explicit specializations for {@code HullWhite} and {@code G2} in
 * the .cpp).
 * <p>
 * <strong>Java generic adaptation:</strong> the C++ template parameter
 * {@code ModelType} becomes a bounded Java generic
 * {@code <M extends AffineModel>}. Same precedent as Phase 2d WI-3
 * {@code XABRSpecs<S extends XABRSpecs>}.
 * <p>
 * <strong>Java-side simplification:</strong> the C++ implementation
 * builds an {@code FdmAffineModelTermStructure} on each
 * {@code innerValue} call, relinks discount/forward handles, and
 * re-prices the cloned swap. The Java port avoids that intermediate
 * term-structure (not yet ported) by re-pricing the swap legs directly
 * via {@code model.discountBond(t, paymentTime, factors)} for the
 * discount factors at the given mesh location. The resulting NPV is the
 * swap value at {@code (t, x)} on the mesh.
 * <p>
 * <strong>Limitation:</strong> this Java adaptation collapses the C++
 * "two models" pattern (separate {@code disModel} for discounting and
 * {@code fwdModel} for forwards) into a single {@code model}. The C++
 * default usage (single model for both legs) is the only supported
 * shape; dual-curve support is a follow-up. Furthermore, since the
 * floating-leg cash flows in the swap are pre-computed at construction
 * time using the original ibor index, the floating-leg pay-amounts are
 * read off as-is — re-projection through the mesh-relinked forward
 * curve is not performed. This is sufficient for the swaption-engine
 * exerciseAfterMaturity intrinsic-value path used by
 * {@code FdHullWhiteSwaptionEngine} / {@code FdG2SwaptionEngine}.
 *
 * @param <M> affine short-rate model type (e.g. {@code HullWhite},
 *            {@code G2})
 *
 * @author Phase 2h WI-1 port
 */
public class FdmAffineModelSwapInnerValue<M extends AffineModel>
        implements FdmInnerValueCalculator {

    private final M model_;
    private final FdmMesher mesher_;
    private final VanillaSwap swap_;
    private final Map<Double, Date> exerciseDates_;
    private final int direction_;
    private final DayCounter dayCounter_;
    private final Date referenceDate_;

    /**
     * @param model         affine short-rate model used for both
     *                      discounting and forward projection.
     * @param mesher        the FDM mesh.
     * @param swap          the underlying swap whose intrinsic value is
     *                      evaluated.
     * @param exerciseTimes mapping from grid time {@code t} to the
     *                      corresponding exercise {@link Date}; the
     *                      look-up populates the discount-bond
     *                      reference date.
     * @param direction     mesh direction whose location supplies the
     *                      affine-model state (or its first component
     *                      for a 2-factor model).
     * @param referenceDate reference date used to year-fraction
     *                      cash-flow payment dates.
     * @param dayCounter    day-counter used for the year-fraction.
     */
    public FdmAffineModelSwapInnerValue(
            final M model,
            final FdmMesher mesher,
            final VanillaSwap swap,
            final Map<Double, Date> exerciseTimes,
            final int direction,
            final Date referenceDate,
            final DayCounter dayCounter) {
        this.model_ = model;
        this.mesher_ = mesher;
        this.swap_ = swap;
        this.exerciseDates_ = exerciseTimes;
        this.direction_ = direction;
        this.referenceDate_ = referenceDate;
        this.dayCounter_ = dayCounter;
    }

    @Override
    public double innerValue(final FdmLinearOpIterator iter, final double t) {
        final Date iterExerciseDate = exerciseDates_.get(t);
        if (iterExerciseDate == null) {
            throw new LibraryException(
                    "no exercise date found for grid time " + t);
        }

        final Array factors = getState(t, iter);
        final double exerciseTime = dayCounter_.yearFraction(
                referenceDate_, iterExerciseDate);

        // C++ legs are accessed via swap_->leg(j) for j = 0 (fixed) and
        // 1 (floating). The Java VanillaSwap exposes them as
        // fixedLeg() / floatingLeg().
        double npv = 0.0;
        for (int j = 0; j < 2; j++) {
            final Leg leg = (j == 0) ? swap_.fixedLeg() : swap_.floatingLeg();
            double legNpv = 0.0;
            for (final CashFlow cf : leg) {
                if (!(cf instanceof Coupon)) {
                    continue;
                }
                final Coupon coupon = (Coupon) cf;
                if (coupon.accrualStartDate().ge(iterExerciseDate)) {
                    final double payTime = dayCounter_.yearFraction(
                            referenceDate_, cf.date());
                    final double df = model_.discountBond(
                            exerciseTime, payTime, factors);
                    legNpv += cf.amount() * df;
                }
            }
            if (j == 0) {
                legNpv = -legNpv;
            }
            npv += legNpv;
        }
        if (swap_.type() == VanillaSwap.Type.Receiver) {
            npv = -npv;
        }
        return Math.max(0.0, npv);
    }

    @Override
    public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
        return innerValue(iter, t);
    }

    /**
     * Build the affine-model state vector at {@code (t, iter)}. For a
     * 1-factor model (e.g. {@code HullWhite}) the state is the short
     * rate at {@code mesher_.location(iter, direction_)}; for a
     * 2-factor model (e.g. {@code G2}) the state is
     * {@code (location[direction_], location[direction_+1])}.
     * <p>
     * In C++ this is realised via explicit template specializations
     * {@code FdmAffineModelSwapInnerValue<HullWhite>::getState} and
     * {@code <G2>::getState}. The Java port uses a runtime check on
     * the model class.
     * <p>
     * <strong>Visibility note (Phase 2h WI-2 align).</strong> This
     * method is {@code protected} so concrete swaption engines that
     * know their model type can override and inject a
     * {@code dynamics()-&gt;shortRate(t, x)}-based state vector
     * (HullWhite/Vasicek) without losing the rest of the swap-NPV
     * machinery. The baseline implementation falls back to the bare
     * mesh location, which under-prices HullWhite swaptions because
     * the deterministic phi(t) shift is missing from the discount-bond
     * formula {@code A(t,T) * exp(-B(t,T) * r)} (it must take the true
     * short rate, not the OU state).
     */
    protected Array getState(final double t, final FdmLinearOpIterator iter) {
        // Detect 2-factor models (G2) by class name, to avoid a direct
        // dependency on the G2 type from this utility class. HullWhite,
        // Vasicek, and other 1-factor models fall through to the
        // single-factor path.
        final String modelClassName = model_.getClass().getSimpleName();
        if ("G2".equals(modelClassName)) {
            final Array state = new Array(2);
            state.set(0, mesher_.location(iter, direction_));
            state.set(1, mesher_.location(iter, direction_ + 1));
            return state;
        }
        // 1-factor model fallback: use the bare mesh location. Concrete
        // engines that know their model is HullWhite/Vasicek should
        // subclass and override to use {@code dynamics().shortRate(t, x)}
        // (which adds the deterministic phi(t) shift); see the C++
        // template specialization {@code <HullWhite>::getState}.
        final Array state = new Array(1);
        state.set(0, mesher_.location(iter, direction_));
        return state;
    }

    /** @return the mesher this calculator was built against. */
    protected final FdmMesher mesher() {
        return mesher_;
    }

    /** @return the direction the mesher state lives on. */
    protected final int direction() {
        return direction_;
    }
}
