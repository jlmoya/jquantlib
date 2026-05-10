/*
 Copyright (C) 2007 Richard Gomes

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

/*
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005, 2006 StatPro Italia srl
 Copyright (C) 2003 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.methods.montecarlo;

import org.jquantlib.time.TimeGrid;

/**
 * Single-factor random walk
 *
 * <p>The path includes the initial asset value as its first point.
 *
 * <p>Java port of {@code QuantLib v1.42.1 ql/methods/montecarlo/path.hpp}
 * (Phase 5h.5-MC-INFRA). The legacy {@code getValues_()} accessors are
 * retained for backward source compatibility with the few in-tree
 * call-sites; new code must use the C++-aligned API ({@code get},
 * {@code at}, {@code value}, {@code front}, {@code back}, {@code set},
 * {@code timeGrid}).
 *
 * @author Richard Gomes
 */
public class Path {

    //
    // private fields
    //

    private TimeGrid timeGrid_;
    private double[] values_;


    //
    // public constructors
    //

    public Path(final TimeGrid timeGrid) {
        this(timeGrid, null);
    }

    public Path(final TimeGrid timeGrid, final double[] values) {
        this.timeGrid_ = timeGrid;
        if (values == null || values.length == 0) {
            this.values_ = new double[timeGrid_.size()];
        } else {
            this.values_ = values;
        }
        if (this.values_.length != timeGrid_.size()) {
            throw new IllegalArgumentException(
                    "different number of times and asset values"
                            + " (timeGrid.size=" + timeGrid_.size()
                            + ", values.length=" + this.values_.length + ")");
        }
    }


    //
    // legacy accessors (kept for source compat with existing call-sites)
    //

    public TimeGrid getTimeGrid_() {
        return timeGrid_;
    }

    public double[] getValues_() {
        return values_;
    }

    public double getValues_(final int i) {
        return values_[i];
    }

    /*@PackagePrivate*/ void setTimeGrid_(final TimeGrid timeGrid_) {
        this.timeGrid_ = timeGrid_;
    }

    /*@PackagePrivate*/ void setValues_(final double[] values) {
        this.values_ = values;
    }

    /*@PackagePrivate*/ void setValues_(final int i, final double value) {
        this.values_[i] = value;
    }


    //
    // C++-aligned inspectors (Phase 5h.5-MC-INFRA)
    //

    /**
     * Mirrors {@code Path::empty()} from C++.
     */
    public boolean empty() /* @ReadOnly */ {
        return timeGrid_.empty();
    }

    /**
     * Mirrors {@code Path::length()} from C++.
     */
    public /* @NonNegative */ int length() /* @ReadOnly */ {
        return timeGrid_.size();
    }

    /**
     * Asset value at the i-th point (mirrors C++ {@code operator[](Size)}
     * and {@code value(Size)} const).
     */
    public /* @Real */ double get(final /* @NonNegative */ int i) /* @ReadOnly */ {
        return values_[i];
    }

    /**
     * Bounds-checked asset value at the i-th point (mirrors C++
     * {@code Path::at(Size)}; throws on out-of-range).
     */
    public /* @Real */ double at(final /* @NonNegative */ int i) /* @ReadOnly */ {
        if (i < 0 || i >= values_.length) {
            throw new IndexOutOfBoundsException("Path.at: index " + i
                    + " out of range [0," + values_.length + ")");
        }
        return values_[i];
    }

    /**
     * Alias of {@link #get(int)} for parity with the C++ {@code value()}
     * accessor.
     */
    public /* @Real */ double value(final /* @NonNegative */ int i) /* @ReadOnly */ {
        return values_[i];
    }

    /**
     * Mirrors {@code Path::front()}: initial asset value.
     */
    public /* @Real */ double front() /* @ReadOnly */ {
        return values_[0];
    }

    /**
     * Mirrors {@code Path::back()}: final asset value.
     */
    public /* @Real */ double back() /* @ReadOnly */ {
        return values_[values_.length - 1];
    }

    /**
     * Mirrors {@code Path::operator[](Size)} non-const and
     * {@code Path::value(Size)} non-const: write-access setter.
     */
    public void set(final /* @NonNegative */ int i, final /* @Real */ double value) {
        values_[i] = value;
    }

    /**
     * Sets the initial asset value (mirrors {@code Path::front()}
     * non-const reference returned by C++).
     */
    public void setFront(final /* @Real */ double value) {
        values_[0] = value;
    }

    /**
     * Sets the final asset value (mirrors {@code Path::back()}
     * non-const reference returned by C++).
     */
    public void setBack(final /* @Real */ double value) {
        values_[values_.length - 1] = value;
    }

    /**
     * Mirrors {@code Path::time(Size)}: time at the i-th point.
     */
    public /* @Time */ double time(final /* @NonNegative */ int i) /* @ReadOnly */ {
        return timeGrid_.get(i);
    }

    /**
     * Mirrors {@code Path::timeGrid()}: read-only access to the
     * underlying time grid.
     */
    public TimeGrid timeGrid() /* @ReadOnly */ {
        return timeGrid_;
    }

    /**
     * Returns the underlying values array. Caller must not retain or
     * mutate this array between successive {@link PathGenerator#next()}
     * calls — the path generator reuses the array for each draw.
     */
    public double[] values() /* @ReadOnly */ {
        return values_;
    }
}
