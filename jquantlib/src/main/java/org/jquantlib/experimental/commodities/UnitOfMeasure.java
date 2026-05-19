/*
 Copyright (C) 2026 JQuantLib

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 J. Erik Radmall
*/

package org.jquantlib.experimental.commodities;

import org.jquantlib.math.Rounding;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit-of-measure specification.
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/experimental/commodities/unitofmeasure.hpp}.
 * <p>
 * Default-constructed instances have undefined behaviour and act only as placeholders until reassigned.
 */
public class UnitOfMeasure {

    /** Shared registry by name, mirroring the C++ static {@code unitsOfMeasure_} map. */
    private static final Map< String, Data > unitsOfMeasure_ = new HashMap<>();
    /** Pimpl-style data; null when this instance is empty. */
    protected Data data_;

    /** Default constructor - produces an empty instance. */
    public UnitOfMeasure() {
        // empty placeholder
    }

    /**
     * Construct or look up a unit of measure by name.
     *
     * @param name     e.g. "Barrels"
     * @param code     e.g. "BBL"
     * @param unitType physical category
     */
    public UnitOfMeasure(final String name, final String code, final Type unitType) {
        final Data existing = unitsOfMeasure_.get(name);
        if ( existing != null ) {
            this.data_ = existing;
        } else {
            this.data_ = new Data(name, code, unitType);
            unitsOfMeasure_.put(name, this.data_);
        }
    }

    /** @return the unit name, e.g. "Barrels" */
    public final String name() {
        return data_.name;
    }

    /** @return the unit code, e.g. "BBL" */
    public final String code() {
        return data_.code;
    }

    /** @return the unit type (mass, volume, ...) */
    public final Type unitType() {
        return data_.unitType;
    }

    /** @return whether this instance carries no data */
    public final boolean empty() {
        return data_ == null;
    }

    /** @return the rounding policy associated with this unit */
    public final Rounding rounding() {
        return data_.rounding;
    }

    /** @return the unit used for triangulation when converting between non-direct pairs */
    public final UnitOfMeasure triangulationUnitOfMeasure() {
        return data_.triangulationUnitOfMeasure;
    }

    @Override
    public boolean equals(final Object obj) {
        if ( this == obj )
            return true;
        if ( !(obj instanceof UnitOfMeasure) )
            return false;
        final UnitOfMeasure other = (UnitOfMeasure) obj;
        if ( this.empty() || other.empty() )
            return this.empty() == other.empty();
        return this.code().equals(other.code());
    }

    @Override
    public int hashCode() {
        return empty() ? 0 : code().hashCode();
    }

    @Override
    public String toString() {
        return empty() ? "null unit of measure" : code();
    }

    /** Quantitative type of the unit. */
    public enum Type {
        Mass, Volume, Energy, Quantity
    }

    /** Pimpl data record. */
    protected static final class Data {
        public final String name;
        public final String code;
        public final Type unitType;
        public final UnitOfMeasure triangulationUnitOfMeasure;
        public final Rounding rounding;

        public Data(final String name, final String code, final Type unitType) {
            this(name, code, unitType, new UnitOfMeasure(), new Rounding());
        }

        public Data(final String name, final String code, final Type unitType,
                final UnitOfMeasure triangulationUnitOfMeasure) {
            this(name, code, unitType, triangulationUnitOfMeasure, new Rounding());
        }

        public Data(final String name, final String code, final Type unitType,
                final UnitOfMeasure triangulationUnitOfMeasure, final Rounding rounding) {
            this.name = name;
            this.code = code;
            this.unitType = unitType;
            this.triangulationUnitOfMeasure = triangulationUnitOfMeasure;
            this.rounding = rounding;
        }
    }
}
