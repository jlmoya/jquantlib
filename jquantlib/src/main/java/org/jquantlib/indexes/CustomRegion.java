/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2007 Chris Kenyon
 Copyright (C) 2014 StatPro Italia srl

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

/**
 * Custom geographical/economic region used for inflation applicability.
 * <p>
 * This class allows one to create an instance of a particular region without
 * having to define and compile a corresponding subclass.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/region.hpp/cpp}
 * {@code CustomRegion}.
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class CustomRegion extends Region {

    public CustomRegion(final String name, final String code) {
        this.data = new Region.Data(name, code);
    }
}
