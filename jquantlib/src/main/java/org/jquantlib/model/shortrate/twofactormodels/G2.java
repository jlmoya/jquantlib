/*
Copyright (C) 2008 Praneet Tiwari
Copyright (C) 2009 Ueli Hofstetter
Copyright (C) 2009 Richard Gomes

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
package org.jquantlib.model.shortrate.twofactormodels;

import static org.jquantlib.pricingengines.BlackFormula.blackFormula;

import org.jquantlib.QL;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Swap;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.integrals.SegmentIntegral;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.BoundaryConstraint;
import org.jquantlib.math.optimization.PositiveConstraint;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.model.AffineModel;
import org.jquantlib.model.ConstantParameter;
import org.jquantlib.model.Parameter;
import org.jquantlib.model.TermStructureFittingParameter;
import org.jquantlib.model.shortrate.onefactormodels.TermStructureConsistentModel;
import org.jquantlib.model.shortrate.onefactormodels.TermStructureConsistentModelClass;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Two-additive-factor gaussian model class.
 * <p>
 * This class implements a two-additive-factor model defined by {@latex$ dr_t = \varphi(t) + x_t + y_t }
 * where {@latex$ x_t } and {@latex$ y_t } are defined by
 * {@latex[ dx_t = -a x_t dt + \sigma dW^1_t, x_0 = 0 }
 * {@latex[ dy_t = -b y_t dt + \sigma dW^2_t, y_0 = 0 } and {@latex$ dW^1_t dW^2_t = \rho dt }
 *
 * @note This class was not tested enough to guarantee its functionality.
 *
 * @category shortrate
 *
 * @author Praneet Tiwari
 * @author Richard Gomes
 */
@QualityAssurance(quality=Quality.Q1_TRANSLATION, version=Version.V097, reviewers="Richard Gomes")
public class G2 extends TwoFactorModel implements AffineModel, TermStructureConsistentModel {

    private static final String g2_model_needs_two_factors =
            "g2 model needs two factors to compute discount bond";

    // Phase 2e WI-1: TermStructureConsistentModel is implemented by composition
    // (Java single-inheritance limit — TwoFactorModel extends ShortRateModel).
    private final TermStructureConsistentModelClass termStructureConsistentModelClass;

    // Phase 2e WI-1: arguments_-indirection (Phase 2b precedent — see Vasicek
    // §3.3 of phase2b-design.md). The C++ G2 ctor uses Parameter& reference
    // binding to arguments_[i]; Java replicates that by writing the
    // ConstantParameters directly into arguments_ and reading them back via
    // arguments_.get(i) in the *() accessors. arguments_[0..4] = a, sigma,
    // b, eta, rho. phi_ is the fitted term-structure parameter; it lives
    // outside arguments_ because it is not part of the calibratable vector.
    private Parameter phi_;

    public G2(final Handle<YieldTermStructure> termStructure) {
        this(termStructure, 0.1, 0.01, 0.1, 0.01, -0.75);
    }

    public G2(
            final Handle<YieldTermStructure> termStructure,
            final double /* @Real */ a) {
        this(termStructure, a, 0.01, 0.1, 0.01, -0.75);
    }

    public G2(
            final Handle<YieldTermStructure> termStructure,
            final double /* @Real */ a,
            final double /* @Real */ sigma) {
        this(termStructure, a, sigma, 0.1, 0.01, -0.75);
    }

    public G2(
            final Handle<YieldTermStructure> termStructure,
            final double /* @Real */ a,
            final double /* @Real */ sigma,
            final double /* @Real */ b) {
        this(termStructure, a, sigma, b, 0.01, -0.75);
    }

    public G2(
            final Handle<YieldTermStructure> termStructure,
            final double /* @Real */ a,
            final double /* @Real */ sigma,
            final double /* @Real */ b,
            final double /* @Real */ eta) {
        this(termStructure, a, sigma, b, eta, -0.75);
    }

    public G2(
            final Handle<YieldTermStructure> termStructure,
            final double /* @Real */ a,
            final double /* @Real */ sigma,
            final double /* @Real */ b,
            final double /* @Real */ eta,
            final double /* @Real */ rho) {
        super(5);

        termStructureConsistentModelClass = new TermStructureConsistentModelClass(termStructure);
        // Phase 2e WI-1: write Parameters directly into arguments_ so the
        // calibratable vector and the read accessors share one source of
        // truth (replaces C++'s Parameter& reference-binding pattern).
        arguments_.set(0, new ConstantParameter(a,     new PositiveConstraint()));
        arguments_.set(1, new ConstantParameter(sigma, new PositiveConstraint()));
        arguments_.set(2, new ConstantParameter(b,     new PositiveConstraint()));
        arguments_.set(3, new ConstantParameter(eta,   new PositiveConstraint()));
        arguments_.set(4, new ConstantParameter(rho,   new BoundaryConstraint(-1.0, 1.0)));

        generateArguments();

        termStructure.addObserver(this);
    }


    //
    // public methods
    //

    @Override
    public TwoFactorModel.ShortRateDynamics dynamics() {
        return new Dynamics(phi_, a(), sigma(), b(), eta(), rho());
    }

    /**
     * Two-factor discount bond closed form.
     * <p>
     * Mirrors C++ v1.42.1 g2.cpp:
     * {@code A(t,T) * exp(-B(a,T-t)*x - B(b,T-t)*y)}.
     */
    public double discountBond(final double /* @Time */ t, final double /* @Time */ T,
                               final double /* @Real */ x, final double /* @Real */ y) {
        return A(t, T) * Math.exp(-B(a(), T - t) * x - B(b(), T - t) * y);
    }

    @Override
    public double discountBond(final double /* @Time */ now, final double /* @Time */ maturity, final Array factors) {
        QL.require(factors.size() > 1, g2_model_needs_two_factors);
        return discountBond(now, maturity, factors.get(0), factors.get(1));
    }

    @Override
    public double /* @Real */ discountBondOption(
            final Option.Type type,
            final double /* @Real */ strike,
            final double /* @Time */ maturity,
            final double /* @Time */ bondMaturity) {

        final double /* @Real */ v = sigmaP(maturity, bondMaturity);
        final double /* @Real */ f = termStructureConsistentModelClass.termStructure().currentLink().discount(bondMaturity);
        final double /* @Real */ k = termStructureConsistentModelClass.termStructure().currentLink().discount(maturity) * strike;

        return blackFormula(type, k, f, v);
    }

    @Override
    public double discount(/* @Time */ final double t) {
        return termStructureConsistentModelClass.termStructure().currentLink().discount(t);
    }

    public double /* @Real */ a()     { return arguments_.get(0).get(0.0); }
    public double /* @Real */ sigma() { return arguments_.get(1).get(0.0); }
    public double /* @Real */ b()     { return arguments_.get(2).get(0.0); }
    public double /* @Real */ eta()   { return arguments_.get(3).get(0.0); }
    public double /* @Real */ rho()   { return arguments_.get(4).get(0.0); }

    /**
     * Swaption pricing via the inner {@link SwaptionPricingFunction}
     * integrated over the x-process axis.
     * <p>
     * Mirrors C++ v1.42.1 g2.cpp lines 218-246 verbatim. The integration
     * domain is {@code [mux ± range*sigmax]} and uses {@link SegmentIntegral}
     * with {@code intervals} subdivisions. The inner Brent solver finds the
     * y-root of the bond-equality equation at each x sample.
     *
     * @param arguments  swaption arguments (must carry a {@link VanillaSwap}
     *                   reference and the European exercise; nominal must be
     *                   non-null).
     * @param fixedRate  fixed rate of the underlying swap.
     * @param range      half-width of the integration domain in units of
     *                   {@code sigmax}.
     * @param intervals  number of trapezoid-rule sub-intervals.
     */
    public double swaption(final Swaption.ArgumentsImpl arguments, final double fixedRate,
                           final double range, final int intervals) {
        final double nominal = arguments.swap.nominal();
        QL.require(!Double.isNaN(nominal) && nominal != Constants.NULL_REAL,
                "non-constant nominals are not supported yet");

        final Date settlement = termStructure().currentLink().referenceDate();
        final DayCounter dayCounter = termStructure().currentLink().dayCounter();

        // C++ reads arguments.floatingResetDates[0]; Java pulls it from the
        // underlying swap's floating leg directly to avoid depending on
        // VanillaSwap.setupArguments' projection (Phase 2e WI-3 retro-note —
        // the projection chain has a known inverted isAssignableFrom check
        // that prevents Swaption.ArgumentsImpl from receiving these fields).
        final VanillaSwap swap = arguments.swap;
        final Leg floatLeg = swap.floatingLeg();
        final Date firstFloatReset = ((Coupon) floatLeg.get(0)).accrualStartDate();
        final double start = dayCounter.yearFraction(settlement, firstFloatReset);

        final double w = (swap.type() == VanillaSwap.Type.Payer) ? 1.0 : -1.0;

        final Leg fixedLeg = swap.fixedLeg();
        final int n = fixedLeg.size();
        final double[] fixedPayTimes = new double[n];
        for (int i = 0; i < n; i++) {
            fixedPayTimes[i] = dayCounter.yearFraction(settlement,
                    ((Coupon) fixedLeg.get(i)).date());
        }

        final SwaptionPricingFunction function = new SwaptionPricingFunction(
                a(), sigma(), b(), eta(), rho(), w, start,
                fixedPayTimes, fixedRate);

        final double upper = function.mux() + range * function.sigmax();
        final double lower = function.mux() - range * function.sigmax();
        final SegmentIntegral integrator = new SegmentIntegral(intervals);
        return nominal * w * termStructure().currentLink().discount(start)
                * integrator.op(function, lower, upper);
    }

    /**
     * Inner pricing-kernel function evaluated by SegmentIntegral over x.
     * Mirrors C++ {@code G2::SwaptionPricingFunction} (g2.cpp lines 100-216).
     */
    private final class SwaptionPricingFunction implements Ops.DoubleOp {

        private final double a_;
        private final double sigma_;
        private final double b_;
        private final double eta_;
        private final double rho_;
        private final double w_;
        private final double T_;
        private final double[] t_;
        private final double rate_;
        private final int size_;
        private final double[] A_;
        private final double[] Ba_;
        private final double[] Bb_;

        private final double mux_;
        private final double muy_;
        private final double sigmax_;
        private final double sigmay_;
        private final double rhoxy_;

        SwaptionPricingFunction(final double a, final double sigma,
                final double b, final double eta, final double rho,
                final double w, final double T, final double[] payTimes,
                final double fixedRate) {
            this.a_ = a;
            this.sigma_ = sigma;
            this.b_ = b;
            this.eta_ = eta;
            this.rho_ = rho;
            this.w_ = w;
            this.T_ = T;
            this.t_ = payTimes;
            this.rate_ = fixedRate;
            this.size_ = payTimes.length;
            this.A_ = new double[size_];
            this.Ba_ = new double[size_];
            this.Bb_ = new double[size_];

            this.sigmax_ = sigma_ * Math.sqrt(0.5 * (1.0 - Math.exp(-2.0 * a_ * T_)) / a_);
            this.sigmay_ = eta_ * Math.sqrt(0.5 * (1.0 - Math.exp(-2.0 * b_ * T_)) / b_);
            this.rhoxy_ = rho_ * eta_ * sigma_ * (1.0 - Math.exp(-(a_ + b_) * T_))
                    / ((a_ + b_) * sigmax_ * sigmay_);

            double temp = sigma_ * sigma_ / (a_ * a_);
            this.mux_ = -((temp + rho_ * sigma_ * eta_ / (a_ * b_))
                            * (1.0 - Math.exp(-a_ * T_))
                          - 0.5 * temp * (1.0 - Math.exp(-2.0 * a_ * T_))
                          - rho_ * sigma_ * eta_ / (b_ * (a_ + b_))
                            * (1.0 - Math.exp(-(b_ + a_) * T_)));

            temp = eta_ * eta_ / (b_ * b_);
            this.muy_ = -((temp + rho_ * sigma_ * eta_ / (a_ * b_))
                            * (1.0 - Math.exp(-b_ * T_))
                          - 0.5 * temp * (1.0 - Math.exp(-2.0 * b_ * T_))
                          - rho_ * sigma_ * eta_ / (a_ * (a_ + b_))
                            * (1.0 - Math.exp(-(b_ + a_) * T_)));

            for (int i = 0; i < size_; i++) {
                A_[i]  = G2.this.A(T_, t_[i]);
                Ba_[i] = G2.this.B(a_, t_[i] - T_);
                Bb_[i] = G2.this.B(b_, t_[i] - T_);
            }
        }

        double mux()    { return mux_; }
        double sigmax() { return sigmax_; }

        @Override
        public double op(final double x) {
            final CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
            final double temp = (x - mux_) / sigmax_;
            final double txy = Math.sqrt(1.0 - rhoxy_ * rhoxy_);

            final double[] lambda = new double[size_];
            for (int i = 0; i < size_; i++) {
                final double tau = (i == 0) ? (t_[0] - T_) : (t_[i] - t_[i - 1]);
                final double c = (i == size_ - 1) ? (1.0 + rate_ * tau) : (rate_ * tau);
                lambda[i] = c * A_[i] * Math.exp(-Ba_[i] * x);
            }

            final SolvingFunction function = new SolvingFunction(lambda, Bb_);
            final Brent s1d = new Brent();
            s1d.setMaxEvaluations(1000);
            final double searchBound = Math.max(10.0 * sigmay_, 1.0);
            final double yb = s1d.solve(function, 1.0e-6, 0.00, -searchBound, searchBound);

            final double h1 = (yb - muy_) / (sigmay_ * txy)
                    - rhoxy_ * (x - mux_) / (sigmax_ * txy);
            double value = phi.op(-w_ * h1);

            for (int i = 0; i < size_; i++) {
                final double h2 = h1 + Bb_[i] * sigmay_ * Math.sqrt(1.0 - rhoxy_ * rhoxy_);
                final double kappa = -Bb_[i]
                        * (muy_ - 0.5 * txy * txy * sigmay_ * sigmay_ * Bb_[i]
                                + rhoxy_ * sigmay_ * (x - mux_) / sigmax_);
                value -= lambda[i] * Math.exp(kappa) * phi.op(-w_ * h2);
            }

            return Math.exp(-0.5 * temp * temp) * value
                    / (sigmax_ * Math.sqrt(2.0 * Math.PI));
        }

        /**
         * Inner Brent cost function: returns
         * {@code 1 - sum(lambda[i] * exp(-Bb[i] * y))}.
         * Mirrors C++ {@code SolvingFunction} (g2.cpp lines 193-207).
         */
        private final class SolvingFunction implements Ops.DoubleOp {
            private final double[] lambda_;
            private final double[] Bb_;

            SolvingFunction(final double[] lambda, final double[] Bb) {
                this.lambda_ = lambda;
                this.Bb_ = Bb;
            }

            @Override
            public double op(final double y) {
                double value = 1.0;
                for (int i = 0; i < lambda_.length; i++) {
                    value -= lambda_[i] * Math.exp(-Bb_[i] * y);
                }
                return value;
            }
        }
    }


    //
    // implements TermStructureConsistentModel
    //

    @Override
    public Handle<YieldTermStructure> termStructure() {
        return termStructureConsistentModelClass.termStructure();
    }


    //
    // protected methods
    //

    @Override
    public void generateArguments() {
        phi_ = new FittingParameter(termStructureConsistentModelClass.termStructure(),
                a(), sigma(), b(), eta(), rho());
    }

    protected double /* @Real */ A(final double /* @Time */ t, final double /* @Time */ T) {
        return termStructureConsistentModelClass.termStructure().currentLink().discount(T)
             / termStructureConsistentModelClass.termStructure().currentLink().discount(t)
             * Math.exp(0.5 * (V(T - t) - V(T) + V(t)));
    }

    protected double /* @Real */ B(final double /* @Real */ x, final double /* @Time */ t) {
        return (1.0 - Math.exp(-x * t)) / x;
    }


    //
    // private methods
    //

    private double /* @Real */ V(final double /* @Time */ t) {
        final double expat = Math.exp(-a() * t);
        final double expbt = Math.exp(-b() * t);
        final double cx = sigma() / a();
        final double cy = eta()   / b();
        final double valuex = cx * cx * (t + (2.0 * expat - 0.5 * expat * expat - 1.5) / a());
        final double valuey = cy * cy * (t + (2.0 * expbt - 0.5 * expbt * expbt - 1.5) / b());
        final double value = 2.0 * rho() * cx * cy
                * (t + (expat - 1.0) / a()
                     + (expbt - 1.0) / b()
                     - (expat * expbt - 1.0) / (a() + b()));
        return valuex + valuey + value;
    }

    private double /* @Real */ sigmaP(final double /* @Time */ t, final double /* @Time */ s) {
        final double temp  = 1.0 - Math.exp(-(a() + b()) * t);
        final double temp1 = 1.0 - Math.exp(-a() * (s - t));
        final double temp2 = 1.0 - Math.exp(-b() * (s - t));
        final double a3 = a() * a() * a();
        final double b3 = b() * b() * b();
        final double sigma2 = sigma() * sigma();
        final double eta2   = eta()   * eta();
        final double value =
                0.5 * sigma2 * temp1 * temp1 * (1.0 - Math.exp(-2.0 * a() * t)) / a3
              + 0.5 * eta2   * temp2 * temp2 * (1.0 - Math.exp(-2.0 * b() * t)) / b3
              + 2.0 * rho() * sigma() * eta() / (a() * b() * (a() + b()))
                * temp1 * temp2 * temp;
        return Math.sqrt(value);
    }


    //
    // private inner classes
    //

    /**
     * Short-rate dynamics in the G2++ model.
     * <p>
     * Two correlated Ornstein-Uhlenbeck processes {@latex$ x_t, y_t } with
     * {@latex$ r_t = \varphi(t) + x_t + y_t }.
     */
    private final class Dynamics extends TwoFactorModel.ShortRateDynamics {

        private final Parameter fitting_;

        Dynamics(final Parameter fitting,
                 final double /* @Real */ a, final double /* @Real */ sigma,
                 final double /* @Real */ b, final double /* @Real */ eta,
                 final double /* @Real */ rho) {
            super(new OrnsteinUhlenbeckProcess(a, sigma, 0.0, 0.0),
                  new OrnsteinUhlenbeckProcess(b, eta,  0.0, 0.0),
                  rho);
            this.fitting_ = fitting;
        }

        @Override
        public double /* @Rate */ shortRate(final double /* @Time */ t,
                                            final double /* @Real */ x,
                                            final double /* @Real */ y) {
            return fitting_.get(t) + x + y;
        }
    }


    //
    // static private inner classes
    //

    /**
     * Analytical term-structure fitting parameter {@latex$ \varphi(t) }.
     * <p>
     * {@latex$ \varphi(t) } is analytically defined by
     * <p>
     * {@latex[ \varphi(t) =
     *          f(t)
     *          + \frac{1}{2}(\frac{\sigma(1-e^{-at})}{a})^2
     *          + \frac{1}{2}(\frac{\eta(1-e^{-bt})}{b})^2 + \rho\frac{\sigma(1-e^{-at})}{a}\frac{\eta(1-e^{-bt})}{b} },
     * <p>
     * where {@latex$ f(t)} is the instantaneous forward rate at {@latex$ t}.
     */
    static private class FittingParameter extends TermStructureFittingParameter {

        public FittingParameter(
                final Handle<YieldTermStructure> termStructure,
                final double /* @Real */ a,
                final double /* @Real */ sigma,
                final double /* @Real */ b,
                final double /* @Real */ eta,
                final double /* @Real */ rho) {
            super(new Impl(termStructure, a, sigma, b, eta, rho));
        }


        //
        // static private inner classes
        //

        static private class Impl implements Parameter.Impl {

            private final Handle<YieldTermStructure> termStructure_;
            private final double /* @Real */ a_;
            private final double /* @Real */ sigma_;
            private final double /* @Real */ b_;
            private final double /* @Real */ eta_;
            private final double /* @Real */ rho_;

            public Impl(
                    final Handle<YieldTermStructure> termStructure,
                    final double /* @Real */ a,
                    final double /* @Real */ sigma,
                    final double /* @Real */ b,
                    final double /* @Real */ eta,
                    final double /* @Real */ rho) {
                this.termStructure_ = termStructure;
                this.a_ = a;
                this.sigma_ = sigma;
                this.b_ = b;
                this.eta_ = eta;
                this.rho_ = rho;
            }

            @Override
            public double /* @Real */ value(final Array params, final double /* @Time */ t) {
                final double /* @Rate */ forward =
                        termStructure_.currentLink().forwardRate(
                                t, t, Compounding.Continuous, Frequency.NoFrequency).rate();

                final double temp1 = sigma_ * (1.0 - Math.exp(-a_ * t)) / a_;
                final double temp2 = eta_   * (1.0 - Math.exp(-b_ * t)) / b_;
                return 0.5 * temp1 * temp1
                     + 0.5 * temp2 * temp2
                     + rho_ * temp1 * temp2
                     + forward;
            }

        }

    }

}
