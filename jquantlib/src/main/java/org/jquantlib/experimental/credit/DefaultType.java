/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2009 StatPro Italia srl
 Copyright (C) 2009 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.QL;

/**
 * Atomic credit-event type. Encapsulates the ISDA default contractual
 * types and their combinations.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::DefaultType}
 * ({@code ql/experimental/credit/defaulttype.{hpp,cpp}}).
 *
 * <p>Equality is the criteria for indexing the curves and depends only on
 * the atomic types, not on idiosyncrasies of derived types: see
 * {@link #equals(Object)} below mirroring the free C++
 * {@code operator==(const DefaultType&, const DefaultType&)}.
 *
 * <p>Phase 4m foundation.
 */
public class DefaultType {

    protected AtomicDefault.Type defTypes;
    protected Restructuring.Type restrType;

    public DefaultType() {
        this(AtomicDefault.Type.Bankruptcy, Restructuring.XR);
    }

    public DefaultType(final AtomicDefault.Type defType,
                       final Restructuring.Type restType) {
        this.defTypes = defType;
        this.restrType = restType;
        // checks restruct and norestruct are never together (XOR).
        final boolean defIsRestruct = defType == AtomicDefault.Type.Restructuring;
        final boolean noRestruct = restrType == Restructuring.Type.NoRestructuring;
        QL.require(defIsRestruct ^ noRestruct,
                "Incoherent credit event type definition.");
    }

    public AtomicDefault.Type defaultType() {
        return defTypes;
    }

    public Restructuring.Type restructuringType() {
        return restrType;
    }

    public boolean isRestructuring() {
        return restrType != Restructuring.Type.NoRestructuring;
    }

    /**
     * Returns true if {@code defType} is within this one and as such will
     * be recognised as a trigger. Strict match (no event hierarchy).
     */
    public boolean containsDefaultType(final AtomicDefault.Type defType) {
        return defTypes == defType;
    }

    public boolean containsRestructuringType(final Restructuring.Type resType) {
        return (restrType == resType) ||
               (Restructuring.Type.AnyRestructuring == resType);
    }

    /** Mirrors C++ free {@code operator==(const DefaultType&, const DefaultType&)}. */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultType)) {
            return false;
        }
        final DefaultType rhs = (DefaultType) o;
        return defTypes == rhs.defTypes && restrType == rhs.restrType;
    }

    @Override
    public int hashCode() {
        int h = (defTypes != null ? defTypes.hashCode() : 0);
        h = 31 * h + (restrType != null ? restrType.hashCode() : 0);
        return h;
    }
}
