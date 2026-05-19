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
 Copyright (C) 2009 StatPro Italia srl
*/

package org.jquantlib.experimental.commodities;

import org.jquantlib.lang.exceptions.LibraryException;

/**
 * Conversion factor between two units of measure for a given commodity.
 * <p>
 * Java port of QuantLib v1.42.1 {@code unitofmeasureconversion.{hpp,cpp}}.
 */
public class UnitOfMeasureConversion {

    /** Pimpl-style data. */
    protected Data data_;

    /** Default - empty conversion. */
    public UnitOfMeasureConversion() {
        // empty
    }

    /**
     * Direct conversion: a unit of {@code source} is worth {@code conversionFactor} units of {@code target}.
     */
    public UnitOfMeasureConversion(final CommodityType commodityType, final UnitOfMeasure source,
            final UnitOfMeasure target, final double conversionFactor) {
        this.data_ = new Data(commodityType, source, target, conversionFactor, Type.Direct);
    }

    /** Derived conversion built by chaining two existing conversions. */
    protected UnitOfMeasureConversion(final UnitOfMeasureConversion r1, final UnitOfMeasureConversion r2) {
        this.data_ = new Data(r1, r2);
    }

    /** Chain two conversions, mirroring the C++ {@code chain} static method. */
    public static UnitOfMeasureConversion chain(final UnitOfMeasureConversion r1, final UnitOfMeasureConversion r2) {
        final UnitOfMeasureConversion result = new UnitOfMeasureConversion(r1, r2);
        result.data_.type = Type.Derived;
        if ( r1.data_.source.equals(r2.data_.source) ) {
            result.data_.source = r1.data_.target;
            result.data_.target = r2.data_.target;
            result.data_.conversionFactor = r2.data_.conversionFactor / r1.data_.conversionFactor;
        } else if ( r1.data_.source.equals(r2.data_.target) ) {
            result.data_.source = r1.data_.target;
            result.data_.target = r2.data_.source;
            result.data_.conversionFactor = 1.0 / (r1.data_.conversionFactor * r2.data_.conversionFactor);
        } else if ( r1.data_.target.equals(r2.data_.source) ) {
            result.data_.source = r1.data_.source;
            result.data_.target = r2.data_.target;
            result.data_.conversionFactor = r1.data_.conversionFactor * r2.data_.conversionFactor;
        } else if ( r1.data_.target.equals(r2.data_.target) ) {
            result.data_.source = r1.data_.source;
            result.data_.target = r2.data_.source;
            result.data_.conversionFactor = r1.data_.conversionFactor / r2.data_.conversionFactor;
        } else {
            throw new LibraryException("conversion factors not chainable");
        }
        return result;
    }

    public CommodityType commodityType() {
        return data_.commodityType;
    }

    public UnitOfMeasure source() {
        return data_.source;
    }

    public UnitOfMeasure target() {
        return data_.target;
    }

    public double conversionFactor() {
        return data_.conversionFactor;
    }

    public Type type() {
        return data_.type;
    }

    public String code() {
        return data_.code;
    }

    /** Apply this conversion to a quantity. */
    public Quantity convert(final Quantity quantity) {
        switch ( data_.type ) {
        case Direct: {
            if ( quantity.unitOfMeasure().equals(data_.source) )
                return new Quantity(quantity.commodityType(), data_.target, quantity.amount() * data_.conversionFactor);
            if ( quantity.unitOfMeasure().equals(data_.target) )
                return new Quantity(quantity.commodityType(), data_.source, quantity.amount() / data_.conversionFactor);
            throw new LibraryException("direct conversion not applicable");
        }
        case Derived: {
            if ( quantity.unitOfMeasure().equals(data_.conversionFactorChainFirst.source()) || quantity.unitOfMeasure()
                    .equals(data_.conversionFactorChainFirst.target()) ) {
                return data_.conversionFactorChainSecond.convert(data_.conversionFactorChainFirst.convert(quantity));
            }
            if ( quantity.unitOfMeasure().equals(data_.conversionFactorChainSecond.source()) || quantity.unitOfMeasure()
                    .equals(data_.conversionFactorChainSecond.target()) ) {
                return data_.conversionFactorChainFirst.convert(data_.conversionFactorChainSecond.convert(quantity));
            }
            throw new LibraryException("derived conversion factor not applicable");
        }
        default:
            throw new LibraryException("unknown conversion-factor type");
        }
    }

    /** Source of the factor. */
    public enum Type {
        /** given directly by the user */
        Direct,
        /** derived from conversion factors between other UoMs */
        Derived
    }

    /** Pimpl data record. */
    protected static final class Data {
        CommodityType commodityType;
        UnitOfMeasure source;
        UnitOfMeasure target;
        double conversionFactor;
        Type type;
        String code;
        UnitOfMeasureConversion conversionFactorChainFirst;
        UnitOfMeasureConversion conversionFactorChainSecond;

        Data(final CommodityType commodityType, final UnitOfMeasure source, final UnitOfMeasure target,
                final double conversionFactor, final Type type) {
            this.commodityType = commodityType;
            this.source = source;
            this.target = target;
            this.conversionFactor = conversionFactor;
            this.type = type;
            this.code = commodityType.name() + source.code() + target.code();
        }

        Data(final UnitOfMeasureConversion r1, final UnitOfMeasureConversion r2) {
            // Mirrors C++ which sets only the chain pair here; remaining
            // fields are filled in by chain(...). We init code to empty to
            // avoid NPE in the unlikely event of debug printing.
            this.code = "";
            this.conversionFactorChainFirst = r1;
            this.conversionFactorChainSecond = r2;
        }
    }
}
