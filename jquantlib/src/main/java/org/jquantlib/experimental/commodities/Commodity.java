/*
 Copyright (C) 2026 JQuantLib

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 J. Erik Radmall
*/

package org.jquantlib.experimental.commodities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jquantlib.currencies.Money;
import org.jquantlib.instruments.Instrument;

/**
 * Commodity base class.
 * <p>
 * Java port of QuantLib v1.42.1 {@code commodity.{hpp,cpp}}.
 * <p>
 * SecondaryCosts are modelled in C++ as {@code std::map<std::string, ext::any>};
 * in Java we use {@code Map<String, Object>} where the value is expected to be
 * either a {@link CommodityUnitCost} or a {@link Money}.
 */
public abstract class Commodity extends Instrument {

    /** Map from cost name -> {@link CommodityUnitCost} or {@link Money}. */
    public static final class SecondaryCosts extends HashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    /** Map from cost name -> {@link Money} amount in the base currency. */
    public static final class SecondaryCostAmounts extends HashMap<String, Money> {
        private static final long serialVersionUID = 1L;
    }

    protected final SecondaryCosts secondaryCosts_;
    protected final List<PricingError> pricingErrors_ = new ArrayList<>();
    protected final SecondaryCostAmounts secondaryCostAmounts_ = new SecondaryCostAmounts();

    protected Commodity(final SecondaryCosts secondaryCosts) {
        this.secondaryCosts_ = secondaryCosts;
    }

    public final SecondaryCosts secondaryCosts() {
        return secondaryCosts_;
    }

    public final SecondaryCostAmounts secondaryCostAmounts() {
        return secondaryCostAmounts_;
    }

    public final List<PricingError> pricingErrors() {
        return pricingErrors_;
    }

    public final void addPricingError(final PricingError.Level errorLevel,
                                      final String error) {
        addPricingError(errorLevel, error, "");
    }

    public final void addPricingError(final PricingError.Level errorLevel,
                                      final String error,
                                      final String detail) {
        pricingErrors_.add(new PricingError(errorLevel, error, detail));
    }
}
