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

 JQuantLib is based on QuantLib. http://quantlib.org/
*/

/*
 Copyright (C) 2007 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.volatility;

import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.quotes.Handle;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

/**
 * Interest rate (optionlet/swaption) volatility cube container.
 *
 * <p>Faithful port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/volcube.{hpp,cpp}}. Aggregates a list of
 * smile surfaces ({@link InterestRateVolSurface}) with a parallel list of ATM
 * curves ({@link AbcdAtmVolCurve}); enforces a shared reference date across
 * every member and requires at least two surfaces.
 *
 * <p>The C++ inline {@code minIndexTenor()} / {@code maxIndexTenor()}
 * accessors are declared but never defined in v1.42.1; we therefore expose
 * the surface and curve lists only (mirrors the {@code surfaces()} /
 * {@code curves()} inline accessors).
 */
public class VolatilityCube {

    protected final List< Handle< InterestRateVolSurface > > surfaces_;
    protected final List< Handle< AbcdAtmVolCurve > > curves_;

    public VolatilityCube(final List< Handle< InterestRateVolSurface > > surfaces,
            final List< Handle< AbcdAtmVolCurve > > curves) {
        QL.require(surfaces != null && surfaces.size() > 1, "at least 2 surfaces are needed");

        final Date refDate = surfaces.get(0).currentLink().referenceDate();
        for ( final Handle< InterestRateVolSurface > h : surfaces ) {
            QL.require(h.currentLink().referenceDate().eq(refDate), "different reference dates");
        }
        if ( curves != null ) {
            for ( final Handle< AbcdAtmVolCurve > h : curves ) {
                QL.require(h.currentLink().referenceDate().eq(refDate), "different reference dates");
            }
        }
        this.surfaces_ = Collections.unmodifiableList(surfaces);
        this.curves_ = (curves != null) ? Collections.unmodifiableList(curves) : Collections.emptyList();
    }

    /**
     * Returns the surfaces in index-tenor order. Mirrors C++ inline
     * {@code surfaces()}.
     */
    public List< Handle< InterestRateVolSurface > > surfaces() {
        return surfaces_;
    }

    /**
     * Returns the ATM curves in index-tenor order. Mirrors C++ inline
     * {@code curves()}.
     */
    public List< Handle< AbcdAtmVolCurve > > curves() {
        return curves_;
    }

    /**
     * Declared by the C++ header but never defined in v1.42.1; provided here
     * for API parity and returns the empty period.
     */
    public Period minIndexTenor() {
        return new Period();
    }

    /**
     * Declared by the C++ header but never defined in v1.42.1; provided here
     * for API parity and returns the empty period.
     */
    public Period maxIndexTenor() {
        return new Period();
    }
}
