/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
 Copyright (C) 2014 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.annotation.Natural;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.math.interpolations.Interpolation2D;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

/**
 * Matrix based base correlation term structure.
 *
 * <p>Loss-level versus time interpolated scalar copula-type parametric
 * correlation term structure. Represents the correlation for the credit
 * loss level of a given portfolio at a given loss level and time.
 *
 * <p>Java port of QuantLib v1.42.1 templated
 * {@code QuantLib::BaseCorrelationTermStructure<Interpolator2D_T>}
 * ({@code ql/experimental/credit/basecorrelationstructure.{hpp,cpp}}).
 *
 * <p>C++ uses template specialization to pick the 2D interpolator
 * (BilinearInterpolation, BicubicSpline). Java has no equivalent
 * mechanism, so this class is abstract and the concrete subclasses
 * {@link BilinearBaseCorrelationTermStructure} and
 * {@link BicubicBaseCorrelationTermStructure} supply the interpolator
 * via {@link #setupInterpolation()}.
 *
 * <p>Tier rationale:
 * <ul>
 *   <li>Grid-point evaluation: TIGHT (interpolator returns input matrix
 *       value exactly at knot intersections).</li>
 *   <li>Interior interpolation: LOOSE (depends on 2D interpolator).</li>
 * </ul>
 *
 * <p>Phase 4m.7c-c.
 */
public abstract class BaseCorrelationTermStructure extends CorrelationTermStructure {

    // ---------------------------------------------------------------------
    // Inputs (kept for reactive market-data observation)
    // ---------------------------------------------------------------------

    private final List<List<Handle<Quote>>> correlHandles;

    /** Snapshot pulled from {@link #correlHandles} on every {@link #update()}. */
    protected final Matrix correlations;

    /** Tranche tenor count (rows in tenor axis, time-direction). */
    protected final int nTrancheTenors;

    /** Loss-level count (columns in loss axis). */
    protected final int nLosses;

    private final List<Period> tenors;
    private final List<Double> lossLevel;
    private final List<Date> trancheDates = new ArrayList<>();
    private final List<Double> trancheTimes;

    /** 2D interpolator built on (trancheTimes_axis, lossLevel_axis, correlations). */
    protected Interpolation2D interpolation;

    // ---------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------

    /**
     * Mirrors the C++ constructor:
     * {@code BaseCorrelationTermStructure(Natural settlementDays, Calendar, BDC,
     * vector<Period> tenors, vector<Real> lossLevel,
     * vector<vector<Handle<Quote>>> correls, DayCounter)}.
     *
     * <p>{@code correls[iLoss][iTenor]} — same row/column convention as the
     * C++ side; row index runs over loss levels, column index over tranche
     * tenors. The internal {@link #correlations} matrix mirrors that layout
     * before being handed to the 2D interpolator.
     *
     * <p>Subclass note: the constructor calls {@link #setupInterpolation()}
     * after building the time grid and snapshotting the matrix; subclasses
     * must NOT rely on subclass-specific state at the moment of construction.
     */
    protected BaseCorrelationTermStructure(
            final @Natural int settlementDays,
            final Calendar cal,
            final BusinessDayConvention bdc,
            final List<Period> tenors,
            final List<Double> lossLevel,
            final List<List<Handle<Quote>>> correls,
            final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
        QL.require(correls != null && !correls.isEmpty(),
                "BaseCorrelationTermStructure: correls cannot be empty");
        QL.require(tenors != null && !tenors.isEmpty(),
                "BaseCorrelationTermStructure: tenors cannot be empty");
        QL.require(lossLevel != null && !lossLevel.isEmpty(),
                "BaseCorrelationTermStructure: lossLevel cannot be empty");

        // Defensive copies — caller may mutate the inputs after construction;
        // we own the snapshot for the lifetime of this object.
        this.correlHandles = new ArrayList<>(correls.size());
        for (final List<Handle<Quote>> row : correls) {
            this.correlHandles.add(new ArrayList<>(row));
        }
        this.nTrancheTenors = tenors.size();
        this.nLosses = lossLevel.size();
        this.tenors = new ArrayList<>(tenors);
        this.lossLevel = new ArrayList<>(lossLevel);
        // Matrix dimensioned (rows = nLosses, cols = nTrancheTenors), mirroring C++.
        this.correlations = new Matrix(correls.size(), correls.get(0).size());
        this.trancheTimes = new ArrayList<>(this.nTrancheTenors);
        for (int i = 0; i < nTrancheTenors; ++i) {
            this.trancheTimes.add(0.0);
        }

        checkTrancheTenors();
        checkLosses();

        for (final Period tenor : this.tenors) {
            this.trancheDates.add(calendar().advance(referenceDate(), tenor, businessDayConvention()));
        }

        initializeTrancheTimes();
        checkInputs(this.correlations.rows(), this.correlations.columns());
        updateMatrix();
        registerWithMarketData();
        // Subclass factory hook for the 2D interpolator.
        setupInterpolation();
    }

    // ---------------------------------------------------------------------
    // Concrete public API mirroring C++ surface
    // ---------------------------------------------------------------------

    @Override
    public int correlationSize() {
        // Mirrors C++ scalar specialisation (correlationSize() == 1).
        return 1;
    }

    /** Implicit correlation for the given loss interval. C++ stub — no body in v1.42.1. */
    public double implicitCorrelation(final double a, final double b) {
        QL.error("BaseCorrelationTermStructure.implicitCorrelation: not implemented (matches C++ stub)");
        return Double.NaN; // unreachable
    }

    public Date maxDate() {
        return trancheDates.get(trancheDates.size() - 1);
    }

    public double correlation(final Date d, final double lossLevel) {
        return correlation(d, lossLevel, false);
    }

    public double correlation(final Date d, final double lossLevel, final boolean extrapolate) {
        return correlation(timeFromReference(d), lossLevel, extrapolate);
    }

    public double correlation(final @Time double t, final double lossLevel) {
        return correlation(t, lossLevel, false);
    }

    public double correlation(final @Time double t, final double lossLevel, final boolean extrapolate) {
        // Mirrors C++ correlation(Time, Real, bool) → interpolation_(t, lossLevel, true).
        // The C++ form passes 'true' (allow extrapolation in the interpolator) regardless;
        // we mirror that, but also surface the boolean flag for symmetry with the rest
        // of the JQuantLib termstructure API.
        // Mirroring C++ behaviour first; then the boolean is preserved as a signal for
        // future subclass overrides.
        final boolean allow = extrapolate || true;
        return interpolation.op(t, lossLevel, allow);
    }

    @Override
    public void update() {
        updateMatrix();
        super.update();
    }

    /**
     * Snapshot {@link #correlHandles} into the internal {@link #correlations}
     * matrix. Called from the constructor and from {@link #update()}.
     */
    public void updateMatrix() {
        for (int i = 0; i < correlHandles.size(); ++i) {
            for (int j = 0; j < correlHandles.get(0).size(); ++j) {
                correlations.set(i, j, correlHandles.get(i).get(j).currentLink().value());
            }
        }
    }

    // ---------------------------------------------------------------------
    // Validation helpers — mirror C++ inline definitions
    // ---------------------------------------------------------------------

    /** Mirrors C++ {@code checkTrancheTenors()}. Tenors must be strictly increasing and positive. */
    public final void checkTrancheTenors() {
        QL.require(tenors.get(0).length() > 0,
                "first tranche tenor is negative (" + tenors.get(0) + ")");
        for (int i = 1; i < nTrancheTenors; ++i) {
            QL.require(compare(tenors.get(i), tenors.get(i - 1)) > 0,
                    "non increasing tranche tenor: " + i + "th is " + tenors.get(i - 1)
                            + ", " + (i + 1) + "th is " + tenors.get(i));
        }
    }

    /** Mirrors C++ {@code checkLosses()}. Loss levels strictly increasing in (0, 1]. */
    public final void checkLosses() {
        QL.require(lossLevel.get(0) > 0.0,
                "first loss level is negative (" + lossLevel.get(0) + ")");
        QL.require(lossLevel.get(0) <= 1.0,
                "first loss level larger than 100% (" + lossLevel.get(0) + ")");
        for (int i = 1; i < nLosses; ++i) {
            QL.require(lossLevel.get(i) > lossLevel.get(i - 1),
                    "non increasing losses: " + i + "th is " + lossLevel.get(i - 1)
                            + ", " + (i + 1) + "th is " + lossLevel.get(i));
            QL.require(lossLevel.get(i) <= 1.0,
                    "loss level " + i + " larger than 100% (" + lossLevel.get(i) + ")");
        }
    }

    /** Mirrors C++ {@code initializeTrancheTimes()}. */
    public final void initializeTrancheTimes() {
        for (int i = 0; i < nTrancheTenors; ++i) {
            trancheTimes.set(i, timeFromReference(trancheDates.get(i)));
        }
    }

    /** Mirrors C++ {@code checkInputs()}. */
    public final void checkInputs(final int volRows, final int volsColumns) {
        QL.require(nLosses == volRows,
                "mismatch between number of loss levels (" + nLosses
                        + ") and number of rows (" + volRows + ") in the correl matrix");
        QL.require(nTrancheTenors == volsColumns,
                "mismatch between number of tranche tenors (" + nTrancheTenors
                        + ") and number of columns (" + volsColumns + ") in the correl matrix");
    }

    /** Subscribe to updates from each Handle&lt;Quote&gt; in the matrix. */
    public final void registerWithMarketData() {
        for (final List<Handle<Quote>> row : correlHandles) {
            for (final Handle<Quote> h : row) {
                h.addObserver(this);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Subclass factory hook
    // ---------------------------------------------------------------------

    /**
     * Build the {@link #interpolation} object using the snapshotted
     * {@link #correlations} matrix and the (trancheTimes, lossLevel) axes.
     *
     * <p>Called from the constructor after the time grid and matrix are
     * populated. Subclasses must build their concrete 2D interpolator and
     * assign it to {@link #interpolation}.
     */
    protected abstract void setupInterpolation();

    // ---------------------------------------------------------------------
    // Subclass accessors (to keep concrete classes terse)
    // ---------------------------------------------------------------------

    /** X-axis (trancheTimes) for the 2D interpolator. */
    protected final Array trancheTimesArray() {
        final double[] xs = new double[trancheTimes.size()];
        for (int i = 0; i < xs.length; ++i) {
            xs[i] = trancheTimes.get(i);
        }
        return new Array(xs);
    }

    /** Y-axis (lossLevel) for the 2D interpolator. */
    protected final Array lossLevelArray() {
        final double[] ys = new double[lossLevel.size()];
        for (int i = 0; i < ys.length; ++i) {
            ys[i] = lossLevel.get(i);
        }
        return new Array(ys);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Compare two periods by total-day equivalence (sufficient for monotonicity check). */
    private static int compare(final Period a, final Period b) {
        return Integer.compare(toDays(a), toDays(b));
    }

    private static int toDays(final Period p) {
        // Coarse comparison is enough for monotonicity validation; use rough day counts.
        switch (p.units()) {
        case Days:   return p.length();
        case Weeks:  return p.length() * 7;
        case Months: return p.length() * 30;
        case Years:  return p.length() * 365;
        default:     return p.length();
        }
    }
}
