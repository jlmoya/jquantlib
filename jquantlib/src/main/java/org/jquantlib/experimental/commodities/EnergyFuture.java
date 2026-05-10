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

import java.util.Map;

import org.jquantlib.Settings;
import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.Money;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Date;

/**
 * Energy futures contract.
 * <p>
 * Java port of QuantLib v1.42.1 {@code energyfuture.{hpp,cpp}}.
 */
public class EnergyFuture extends EnergyCommodity {

    private final int buySell_;
    private final Quantity quantity_;
    private final CommodityUnitCost tradePrice_;
    private final CommodityIndex index_;

    public EnergyFuture(final int buySell,
                        final Quantity quantity,
                        final CommodityUnitCost tradePrice,
                        final CommodityIndex index,
                        final CommodityType commodityType,
                        final SecondaryCosts secondaryCosts) {
        super(commodityType, secondaryCosts);
        this.buySell_ = buySell;
        this.quantity_ = quantity;
        this.tradePrice_ = tradePrice;
        this.index_ = index;
    }

    /** Mirrors the C++ default of {@code false}. */
    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public Quantity quantity() {
        return quantity_;
    }

    public int buySell() {
        return buySell_;
    }

    public CommodityUnitCost tradePrice() {
        return tradePrice_;
    }

    public CommodityIndex index() {
        return index_;
    }

    @Override
    protected void performCalculations() {
        // C++ v1.42.1 energyfuture.cpp ::performCalculations.
        // NPV = sign * lot * Q * (quotePriceValue - tradePriceValue) - secondary costs,
        // where quotePriceValue and tradePriceValue are the index quote /
        // trade price converted to (baseCurrency, baseUnitOfMeasure), and Q
        // is the trade quantity in baseUnitOfMeasure scaled by index lot
        // quantity (matching the multiplication chain in the C++ code).
        this.NPV = 0.0;
        additionalResults_.clear();

        final Date evaluationDate = new Settings().evaluationDate();
        final Currency baseCurrency = CommoditySettings.getInstance().currency();
        final UnitOfMeasure baseUnitOfMeasure =
                CommoditySettings.getInstance().unitOfMeasure();

        final double quantityUomConversionFactor =
                calculateUomConversionFactor(quantity_.commodityType(),
                        baseUnitOfMeasure, quantity_.unitOfMeasure())
                        * index_.lotQuantity();
        final double indexUomConversionFactor =
                calculateUomConversionFactor(index_.commodityType(),
                        index_.unitOfMeasure(), baseUnitOfMeasure);
        final double tradePriceUomConversionFactor =
                calculateUomConversionFactor(quantity_.commodityType(),
                        tradePrice_.unitOfMeasure(), baseUnitOfMeasure);

        final double tradePriceFxConversionFactor =
                calculateFxConversionFactor(tradePrice_.amount().currency(),
                        baseCurrency, evaluationDate);
        final double indexPriceFxConversionFactor =
                calculateFxConversionFactor(index_.currency(),
                        baseCurrency, evaluationDate);

        // Read the index quote (or fall back to forward price if quotes are stale).
        double quoteValue;
        final Date lastQuoteDate = index_.lastQuoteDate();
        if (lastQuoteDate.ge(evaluationDate.sub(1))) {
            quoteValue = index_.fixing(evaluationDate, false);
        } else {
            quoteValue = index_.forwardPrice(evaluationDate);
            addPricingError(PricingError.Level.Warning,
                    "curve [" + index_.name() + "] has stale quotes; "
                            + "using forward price from ["
                            + (index_.forwardCurve() != null
                                    ? index_.forwardCurve().name() : "<no forward curve>")
                            + "]");
        }
        if (Double.isNaN(quoteValue)) {
            throw new LibraryException("missing quote for [" + index_.name() + "]");
        }

        final double tradePriceValue =
                tradePrice_.amount().value() * tradePriceUomConversionFactor
                        * tradePriceFxConversionFactor;
        final double quotePriceValue =
                quoteValue * indexUomConversionFactor * indexPriceFxConversionFactor;

        final double quantityAmount = quantity_.amount() * quantityUomConversionFactor;

        final double delta = (((quotePriceValue - tradePriceValue) * quantityAmount)
                * index_.lotQuantity()) * buySell_;

        this.NPV = delta;

        // Subtract secondary costs (cf. EnergyCommodity::calculateSecondaryCostAmounts).
        calculateSecondaryCostAmounts(quantity_.commodityType(),
                quantity_.amount(), evaluationDate);
        for (final Map.Entry<String, Money> entry : secondaryCostAmounts_.entrySet()) {
            this.NPV -= entry.getValue().value();
        }
    }
}
