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
 Copyright (C) 2010 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.
*/

package org.jquantlib.math.interpolations;

import org.jquantlib.QL;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;

/**
 * Mixed linear/cubic interpolation between discrete points.
 * <p>
 * The interpolation evaluates as a {@link LinearInterpolation} on the first portion of the data (up to the {@code n}-th
 * abscissa, exclusive) and as a {@link CubicInterpolation} above. The two segments are joined either by sharing the
 * full data range ({@link Behavior#ShareRanges}) or by splitting the data so each interpolation owns disjoint subranges
 * ({@link Behavior#SplitRanges}); see the {@link Behavior} enum.
 * <p>
 * When {@link Behavior#SplitRanges} is selected, the cubic segment's left boundary condition can be set to
 * {@link CubicInterpolation.BoundaryCondition#FirstDerivative} with {@link Constants#NULL_REAL} as the condition value,
 * requesting that the cubic's left derivative match the linear segment's slope at the switch point.
 * <p>
 * Mirrors C++ {@code MixedLinearCubicInterpolation} in {@code ql/math/interpolations/mixedinterpolation.hpp} (v1.42.1,
 * lines 59-92, detail::MixedInterpolationImpl lines 218-298).
 *
 * @author JQuantLib migration contributors
 */
public class MixedLinearCubicInterpolation extends AbstractInterpolation {

    public MixedLinearCubicInterpolation(final Array vx, final Array vy, final int n, final Behavior behavior,
            final CubicInterpolation.DerivativeApprox da, final boolean monotonic,
            final CubicInterpolation.BoundaryCondition leftC, final double leftConditionValue,
            final CubicInterpolation.BoundaryCondition rightC, final double rightConditionValue) {
        super.impl = new MixedInterpolationImpl(vx, vy, n, behavior, da, monotonic, leftC, leftConditionValue, rightC,
                rightConditionValue);
        super.impl.update();
    }

    /**
     * Choose how the two interpolators see the discrete data.
     */
    public enum Behavior {
        /** Both interpolations defined over the entire data range. */
        ShareRanges,
        /** First interpolation over the first {@code n+1} points, second over the rest. */
        SplitRanges
    }

    //
    // private inner class
    //

    /** Mixed linear / natural-cubic spline (no monotonicity filter). */
    public static class MixedLinearCubicNaturalSpline extends MixedLinearCubicInterpolation {
        public MixedLinearCubicNaturalSpline(final Array vx, final Array vy, final int n) {
            this(vx, vy, n, Behavior.ShareRanges);
        }

        public MixedLinearCubicNaturalSpline(final Array vx, final Array vy, final int n, final Behavior behavior) {
            super(vx, vy, n, behavior, CubicInterpolation.DerivativeApprox.Spline, false,
                    CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                    CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        }
    }

    //
    // public convenience subclasses (mirror C++ mixedinterpolation.hpp lines 134-216)
    //

    /** Mixed linear / monotonic natural-cubic spline. */
    public static class MixedLinearMonotonicCubicNaturalSpline extends MixedLinearCubicInterpolation {
        public MixedLinearMonotonicCubicNaturalSpline(final Array vx, final Array vy, final int n) {
            this(vx, vy, n, Behavior.ShareRanges);
        }

        public MixedLinearMonotonicCubicNaturalSpline(final Array vx, final Array vy, final int n,
                final Behavior behavior) {
            super(vx, vy, n, behavior, CubicInterpolation.DerivativeApprox.Spline, true,
                    CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                    CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        }
    }

    /** Mixed linear / Kruger cubic. */
    public static class MixedLinearKrugerCubic extends MixedLinearCubicInterpolation {
        public MixedLinearKrugerCubic(final Array vx, final Array vy, final int n) {
            this(vx, vy, n, Behavior.ShareRanges);
        }

        public MixedLinearKrugerCubic(final Array vx, final Array vy, final int n, final Behavior behavior) {
            super(vx, vy, n, behavior, CubicInterpolation.DerivativeApprox.Kruger, false,
                    CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                    CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        }
    }

    /** Mixed linear / Fritsch-Butland cubic. */
    public static class MixedLinearFritschButlandCubic extends MixedLinearCubicInterpolation {
        public MixedLinearFritschButlandCubic(final Array vx, final Array vy, final int n) {
            this(vx, vy, n, Behavior.ShareRanges);
        }

        public MixedLinearFritschButlandCubic(final Array vx, final Array vy, final int n, final Behavior behavior) {
            super(vx, vy, n, behavior, CubicInterpolation.DerivativeApprox.FritschButland, false,
                    CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                    CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        }
    }

    /** Mixed linear / parabolic cubic (non-monotonic). */
    public static class MixedLinearParabolic extends MixedLinearCubicInterpolation {
        public MixedLinearParabolic(final Array vx, final Array vy, final int n) {
            this(vx, vy, n, Behavior.ShareRanges);
        }

        public MixedLinearParabolic(final Array vx, final Array vy, final int n, final Behavior behavior) {
            super(vx, vy, n, behavior, CubicInterpolation.DerivativeApprox.Parabolic, false,
                    CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                    CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        }
    }

    /** Mixed linear / monotonic-parabolic cubic. */
    public static class MixedLinearMonotonicParabolic extends MixedLinearCubicInterpolation {
        public MixedLinearMonotonicParabolic(final Array vx, final Array vy, final int n) {
            this(vx, vy, n, Behavior.ShareRanges);
        }

        public MixedLinearMonotonicParabolic(final Array vx, final Array vy, final int n, final Behavior behavior) {
            super(vx, vy, n, behavior, CubicInterpolation.DerivativeApprox.Parabolic, true,
                    CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                    CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        }
    }

    private class MixedInterpolationImpl extends AbstractInterpolation.Impl {

        private final int n_;
        private final double xSwitch_;
        private final Behavior behavior_;
        private final boolean matchDerivatives_;
        private final CubicInterpolation.DerivativeApprox da_;
        private final boolean monotonic_;
        private final CubicInterpolation.BoundaryCondition leftC_;
        private final double leftConditionValue_;
        private final CubicInterpolation.BoundaryCondition rightC_;
        private final double rightConditionValue_;

        // Sub-ranges (for SplitRanges) or full views (for ShareRanges).
        private final Array vx1_;
        private final Array vy1_;
        private final Array vx2_;
        private final Array vy2_;

        // Linear segment is built once; cubic may be rebuilt each update()
        // when match-derivatives is requested.
        private final Interpolation interpolation1_;
        private Interpolation interpolation2_;

        protected MixedInterpolationImpl(final Array vx, final Array vy, final int n, final Behavior behavior,
                final CubicInterpolation.DerivativeApprox da, final boolean monotonic,
                final CubicInterpolation.BoundaryCondition leftC, final double leftConditionValue,
                final CubicInterpolation.BoundaryCondition rightC, final double rightConditionValue) {
            super(vx, vy);

            this.matchDerivatives_ = leftC == CubicInterpolation.BoundaryCondition.FirstDerivative
                    && leftConditionValue == Constants.NULL_REAL;
            QL.require(!matchDerivatives_ || behavior == Behavior.SplitRanges,
                    "matching derivatives is only supported with SplitRanges");

            // Validate n vs. data size, mirroring C++ v1.43 MixedInterpolationImpl
            // (mixedinterpolation.hpp:238-243). The bound is dataSize - 1 for BOTH behaviours: the switch point
            // xBegin + n is dereferenced by update() and value() regardless, so n == dataSize walks off the end.
            // Java previously allowed it for ShareRanges and then failed with an index error on the very next line.
            final int dataSize = vx.size();
            final int maxN = dataSize - 1;
            QL.require(n <= maxN, "n is too large (" + n + " > " + maxN + ")");

            this.n_ = n;
            this.behavior_ = behavior;
            this.xSwitch_ = vx.get(n);
            this.da_ = da;
            this.monotonic_ = monotonic;
            this.leftC_ = leftC;
            this.leftConditionValue_ = leftConditionValue;
            this.rightC_ = rightC;
            this.rightConditionValue_ = rightConditionValue;

            switch ( behavior ) {
            case ShareRanges:
                this.vx1_ = vx;
                this.vy1_ = vy;
                this.vx2_ = vx;
                this.vy2_ = vy;
                break;
            case SplitRanges:
                // interpolation1 over [0, n], inclusive (n+1 points)
                this.vx1_ = new Array(n + 1);
                this.vy1_ = new Array(n + 1);
                for ( int i = 0; i <= n; ++i ) {
                    vx1_.set(i, vx.get(i));
                    vy1_.set(i, vy.get(i));
                }
                // interpolation2 over [n, dataSize-1] (dataSize - n points)
                this.vx2_ = new Array(dataSize - n);
                this.vy2_ = new Array(dataSize - n);
                for ( int i = 0; i + n < dataSize; ++i ) {
                    vx2_.set(i, vx.get(i + n));
                    vy2_.set(i, vy.get(i + n));
                }
                break;
            default:
                throw new LibraryException("unknown mixed-interpolation behavior");
            }

            this.interpolation1_ = new LinearInterpolation(vx1_, vy1_);
            // Always construct an initial cubic; if matchDerivatives, it will
            // be rebuilt inside update() with the linear segment's derivative.
            final double initialLeftValue = matchDerivatives_ ? 0.0 : leftConditionValue_;
            this.interpolation2_ = new CubicInterpolation(vx2_, vy2_, da_, monotonic_, leftC_, initialLeftValue,
                    rightC_, rightConditionValue_);
        }

        //
        // overrides AbstractInterpolation.Impl
        //

        @Override
        public void update() {
            // refresh sub-range copies if SplitRanges (parent data may have changed)
            if ( behavior_ == Behavior.SplitRanges ) {
                final int dataSize = vx.size();
                for ( int i = 0; i <= n_; ++i ) {
                    vx1_.set(i, vx.get(i));
                    vy1_.set(i, vy.get(i));
                }
                for ( int i = 0; i + n_ < dataSize; ++i ) {
                    vx2_.set(i, vx.get(i + n_));
                    vy2_.set(i, vy.get(i + n_));
                }
            }
            interpolation1_.update();
            // Mirrors C++ switchFn (mixedinterpolation.hpp lines 77-82):
            // set the cubic's left-condition value to the linear segment's
            // derivative at the switch point. Java has no mutable
            // leftConditionValue accessor on CubicInterpolation, so we
            // reconstruct the cubic with the freshly computed value.
            if ( matchDerivatives_ ) {
                final double leftDeriv = interpolation1_.derivative(xSwitch_, true);
                this.interpolation2_ = new CubicInterpolation(vx2_, vy2_, da_, monotonic_, leftC_, leftDeriv, rightC_,
                        rightConditionValue_);
            } else {
                interpolation2_.update();
            }
        }

        @Override
        public double op(final double x) {
            if ( x < xSwitch_ ) {
                return interpolation1_.op(x, true);
            }
            return interpolation2_.op(x, true);
        }

        @Override
        public double primitive(final double x) {
            if ( x < xSwitch_ ) {
                return interpolation1_.primitive(x, true);
            }
            return interpolation2_.primitive(x, true) - interpolation2_.primitive(xSwitch_, true)
                    + interpolation1_.primitive(xSwitch_, true);
        }

        @Override
        public double derivative(final double x) {
            if ( x < xSwitch_ ) {
                return interpolation1_.derivative(x, true);
            }
            return interpolation2_.derivative(x, true);
        }

        @Override
        public double secondDerivative(final double x) {
            if ( x < xSwitch_ ) {
                return interpolation1_.secondDerivative(x, true);
            }
            return interpolation2_.secondDerivative(x, true);
        }
    }
}
