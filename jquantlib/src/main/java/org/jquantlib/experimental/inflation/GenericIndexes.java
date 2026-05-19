/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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
 Copyright (C) 2009 Chris Kenyon
 Copyright (C) 2021 Ralf Konrad Eckel

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.experimental.inflation;

import org.jquantlib.currencies.Currency;
import org.jquantlib.indexes.Region;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;

/**
 * Generic inflation index classes for testing and reference implementations.
 *
 * <p>Mirrors C++ v1.42.1 {@code ql/experimental/inflation/genericindexes.hpp}.
 *
 * <p>Contains:
 * <ul>
 *   <li>{@link GenericRegion} — generic geographical/economic region</li>
 *   <li>{@link GenericCPI} — generic zero-inflation CPI index</li>
 *   <li>{@link YYGenericCPI} — generic year-on-year CPI index</li>
 * </ul>
 *
 * @author JQuantLib migration contributors (Phase 2s L0)
 */
public final class GenericIndexes {

    private GenericIndexes() {
        // static container
    }

    // -------------------------------------------------------------------------
    // GenericRegion
    // -------------------------------------------------------------------------

    /**
     * Generic geographical/economic region.
     *
     * <p>Mirrors C++ {@code QuantLib::GenericRegion}.
     * Region name = "Generic", code = "GENERIC".
     */
    public static class GenericRegion extends Region {

        public GenericRegion() {
            this.data = new Region.Data("Generic", "GENERIC");
        }

    }

    // -------------------------------------------------------------------------
    // GenericCPI
    // -------------------------------------------------------------------------

    /**
     * Generic zero-inflation CPI index.
     *
     * <p>Mirrors C++ {@code QuantLib::GenericCPI}
     * ({@code ql/experimental/inflation/genericindexes.hpp}).
     *
     * <p>Family name is "CPI"; region is {@link GenericRegion}.
     * Constructed with {@code interpolated=false} (C++ v1.42.1 deprecated the {@code interpolated} parameter in v1.38;
     * the non-interpolated constructor is the primary one in v1.42.1).
     */
    public static class GenericCPI extends ZeroInflationIndex {

        /**
         * Constructs a GenericCPI index without an attached term structure.
         *
         * @param frequency observation frequency (e.g., Monthly)
         * @param revised   whether the index is revised
         * @param lag       availability lag
         * @param currency  currency of the index
         */
        public GenericCPI(final Frequency frequency, final boolean revised, final Period lag, final Currency currency) {
            this(frequency, revised, lag, currency, new Handle< ZeroInflationTermStructure >());
        }

        /**
         * Constructs a GenericCPI index with an attached term structure.
         *
         * @param frequency observation frequency (e.g., Monthly)
         * @param revised   whether the index is revised
         * @param lag       availability lag
         * @param currency  currency of the index
         * @param ts        zero-inflation term structure handle
         */
        public GenericCPI(final Frequency frequency, final boolean revised, final Period lag, final Currency currency,
                final Handle< ZeroInflationTermStructure > ts) {
            // interpolated=false mirrors C++ v1.42.1 non-interpolated constructor
            super("CPI", new GenericRegion(), revised, /* interpolated= */ false, frequency, lag, currency, ts);
        }

    }

    // -------------------------------------------------------------------------
    // YYGenericCPI
    // -------------------------------------------------------------------------

    /**
     * Generic quoted year-on-year CPI index (genuine, not a ratio).
     *
     * <p>Mirrors C++ {@code QuantLib::YYGenericCPI}
     * ({@code ql/experimental/inflation/genericindexes.hpp}).
     *
     * <p>Family name is "YY_CPI"; region is {@link GenericRegion}.
     */
    public static class YYGenericCPI extends YoYInflationIndex {

        /**
         * Constructs a YYGenericCPI index without an attached term structure.
         *
         * @param frequency observation frequency (e.g., Monthly)
         * @param revised   whether the index is revised
         * @param lag       availability lag
         * @param currency  currency of the index
         */
        public YYGenericCPI(final Frequency frequency, final boolean revised, final Period lag,
                final Currency currency) {
            this(frequency, revised, lag, currency, new Handle< YoYInflationTermStructure >());
        }

        /**
         * Constructs a YYGenericCPI index with an attached term structure.
         *
         * @param frequency observation frequency (e.g., Monthly)
         * @param revised   whether the index is revised
         * @param lag       availability lag
         * @param currency  currency of the index
         * @param ts        year-on-year inflation term structure handle
         */
        public YYGenericCPI(final Frequency frequency, final boolean revised, final Period lag, final Currency currency,
                final Handle< YoYInflationTermStructure > ts) {
            // ratio=false: genuine YoY (not computed as ratio of zero levels)
            // interpolated=false mirrors C++ v1.42.1 non-interpolated constructor
            super("YY_CPI", new GenericRegion(), revised, /* interpolated= */ false,
                    /* ratio= */ false, frequency, lag, currency, ts);
        }

    }

}
