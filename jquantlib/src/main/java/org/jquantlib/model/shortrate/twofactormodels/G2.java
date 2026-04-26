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
import org.jquantlib.instruments.Option;
import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.BoundaryConstraint;
import org.jquantlib.math.optimization.PositiveConstraint;
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
     * Swaption pricing via inner {@code SwaptionPricingFunction} integrated
     * over the x-process axis.
     * <p>
     * Phase 2e WI-1: deferred to Phase 2f (see Phase 2e design §A11). The
     * C++ implementation relies on {@code SegmentIntegral}'s function-object
     * {@code operator()} interface plus a {@code Swaption::arguments} struct
     * the Java port has not yet aligned with v1.42.1. Both the Brent-based
     * inner pricing function and the surrounding integral wrapper are out of
     * scope for the model-body port; the analytic + tree paths below carry
     * the primary G2 value.
     */
    public double swaption(final Object arguments, final double fixedRate,
                           final double range, final int intervals) {
        throw new UnsupportedOperationException(
                "G2.swaption(arguments,fixedRate,range,intervals) deferred to Phase 2f");
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
