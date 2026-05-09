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
package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.distributions.Derivative;
import org.jquantlib.math.solvers1D.Newton;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;

/**
 * Factory for {@link GFunction} variants used by Hagan-style CMS pricing.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code GFunctionFactory} in
 * {@code ql/cashflows/conundrumpricer.{hpp,cpp}}. Three variants are
 * exposed:
 * <ul>
 *   <li>{@link #newGFunctionStandard(int, double, int)} — closed-form
 *       parametric form (Hagan eq. 3.5b).</li>
 *   <li>{@link #newGFunctionExactYield(CmsCoupon)} — exact-yield model
 *       built from the CMS coupon's underlying-swap accruals.</li>
 *   <li>{@link #newGFunctionWithShifts(CmsCoupon, Handle)} — non-parallel
 *       shifts model with a Newton-calibrated shift parameter.</li>
 * </ul>
 *
 * <h3>YieldCurveModel enum</h3>
 *
 * <p>Selects which {@code GFunction} a {@code HaganPricer} will build.
 * Mirrors C++ {@code GFunctionFactory::YieldCurveModel}.
 */
public final class GFunctionFactory {

    /** Selector for {@link HaganPricer}'s G-function variant. */
    public enum YieldCurveModel {
        Standard,
        ExactYield,
        ParallelShifts,
        NonParallelShifts
    }

    private GFunctionFactory() {
        // utility - no instances
    }

    /** {@code GFunctionStandard} — Hagan closed-form 3.5b. */
    public static GFunction newGFunctionStandard(final int q,
                                                 final double delta,
                                                 final int swapLength) {
        return new GFunctionStandard(q, delta, swapLength);
    }

    /** {@code GFunctionExactYield} — exact-yield from CMS coupon's swap accruals. */
    public static GFunction newGFunctionExactYield(final CmsCoupon coupon) {
        return new GFunctionExactYield(coupon);
    }

    /** {@code GFunctionWithShifts} — non-parallel shifts with Newton-calibrated shift. */
    public static GFunction newGFunctionWithShifts(final CmsCoupon coupon,
                                                   final Handle<Quote> meanReversion) {
        return new GFunctionWithShifts(coupon, meanReversion);
    }

    //========================================================================
    //                          GFunctionStandard
    //========================================================================
    static final class GFunctionStandard implements GFunction {
        private final int q_;
        private final double delta_;
        private final int swapLength_;

        GFunctionStandard(final int q, final double delta, final int swapLength) {
            this.q_ = q;
            this.delta_ = delta;
            this.swapLength_ = swapLength;
        }

        @Override
        public double evaluate(final double x) {
            final double n = (double) swapLength_ * (double) q_;
            return x / Math.pow(1.0 + x / q_, delta_)
                   * 1.0 / (1.0 - 1.0 / Math.pow(1.0 + x / q_, n));
        }

        @Override
        public double firstDerivative(final double x) {
            final double n = (double) swapLength_ * (double) q_;
            final double a = 1.0 + x / q_;
            final double aPow_n = Math.pow(a, n);
            final double AA = a - delta_ / q_ * x;
            final double B = Math.pow(a, n - delta_ - 1.0) / (aPow_n - 1.0);

            final double secNum = n * x * Math.pow(a, n - 1.0);
            final double secDen = q_ * Math.pow(a, delta_) * (aPow_n - 1.0) * (aPow_n - 1.0);
            final double sec = secNum / secDen;

            return AA * B - sec;
        }

        @Override
        public double secondDerivative(final double x) {
            final double n = (double) swapLength_ * (double) q_;
            final double a = 1.0 + x / q_;
            final double aPow_n = Math.pow(a, n);
            final double aPow_nm1 = Math.pow(a, n - 1.0);
            final double aPow_2nm2 = Math.pow(a, 2.0 * (n - 1.0));
            final double aPow_delta = Math.pow(a, delta_);
            final double aPow_2delta = Math.pow(a, 2.0 * delta_);
            final double aPow_dm1 = Math.pow(a, delta_ - 1.0);

            final double AA = a - delta_ / q_ * x;
            final double A1 = (1.0 - delta_) / q_;
            final double B = Math.pow(a, n - delta_ - 1.0) / (aPow_n - 1.0);
            final double Num = (1.0 + delta_ - n) * Math.pow(a, n - delta_ - 2.0)
                               - (1.0 + delta_) * Math.pow(a, 2.0 * n - delta_ - 2.0);
            final double Den = (aPow_n - 1.0) * (aPow_n - 1.0);
            final double B1 = 1.0 / q_ * Num / Den;

            final double C = x / aPow_delta;
            final double C1 = (aPow_delta - delta_ / q_ * x * aPow_dm1) / aPow_2delta;

            final double D = aPow_nm1 / ((aPow_n - 1.0) * (aPow_n - 1.0));
            final double D1 = ((n - 1.0) * Math.pow(a, n - 2.0) * (aPow_n - 1.0)
                               - 2.0 * n * aPow_2nm2)
                              / (q_ * (aPow_n - 1.0) * (aPow_n - 1.0) * (aPow_n - 1.0));

            return A1 * B + AA * B1 - n / q_ * (C1 * D + C * D1);
        }
    }

    //========================================================================
    //                          GFunctionExactYield
    //========================================================================
    static final class GFunctionExactYield implements GFunction {
        private final double delta_;
        private final double[] accruals_;

        GFunctionExactYield(final CmsCoupon coupon) {
            final SwapIndex swapIndex = coupon.swapIndex();
            final VanillaSwap swap = swapIndex.underlyingSwap(coupon.fixingDate());

            final Schedule schedule = swap.fixedSchedule();
            final YieldTermStructure rateCurve = swapIndex.termStructure().currentLink();
            final DayCounter dc = swapIndex.dayCounter();

            final double swapStartTime = dc.yearFraction(rateCurve.referenceDate(), schedule.startDate());
            final double swapFirstPaymentTime = dc.yearFraction(rateCurve.referenceDate(), schedule.date(1));

            final double paymentTime = dc.yearFraction(rateCurve.referenceDate(), coupon.date());

            this.delta_ = (paymentTime - swapStartTime) / (swapFirstPaymentTime - swapStartTime);

            final Leg fixedLeg = swap.fixedLeg();
            final int n = fixedLeg.size();
            this.accruals_ = new double[n];
            for (int i = 0; i < n; ++i) {
                final Coupon cpn = (Coupon) fixedLeg.get(i);
                this.accruals_[i] = cpn.accrualPeriod();
            }
        }

        @Override
        public double evaluate(final double x) {
            double product = 1.0;
            for (final double accrual : accruals_) {
                product *= 1.0 / (1.0 + accrual * x);
            }
            return x * Math.pow(1.0 + accruals_[0] * x, -delta_) * (1.0 / (1.0 - product));
        }

        @Override
        public double firstDerivative(final double x) {
            double c = -1.0;
            double derC = 0.0;
            final double[] b = new double[accruals_.length];
            for (int i = 0; i < accruals_.length; ++i) {
                final double accrual = accruals_[i];
                final double temp = 1.0 / (1.0 + accrual * x);
                b[i] = temp;
                c *= temp;
                derC += accrual * temp;
            }
            c += 1.0;
            c = 1.0 / c;
            derC *= (c - c * c);

            return -delta_ * accruals_[0] * Math.pow(b[0], delta_ + 1.0) * x * c
                   + Math.pow(b[0], delta_) * c
                   + Math.pow(b[0], delta_) * x * derC;
        }

        @Override
        public double secondDerivative(final double x) {
            double c = -1.0;
            double sum = 0.0;
            double sumOfSquare = 0.0;
            final double[] b = new double[accruals_.length];
            for (int i = 0; i < accruals_.length; ++i) {
                final double accrual = accruals_[i];
                final double temp = 1.0 / (1.0 + accrual * x);
                b[i] = temp;
                c *= temp;
                sum += accrual * temp;
                sumOfSquare += Math.pow(accrual * temp, 2.0);
            }
            c += 1.0;
            c = 1.0 / c;
            final double derC = sum * (c - c * c);

            return (-delta_ * accruals_[0] * Math.pow(b[0], delta_ + 1.0) * c
                    + Math.pow(b[0], delta_) * derC)
                   * (-delta_ * accruals_[0] * b[0] * x + 1.0 + x * (1.0 - c) * sum)
                   + Math.pow(b[0], delta_) * c
                   * (delta_ * Math.pow(accruals_[0] * b[0], 2.0) * x
                      - delta_ * accruals_[0] * b[0]
                      - x * derC * sum + (1.0 - c) * sum
                      - x * (1.0 - c) * sumOfSquare);
        }
    }

    //========================================================================
    //                          GFunctionWithShifts
    //========================================================================
    static final class GFunctionWithShifts implements GFunction {

        private final double swapStartTime_;

        private final double shapedPaymentTime_;
        private final double[] shapedSwapPaymentTimes_;

        private final double[] accruals_;
        private final double[] swapPaymentDiscounts_;
        private final double discountAtStart_;
        private final double discountRatio_;

        private final double swapRateValue_;
        private final Handle<Quote> meanReversion_;

        private double calibratedShift_ = 0.03;
        private double tmpRs_ = 1.0e7;
        private static final double ACCURACY = 1.0e-14;

        private final ObjectiveFunction objectiveFunction_;

        GFunctionWithShifts(final CmsCoupon coupon, final Handle<Quote> meanReversion) {
            this.meanReversion_ = meanReversion;

            final SwapIndex swapIndex = coupon.swapIndex();
            final VanillaSwap swap = swapIndex.underlyingSwap(coupon.fixingDate());

            this.swapRateValue_ = swap.fairRate();

            this.objectiveFunction_ = new ObjectiveFunction(this, swapRateValue_);

            final Schedule schedule = swap.fixedSchedule();
            final YieldTermStructure rateCurve = swapIndex.termStructure().currentLink();
            final DayCounter dc = swapIndex.dayCounter();

            this.swapStartTime_ = dc.yearFraction(rateCurve.referenceDate(), schedule.startDate());
            this.discountAtStart_ = rateCurve.discount(schedule.startDate());

            final double paymentTime = dc.yearFraction(rateCurve.referenceDate(), coupon.date());

            this.shapedPaymentTime_ = shapeOfShift(paymentTime);

            final Leg fixedLeg = swap.fixedLeg();
            final int n = fixedLeg.size();
            this.accruals_ = new double[n];
            this.shapedSwapPaymentTimes_ = new double[n];
            this.swapPaymentDiscounts_ = new double[n];
            for (int i = 0; i < n; ++i) {
                final Coupon cpn = (Coupon) fixedLeg.get(i);
                this.accruals_[i] = cpn.accrualPeriod();
                final Date paymentDate = cpn.date();
                final double swapPaymentTime = dc.yearFraction(rateCurve.referenceDate(), paymentDate);
                this.shapedSwapPaymentTimes_[i] = shapeOfShift(swapPaymentTime);
                this.swapPaymentDiscounts_[i] = rateCurve.discount(paymentDate);
            }
            this.discountRatio_ = swapPaymentDiscounts_[n - 1] / discountAtStart_;
        }

        @Override
        public double evaluate(final double Rs) {
            final double calibratedShift = calibrationOfShift(Rs);
            return Rs * functionZ(calibratedShift);
        }

        @Override
        public double firstDerivative(final double Rs) {
            final double calibratedShift = calibrationOfShift(Rs);
            return functionZ(calibratedShift) + Rs * derZ_derX(calibratedShift) / derRs_derX(calibratedShift);
        }

        @Override
        public double secondDerivative(final double Rs) {
            final double calibratedShift = calibrationOfShift(Rs);
            return 2.0 * derZ_derX(calibratedShift) / derRs_derX(calibratedShift)
                   + Rs * der2Z_derX2(calibratedShift) / Math.pow(derRs_derX(calibratedShift), 2.0)
                   - Rs * derZ_derX(calibratedShift) * der2Rs_derX2(calibratedShift)
                     / Math.pow(derRs_derX(calibratedShift), 3.0);
        }

        private double functionZ(final double x) {
            final int last = shapedSwapPaymentTimes_.length - 1;
            return Math.exp(-shapedPaymentTime_ * x)
                   / (1.0 - discountRatio_ * Math.exp(-shapedSwapPaymentTimes_[last] * x));
        }

        private double derRs_derX(final double x) {
            double sqrtDenominator = 0.0;
            double derSqrtDenominator = 0.0;
            for (int i = 0; i < accruals_.length; ++i) {
                sqrtDenominator += accruals_[i] * swapPaymentDiscounts_[i]
                                   * Math.exp(-shapedSwapPaymentTimes_[i] * x);
                derSqrtDenominator -= shapedSwapPaymentTimes_[i] * accruals_[i] * swapPaymentDiscounts_[i]
                                      * Math.exp(-shapedSwapPaymentTimes_[i] * x);
            }
            final double denominator = sqrtDenominator * sqrtDenominator;

            final int last = shapedSwapPaymentTimes_.length - 1;
            double numerator = 0.0;
            numerator += shapedSwapPaymentTimes_[last] * swapPaymentDiscounts_[last]
                         * Math.exp(-shapedSwapPaymentTimes_[last] * x) * sqrtDenominator;
            numerator -= (discountAtStart_ - swapPaymentDiscounts_[last]
                          * Math.exp(-shapedSwapPaymentTimes_[last] * x))
                         * derSqrtDenominator;
            QL.require(denominator != 0.0, "GFunctionWithShifts::derRs_derX: denominator == 0");
            return numerator / denominator;
        }

        private double der2Rs_derX2(final double x) {
            double denOfRfunztion = 0.0;
            double derDenOfRfunztion = 0.0;
            double der2DenOfRfunztion = 0.0;
            for (int i = 0; i < accruals_.length; ++i) {
                denOfRfunztion += accruals_[i] * swapPaymentDiscounts_[i]
                                  * Math.exp(-shapedSwapPaymentTimes_[i] * x);
                derDenOfRfunztion -= shapedSwapPaymentTimes_[i] * accruals_[i] * swapPaymentDiscounts_[i]
                                     * Math.exp(-shapedSwapPaymentTimes_[i] * x);
                der2DenOfRfunztion += shapedSwapPaymentTimes_[i] * shapedSwapPaymentTimes_[i]
                                       * accruals_[i] * swapPaymentDiscounts_[i]
                                       * Math.exp(-shapedSwapPaymentTimes_[i] * x);
            }

            final double denominator = Math.pow(denOfRfunztion, 4);

            final int last = shapedSwapPaymentTimes_.length - 1;
            double numOfDerR = 0.0;
            numOfDerR += shapedSwapPaymentTimes_[last] * swapPaymentDiscounts_[last]
                         * Math.exp(-shapedSwapPaymentTimes_[last] * x) * denOfRfunztion;
            numOfDerR -= (discountAtStart_ - swapPaymentDiscounts_[last]
                          * Math.exp(-shapedSwapPaymentTimes_[last] * x)) * derDenOfRfunztion;

            final double denOfDerR = Math.pow(denOfRfunztion, 2);

            double derNumOfDerR = 0.0;
            derNumOfDerR -= shapedSwapPaymentTimes_[last] * shapedSwapPaymentTimes_[last]
                            * swapPaymentDiscounts_[last]
                            * Math.exp(-shapedSwapPaymentTimes_[last] * x) * denOfRfunztion;
            derNumOfDerR += shapedSwapPaymentTimes_[last] * swapPaymentDiscounts_[last]
                            * Math.exp(-shapedSwapPaymentTimes_[last] * x) * derDenOfRfunztion;
            derNumOfDerR -= (shapedSwapPaymentTimes_[last] * swapPaymentDiscounts_[last]
                              * Math.exp(-shapedSwapPaymentTimes_[last] * x)) * derDenOfRfunztion;
            derNumOfDerR -= (discountAtStart_ - swapPaymentDiscounts_[last]
                              * Math.exp(-shapedSwapPaymentTimes_[last] * x)) * der2DenOfRfunztion;

            final double derDenOfDerR = 2.0 * denOfRfunztion * derDenOfRfunztion;

            final double numerator = derNumOfDerR * denOfDerR - numOfDerR * derDenOfDerR;
            QL.require(denominator != 0.0, "GFunctionWithShifts::der2Rs_derX2: denominator == 0");
            return numerator / denominator;
        }

        private double derZ_derX(final double x) {
            final int last = shapedSwapPaymentTimes_.length - 1;
            final double sqrtDenominator = 1.0 - discountRatio_ * Math.exp(-shapedSwapPaymentTimes_[last] * x);
            final double denominator = sqrtDenominator * sqrtDenominator;
            QL.require(denominator != 0.0, "GFunctionWithShifts::derZ_derX: denominator == 0");

            double numerator = 0.0;
            numerator -= shapedPaymentTime_ * Math.exp(-shapedPaymentTime_ * x) * sqrtDenominator;
            numerator -= shapedSwapPaymentTimes_[last] * Math.exp(-shapedPaymentTime_ * x)
                         * (1.0 - sqrtDenominator);

            return numerator / denominator;
        }

        private double der2Z_derX2(final double x) {
            final int last = shapedSwapPaymentTimes_.length - 1;
            final double denOfZfunction = 1.0 - discountRatio_ * Math.exp(-shapedSwapPaymentTimes_[last] * x);
            final double derDenOfZfunction = shapedSwapPaymentTimes_[last] * discountRatio_
                                              * Math.exp(-shapedSwapPaymentTimes_[last] * x);
            final double denominator = Math.pow(denOfZfunction, 4);
            QL.require(denominator != 0.0, "GFunctionWithShifts::der2Z_derX2: denominator == 0");

            double numOfDerZ = 0.0;
            numOfDerZ -= shapedPaymentTime_ * Math.exp(-shapedPaymentTime_ * x) * denOfZfunction;
            numOfDerZ -= shapedSwapPaymentTimes_[last] * Math.exp(-shapedPaymentTime_ * x)
                         * (1.0 - denOfZfunction);

            final double denOfDerZ = Math.pow(denOfZfunction, 2);
            final double derNumOfDerZ = -shapedPaymentTime_ * Math.exp(-shapedPaymentTime_ * x)
                    * (-shapedPaymentTime_
                       + (shapedPaymentTime_ * discountRatio_ - shapedSwapPaymentTimes_[last] * discountRatio_)
                         * Math.exp(-shapedSwapPaymentTimes_[last] * x))
                    - shapedSwapPaymentTimes_[last] * Math.exp(-shapedPaymentTime_ * x)
                      * (shapedPaymentTime_ * discountRatio_ - shapedSwapPaymentTimes_[last] * discountRatio_)
                      * Math.exp(-shapedSwapPaymentTimes_[last] * x);

            final double derDenOfDerZ = 2.0 * denOfZfunction * derDenOfZfunction;
            final double numerator = derNumOfDerZ * denOfDerZ - numOfDerZ * derDenOfDerZ;

            return numerator / denominator;
        }

        private double shapeOfShift(final double s) {
            final double x = s - swapStartTime_;
            final double meanReversion = meanReversion_.currentLink().value();
            if (meanReversion > 0) {
                return (1.0 - Math.exp(-meanReversion * x)) / meanReversion;
            }
            return x;
        }

        private double calibrationOfShift(final double Rs) {
            if (Rs != tmpRs_) {
                final int last = shapedSwapPaymentTimes_.length - 1;
                double initialGuess;
                double N = 0.0;
                double D = 0.0;
                for (int i = 0; i < accruals_.length; ++i) {
                    N += accruals_[i] * swapPaymentDiscounts_[i];
                    D += accruals_[i] * swapPaymentDiscounts_[i] * shapedSwapPaymentTimes_[i];
                }
                N *= Rs;
                D *= Rs;
                N += accruals_[last] * swapPaymentDiscounts_[last] - discountAtStart_;
                D += accruals_[last] * swapPaymentDiscounts_[last] * shapedSwapPaymentTimes_[last];
                initialGuess = N / D;

                objectiveFunction_.setSwapRateValue(Rs);
                final Newton solver = new Newton();
                solver.setMaxEvaluations(1000);

                // these boundaries might not be big enough if the volatility
                // of big swap rate values is too high. In that case the G function
                // is not even integrable, so better to fix the vol than increasing
                // these values
                final double lower = -20.0;
                final double upper = 20.0;

                try {
                    calibratedShift_ = solver.solve(objectiveFunction_, ACCURACY,
                            Math.max(Math.min(initialGuess, upper * 0.99), lower * 0.99),
                            lower, upper);
                } catch (final Exception e) {
                    QL.error("GFunctionWithShifts.calibrationOfShift: meanReversion="
                            + meanReversion_.currentLink().value()
                            + ", swapRateValue=" + swapRateValue_
                            + ", swapStartTime=" + swapStartTime_
                            + ", shapedPaymentTime=" + shapedPaymentTime_
                            + " — " + e.getMessage());
                }
                tmpRs_ = Rs;
            }
            return calibratedShift_;
        }

        /**
         * Newton-friendly root-finder objective F(x) for shift calibration.
         * Mirrors C++ {@code GFunctionWithShifts::ObjectiveFunction}.
         */
        static final class ObjectiveFunction implements Derivative {
            private final GFunctionWithShifts o_;
            private double Rs_;
            private double derivative_;

            ObjectiveFunction(final GFunctionWithShifts o, final double Rs) {
                this.o_ = o;
                this.Rs_ = Rs;
            }

            @Override
            public double op(final double x) {
                double result = 0.0;
                derivative_ = 0.0;
                for (int i = 0; i < o_.accruals_.length; ++i) {
                    final double temp = o_.accruals_[i] * o_.swapPaymentDiscounts_[i]
                                        * Math.exp(-o_.shapedSwapPaymentTimes_[i] * x);
                    result += temp;
                    derivative_ -= o_.shapedSwapPaymentTimes_[i] * temp;
                }
                result *= Rs_;
                derivative_ *= Rs_;
                final int last = o_.shapedSwapPaymentTimes_.length - 1;
                final double temp = o_.swapPaymentDiscounts_[last]
                                    * Math.exp(-o_.shapedSwapPaymentTimes_[last] * x);

                result += temp - o_.discountAtStart_;
                derivative_ -= o_.shapedSwapPaymentTimes_[last] * temp;
                return result;
            }

            @Override
            public double derivative(final double x) {
                return derivative_;
            }

            void setSwapRateValue(final double x) {
                this.Rs_ = x;
            }

            GFunctionWithShifts gFunctionWithShifts() {
                return o_;
            }
        }
    }
}
