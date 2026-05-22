/*
 Copyright (C) 2026 JQuantLib

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 J. Erik Radmall
*/

package org.jquantlib.experimental.commodities;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Closeness;

/**
 * Amount of a commodity.
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/experimental/commodities/quantity.hpp} and {@code quantity.cpp}.
 * <p>
 * In Java the C++ overloaded operators are translated to named methods (see {@link #plus(Quantity)},
 * {@link #minus(Quantity)}, {@link #times(double)}, {@link #divide(double)}, {@link #divide(Quantity)},
 * {@link #lt(Quantity)} etc.).
 */
public class Quantity {

    /** Static conversion strategy. Mirrors the C++ static of the same name. */
    public static ConversionType conversionType = ConversionType.NoConversion;
    /** Static base UoM, used when {@link #conversionType} is {@link ConversionType#BaseUnitOfMeasureConversion}. */
    public static UnitOfMeasure baseUnitOfMeasure = new UnitOfMeasure();
    private CommodityType commodityType_;
    private UnitOfMeasure unitOfMeasure_;
    private double amount_;
    /** Default constructor - empty quantity. */
    public Quantity() {
        this.commodityType_ = new CommodityType();
        this.unitOfMeasure_ = new UnitOfMeasure();
        this.amount_ = 0.0;
    }

    public Quantity(final CommodityType commodityType, final UnitOfMeasure unitOfMeasure, final double amount) {
        this.commodityType_ = commodityType;
        this.unitOfMeasure_ = unitOfMeasure;
        this.amount_ = amount;
    }

    public static Quantity plus(final Quantity m1, final Quantity m2) {
        final Quantity tmp = new Quantity(m1.commodityType_, m1.unitOfMeasure_, m1.amount_);
        tmp.addAssign(m2);
        return tmp;
    }

    public static Quantity minus(final Quantity m1, final Quantity m2) {
        final Quantity tmp = new Quantity(m1.commodityType_, m1.unitOfMeasure_, m1.amount_);
        tmp.subAssign(m2);
        return tmp;
    }

    public static Quantity times(final Quantity m, final double x) {
        final Quantity tmp = new Quantity(m.commodityType_, m.unitOfMeasure_, m.amount_);
        tmp.mulAssign(x);
        return tmp;
    }

    public static Quantity times(final double x, final Quantity m) {
        return times(m, x);
    }

    public static Quantity divide(final Quantity m, final double x) {
        final Quantity tmp = new Quantity(m.commodityType_, m.unitOfMeasure_, m.amount_);
        tmp.divAssign(x);
        return tmp;
    }

    public static double divide(final Quantity m1, final Quantity m2) {
        if ( m1.unitOfMeasure().equals(m2.unitOfMeasure()) ) {
            return m1.amount() / m2.amount();
        } else if ( Quantity.conversionType == ConversionType.BaseUnitOfMeasureConversion ) {
            final Quantity tmp1 = new Quantity(m1.commodityType_, m1.unitOfMeasure_, m1.amount_);
            convertToBase(tmp1);
            final Quantity tmp2 = new Quantity(m2.commodityType_, m2.unitOfMeasure_, m2.amount_);
            convertToBase(tmp2);
            return divide(tmp1, tmp2);
        } else if ( Quantity.conversionType == ConversionType.AutomatedConversion ) {
            final Quantity tmp = new Quantity(m2.commodityType_, m2.unitOfMeasure_, m2.amount_);
            convertTo(tmp, m1.unitOfMeasure());
            return divide(m1, tmp);
        } else {
            throw new LibraryException("unitOfMeasure mismatch and no conversion specified");
        }
    }

    public static boolean eq(final Quantity m1, final Quantity m2) {
        if ( m1.unitOfMeasure().equals(m2.unitOfMeasure()) ) {
            return m1.amount() == m2.amount();
        } else if ( Quantity.conversionType == ConversionType.BaseUnitOfMeasureConversion ) {
            final Quantity tmp1 = new Quantity(m1.commodityType_, m1.unitOfMeasure_, m1.amount_);
            convertToBase(tmp1);
            final Quantity tmp2 = new Quantity(m2.commodityType_, m2.unitOfMeasure_, m2.amount_);
            convertToBase(tmp2);
            return eq(tmp1, tmp2);
        } else if ( Quantity.conversionType == ConversionType.AutomatedConversion ) {
            final Quantity tmp = new Quantity(m2.commodityType_, m2.unitOfMeasure_, m2.amount_);
            convertTo(tmp, m1.unitOfMeasure());
            return eq(m1, tmp);
        } else {
            throw new LibraryException("unitOfMeasure mismatch and no conversion specified");
        }
    }

    public static boolean lt(final Quantity m1, final Quantity m2) {
        if ( m1.unitOfMeasure().equals(m2.unitOfMeasure()) ) {
            return m1.amount() < m2.amount();
        } else if ( Quantity.conversionType == ConversionType.BaseUnitOfMeasureConversion ) {
            final Quantity tmp1 = new Quantity(m1.commodityType_, m1.unitOfMeasure_, m1.amount_);
            convertToBase(tmp1);
            final Quantity tmp2 = new Quantity(m2.commodityType_, m2.unitOfMeasure_, m2.amount_);
            convertToBase(tmp2);
            return lt(tmp1, tmp2);
        } else if ( Quantity.conversionType == ConversionType.AutomatedConversion ) {
            final Quantity tmp = new Quantity(m2.commodityType_, m2.unitOfMeasure_, m2.amount_);
            convertTo(tmp, m1.unitOfMeasure());
            return lt(m1, tmp);
        } else {
            throw new LibraryException("unitOfMeasure mismatch and no conversion specified");
        }
    }

    public static boolean le(final Quantity m1, final Quantity m2) {
        if ( m1.unitOfMeasure().equals(m2.unitOfMeasure()) ) {
            return m1.amount() <= m2.amount();
        } else if ( Quantity.conversionType == ConversionType.BaseUnitOfMeasureConversion ) {
            final Quantity tmp1 = new Quantity(m1.commodityType_, m1.unitOfMeasure_, m1.amount_);
            convertToBase(tmp1);
            final Quantity tmp2 = new Quantity(m2.commodityType_, m2.unitOfMeasure_, m2.amount_);
            convertToBase(tmp2);
            return le(tmp1, tmp2);
        } else if ( Quantity.conversionType == ConversionType.AutomatedConversion ) {
            final Quantity tmp = new Quantity(m2.commodityType_, m2.unitOfMeasure_, m2.amount_);
            convertTo(tmp, m1.unitOfMeasure());
            return le(m1, tmp);
        } else {
            throw new LibraryException("unitOfMeasure mismatch and no conversion specified");
        }
    }

    public static boolean gt(final Quantity m1, final Quantity m2) {
        return lt(m2, m1);
    }

    public static boolean ge(final Quantity m1, final Quantity m2) {
        return le(m2, m1);
    }

    // ---- non-member-style helpers ----

    public static boolean ne(final Quantity m1, final Quantity m2) {
        return !eq(m1, m2);
    }

    public static boolean close(final Quantity m1, final Quantity m2) {
        return close(m1, m2, 42);
    }

    public static boolean close(final Quantity m1, final Quantity m2, final int n) {
        if ( m1.unitOfMeasure().equals(m2.unitOfMeasure()) ) {
            return Closeness.isClose(m1.amount(), m2.amount(), n);
        } else if ( Quantity.conversionType == ConversionType.BaseUnitOfMeasureConversion ) {
            final Quantity tmp1 = new Quantity(m1.commodityType_, m1.unitOfMeasure_, m1.amount_);
            convertToBase(tmp1);
            final Quantity tmp2 = new Quantity(m2.commodityType_, m2.unitOfMeasure_, m2.amount_);
            convertToBase(tmp2);
            return close(tmp1, tmp2, n);
        } else if ( Quantity.conversionType == ConversionType.AutomatedConversion ) {
            final Quantity tmp = new Quantity(m2.commodityType_, m2.unitOfMeasure_, m2.amount_);
            convertTo(tmp, m1.unitOfMeasure());
            return close(m1, tmp, n);
        } else {
            throw new LibraryException("unitOfMeasure mismatch and no conversion specified");
        }
    }

    public static boolean closeEnough(final Quantity m1, final Quantity m2) {
        return closeEnough(m1, m2, 42);
    }

    public static boolean closeEnough(final Quantity m1, final Quantity m2, final int n) {
        if ( m1.unitOfMeasure().equals(m2.unitOfMeasure()) ) {
            return Closeness.isCloseEnough(m1.amount(), m2.amount(), n);
        } else if ( Quantity.conversionType == ConversionType.BaseUnitOfMeasureConversion ) {
            final Quantity tmp1 = new Quantity(m1.commodityType_, m1.unitOfMeasure_, m1.amount_);
            convertToBase(tmp1);
            final Quantity tmp2 = new Quantity(m2.commodityType_, m2.unitOfMeasure_, m2.amount_);
            convertToBase(tmp2);
            return closeEnough(tmp1, tmp2, n);
        } else if ( Quantity.conversionType == ConversionType.AutomatedConversion ) {
            final Quantity tmp = new Quantity(m2.commodityType_, m2.unitOfMeasure_, m2.amount_);
            convertTo(tmp, m1.unitOfMeasure());
            return closeEnough(m1, tmp, n);
        } else {
            throw new LibraryException("unitOfMeasure mismatch and no conversion specified");
        }
    }

    private static void convertTo(final Quantity m, final UnitOfMeasure target) {
        if ( !m.unitOfMeasure().equals(target) ) {
            final UnitOfMeasureConversion rate = UnitOfMeasureConversionManager.getInstance()
                    .lookup(m.commodityType(), m.unitOfMeasure(), target);
            final Quantity converted = rate.convert(m).rounded();
            m.commodityType_ = converted.commodityType_;
            m.unitOfMeasure_ = converted.unitOfMeasure_;
            m.amount_ = converted.amount_;
        }
    }

    private static void convertToBase(final Quantity m) {
        if ( Quantity.baseUnitOfMeasure.empty() ) {
            throw new LibraryException("no base unitOfMeasure set");
        }
        convertTo(m, Quantity.baseUnitOfMeasure);
    }

    public final CommodityType commodityType() {
        return commodityType_;
    }

    public final UnitOfMeasure unitOfMeasure() {
        return unitOfMeasure_;
    }

    public final double amount() {
        return amount_;
    }

    public Quantity rounded() {
        return new Quantity(commodityType_, unitOfMeasure_, unitOfMeasure_.rounding().operator(amount_));
    }

    /** Unary plus - returns a copy. */
    public Quantity positiveValue() {
        return new Quantity(commodityType_, unitOfMeasure_, amount_);
    }

    /** Unary minus. */
    public Quantity negativeValue() {
        return new Quantity(commodityType_, unitOfMeasure_, -amount_);
    }

    /** In-place {@code *=}. */
    public Quantity mulAssign(final double x) {
        amount_ *= x;
        return this;
    }

    /** In-place {@code /=}. */
    public Quantity divAssign(final double x) {
        amount_ /= x;
        return this;
    }

    /** In-place {@code +=}. */
    public Quantity addAssign(final Quantity m) {
        if ( unitOfMeasure_.equals(m.unitOfMeasure_) ) {
            amount_ += m.amount_;
        } else if ( conversionType == ConversionType.BaseUnitOfMeasureConversion ) {
            convertToBase(this);
            final Quantity tmp = new Quantity(m.commodityType_, m.unitOfMeasure_, m.amount_);
            convertToBase(tmp);
            this.addAssign(tmp);
        } else if ( conversionType == ConversionType.AutomatedConversion ) {
            final Quantity tmp = new Quantity(m.commodityType_, m.unitOfMeasure_, m.amount_);
            convertTo(tmp, unitOfMeasure_);
            this.addAssign(tmp);
        } else {
            throw new LibraryException("unitOfMeasure mismatch and no conversion specified");
        }
        return this;
    }

    /** In-place {@code -=}. */
    public Quantity subAssign(final Quantity m) {
        if ( unitOfMeasure_.equals(m.unitOfMeasure_) ) {
            amount_ -= m.amount_;
        } else if ( conversionType == ConversionType.BaseUnitOfMeasureConversion ) {
            convertToBase(this);
            final Quantity tmp = new Quantity(m.commodityType_, m.unitOfMeasure_, m.amount_);
            convertToBase(tmp);
            this.subAssign(tmp);
        } else if ( conversionType == ConversionType.AutomatedConversion ) {
            final Quantity tmp = new Quantity(m.commodityType_, m.unitOfMeasure_, m.amount_);
            convertTo(tmp, unitOfMeasure_);
            this.subAssign(tmp);
        } else {
            throw new LibraryException("unitOfMeasure mismatch and no conversion specified");
        }
        return this;
    }

    @Override
    public boolean equals(final Object obj) {
        if ( this == obj )
            return true;
        if (!(obj instanceof Quantity quantity))
            return false;
        return eq(this, quantity);
    }

    // ---- private helpers ----

    @Override
    public String toString() {
        return commodityType_.code() + " " + amount_ + " " + unitOfMeasure_.code();
    }

    /** Conversion strategy used when combining quantities in different UoMs. */
    public enum ConversionType {
        /** Do not perform conversions. */
        NoConversion,
        /** Convert both operands to the base UoM before combining. */
        BaseUnitOfMeasureConversion,
        /** Return the result in the UoM of the first operand. */
        AutomatedConversion
    }
}
