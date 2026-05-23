/*
 Copyright (C) 2026 JQuantLib contributors

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
 Copyright (C) 2013, 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.model.shortrate.onefactormodels.gaussian1d;

import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

import java.util.Objects;

/**
 * Cache key for {@link org.jquantlib.instruments.VanillaSwap} instances built
 * from a {@link SwapIndex} template by {@link Gaussian1dModel#underlyingSwap}.
 *
 * <p>Java port of the C++ nested {@code QuantLib::Gaussian1dModel::CachedSwapKey} struct in
 * {@code ql/models/shortrate/onefactormodels/gaussian1d.hpp} lines 156-164 (v1.42.1 @ 099987f0).
 *
 * <p>The C++ struct provides a custom {@code operator==} that compares the SwapIndex by
 * <i>name</i> (not by object identity) so that two SwapIndex instances with the same
 * conventions but different addresses still cache-hit. This Java record overrides
 * {@link #equals(Object)} and {@link #hashCode()} with the same semantics — the {@code index}
 * field is compared via {@link SwapIndex#name()}, while {@code fixing} and {@code tenor}
 * are compared structurally.
 *
 * <p><strong>JDK 25 idiom.</strong> Implemented as a {@link Record} (the canonical key
 * use-case): immutable, structurally-equal, value-semantics by construction. The custom
 * equals/hashCode are required because the default record {@code equals} compares the
 * SwapIndex by reference, not by name, which would defeat the cache.
 *
 * @param index  the swap index template
 * @param fixing the option's fixing date
 * @param tenor  the swap tenor
 * @author Peter Caspers (C++ original)
 * @author JQuantLib contributors (Java port)
 */
public record CachedSwapKey(SwapIndex index, Date fixing, Period tenor) {

    /**
     * Equality on (index.name(), fixing, tenor).
     *
     * <p>Mirrors the C++ {@code bool operator==(const CachedSwapKey &o) const {
     *   return index->name() == o.index->name() && fixing == o.fixing && tenor == o.tenor;
     * }}.
     */
    @Override
    public boolean equals(final Object obj) {
        if ( this == obj ) {
            return true;
        }
        if ( ! (obj instanceof final CachedSwapKey other) ) {
            return false;
        }
        return Objects.equals(this.index.name(), other.index.name())
                && Objects.equals(this.fixing, other.fixing)
                && Objects.equals(this.tenor, other.tenor);
    }

    /**
     * Hash code over (index.name(), fixing.serialNumber(), tenor.length(), tenor.units()).
     *
     * <p>Mirrors the four {@code boost::hash_combine} calls in
     * {@link CachedSwapKeyHasher#hash(CachedSwapKey)}.
     */
    @Override
    public int hashCode() {
        return CachedSwapKeyHasher.hash(this);
    }
}
