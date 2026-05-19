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
 Copyright (C) 2010 SunTrust Bank
 Copyright (C) 2010, 2014 Cavit Hafizoglu

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.experimental.shortrate;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.SimpsonIntegral;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.factories.BackwardFlat;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.PositiveConstraint;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.methods.lattices.Lattice;
import org.jquantlib.methods.lattices.TrinomialTree;
import org.jquantlib.model.NullParameter;
import org.jquantlib.model.Parameter;
import org.jquantlib.model.TermStructureFittingParameter;
import org.jquantlib.model.shortrate.onefactormodels.OneFactorAffineModel;
import org.jquantlib.model.shortrate.onefactormodels.TermStructureConsistentModel;
import org.jquantlib.model.shortrate.onefactormodels.TermStructureConsistentModelClass;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Generalized Hull-White model.
 *
 * <p>Phase 4c port of {@code QuantLib::GeneralizedHullWhite}
 * (v1.42.1 ql/experimental/shortrate/generalizedhullwhite.{hpp,cpp}).
 *
 * <p>Implements the Black-Karasinski-style model defined by
 * d f(r_t) = (theta(t) - alpha f(r_t)) dt + sigma dW_t, where {@code alpha} and {@code sigma} are piecewise functions
 * and {@code f} is an optional invertible transform (defaults to identity).
 *
 * <p>The classical Hull-White entry point — constant {@code a, sigma} —
 * matches the C++ second constructor and exercises the affine analytic formulas {@link #A(double, double)},
 * {@link #B(double, double)} and {@link #discountBondOption(Option.Type, double, double, double)}.
 *
 * <p>The piecewise constructor accepts arbitrary date schedules and
 * interpolation traits via {@link Linear} / {@link BackwardFlat}; see the
 * {@link #GeneralizedHullWhite(Handle, java.util.List, java.util.List, double[], double[], InterpolationFactory,
 * InterpolationFactory, Ops.DoubleOp, Ops.DoubleOp)} 9-arg form for the full power.
 *
 * @category shortrate
 */
public class GeneralizedHullWhite extends OneFactorAffineModel implements TermStructureConsistentModel {

    /** Adapter from {@code Interpolation.Interpolator} to {@link InterpolationFactory}. */
    public static final InterpolationFactory LINEAR_FACTORY = (vx, vy) -> new Linear().interpolate(vx, vy);
    public static final InterpolationFactory BACKWARD_FLAT_FACTORY = (vx, vy) -> new BackwardFlat().interpolate(vx, vy);
    /**
     * Factory matching the C++ {@code LinearFlat} traits — linear inside [xMin, xMax], flat at the endpoint values
     * outside. Used as the default convenience-overload factory because the C++ piecewise GHW constructor uses
     * {@code LinearFlat}.
     */
    public static final InterpolationFactory LINEAR_FLAT_FACTORY = (vx, vy) -> {
        final Interpolation linear = new Linear().interpolate(vx, vy);
        linear.enableExtrapolation();
        return new LinearFlatInterpolationAdapter(linear, vx, vy);
    };
    private final TermStructureConsistentModelClass termStructureConsistentModelClass;
    private final List< Date > speedstructure_;
    private final List< Date > volstructure_;
    private final List< Double > speedperiods_;
    private final List< Double > volperiods_;
    private Interpolation speed_;
    private Interpolation vol_;
    private Parameter phi_;
    private Ops.DoubleOp f_;
    private Ops.DoubleOp fInverse_;
    /**
     * Piecewise-linear (or other-traits) constructor.
     *
     * @param yieldtermStructure underlying discount curve
     * @param speedstructure     dates at which mean-reversion is sampled
     * @param volstructure       dates at which volatility is sampled
     * @param speed              mean-reversion sample values (size matches speedstructure)
     * @param vol                volatility sample values (size matches volstructure)
     * @param speedTraits        factory for interpolating mean-reversion in time
     * @param volTraits          factory for interpolating volatility in time
     * @param f                  forward transform (may be null → identity)
     * @param fInverse           inverse transform (may be null → identity)
     */
    public GeneralizedHullWhite(final Handle< YieldTermStructure > yieldtermStructure,
            final List< Date > speedstructure, final List< Date > volstructure, final double[] speed,
            final double[] vol, final InterpolationFactory speedTraits, final InterpolationFactory volTraits,
            final Ops.DoubleOp f, final Ops.DoubleOp fInverse) {

        super(2);
        this.termStructureConsistentModelClass = new TermStructureConsistentModelClass(yieldtermStructure);
        this.speedstructure_ = new ArrayList<>(speedstructure);
        this.volstructure_ = new ArrayList<>(volstructure);
        this.speedperiods_ = new ArrayList<>();
        this.volperiods_ = new ArrayList<>();
        this.f_ = f;
        this.fInverse_ = fInverse;
        initialize(yieldtermStructure, speedstructure, volstructure, speed, vol, speedTraits, volTraits);
    }

    /** Convenience overload: linear traits, identity transform. */
    public GeneralizedHullWhite(final Handle< YieldTermStructure > yieldtermStructure,
            final List< Date > speedstructure, final List< Date > volstructure, final double[] speed,
            final double[] vol) {
        // Auto-pad single-point inputs (Java factories require >= 2 points;
        // C++ LinearFlat / BackwardFlat both accept 1).
        this(yieldtermStructure, padDates(speedstructure, yieldtermStructure),
                padDates(volstructure, yieldtermStructure), padValues(speed), padValues(vol), LINEAR_FLAT_FACTORY,
                LINEAR_FLAT_FACTORY, null, null);
    }

    /** Classical (constant a, sigma) Hull-White entry point. */
    public GeneralizedHullWhite(final Handle< YieldTermStructure > yieldtermStructure, final double a,
            final double sigma) {
        super(2);
        this.termStructureConsistentModelClass = new TermStructureConsistentModelClass(yieldtermStructure);
        this.speedstructure_ = new ArrayList<>();
        this.volstructure_ = new ArrayList<>();
        this.speedperiods_ = new ArrayList<>();
        this.volperiods_ = new ArrayList<>();
        this.f_ = null;
        this.fInverse_ = null;

        // Java's BackwardFlat / Linear factories require 2 points
        // (C++ LinearFlat / BackwardFlat allow 1). For the classical-mode
        // entry point we duplicate the single sample at ref + 100y so the
        // resulting flat interpolation is constant over the entire
        // practical-use horizon — semantically equivalent to the C++ path.
        final Date ref = yieldtermStructure.currentLink().referenceDate();
        final Date refPlus = ref.add(100 * 365);
        speedstructure_.add(ref);
        speedstructure_.add(refPlus);
        volstructure_.add(ref);
        volstructure_.add(refPlus);
        // The C++ classical-mode constructor uses BackwardFlat traits, but
        // its requiredPoints=1 lets a single sample yield a flat curve.
        // Java's BackwardFlat factory rejects single-point inputs, so we
        // pad the schedule and use LinearFlat — over the [ref, ref+100y]
        // interval with identical y-values, both produce the same constant.
        initialize(yieldtermStructure, speedstructure_, volstructure_, new double[] { a, a },
                new double[] { sigma, sigma }, LINEAR_FLAT_FACTORY, LINEAR_FLAT_FACTORY);
    }

    /** Classical Hull-White with default {@code a=0.1, sigma=0.01}. */
    public GeneralizedHullWhite(final Handle< YieldTermStructure > yieldtermStructure) {
        this(yieldtermStructure, 0.1, 0.01);
    }

    private static double identity(final double x) {
        return x;
    }

    private static List< Date > padDates(final List< Date > ds, final Handle< YieldTermStructure > ts) {
        if ( ds.size() != 1 )
            return ds;
        final List< Date > padded = new ArrayList<>(ds);
        // Use a far-future date so the resulting flat interpolation is
        // constant over the entire practical-use horizon.
        padded.add(ds.get(0).add(100 * 365));
        return padded;
    }

    private static double[] padValues(final double[] xs) {
        if ( xs.length != 1 )
            return xs;
        return new double[] { xs[0], xs[0] };
    }

    private static Array toArray(final List< Double > xs) {
        final Array a = new Array(xs.size());
        for ( int i = 0; i < xs.size(); i++ ) {
            a.set(i, xs.get(i));
        }
        return a;
    }

    private static double integrateMeanReversion(final Interpolation a, final double t, final double T) {
        if ( (T - t) < org.jquantlib.math.Constants.QL_EPSILON ) {
            return 0.0;
        }
        final SimpsonIntegral integrator = new SimpsonIntegral(1e-5, 1000);
        return integrator.op(a, t, T);
    }

    private void initialize(final Handle< YieldTermStructure > yieldtermStructure, final List< Date > speedstructure,
            final List< Date > volstructure, final double[] speed, final double[] vol,
            final InterpolationFactory speedTraits, final InterpolationFactory volTraits) {

        QL.require(speedstructure.size() == speed.length, "mean reversion inputs inconsistent");
        QL.require(volstructure.size() == vol.length, "volatility inputs inconsistent");
        if ( this.f_ == null ) {
            this.f_ = GeneralizedHullWhite::identity;
        }
        if ( this.fInverse_ == null ) {
            this.fInverse_ = GeneralizedHullWhite::identity;
        }

        final DayCounter dc = yieldtermStructure.currentLink().dayCounter();
        final Date ref = yieldtermStructure.currentLink().referenceDate();
        for ( final Date d : speedstructure ) {
            speedperiods_.add(dc.yearFraction(ref, d));
        }
        for ( final Date d : volstructure ) {
            volperiods_.add(dc.yearFraction(ref, d));
        }

        // a_ slot: NoConstraint, x = speed values.
        final InterpolationParameter aParam = new InterpolationParameter(speedperiods_.size(), new NoConstraint());
        for ( int i = 0; i < speedperiods_.size(); i++ ) {
            aParam.setParam(i, speed[i]);
        }
        // The Java-side BackwardFlat / Linear factory takes Array.
        final Array speedX = toArray(speedperiods_);
        final Array speedY = aParam.params();
        speed_ = speedTraits.interpolate(speedX, speedY);
        speed_.enableExtrapolation();
        aParam.reset(speed_);
        // arguments_ slot 0 (preallocated by CalibratedModel(2) as NullParameter).
        arguments_.set(0, aParam);

        // sigma_ slot: PositiveConstraint, x = vol values.
        final InterpolationParameter sigmaParam = new InterpolationParameter(volperiods_.size(),
                new PositiveConstraint());
        for ( int i = 0; i < volperiods_.size(); i++ ) {
            sigmaParam.setParam(i, vol[i]);
        }
        final Array volX = toArray(volperiods_);
        final Array volY = sigmaParam.params();
        vol_ = volTraits.interpolate(volX, volY);
        vol_.enableExtrapolation();
        sigmaParam.reset(vol_);
        arguments_.set(1, sigmaParam);

        // Pre-fill any remaining slots with NullParameter for safety.
        for ( int i = 2; i < arguments_.size(); i++ ) {
            if ( arguments_.get(i) == null ) {
                arguments_.set(i, new NullParameter());
            }
        }

        generateArguments();
        termStructureConsistentModelClass.termStructure().addObserver(this);
    }

    @Override
    protected void generateArguments() {
        // C++ calls speed_.update() / vol_.update(), then phi_ = FittingParameter.
        if ( speed_ != null ) {
            speed_.update();
        }
        if ( vol_ != null ) {
            vol_.update();
        }
        phi_ = new FittingParameter(termStructureConsistentModelClass.termStructure(), a(), sigma());
    }

    /** Classical-mode constant {@code a()} accessor (uses {@code a_(0.0)}). */
    public double a() /*@ReadOnly*/ {
        return arguments_.get(0).get(0.0);
    }

    /** Classical-mode constant {@code sigma()} accessor. */
    public double sigma() /*@ReadOnly*/ {
        return arguments_.get(1).get(0.0);
    }

    @Override
    public ShortRateDynamics dynamics() {
        throw new org.jquantlib.lang.exceptions.LibraryException(
                "no defined process for generalized Hull-White model, use HWdynamics()");
    }

    /** Analytical Hull-White short-rate dynamics (classical mode). */
    public ShortRateDynamics HWdynamics() /*@ReadOnly*/ {
        return new Dynamics(phi_, a(), sigma());
    }

    /** Time-varying-coefficient short-rate dynamics (numerical mode). */
    public ShortRateDynamics numericDynamics(final Parameter fitting) /*@ReadOnly*/ {
        return new Dynamics(fitting, speedFunction(), volFunction(), f_, fInverse_);
    }

    /**
     * Discount-bond option price. Only valid under classical Hull-White (constant a, sigma), per the C++ comment.
     */
    @Override
    public double discountBondOption(final Option.Type type, final double strike, final double /*@Time*/ maturity,
            final double /*@Time*/ bondMaturity) /*@ReadOnly*/ {
        // Hull-White bond option pricing with time-varying sigma and mean
        // reversion. Based on Gurrieri, Nakabayashi & Wong (2009),
        // "Calibration Methods of Hull-White Model".
        final double BtT = B(maturity, bondMaturity);
        final double Vr = V(0.0, maturity);
        final double Vp = Vr * BtT * BtT;
        final double vol = Math.sqrt(Vp);
        final double f = termStructureConsistentModelClass.termStructure().currentLink().discount(bondMaturity);
        final double k = termStructureConsistentModelClass.termStructure().currentLink().discount(maturity) * strike;
        return BlackFormula.blackFormula(type, k, f, vol);
    }

    /** B(t,T) — Gurrieri et al, equations (30) and (31). */
    @Override
    protected double B(final /*@Time*/ double t, final /*@Time*/ double T) /*@ReadOnly*/ {
        final double lnEt = integrateMeanReversion(speed_, 0.0, t);
        final double Et = Math.exp(lnEt);
        double B = 0.0;
        int N = (int) Math.min((double) ((long) (T - t) * 365L), 2000.0);
        // The C++ code is `Size N = std::min<Size>(Size((T-t)*365), 2000);`
        // which is integer truncation of (T-t)*365 capped at 2000.
        N = (int) Math.min(2000L, (long) ((T - t) * 365.0));
        if ( N == 0 )
            N = 1;
        final double dt = 0.5 * (T - t) / N;
        double a, b, c, _t, total = 0.0;
        _t = t;
        c = speed_.op(_t);
        _t += dt;
        for ( int i = 0; i < N; i++ ) {
            a = c;
            b = speed_.op(_t);
            c = speed_.op(_t + dt);
            total += (dt * (2.0 / 6.0)) * (a + 4.0 * b + c);
            B += (2.0 * dt) / Math.exp(lnEt + total);
            _t += 2.0 * dt;
        }
        B *= Et;
        return B;
    }

    /** V(t,T) — Gurrieri et al, equation (37). */
    protected double V(final /*@Time*/ double t, final /*@Time*/ double T) /*@ReadOnly*/ {
        final double lnEt = integrateMeanReversion(speed_, 0.0, t);
        double V = 0.0, Eu;
        int N = (int) Math.min(2000L, (long) ((T - t) * 365.0));
        if ( N == 0 )
            N = 1;
        final double dt = 0.5 * (T - t) / N;
        double a, b, c, _t, lnE = lnEt;
        _t = t;
        double vol = vol_.op(_t);
        Eu = Math.exp(lnE);
        c = Eu * Eu * vol * vol;
        _t += dt;
        for ( int i = 0; i < N; i++ ) {
            a = c;
            vol = vol_.op(_t);
            lnE += speed_.op(_t) * dt;
            Eu = Math.exp(lnE);
            b = Eu * Eu * vol * vol;
            vol = vol_.op(_t + dt);
            lnE += speed_.op(_t + dt) * dt;
            Eu = Math.exp(lnE);
            c = Eu * Eu * vol * vol;
            V += (dt * (2.0 / 6.0)) * (a + 4.0 * b + c);
            _t += 2.0 * dt;
        }
        return V / (Eu * Eu);
    }

    @Override
    protected double A(final /*@Time*/ double t, final /*@Time*/ double T) /*@ReadOnly*/ {
        // Gurrieri et al, equation (43).
        final double discount1 = termStructureConsistentModelClass.termStructure().currentLink().discount(t);
        final double discount2 = termStructureConsistentModelClass.termStructure().currentLink().discount(T);
        final double forward = termStructureConsistentModelClass.termStructure().currentLink()
                .forwardRate(t, t, Compounding.Continuous, Frequency.NoFrequency).rate();
        final double BtT = B(t, T);
        final double Vr = V(0.0, t);
        final double AtT = Math.log(discount2 / discount1) + BtT * forward - 0.5 * BtT * BtT * Vr;
        return Math.exp(AtT);
    }

    /**
     * Returns true / false vector to pass to {@code calibrate} to fit only volatility (i.e., fix the mean-reversion
     * piecewise samples).
     */
    public boolean[] fixedReversion() /*@ReadOnly*/ {
        final int na = arguments_.get(0).size();
        final int nsigma = arguments_.get(1).size();
        final boolean[] fixr = new boolean[na + nsigma];
        for ( int i = 0; i < na; i++ )
            fixr[i] = true;
        for ( int i = na; i < na + nsigma; i++ )
            fixr[i] = false;
        return fixr;
    }

    @Override
    public Lattice tree(final TimeGrid grid) {
        final TermStructureFittingParameter phi = new TermStructureFittingParameter(
                termStructureConsistentModelClass.termStructure());
        final ShortRateDynamics numericDynamics = new Dynamics(phi, speedFunction(), volFunction(), f_, fInverse_);
        final TrinomialTree trinomial = new TrinomialTree(numericDynamics.process(), grid);
        final ShortRateTree numericTree = new ShortRateTree(trinomial, numericDynamics, grid);

        final TermStructureFittingParameter.NumericalImpl impl = (TermStructureFittingParameter.NumericalImpl) phi.implementation();
        impl.reset();

        double value = 1.0;
        final double vMin = -50.0;
        final double vMax = 50.0;

        final Ops.DoubleOp finvOp = fInverse_;
        for ( int i = 0; i < grid.size() - 1; i++ ) {
            final double discountBond = termStructureConsistentModelClass.termStructure().currentLink()
                    .discount(grid.at(i + 1));
            final double xMin = trinomial.underlying(i, 0);
            final double dx = trinomial.dx(i);
            final int sizeI = numericTree.size(i);
            final double dt = numericTree.timeGrid().dt(i);
            final Array statePrices = numericTree.statePrices(i);
            final double xMinFinal = xMin;
            final double dxFinal = dx;
            final double dtFinal = dt;
            final Ops.DoubleOp finder = (theta) -> {
                double v = discountBond;
                double x = xMinFinal;
                for ( int j = 0; j < sizeI; j++ ) {
                    final double discount = Math.exp(-finvOp.op(theta + x) * dtFinal);
                    v -= statePrices.get(j) * discount;
                    x += dxFinal;
                }
                return v;
            };
            final Brent solver = new Brent();
            solver.setMaxEvaluations(2000);
            value = solver.solve(finder, 1e-8, value, vMin, vMax);
            impl.set(grid.at(i), value);
        }
        return numericTree;
    }

    @Override
    public Handle< YieldTermStructure > termStructure() {
        return termStructureConsistentModelClass.termStructure();
    }

    /** Returns {@code speed_} as an {@link Ops.DoubleOp}. */
    public Ops.DoubleOp speedFunction() /*@ReadOnly*/ {
        return speed_;
    }

    /** Returns {@code vol_} as an {@link Ops.DoubleOp}. */
    public Ops.DoubleOp volFunction() /*@ReadOnly*/ {
        return vol_;
    }

    /**
     * Tiny strategy interface for interpolation factories. Mirrors the C++ traits-template parameters
     * {@code SpeedInterpolationTraits} and {@code VolInterpolationTraits}. {@link Linear} and {@link BackwardFlat} both
     * implement {@link org.jquantlib.math.interpolations.Interpolation.Interpolator}, which has the same
     * {@code interpolate(vx, vy)} contract.
     */
    public interface InterpolationFactory {
        Interpolation interpolate(Array vx, Array vy);
    }

    /**
     * Adapter wrapping a base {@link Interpolation} so values outside [xMin, xMax] are clamped to
     * {@code yMin}/{@code yMax}, matching the C++ {@code LinearFlat} traits behaviour.
     */
    private static final class LinearFlatInterpolationAdapter implements Interpolation {
        private final Interpolation base;
        private final double xMin;
        private final double xMax;
        private final double yMin;
        private final double yMax;

        LinearFlatInterpolationAdapter(final Interpolation base, final Array vx, final Array vy) {
            this.base = base;
            this.xMin = vx.first();
            this.xMax = vx.last();
            this.yMin = vy.first();
            this.yMax = vy.last();
        }

        @Override
        public double op(final double x) {
            if ( x <= xMin )
                return yMin;
            if ( x >= xMax )
                return yMax;
            return base.op(x, true);
        }

        @Override
        public double op(final double x, final boolean allowExtrapolation) {
            return op(x);
        }

        @Override
        public double primitive(final double x) {
            return base.primitive(x, true);
        }

        @Override
        public double primitive(final double x, final boolean allowExtrapolation) {
            return primitive(x);
        }

        @Override
        public double derivative(final double x) {
            if ( x < xMin || x > xMax )
                return 0.0;
            return base.derivative(x, true);
        }

        @Override
        public double derivative(final double x, final boolean allowExtrapolation) {
            return derivative(x);
        }

        @Override
        public double secondDerivative(final double x) {
            if ( x < xMin || x > xMax )
                return 0.0;
            return base.secondDerivative(x);
        }

        @Override
        public double secondDerivative(final double x, final boolean allowExtrapolation) {
            return secondDerivative(x);
        }

        @Override
        public double xMin() {
            return xMin;
        }

        @Override
        public double xMax() {
            return xMax;
        }

        @Override
        public boolean isInRange(final double x) {
            return true;
        }

        @Override
        public boolean empty() {
            return false;
        }

        @Override
        public void update() {
            base.update();
        }

        @Override
        public void enableExtrapolation() {
            base.enableExtrapolation();
        }

        @Override
        public void disableExtrapolation() {
            base.disableExtrapolation();
        }

        @Override
        public boolean allowsExtrapolation() {
            return true;
        }
    }

    /**
     * Analytical term-structure fitting parameter phi(t).
     *
     * phi(t) = f(t) + 0.5 * [sigma * (1 - exp(-a*t)) / a]^2, where f(t) is the instantaneous forward rate at t.
     */
    private static final class FittingParameter extends TermStructureFittingParameter {
        FittingParameter(final Handle< YieldTermStructure > termStructure, final double a, final double sigma) {
            super(new Impl(termStructure, a, sigma));
        }

        private static final class Impl implements Parameter.Impl {
            private final Handle< YieldTermStructure > termStructure;
            private final double a;
            private final double sigma;

            Impl(final Handle< YieldTermStructure > termStructure, final double a, final double sigma) {
                this.termStructure = termStructure;
                this.a = a;
                this.sigma = sigma;
            }

            @Override
            public double value(final Array params, final /*@Time*/ double t) {
                final double forwardRate = termStructure.currentLink()
                        .forwardRate(t, t, Compounding.Continuous, Frequency.NoFrequency).rate();
                final double temp = (a < Math.sqrt(org.jquantlib.math.Constants.QL_EPSILON))
                        ? sigma * t
                        : sigma * (1.0 - Math.exp(-a * t)) / a;
                return forwardRate + 0.5 * temp * temp;
            }
        }
    }

    /**
     * Short-rate dynamics in the generalized Hull-White model.
     *
     * <p>The short-rate is here {@code f(r_t) = x_t + g(t)}, where
     * {@code g} is the deterministic time-dependent parameter (which can't be determined analytically) used for initial
     * term-structure fitting and {@code x_t} is the state variable following an Ornstein-Uhlenbeck process. {@code f}
     * may be invertible-piecewise.
     */
    public class Dynamics extends ShortRateDynamics {

        private final Parameter fitting_;
        private final Ops.DoubleOp _f_;
        private final Ops.DoubleOp _fInverse_;

        /** Generalized constructor (time-varying). */
        public Dynamics(final Parameter fitting, final Ops.DoubleOp alpha, final Ops.DoubleOp sigma,
                final Ops.DoubleOp f, final Ops.DoubleOp fInverse) {
            super(new GeneralizedOrnsteinUhlenbeckProcess(alpha, sigma));
            this.fitting_ = fitting;
            this._f_ = (f == null ? GeneralizedHullWhite::identity : f);
            this._fInverse_ = (fInverse == null ? GeneralizedHullWhite::identity : fInverse);
        }

        /** Classical-HW constructor (constant a, sigma). */
        public Dynamics(final Parameter fitting, final double a, final double sigma) {
            super(new OrnsteinUhlenbeckProcess(a, sigma, 0.0, 0.0));
            this.fitting_ = fitting;
            this._f_ = GeneralizedHullWhite::identity;
            this._fInverse_ = GeneralizedHullWhite::identity;
        }

        @Override
        public double variable(final /*@Time*/ double t, final /*@Rate*/ double r) /*@ReadOnly*/ {
            return _f_.op(r) - fitting_.get(t);
        }

        @Override
        public double shortRate(final /*@Time*/ double t, final /*@Real*/ double x) /*@ReadOnly*/ {
            return _fInverse_.op(x + fitting_.get(t));
        }
    }
}
