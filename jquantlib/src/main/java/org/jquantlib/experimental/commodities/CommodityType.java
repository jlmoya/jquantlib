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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 J. Erik Radmall

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.commodities;

import java.util.HashMap;
import java.util.Map;

/**
 * Commodity type.
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/experimental/commodities/commoditytype.hpp}.
 * <p>
 * Default-constructed instances have undefined behaviour and act only as placeholders until reassigned.
 */
public class CommodityType {

    /** Shared registry by code, mirroring the C++ static {@code commodityTypes_} map. */
    private static final Map< String, Data > commodityTypes_ = new HashMap<>();

    /** Pimpl-style data; null when this instance is empty. */
    protected Data data_;

    /** Default constructor - produces an empty instance. */
    public CommodityType() {
        // empty placeholder
    }

    /**
     * Construct or look up a commodity type by code.
     *
     * @param code commodity code, e.g. "HO"
     * @param name commodity name, e.g. "Heating Oil"
     */
    public CommodityType(final String code, final String name) {
        // C++ keys by code (see commoditytype.cpp)
        final Data existing = commodityTypes_.get(code);
        if ( existing != null ) {
            this.data_ = existing;
        } else {
            this.data_ = new Data(name, code);
            commodityTypes_.put(code, this.data_);
        }
    }

    /** @return the commodity code, e.g. "HO" */
    public final String code() {
        return data_.code;
    }

    /** @return the commodity name, e.g. "Heating Oil" */
    public final String name() {
        return data_.name;
    }

    /** @return whether this instance carries no data */
    public final boolean empty() {
        return data_ == null;
    }

    @Override
    public boolean equals(final Object obj) {
        if ( this == obj )
            return true;
        if ( !(obj instanceof CommodityType) )
            return false;
        final CommodityType other = (CommodityType) obj;
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
        return empty() ? "null commodity type" : code();
    }

    /** Pimpl data record. */
    protected static final class Data {
        final String name;
        final String code;

        Data(final String name, final String code) {
            this.name = name;
            this.code = code;
        }
    }
}
