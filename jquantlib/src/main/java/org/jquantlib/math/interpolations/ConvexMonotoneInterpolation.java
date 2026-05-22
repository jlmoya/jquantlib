/*
 Copyright (C) 2026 JQuantLib Migration

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
 Copyright (C) 2008 Simon Ibbotson

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.math.interpolations;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;

/**
 * Convex-monotone yield-curve interpolation method.
 * <p>
 * Faithful port of QuantLib v1.42.1 C++ template
 * {@code ConvexMonotoneInterpolation} and the supporting
 * {@code detail::SectionHelper} hierarchy from
 * {@code ql/math/interpolations/convexmonotoneinterpolation.hpp}.
 * <p>
 * Implements the enhanced Hagan/West method described in
 * "Interpolation Methods for Curve Construction"
 * (Hagan & West, AMF Vol 13, No 2 2006).
 * <p>
 * {@code monotonicity = 1} and {@code quadraticity = 0} reproduces the basic
 * Hagan/West method. Other settings smooth the resulting curve and (with
 * {@code forcePositive}) avoid negative interpolated values.
 *
 * @author Phase 1 closure (A7-B-562)
 */
public class ConvexMonotoneInterpolation extends AbstractInterpolation {

    public ConvexMonotoneInterpolation(final Array vx, final Array vy,
            final double quadraticity, final double monotonicity,
            final boolean forcePositive, final boolean flatFinalPeriod) {
        this(vx, vy, quadraticity, monotonicity, forcePositive, flatFinalPeriod,
                new TreeMap<>());
    }

    public ConvexMonotoneInterpolation(final Array vx, final Array vy,
            final double quadraticity, final double monotonicity,
            final boolean forcePositive, final boolean flatFinalPeriod,
            final Map<Double, SectionHelper> preExistingHelpers) {
        super.impl = new ConvexMonotoneImpl(vx, vy, quadraticity, monotonicity,
                forcePositive, flatFinalPeriod, preExistingHelpers);
        super.impl.update();
    }

    /** Returns a snapshot of the section helpers built so far. */
    public Map<Double, SectionHelper> getExistingHelpers() {
        return ((ConvexMonotoneImpl) impl).getExistingHelpers();
    }

    //
    // SectionHelper hierarchy (mirrors C++ detail::SectionHelper subclasses)
    //

    public interface SectionHelper {
        double value(double x);
        double primitive(double x);
        double fNext();
    }

    private static final class EverywhereConstantHelper implements SectionHelper {
        private final double value;
        private final double prevPrimitive;
        private final double xPrev;

        EverywhereConstantHelper(final double value, final double prevPrimitive, final double xPrev) {
            this.value = value;
            this.prevPrimitive = prevPrimitive;
            this.xPrev = xPrev;
        }
        @Override public double value(final double x) { return value; }
        @Override public double primitive(final double x) {
            return prevPrimitive + (x - xPrev) * value;
        }
        @Override public double fNext() { return value; }
    }

    private static final class ComboHelper implements SectionHelper {
        private final double quadraticity;
        private final SectionHelper quadraticHelper;
        private final SectionHelper convMonoHelper;

        ComboHelper(final SectionHelper quadraticHelper,
                    final SectionHelper convMonoHelper,
                    final double quadraticity) {
            QL.require(quadraticity < 1.0 && quadraticity > 0.0,
                    "Quadratic value must lie between 0 and 1");
            this.quadraticity = quadraticity;
            this.quadraticHelper = quadraticHelper;
            this.convMonoHelper = convMonoHelper;
        }
        @Override public double value(final double x) {
            return quadraticity * quadraticHelper.value(x)
                    + (1.0 - quadraticity) * convMonoHelper.value(x);
        }
        @Override public double primitive(final double x) {
            return quadraticity * quadraticHelper.primitive(x)
                    + (1.0 - quadraticity) * convMonoHelper.primitive(x);
        }
        @Override public double fNext() {
            return quadraticity * quadraticHelper.fNext()
                    + (1.0 - quadraticity) * convMonoHelper.fNext();
        }
    }

    private static final class ConvexMonotone2Helper implements SectionHelper {
        private final double xPrev, xScaling, gPrev, gNext, fAverage, eta2, prevPrimitive;
        ConvexMonotone2Helper(final double xPrev, final double xNext,
                final double gPrev, final double gNext,
                final double fAverage, final double eta2,
                final double prevPrimitive) {
            this.xPrev = xPrev;
            this.xScaling = xNext - xPrev;
            this.gPrev = gPrev;
            this.gNext = gNext;
            this.fAverage = fAverage;
            this.eta2 = eta2;
            this.prevPrimitive = prevPrimitive;
        }
        @Override public double value(final double x) {
            final double xVal = (x - xPrev) / xScaling;
            if (xVal <= eta2) {
                return fAverage + gPrev;
            }
            return fAverage + gPrev
                    + (gNext - gPrev) / ((1 - eta2) * (1 - eta2)) * (xVal - eta2) * (xVal - eta2);
        }
        @Override public double primitive(final double x) {
            final double xVal = (x - xPrev) / xScaling;
            if (xVal <= eta2) {
                return prevPrimitive + xScaling * (fAverage * xVal + gPrev * xVal);
            }
            return prevPrimitive + xScaling * (fAverage * xVal + gPrev * xVal
                    + (gNext - gPrev) / ((1 - eta2) * (1 - eta2))
                    * (1.0 / 3.0 * (xVal * xVal * xVal - eta2 * eta2 * eta2)
                            - eta2 * xVal * xVal + eta2 * eta2 * xVal));
        }
        @Override public double fNext() { return fAverage + gNext; }
    }

    private static final class ConvexMonotone3Helper implements SectionHelper {
        private final double xPrev, xScaling, gPrev, gNext, fAverage, eta3, prevPrimitive;
        ConvexMonotone3Helper(final double xPrev, final double xNext,
                final double gPrev, final double gNext,
                final double fAverage, final double eta3,
                final double prevPrimitive) {
            this.xPrev = xPrev;
            this.xScaling = xNext - xPrev;
            this.gPrev = gPrev;
            this.gNext = gNext;
            this.fAverage = fAverage;
            this.eta3 = eta3;
            this.prevPrimitive = prevPrimitive;
        }
        @Override public double value(final double x) {
            final double xVal = (x - xPrev) / xScaling;
            if (xVal <= eta3) {
                return fAverage + gNext
                        + (gPrev - gNext) / (eta3 * eta3) * (eta3 - xVal) * (eta3 - xVal);
            }
            return fAverage + gNext;
        }
        @Override public double primitive(final double x) {
            final double xVal = (x - xPrev) / xScaling;
            if (xVal <= eta3) {
                return prevPrimitive + xScaling * (fAverage * xVal + gNext * xVal
                        + (gPrev - gNext) / (eta3 * eta3)
                        * (1.0 / 3.0 * xVal * xVal * xVal - eta3 * xVal * xVal + eta3 * eta3 * xVal));
            }
            return prevPrimitive + xScaling * (fAverage * xVal + gNext * xVal
                    + (gPrev - gNext) / (eta3 * eta3) * (1.0 / 3.0 * eta3 * eta3 * eta3));
        }
        @Override public double fNext() { return fAverage + gNext; }
    }

    private static class ConvexMonotone4Helper implements SectionHelper {
        protected final double xPrev, xScaling, eta4, prevPrimitive;
        protected double gPrev, gNext, fAverage, A;

        ConvexMonotone4Helper(final double xPrev, final double xNext,
                final double gPrev, final double gNext,
                final double fAverage, final double eta4,
                final double prevPrimitive) {
            this.xPrev = xPrev;
            this.xScaling = xNext - xPrev;
            this.gPrev = gPrev;
            this.gNext = gNext;
            this.fAverage = fAverage;
            this.eta4 = eta4;
            this.prevPrimitive = prevPrimitive;
            this.A = -0.5 * (eta4 * gPrev + (1 - eta4) * gNext);
        }
        @Override public double value(final double x) {
            final double xVal = (x - xPrev) / xScaling;
            if (xVal <= eta4) {
                return fAverage + A + (gPrev - A) * (eta4 - xVal) * (eta4 - xVal) / (eta4 * eta4);
            }
            return fAverage + A
                    + (gNext - A) * (xVal - eta4) * (xVal - eta4) / ((1 - eta4) * (1 - eta4));
        }
        @Override public double primitive(final double x) {
            final double xVal = (x - xPrev) / xScaling;
            if (xVal <= eta4) {
                return prevPrimitive + xScaling * (fAverage + A
                        + (gPrev - A) / (eta4 * eta4)
                        * (eta4 * eta4 - eta4 * xVal + 1.0 / 3.0 * xVal * xVal)) * xVal;
            }
            return prevPrimitive + xScaling * (fAverage * xVal + A * xVal
                    + (gPrev - A) * (1.0 / 3.0 * eta4)
                    + (gNext - A) / ((1 - eta4) * (1 - eta4))
                    * (1.0 / 3.0 * xVal * xVal * xVal - eta4 * xVal * xVal
                            + eta4 * eta4 * xVal - 1.0 / 3.0 * eta4 * eta4 * eta4));
        }
        @Override public double fNext() { return fAverage + gNext; }
    }

    private static final class ConvexMonotone4MinHelper extends ConvexMonotone4Helper {
        private boolean splitRegion = false;
        private double xRatio, x2, x3;

        ConvexMonotone4MinHelper(final double xPrev, final double xNext,
                final double gPrev, final double gNext,
                final double fAverage, final double eta4,
                final double prevPrimitive) {
            super(xPrev, xNext, gPrev, gNext, fAverage, eta4, prevPrimitive);
            if (A + fAverage <= 0.0) {
                splitRegion = true;
                final double fPrev = this.gPrev + fAverage;
                final double fNext = this.gNext + fAverage;
                final double reqdShift = (eta4 * fPrev + (1 - eta4) * fNext) / 3.0 - fAverage;
                final double reqdPeriod = reqdShift * xScaling / (fAverage + reqdShift);
                final double xAdjust = xScaling - reqdPeriod;
                xRatio = xAdjust / xScaling;

                this.fAverage += reqdShift;
                this.gNext = fNext - this.fAverage;
                this.gPrev = fPrev - this.fAverage;
                this.A = -(eta4 * this.gPrev + (1.0 - eta4) * this.gNext) / 2.0;
                x2 = xPrev + xAdjust * eta4;
                x3 = xPrev + xScaling - xAdjust * (1.0 - eta4);
            }
        }
        @Override public double value(final double x) {
            if (!splitRegion) {
                return super.value(x);
            }
            double xVal = (x - xPrev) / xScaling;
            if (x <= x2) {
                xVal /= xRatio;
                return fAverage + A
                        + (gPrev - A) * (eta4 - xVal) * (eta4 - xVal) / (eta4 * eta4);
            } else if (x < x3) {
                return 0.0;
            }
            xVal = 1.0 - (1.0 - xVal) / xRatio;
            return fAverage + A
                    + (gNext - A) * (xVal - eta4) * (xVal - eta4) / ((1 - eta4) * (1 - eta4));
        }
        @Override public double primitive(final double x) {
            if (!splitRegion) {
                return super.primitive(x);
            }
            double xVal = (x - xPrev) / xScaling;
            if (x <= x2) {
                xVal /= xRatio;
                return prevPrimitive + xScaling * xRatio * (fAverage + A
                        + (gPrev - A) / (eta4 * eta4)
                        * (eta4 * eta4 - eta4 * xVal + 1.0 / 3.0 * xVal * xVal)) * xVal;
            } else if (x <= x3) {
                return prevPrimitive + xScaling * xRatio * (fAverage * eta4 + A * eta4
                        + (gPrev - A) / (eta4 * eta4) * (1.0 / 3.0 * eta4 * eta4 * eta4));
            }
            xVal = 1.0 - (1.0 - xVal) / xRatio;
            return prevPrimitive + xScaling * xRatio * (fAverage * xVal + A * xVal
                    + (gPrev - A) * (1.0 / 3.0 * eta4)
                    + (gNext - A) / ((1.0 - eta4) * (1.0 - eta4))
                    * (1.0 / 3.0 * xVal * xVal * xVal - eta4 * xVal * xVal
                            + eta4 * eta4 * xVal - 1.0 / 3.0 * eta4 * eta4 * eta4));
        }
    }

    private static final class ConstantGradHelper implements SectionHelper {
        private final double fPrev, prevPrimitive, xPrev, fGrad, fNext;
        ConstantGradHelper(final double fPrev, final double prevPrimitive,
                final double xPrev, final double xNext, final double fNext) {
            this.fPrev = fPrev;
            this.prevPrimitive = prevPrimitive;
            this.xPrev = xPrev;
            this.fGrad = (fNext - fPrev) / (xNext - xPrev);
            this.fNext = fNext;
        }
        @Override public double value(final double x) { return fPrev + (x - xPrev) * fGrad; }
        @Override public double primitive(final double x) {
            return prevPrimitive + (x - xPrev) * (fPrev + 0.5 * (x - xPrev) * fGrad);
        }
        @Override public double fNext() { return fNext; }
    }

    private static final class QuadraticHelper implements SectionHelper {
        private final double xPrev, prevPrimitive, fNext;
        private final double a, b, c, xScaling;
        QuadraticHelper(final double xPrev, final double xNext,
                final double fPrev, final double fNext,
                final double fAverage, final double prevPrimitive) {
            this.xPrev = xPrev;
            this.prevPrimitive = prevPrimitive;
            this.fNext = fNext;
            this.a = 3 * fPrev + 3 * fNext - 6 * fAverage;
            this.b = -(4 * fPrev + 2 * fNext - 6 * fAverage);
            this.c = fPrev;
            this.xScaling = xNext - xPrev;
        }
        @Override public double value(final double x) {
            final double xVal = (x - xPrev) / xScaling;
            return a * xVal * xVal + b * xVal + c;
        }
        @Override public double primitive(final double x) {
            final double xVal = (x - xPrev) / xScaling;
            return prevPrimitive + xScaling * (a / 3.0 * xVal * xVal + b / 2.0 * xVal + c) * xVal;
        }
        @Override public double fNext() { return fNext; }
    }

    private static final class QuadraticMinHelper implements SectionHelper {
        private boolean splitRegion = false;
        private final double x1, x4;
        private double x2, x3;
        private double a, b, c;
        private final double primitive1;
        private double primitive2;
        private final double fNext;
        private double xScaling;
        private double xRatio = 1.0;

        QuadraticMinHelper(final double xPrev, final double xNext,
                final double fPrev, final double fNext,
                final double fAverage, final double prevPrimitive) {
            this.x1 = xPrev;
            this.x4 = xNext;
            this.primitive1 = prevPrimitive;
            this.fNext = fNext;
            this.a = 3 * fPrev + 3 * fNext - 6 * fAverage;
            this.b = -(4 * fPrev + 2 * fNext - 6 * fAverage);
            this.c = fPrev;
            final double d = b * b - 4 * a * c;
            this.xScaling = x4 - x1;
            if (d > 0) {
                final double aAv = 36;
                final double bAv = -24 * (fPrev + fNext);
                final double cAv = 4 * (fPrev * fPrev + fPrev * fNext + fNext * fNext);
                final double dAv = bAv * bAv - 4.0 * aAv * cAv;
                if (dAv >= 0.0) {
                    splitRegion = true;
                    final double avRoot = (-bAv - Math.sqrt(dAv)) / (2 * aAv);

                    xRatio = fAverage / avRoot;
                    xScaling *= xRatio;

                    this.a = 3 * fPrev + 3 * fNext - 6 * avRoot;
                    this.b = -(4 * fPrev + 2 * fNext - 6 * avRoot);
                    this.c = fPrev;
                    final double xRoot = -b / (2 * a);
                    x2 = x1 + xRatio * (x4 - x1) * xRoot;
                    x3 = x4 - xRatio * (x4 - x1) * (1 - xRoot);
                    primitive2 = primitive1
                            + xScaling * (a / 3.0 * xRoot * xRoot + b / 2.0 * xRoot + c) * xRoot;
                }
            }
        }
        @Override public double value(final double x) {
            double xVal = (x - x1) / (x4 - x1);
            if (splitRegion) {
                if (x <= x2) {
                    xVal /= xRatio;
                } else if (x < x3) {
                    return 0.0;
                } else {
                    xVal = 1.0 - (1.0 - xVal) / xRatio;
                }
            }
            return c + b * xVal + a * xVal * xVal;
        }
        @Override public double primitive(final double x) {
            double xVal = (x - x1) / (x4 - x1);
            if (splitRegion) {
                if (x < x2) {
                    xVal /= xRatio;
                } else if (x < x3) {
                    return primitive2;
                } else {
                    xVal = 1.0 - (1.0 - xVal) / xRatio;
                }
            }
            return primitive1 + xScaling * (a / 3.0 * xVal * xVal + b / 2.0 * xVal + c) * xVal;
        }
        @Override public double fNext() { return fNext; }
    }

    //
    // ConvexMonotoneImpl — mirrors C++ detail::ConvexMonotoneImpl<I1,I2>
    //

    private final class ConvexMonotoneImpl extends AbstractInterpolation.Impl {

        private final NavigableMap<Double, SectionHelper> sectionHelpers = new TreeMap<>();
        private final NavigableMap<Double, SectionHelper> preSectionHelpers;
        private SectionHelper extrapolationHelper;
        private final boolean forcePositive;
        private final boolean constantLastPeriod;
        private final double quadraticity;
        private final double monotonicity;
        private final int length;

        ConvexMonotoneImpl(final Array vx, final Array vy,
                final double quadraticity, final double monotonicity,
                final boolean forcePositive, final boolean constantLastPeriod,
                final Map<Double, SectionHelper> preExistingHelpers) {
            super(vx, vy);
            QL.require(monotonicity >= 0 && monotonicity <= 1,
                    "Monotonicity must lie between 0 and 1");
            QL.require(quadraticity >= 0 && quadraticity <= 1,
                    "Quadraticity must lie between 0 and 1");
            this.length = (int) vx.size();
            QL.require(length >= 2,
                    "Single point provided, not supported by convex monotone method as first point is ignored");
            QL.require((length - preExistingHelpers.size()) > 1,
                    "Too many existing helpers have been supplied");
            this.quadraticity = quadraticity;
            this.monotonicity = monotonicity;
            this.forcePositive = forcePositive;
            this.constantLastPeriod = constantLastPeriod;
            this.preSectionHelpers = new TreeMap<>(preExistingHelpers);
        }

        Map<Double, SectionHelper> getExistingHelpers() {
            final NavigableMap<Double, SectionHelper> retArray = new TreeMap<>(sectionHelpers);
            if (constantLastPeriod) {
                retArray.remove(vx.get((int) vx.size() - 1));
            }
            return retArray;
        }

        @Override
        public void update() {
            sectionHelpers.clear();
            if (length == 2) {
                final SectionHelper singleHelper =
                        new EverywhereConstantHelper(vy.get(1), 0.0, vx.get(0));
                sectionHelpers.put(vx.get(1), singleHelper);
                extrapolationHelper = singleHelper;
                return;
            }

            final double[] f = new double[length];
            sectionHelpers.putAll(preSectionHelpers);
            int startPoint = sectionHelpers.size() + 1;

            // derive boundary forwards
            for (int i = startPoint; i < length - 1; ++i) {
                final double dxPrev = vx.get(i) - vx.get(i - 1);
                final double dx = vx.get(i + 1) - vx.get(i);
                f[i] = dx / (dx + dxPrev) * vy.get(i) + dxPrev / (dx + dxPrev) * vy.get(i + 1);
            }
            if (startPoint > 1) {
                f[startPoint - 1] = preSectionHelpers.lastEntry().getValue().fNext();
            }
            if (startPoint == 1) {
                f[0] = 1.5 * vy.get(1) - 0.5 * f[1];
            }
            f[length - 1] = 1.5 * vy.get(length - 1) - 0.5 * f[length - 2];

            if (forcePositive) {
                if (f[0] < 0) {
                    f[0] = 0.0;
                }
                if (f[length - 1] < 0.0) {
                    f[length - 1] = 0.0;
                }
            }

            double primitive = 0.0;
            for (int i = 0; i < startPoint - 1; ++i) {
                primitive += vy.get(i + 1) * (vx.get(i + 1) - vx.get(i));
            }

            int endPoint = length;
            if (constantLastPeriod) {
                endPoint = endPoint - 1;
            }

            for (int i = startPoint; i < endPoint; ++i) {
                final double gPrev = f[i - 1] - vy.get(i);
                final double gNext = f[i] - vy.get(i);
                if (Math.abs(gPrev) < 1.0E-14 && Math.abs(gNext) < 1.0E-14) {
                    sectionHelpers.put(vx.get(i),
                            new ConstantGradHelper(f[i - 1], primitive,
                                    vx.get(i - 1), vx.get(i), f[i]));
                } else {
                    double q = this.quadraticity;
                    SectionHelper quadraticHelper = null;
                    SectionHelper convMonotoneHelper = null;
                    if (this.quadraticity > 0.0) {
                        if (gPrev >= -2.0 * gNext && gPrev > -0.5 * gNext && forcePositive) {
                            quadraticHelper = new QuadraticMinHelper(vx.get(i - 1), vx.get(i),
                                    f[i - 1], f[i], vy.get(i), primitive);
                        } else {
                            quadraticHelper = new QuadraticHelper(vx.get(i - 1), vx.get(i),
                                    f[i - 1], f[i], vy.get(i), primitive);
                        }
                    }
                    if (this.quadraticity < 1.0) {
                        if ((gPrev > 0.0 && -0.5 * gPrev >= gNext && gNext >= -2.0 * gPrev)
                                || (gPrev < 0.0 && -0.5 * gPrev <= gNext && gNext <= -2.0 * gPrev)) {
                            q = 1.0;
                            if (this.quadraticity == 0) {
                                if (forcePositive) {
                                    quadraticHelper = new QuadraticMinHelper(vx.get(i - 1), vx.get(i),
                                            f[i - 1], f[i], vy.get(i), primitive);
                                } else {
                                    quadraticHelper = new QuadraticHelper(vx.get(i - 1), vx.get(i),
                                            f[i - 1], f[i], vy.get(i), primitive);
                                }
                            }
                        } else if ((gPrev < 0.0 && gNext > -2.0 * gPrev)
                                || (gPrev > 0.0 && gNext < -2.0 * gPrev)) {
                            final double eta = (gNext + 2.0 * gPrev) / (gNext - gPrev);
                            final double b2 = (1.0 + monotonicity) / 2.0;
                            if (eta < b2) {
                                convMonotoneHelper = new ConvexMonotone2Helper(vx.get(i - 1), vx.get(i),
                                        gPrev, gNext, vy.get(i), eta, primitive);
                            } else if (forcePositive) {
                                convMonotoneHelper = new ConvexMonotone4MinHelper(vx.get(i - 1), vx.get(i),
                                        gPrev, gNext, vy.get(i), b2, primitive);
                            } else {
                                convMonotoneHelper = new ConvexMonotone4Helper(vx.get(i - 1), vx.get(i),
                                        gPrev, gNext, vy.get(i), b2, primitive);
                            }
                        } else if ((gPrev > 0.0 && gNext < 0.0 && gNext > -0.5 * gPrev)
                                || (gPrev < 0.0 && gNext > 0.0 && gNext < -0.5 * gPrev)) {
                            final double eta = gNext / (gNext - gPrev) * 3.0;
                            final double b3 = (1.0 - monotonicity) / 2.0;
                            if (eta > b3) {
                                convMonotoneHelper = new ConvexMonotone3Helper(vx.get(i - 1), vx.get(i),
                                        gPrev, gNext, vy.get(i), eta, primitive);
                            } else if (forcePositive) {
                                convMonotoneHelper = new ConvexMonotone4MinHelper(vx.get(i - 1), vx.get(i),
                                        gPrev, gNext, vy.get(i), b3, primitive);
                            } else {
                                convMonotoneHelper = new ConvexMonotone4Helper(vx.get(i - 1), vx.get(i),
                                        gPrev, gNext, vy.get(i), b3, primitive);
                            }
                        } else {
                            double eta = gNext / (gPrev + gNext);
                            final double b2 = (1.0 + monotonicity) / 2.0;
                            final double b3 = (1.0 - monotonicity) / 2.0;
                            if (eta > b2) {
                                eta = b2;
                            }
                            if (eta < b3) {
                                eta = b3;
                            }
                            if (forcePositive) {
                                convMonotoneHelper = new ConvexMonotone4MinHelper(vx.get(i - 1), vx.get(i),
                                        gPrev, gNext, vy.get(i), eta, primitive);
                            } else {
                                convMonotoneHelper = new ConvexMonotone4Helper(vx.get(i - 1), vx.get(i),
                                        gPrev, gNext, vy.get(i), eta, primitive);
                            }
                        }
                    }
                    if (q == 1.0) {
                        sectionHelpers.put(vx.get(i), quadraticHelper);
                    } else if (q == 0.0) {
                        sectionHelpers.put(vx.get(i), convMonotoneHelper);
                    } else {
                        sectionHelpers.put(vx.get(i),
                                new ComboHelper(quadraticHelper, convMonotoneHelper, q));
                    }
                }
                primitive += vy.get(i) * (vx.get(i) - vx.get(i - 1));
            }

            if (constantLastPeriod) {
                final SectionHelper tail = new EverywhereConstantHelper(
                        vy.get(length - 1), primitive, vx.get(length - 2));
                sectionHelpers.put(vx.get(length - 1), tail);
                extrapolationHelper = tail;
            } else {
                final double xEnd = vx.get(length - 1);
                extrapolationHelper = new EverywhereConstantHelper(
                        sectionHelpers.lastEntry().getValue().value(xEnd), primitive, xEnd);
            }
        }

        @Override
        public double op(final double x) {
            final double xEnd = vx.get(length - 1);
            if (x >= xEnd) {
                return extrapolationHelper.value(x);
            }
            // C++ uses std::map::upper_bound which returns the first key strictly > x.
            return sectionHelpers.higherEntry(x).getValue().value(x);
        }

        @Override
        public double primitive(final double x) {
            final double xEnd = vx.get(length - 1);
            if (x >= xEnd) {
                return extrapolationHelper.primitive(x);
            }
            return sectionHelpers.higherEntry(x).getValue().primitive(x);
        }

        @Override
        public double derivative(final double x) {
            throw new UnsupportedOperationException("Convex-monotone spline derivative not implemented");
        }

        @Override
        public double secondDerivative(final double x) {
            throw new UnsupportedOperationException("Convex-monotone spline second derivative not implemented");
        }
    }
}
