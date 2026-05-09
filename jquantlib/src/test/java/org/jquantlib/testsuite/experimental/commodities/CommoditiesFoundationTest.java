/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.commodities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.experimental.commodities.BarrelUnitOfMeasure;
import org.jquantlib.experimental.commodities.CommoditySettings;
import org.jquantlib.experimental.commodities.CommodityType;
import org.jquantlib.experimental.commodities.CommodityUnitCost;
import org.jquantlib.experimental.commodities.GallonUnitOfMeasure;
import org.jquantlib.experimental.commodities.LitreUnitOfMeasure;
import org.jquantlib.experimental.commodities.MBUnitOfMeasure;
import org.jquantlib.experimental.commodities.MTUnitOfMeasure;
import org.jquantlib.experimental.commodities.NullCommodityType;
import org.jquantlib.experimental.commodities.Quantity;
import org.jquantlib.experimental.commodities.UnitOfMeasure;
import org.jquantlib.experimental.commodities.UnitOfMeasureConversion;
import org.jquantlib.experimental.commodities.UnitOfMeasureConversionManager;
import org.jquantlib.lang.exceptions.LibraryException;
import org.junit.Test;

/**
 * Foundation-class unit tests for {@code experimental/commodities}. Mirror the
 * deterministic semantics of QuantLib v1.42.1
 * {@code ql/experimental/commodities/}.
 */
public class CommoditiesFoundationTest {

    private static final double EXACT = 0.0;
    private static final double TIGHT = 1.0e-12;

    @Test
    public void commodityType_codeAndName_areExposed() {
        final CommodityType ho = new CommodityType("HO", "Heating Oil");
        assertEquals("HO", ho.code());
        assertEquals("Heating Oil", ho.name());
        assertFalse(ho.empty());
    }

    @Test
    public void commodityType_default_isEmpty() {
        final CommodityType empty = new CommodityType();
        assertTrue(empty.empty());
        assertEquals("null commodity type", empty.toString());
    }

    @Test
    public void commodityType_sameCode_sharesData() {
        // Mirrors C++ static map keyed by code.
        final CommodityType a = new CommodityType("WTI", "WTI Crude Oil");
        final CommodityType b = new CommodityType("WTI", "Different Name Ignored");
        // both inspect the same underlying data record
        assertEquals(a.code(), b.code());
        assertEquals(a.name(), b.name());
    }

    @Test
    public void nullCommodityType_marker() {
        assertEquals("<NULL>", new NullCommodityType().code());
    }

    @Test
    public void unitOfMeasure_default_isEmpty() {
        assertTrue(new UnitOfMeasure().empty());
    }

    @Test
    public void barrelUnit_basics() {
        final UnitOfMeasure bbl = new BarrelUnitOfMeasure();
        assertEquals("BBL", bbl.code());
        assertEquals("Barrels", bbl.name());
        assertEquals(UnitOfMeasure.Type.Volume, bbl.unitType());
        assertTrue(bbl.triangulationUnitOfMeasure().empty());
    }

    @Test
    public void mbUnit_triangulationIsBarrel() {
        final UnitOfMeasure mb = new MBUnitOfMeasure();
        assertEquals("MB", mb.code());
        assertEquals(new BarrelUnitOfMeasure(), mb.triangulationUnitOfMeasure());
    }

    @Test
    public void quantity_constructionAndAccessors() {
        final CommodityType wti = new CommodityType("WTI", "WTI");
        final UnitOfMeasure bbl = new BarrelUnitOfMeasure();
        final Quantity q = new Quantity(wti, bbl, 100.0);
        assertEquals(100.0, q.amount(), EXACT);
        assertEquals(bbl, q.unitOfMeasure());
        assertEquals(wti, q.commodityType());
    }

    @Test
    public void quantity_arithmetic_sameUom() {
        final CommodityType wti = new CommodityType("WTI", "WTI");
        final UnitOfMeasure bbl = new BarrelUnitOfMeasure();
        final Quantity a = new Quantity(wti, bbl, 100.0);
        final Quantity b = new Quantity(wti, bbl, 25.0);
        // C++: a + b -> 125 BBL
        assertEquals(125.0, Quantity.plus(a, b).amount(), EXACT);
        // C++: a - b -> 75 BBL
        assertEquals(75.0, Quantity.minus(a, b).amount(), EXACT);
        // C++: a * 2 -> 200 BBL
        assertEquals(200.0, Quantity.times(a, 2.0).amount(), EXACT);
        // C++: 2 * a -> 200 BBL
        assertEquals(200.0, Quantity.times(2.0, a).amount(), EXACT);
        // C++: a / 4 -> 25 BBL
        assertEquals(25.0, Quantity.divide(a, 4.0).amount(), EXACT);
        // C++: a / b -> 4
        assertEquals(4.0, Quantity.divide(a, b), EXACT);
        // unary
        assertEquals(-100.0, a.negativeValue().amount(), EXACT);
    }

    @Test
    public void quantity_addAssign_diffUom_throws_byDefault() {
        Quantity.conversionType = Quantity.ConversionType.NoConversion;
        final CommodityType wti = new CommodityType("WTI", "WTI");
        final Quantity inBbl = new Quantity(wti, new BarrelUnitOfMeasure(), 1.0);
        final Quantity inGal = new Quantity(wti, new GallonUnitOfMeasure(), 1.0);
        try {
            Quantity.plus(inBbl, inGal);
            fail("expected LibraryException for UoM mismatch with NoConversion");
        } catch (final LibraryException expected) {
            // pass
        }
    }

    @Test
    public void quantity_compareOps() {
        final CommodityType wti = new CommodityType("WTI", "WTI");
        final UnitOfMeasure bbl = new BarrelUnitOfMeasure();
        final Quantity a = new Quantity(wti, bbl, 1.0);
        final Quantity b = new Quantity(wti, bbl, 2.0);
        assertTrue(Quantity.lt(a, b));
        assertTrue(Quantity.le(a, b));
        assertTrue(Quantity.gt(b, a));
        assertTrue(Quantity.ge(b, a));
        assertTrue(Quantity.ne(a, b));
        assertTrue(Quantity.eq(a, new Quantity(wti, bbl, 1.0)));
    }

    @Test
    public void uomConversion_directConvert() {
        // C++: 1 BBL = 42 GAL (registered in addKnownConversionFactors)
        final UnitOfMeasureConversion conv = new UnitOfMeasureConversion(
                new NullCommodityType(),
                new BarrelUnitOfMeasure(),
                new GallonUnitOfMeasure(),
                42.0);
        // 2 BBL -> 84 GAL
        final Quantity in2Bbl = new Quantity(new NullCommodityType(),
                new BarrelUnitOfMeasure(), 2.0);
        final Quantity out = conv.convert(in2Bbl);
        assertEquals("GAL", out.unitOfMeasure().code());
        assertEquals(84.0, out.amount(), TIGHT);

        // 84 GAL -> 2 BBL (reverse direction)
        final Quantity in84Gal = new Quantity(new NullCommodityType(),
                new GallonUnitOfMeasure(), 84.0);
        final Quantity back = conv.convert(in84Gal);
        assertEquals("BBL", back.unitOfMeasure().code());
        assertEquals(2.0, back.amount(), TIGHT);
    }

    @Test
    public void uomConversion_chainEndsAlign() {
        // Chain: GAL -> BBL (1/42) and BBL -> L (158.987)
        // Per C++ chain rule when r1.target == r2.source:
        //   chained source = r1.source = GAL
        //   chained target = r2.target = L
        //   chained factor = (1/42) * 158.987 = 3.785404761...
        final UnitOfMeasureConversion gal2Bbl = new UnitOfMeasureConversion(
                new NullCommodityType(),
                new GallonUnitOfMeasure(),
                new BarrelUnitOfMeasure(),
                1.0 / 42.0);
        final UnitOfMeasureConversion bbl2Litre = new UnitOfMeasureConversion(
                new NullCommodityType(),
                new BarrelUnitOfMeasure(),
                new LitreUnitOfMeasure(),
                158.987);
        final UnitOfMeasureConversion chained = UnitOfMeasureConversion.chain(gal2Bbl, bbl2Litre);
        assertEquals("GAL", chained.source().code());
        assertEquals("l", chained.target().code());
        assertEquals(158.987 / 42.0, chained.conversionFactor(), TIGHT);
    }

    @Test
    public void uomConversionManager_singletonAndKnownFactors() {
        final UnitOfMeasureConversionManager mgr = UnitOfMeasureConversionManager.getInstance();
        assertNotNull(mgr);
        // Direct lookup MB <-> BBL = 1000 (registered as a known factor)
        final UnitOfMeasureConversion mbToBbl = mgr.lookup(
                new NullCommodityType(),
                new MBUnitOfMeasure(),
                new BarrelUnitOfMeasure(),
                UnitOfMeasureConversion.Type.Direct);
        // Stored MB->BBL with factor 1000.
        assertEquals(1000.0, mbToBbl.conversionFactor(), EXACT);
    }

    @Test
    public void quantity_automatedConversion_endsToFirstOperandUom() {
        // Configure automated conversion so the first operand's UoM is preserved.
        // The manager's known conversion factors are registered against
        // NullCommodityType; per the C++ comment in
        // unitofmeasureconversionmanager.cpp there is intentionally no
        // fall-back from a real commodity type to NullCommodityType, so the
        // operand commodity types must also be NullCommodityType for this path.
        Quantity.conversionType = Quantity.ConversionType.AutomatedConversion;
        try {
            final CommodityType ct = new NullCommodityType();
            // 1 BBL + 42 GAL  ->  in BBL, that's 1 + 42*(1/42) = 2 BBL
            final Quantity oneBbl = new Quantity(ct, new BarrelUnitOfMeasure(), 1.0);
            final Quantity fortyTwoGal = new Quantity(ct, new GallonUnitOfMeasure(), 42.0);
            final Quantity sum = Quantity.plus(oneBbl, fortyTwoGal);
            assertEquals("BBL", sum.unitOfMeasure().code());
            assertEquals(2.0, sum.amount(), TIGHT);
        } finally {
            Quantity.conversionType = Quantity.ConversionType.NoConversion;
        }
    }

    @Test
    public void commoditySettings_defaultsToUSDandBarrel() {
        // Singleton initialised with US dollars and BBL per C++.
        assertEquals("USD", CommoditySettings.getInstance().currency().code());
        assertEquals("BBL", CommoditySettings.getInstance().unitOfMeasure().code());
    }

    @Test
    public void commodityUnitCost_constructionAndAccessors() {
        final CommodityUnitCost empty = new CommodityUnitCost();
        assertNotNull(empty.amount());
        assertNotNull(empty.unitOfMeasure());
    }

    @Test
    public void mtUnit_basics() {
        final UnitOfMeasure mt = new MTUnitOfMeasure();
        assertEquals("MT", mt.code());
        assertEquals(UnitOfMeasure.Type.Mass, mt.unitType());
    }
}
