/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2004, 2005, 2006 StatPro Italia srl

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

package org.jquantlib.indexes;

import java.util.Comparator;

/**
 * Case-insensitive string comparator used by {@link IndexManager} for index
 * name lookups; index names are case insensitive.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/indexmanager.hpp}
 * nested struct {@code CaseInsensitiveCompare}, which performs a
 * lexicographical comparison after {@code std::toupper}-ing each character.
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class CaseInsensitiveCompare implements Comparator<String> {

    @Override
    public int compare(final String s1, final String s2) {
        // Mirrors std::lexicographical_compare with toupper transform.
        // String.compareToIgnoreCase does precisely this character-by-character
        // (folding via Character.toUpperCase) and returns < 0 / 0 / > 0 in the
        // same direction expected by Comparator.
        return s1.compareToIgnoreCase(s2);
    }
}
