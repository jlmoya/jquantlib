/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.optionlet;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.util.LazyObject;

import java.util.List;

/**
 * Abstract base class interface for a (time indexed) vector of (strike indexed) optionlet (i.e. caplet/floorlet)
 * volatilities.
 *
 * <p>Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/optionlet/strippedoptionletbase.{hpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>C++ uses pure virtual methods; Java keeps them abstract on this
 *      class extending {@link LazyObject}.</li>
 *  <li>{@code optionletStrikes(Size)}, {@code optionletVolatilities(Size)}
 *      return {@link List} of {@link Double} for parity with the C++
 *      {@code std::vector<Real>} return.</li>
 * </ul>
 */
public abstract class StrippedOptionletBase extends LazyObject {

    public abstract List< Double > optionletStrikes(int i);

    public abstract List< Double > optionletVolatilities(int i);

    public abstract List< Date > optionletFixingDates();

    public abstract List< Double > optionletFixingTimes();

    public abstract int optionletMaturities();

    public abstract List< Double > atmOptionletRates();

    public abstract DayCounter dayCounter();

    public abstract Calendar calendar();

    public abstract int settlementDays();

    public abstract BusinessDayConvention businessDayConvention();

    public abstract VolatilityType volatilityType();

    public abstract double displacement();
}
