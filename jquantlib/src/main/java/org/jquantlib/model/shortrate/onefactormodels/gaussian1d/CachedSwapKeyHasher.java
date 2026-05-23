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

/**
 * Hash functor for {@link CachedSwapKey}, mirroring the C++ {@code CachedSwapKeyHasher}
 * struct from {@code ql/models/shortrate/onefactormodels/gaussian1d.hpp} lines 166-175
 * (v1.42.1 @ 099987f0).
 *
 * <p>The C++ functor combines four sub-hashes via {@code boost::hash_combine}:
 * <ol>
 *   <li>{@code x.index->name()}</li>
 *   <li>{@code x.fixing.serialNumber()}</li>
 *   <li>{@code x.tenor.length()}</li>
 *   <li>{@code x.tenor.units()}</li>
 * </ol>
 *
 * <p>Java's {@code HashMap} keys on the record's own {@code hashCode()} (which delegates
 * here), so an explicit functor instance is not needed at the Map call site — this class
 * exposes a static {@link #hash(CachedSwapKey)} so {@link CachedSwapKey#hashCode()} can
 * delegate to it, mirroring the C++ class structure for review parity.
 *
 * @author Peter Caspers (C++ original)
 * @author JQuantLib contributors (Java port)
 */
public final class CachedSwapKeyHasher {

    private CachedSwapKeyHasher() {
        // utility class — no instances
    }

    /**
     * Returns the hash code for {@code k} by combining its sub-component hashes.
     *
     * <p>Uses {@link java.util.Objects#hash(Object...)} rather than the exact
     * {@code boost::hash_combine} mixing function: only consistency with
     * {@link CachedSwapKey#equals(Object)} is required (the {@code HashMap} contract);
     * the C++ hash distribution properties are not externally observable.
     */
    public static int hash(final CachedSwapKey k) {
        return java.util.Objects.hash(
                k.index().name(),
                k.fixing().serialNumber(),
                k.tenor().length(),
                k.tenor().units());
    }
}
