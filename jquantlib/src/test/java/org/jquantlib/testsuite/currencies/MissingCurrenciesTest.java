/*
 Copyright (C) 2026 JQuantLib migration

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
*/
package org.jquantlib.testsuite.currencies;

import static org.junit.Assert.assertEquals;

import org.jquantlib.currencies.Africa;
import org.jquantlib.currencies.America;
import org.jquantlib.currencies.Asia;
import org.jquantlib.currencies.Crypto;
import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.Europe;
import org.junit.Test;

/**
 * Cross-validation of the 42 currencies that exist in C++ QuantLib v1.42.1
 * (@099987f0) but were previously missing in JQuantLib.
 * <p>
 * Expected values are transcribed verbatim from the C++ {@code Data(...)}
 * literals in {@code ql/currencies/{africa,america,asia,europe,crypto}.cpp}.
 * Currency {@code Data} is static literal data (no computation), so the EXACT
 * tier applies: name/ISO-code/numeric-code/fractionsPerUnit must match the C++
 * source bit-for-bit. Each {@code check(...)} comment cites the C++ origin line.
 */
public class MissingCurrenciesTest {

    /** Asserts the four EXACT-tier scalar fields of a currency against C++ literals. */
    private static void check(final Currency c, final String name, final String code,
            final int numeric, final int fractionsPerUnit) {
        assertEquals("name", name, c.name());
        assertEquals("code", code, c.code());
        assertEquals("numericCode", numeric, c.numericCode());
        assertEquals("fractionsPerUnit", fractionsPerUnit, c.fractionsPerUnit());
    }

    @Test
    public void testAfricaMissingCurrencies() {
        check(new Africa.AOACurrency(), "Angolan kwanza", "AOA", 973, 100);          // africa.cpp:28
        check(new Africa.BWPCurrency(), "Botswanan pula", "BWP", 72, 100);           // africa.cpp:34
        check(new Africa.EGPCurrency(), "Egyptian pound", "EGP", 818, 100);          // africa.cpp:40
        check(new Africa.ETBCurrency(), "Ethiopian birr", "ETB", 230, 100);          // africa.cpp:46
        check(new Africa.GHSCurrency(), "Ghanaian cedi", "GHS", 936, 100);           // africa.cpp:52
        check(new Africa.KESCurrency(), "Kenyan shilling", "KES", 404, 100);         // africa.cpp:58
        check(new Africa.MADCurrency(), "Moroccan dirham", "MAD", 504, 100);         // africa.cpp:64
        check(new Africa.MURCurrency(), "Mauritian rupee", "MUR", 480, 100);         // africa.cpp:70
        check(new Africa.NGNCurrency(), "Nigerian Naira", "NGN", 566, 100);          // africa.cpp:76
        check(new Africa.TNDCurrency(), "Tunisian dinar", "TND", 788, 1000);         // africa.cpp:82
        check(new Africa.UGXCurrency(), "Ugandan shilling", "UGX", 800, 1);          // africa.cpp:88
        check(new Africa.XOFCurrency(), "West African CFA franc", "XOF", 952, 100);  // africa.cpp:94
        check(new Africa.ZMWCurrency(), "Zambian kwacha", "ZMW", 967, 100);          // africa.cpp:106
    }

    @Test
    public void testAmericaMissingCurrencies() {
        check(new America.MXVCurrency(), "Mexican Unidad de Inversion", "MXV", 979, 1);                  // america.cpp:142
        check(new America.COUCurrency(), "Unidad de Valor Real (UVR) (funds code)", "COU", 970, 100);    // america.cpp:148
        check(new America.CLFCurrency(), "Unidad de Fomento (funds code)", "CLF", 990, 1);               // america.cpp:154
        check(new America.UYUCurrency(), "Uruguayan peso", "UYU", 858, 1);                               // america.cpp:160
    }

    @Test
    public void testAsiaMissingCurrencies() {
        check(new Asia.IDRCurrency(), "Indonesian Rupiah", "IDR", 360, 100);                 // asia.cpp:62
        check(new Asia.KZTCurrency(), "Kazakstanti Tenge", "KZT", 398, 100);                 // asia.cpp:134 (C++ literal typo preserved)
        check(new Asia.MYRCurrency(), "Malaysian Ringgit", "MYR", 458, 100);                 // asia.cpp:143
        check(new Asia.VNDCurrency(), "Vietnamese Dong", "VND", 704, 100);                   // asia.cpp:206
        check(new Asia.QARCurrency(), "Qatari riyal", "QAR", 634, 100);                      // asia.cpp:212
        check(new Asia.BHDCurrency(), "Bahraini dinar", "BHD", 48, 1000);                    // asia.cpp:218
        check(new Asia.OMRCurrency(), "Omani rial", "OMR", 512, 1000);                       // asia.cpp:224
        check(new Asia.JODCurrency(), "Jordanian dinar", "JOD", 400, 1000);                  // asia.cpp:230
        check(new Asia.AEDCurrency(), "United Arab Emirates dirham", "AED", 784, 100);       // asia.cpp:236
        check(new Asia.PHPCurrency(), "Philippine peso", "PHP", 608, 100);                   // asia.cpp:242
        check(new Asia.CNHCurrency(), "Chinese yuan (Hong Kong)", "CNH", 156, 100);          // asia.cpp:248
        check(new Asia.LKRCurrency(), "Sri Lankan rupee", "LKR", 144, 100);                  // asia.cpp:254
    }

    @Test
    public void testEuropeMissingCurrencies() {
        check(new Europe.UAHCurrency(), "Ukrainian hryvnia", "UAH", 980, 100);   // europe.cpp:362
        check(new Europe.RSDCurrency(), "Serbian dinar", "RSD", 941, 100);       // europe.cpp:368
        check(new Europe.HRKCurrency(), "Croatian kuna", "HRK", 191, 100);       // europe.cpp:374
        check(new Europe.BGNCurrency(), "Bulgarian lev", "BGN", 975, 100);       // europe.cpp:380
        check(new Europe.GELCurrency(), "Georgian lari", "GEL", 981, 100);       // europe.cpp:386
    }

    @Test
    public void testCryptoCurrencies() {
        check(new Crypto.BTCCurrency(), "Bitcoin", "BTC", 10000, 100000);            // crypto.cpp:33
        check(new Crypto.ETHCurrency(), "Ethereum", "ETH", 10001, 100000);           // crypto.cpp:41
        check(new Crypto.ETCCurrency(), "Ethereum Classic", "ETC", 10002, 100000);   // crypto.cpp:49
        check(new Crypto.BCHCurrency(), "Bitcoin Cash", "BCH", 10003, 100000);       // crypto.cpp:57
        check(new Crypto.XRPCurrency(), "Ripple", "XRP", 10004, 100000);             // crypto.cpp:65
        check(new Crypto.LTCCurrency(), "Litecoin", "LTC", 10005, 100000);           // crypto.cpp:73
        check(new Crypto.DASHCurrency(), "Dash coin", "DASH", 10006, 100000);        // crypto.cpp:81
        check(new Crypto.ZECCurrency(), "Zcash", "ZEC", 10007, 100000);              // crypto.cpp:89
    }
}
