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
 * South Africa as geographical/economic region used for inflation applicability.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::ZARegion}
 * ({@code ql/indexes/region.cpp:56-59}). Region name is {@code "South Africa"} with code {@code "ZA"}, so an inflation
 * index built with {@code ZARegion} will report a {@link org.jquantlib.indexes.InflationIndex#name()} of
 * {@code "South Africa <familyName>"}.
 */
public class ZARegion extends Region {

    public ZARegion() {
        this.data = new Region.Data("South Africa", "ZA");
    }
}
