/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
package org.jquantlib.indexes;

/**
 * USA as geographical/economic region used for inflation applicability.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::USRegion}
 * ({@code ql/indexes/region.cpp:51-54}). Region name is {@code "USA"} with
 * code {@code "US"}, so an inflation index built with {@code USRegion} will
 * report a {@link org.jquantlib.indexes.InflationIndex#name()} of
 * {@code "USA <familyName>"}.
 */
public class USRegion extends Region {

    public USRegion() {
        this.data = new Region.Data("USA", "US");
    }
}
