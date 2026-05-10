/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2007 Katiuscia Manzoni
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.optionlet;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.math.interpolations.factories.Cubic;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.termstructures.volatilities.InterpolatedSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.Date;

/**
 * Adapter class for turning a {@link StrippedOptionletBase} object into an
 * {@link OptionletVolatilityStructure}.
 *
 * <p>Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/optionlet/strippedoptionletadapter.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>{@code smileSectionImpl} builds an {@link InterpolatedSmileSection}
 *      with a Cubic spline interpolator per query (Lagrange BC for &gt;= 4
 *      strikes, second-derivative=0 BC otherwise). Mirrors C++
 *      {@code InterpolatedSmileSection<Cubic>} construction; ported in
 *      Phase 5g.5b.</li>
 *  <li>Java cannot multi-inherit; C++ inherits both
 *      {@code OptionletVolatilityStructure} and {@code LazyObject} —
 *      Java composes the lazy semantics inline (calculated_ flag +
 *      performCalculations()).</li>
 *  <li>{@code update()} and {@code deepUpdate()} mirror C++; deep-update
 *      forwards to the underlying StrippedOptionletBase first.</li>
 * </ul>
 */
public class StrippedOptionletAdapter extends OptionletVolatilityStructure {

    //
    // private fields
    //

    private final StrippedOptionletBase optionletStripper_;
    private final int nInterpolations_;
    private final List<Interpolation> strikeInterpolations_;

    /** Lazy-calculation flag (mirrors C++ LazyObject). */
    protected boolean calculated_;

    //
    // public constructor
    //

    public StrippedOptionletAdapter(final StrippedOptionletBase s) {
        super(s.settlementDays(), s.calendar(), s.businessDayConvention(), s.dayCounter());
        this.optionletStripper_ = s;
        this.nInterpolations_ = s.optionletMaturities();
        this.strikeInterpolations_ = new ArrayList<Interpolation>(nInterpolations_);
        for (int i = 0; i < nInterpolations_; ++i) {
            strikeInterpolations_.add(null);
        }
        s.addObserver(this);
    }

    //
    // OptionletVolatilityStructure implementation
    //

    @Override
    public Date maxDate() {
        final List<Date> d = optionletStripper_.optionletFixingDates();
        return d.get(d.size() - 1);
    }

    @Override
    public double minStrike() {
        // C++ FIX comment preserved in spirit — first row's first strike.
        return optionletStripper_.optionletStrikes(0).get(0);
    }

    @Override
    public double maxStrike() {
        final List<Double> s = optionletStripper_.optionletStrikes(0);
        return s.get(s.size() - 1);
    }

    @Override
    public VolatilityType volatilityType() {
        return optionletStripper_.volatilityType();
    }

    @Override
    public double displacement() {
        return optionletStripper_.displacement();
    }

    /**
     * Builds an {@link InterpolatedSmileSection} per query. Mirrors C++
     * v1.42.1 {@code StrippedOptionletAdapter::smileSectionImpl}:
     * <pre>
     *   strikes = optionletStripper_.optionletStrikes(0)
     *   stddevs[i] = volatilityImpl(t, strikes[i]) * sqrt(t)
     *   bc = nstrikes >= 4 ? Lagrange : SecondDerivative(0)
     *   return InterpolatedSmileSection&lt;Cubic&gt;(
     *       t, strikes, stddevs, NaN_atm,
     *       Cubic(Spline, false, bc, 0, bc, 0),
     *       Actual365Fixed(), volatilityType(), displacement())
     * </pre>
     */
    @Override
    protected SmileSection smileSectionImpl(final double optionTime) {
        final List<Double> strikesList = optionletStripper_.optionletStrikes(0);
        final int n = strikesList.size();
        final double[] strikes = new double[n];
        final double[] stddevs = new double[n];
        final double sqrtT = Math.sqrt(optionTime);
        for (int i = 0; i < n; ++i) {
            strikes[i] = strikesList.get(i);
            stddevs[i] = volatilityImpl(optionTime, strikes[i]) * sqrtT;
        }
        final CubicInterpolation.BoundaryCondition bc =
                (n >= 4) ? CubicInterpolation.BoundaryCondition.Lagrange
                         : CubicInterpolation.BoundaryCondition.SecondDerivative;
        final Cubic cubic = new Cubic(
                CubicInterpolation.DerivativeApprox.Spline, false,
                bc, 0.0, bc, 0.0);
        return new InterpolatedSmileSection(
                optionTime, strikes, stddevs, Double.NaN,
                cubic, new Actual365Fixed(),
                volatilityType(), displacement(), false);
    }

    @Override
    protected double volatilityImpl(final double length, final double strike) {
        calculate();
        // For each maturity i, evaluate the strike-axis interpolator at
        // the requested strike (with extrapolation), then linearly interpolate
        // those values along the time axis.
        final List<Double> vol = new ArrayList<Double>(nInterpolations_);
        for (int i = 0; i < nInterpolations_; ++i) {
            vol.add(strikeInterpolations_.get(i).op(strike, true));
        }
        final List<Double> times = optionletStripper_.optionletFixingTimes();
        final double[] tArr = new double[nInterpolations_];
        final double[] vArr = new double[nInterpolations_];
        for (int i = 0; i < nInterpolations_; ++i) {
            tArr[i] = times.get(i);
            vArr[i] = vol.get(i);
        }
        final LinearInterpolation timeInterp = new LinearInterpolation(
                new Array(tArr), new Array(vArr));
        return timeInterp.op(length, true);
    }

    /**
     * Returns the underlying {@link OptionletStripper} if the wrapped base is
     * one (mirrors C++ {@code dynamic_pointer_cast<OptionletStripper>}); else
     * returns {@code null}.
     */
    public OptionletStripper optionletStripper() {
        if (optionletStripper_ instanceof OptionletStripper) {
            return (OptionletStripper) optionletStripper_;
        }
        return null;
    }

    /** Mirrors C++ LazyObject::deepUpdate(). */
    public void deepUpdate() {
        optionletStripper_.update();
        update();
    }

    @Override
    public void update() {
        super.update();
        calculated_ = false;
    }

    //
    // lazy plumbing — composed (Java single-inheritance prevents true LazyObject)
    //

    protected final void calculate() {
        if (!calculated_) {
            calculated_ = true;
            try {
                performCalculations();
            } catch (final RuntimeException e) {
                calculated_ = false;
                throw e;
            }
        }
    }

    /**
     * Mirrors C++ performCalculations(): build a per-tenor LinearInterpolation
     * across strikes. (C++ commented-out the SABR branch; we follow suit.)
     */
    protected void performCalculations() {
        for (int i = 0; i < nInterpolations_; ++i) {
            final List<Double> strikes = optionletStripper_.optionletStrikes(i);
            final List<Double> vols = optionletStripper_.optionletVolatilities(i);
            final int n = strikes.size();
            final double[] sx = new double[n];
            final double[] sy = new double[n];
            for (int k = 0; k < n; ++k) {
                sx[k] = strikes.get(k);
                sy[k] = vols.get(k);
            }
            strikeInterpolations_.set(i,
                    new LinearInterpolation(new Array(sx), new Array(sy)));
        }
    }
}
