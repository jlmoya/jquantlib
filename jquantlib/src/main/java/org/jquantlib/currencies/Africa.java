/*
 Copyright (C) 2009 Ueli Hofstetter

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
/*
 Copyright (C) 2004, 2005 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/
package org.jquantlib.currencies;

import org.jquantlib.math.Rounding;

public class Africa {
    /**
     * South-African rand
     *
     * The ISO three-letter code is ZAR; the numeric code is 710 It is divided in 100 cents.
     *
     * @category currencies
     */
    public static class ZARCurrency extends Currency {
        public ZARCurrency() {
            Data zarData = new Data("South-African rand", "ZAR", 710, "R", "", 100, new Rounding(), "%3% %1$.2f");
            data = zarData;
        }
    }

    /**
     * Angolan kwanza
     * <p>
     * The ISO three-letter code is AOA; the numeric code is 973. It is divided in 100 c&ecirc;ntimos.
     *
     * @category currencies
     */
    public static class AOACurrency extends Currency {
        public AOACurrency() {
            // C++ africa.cpp:28
            Data aoaData = new Data("Angolan kwanza", "AOA", 973, "AOA", "", 100, new Rounding(), "%3% %1$.2f");
            data = aoaData;
        }
    }

    /**
     * Botswanan pula
     * <p>
     * The ISO three-letter code is BWP; the numeric code is 72. It is divided in 100 thebe.
     *
     * @category currencies
     */
    public static class BWPCurrency extends Currency {
        public BWPCurrency() {
            // C++ africa.cpp:34
            Data bwpData = new Data("Botswanan pula", "BWP", 72, "P", "", 100, new Rounding(), "%3% %1$.2f");
            data = bwpData;
        }
    }

    /**
     * Egyptian pound
     * <p>
     * The ISO three-letter code is EGP; the numeric code is 818. It is divided in 100 piastres.
     *
     * @category currencies
     */
    public static class EGPCurrency extends Currency {
        public EGPCurrency() {
            // C++ africa.cpp:40
            Data egpData = new Data("Egyptian pound", "EGP", 818, "EGP", "", 100, new Rounding(), "%3% %1$.2f");
            data = egpData;
        }
    }

    /**
     * Ethiopian birr
     * <p>
     * The ISO three-letter code is ETB; the numeric code is 230. It is divided in 100 santim.
     *
     * @category currencies
     */
    public static class ETBCurrency extends Currency {
        public ETBCurrency() {
            // C++ africa.cpp:46
            Data etbData = new Data("Ethiopian birr", "ETB", 230, "ETB", "", 100, new Rounding(), "%3% %1$.2f");
            data = etbData;
        }
    }

    /**
     * Ghanaian cedi
     * <p>
     * The ISO three-letter code is GHS; the numeric code is 936. It is divided in 100 pesewas.
     *
     * @category currencies
     */
    public static class GHSCurrency extends Currency {
        public GHSCurrency() {
            // C++ africa.cpp:52
            Data ghsData = new Data("Ghanaian cedi", "GHS", 936, "GHS", "", 100, new Rounding(), "%3% %1$.2f");
            data = ghsData;
        }
    }

    /**
     * Kenyan shilling
     * <p>
     * The ISO three-letter code is KES; the numeric code is 404. It is divided in 100 cents.
     *
     * @category currencies
     */
    public static class KESCurrency extends Currency {
        public KESCurrency() {
            // C++ africa.cpp:58
            Data kesData = new Data("Kenyan shilling", "KES", 404, "KES", "", 100, new Rounding(), "%3% %1$.2f");
            data = kesData;
        }
    }

    /**
     * Moroccan dirham
     * <p>
     * The ISO three-letter code is MAD; the numeric code is 504. It is divided in 100 santimat.
     *
     * @category currencies
     */
    public static class MADCurrency extends Currency {
        public MADCurrency() {
            // C++ africa.cpp:64
            Data madData = new Data("Moroccan dirham", "MAD", 504, "MAD", "", 100, new Rounding(), "%3% %1$.2f");
            data = madData;
        }
    }

    /**
     * Mauritian rupee
     * <p>
     * The ISO three-letter code is MUR; the numeric code is 480. It is divided in 100 cents.
     *
     * @category currencies
     */
    public static class MURCurrency extends Currency {
        public MURCurrency() {
            // C++ africa.cpp:70
            Data murData = new Data("Mauritian rupee", "MUR", 480, "MUR", "", 100, new Rounding(), "%3% %1$.2f");
            data = murData;
        }
    }

    /**
     * Nigerian Naira
     * <p>
     * The ISO three-letter code is NGN; the numeric code is 566. It is divided in 100 kobo.
     *
     * @category currencies
     */
    public static class NGNCurrency extends Currency {
        public NGNCurrency() {
            // C++ africa.cpp:76
            Data ngnData = new Data("Nigerian Naira", "NGN", 566, "N", "K", 100, new Rounding(), "%3% %1$.2f");
            data = ngnData;
        }
    }

    /**
     * Tunisian dinar
     * <p>
     * The ISO three-letter code is TND; the numeric code is 788. It is divided in 1000 millimes.
     *
     * @category currencies
     */
    public static class TNDCurrency extends Currency {
        public TNDCurrency() {
            // C++ africa.cpp:82
            Data tndData = new Data("Tunisian dinar", "TND", 788, "TND", "", 1000, new Rounding(), "%3% %1$.2f");
            data = tndData;
        }
    }

    /**
     * Ugandan shilling
     * <p>
     * The ISO three-letter code is UGX; the numeric code is 800. It has no subdivisions.
     *
     * @category currencies
     */
    public static class UGXCurrency extends Currency {
        public UGXCurrency() {
            // C++ africa.cpp:88
            Data ugxData = new Data("Ugandan shilling", "UGX", 800, "UGX", "", 1, new Rounding(), "%3% %1$.2f");
            data = ugxData;
        }
    }

    /**
     * West African CFA franc
     * <p>
     * The ISO three-letter code is XOF; the numeric code is 952. It is divided in 100 centimes.
     *
     * @category currencies
     */
    public static class XOFCurrency extends Currency {
        public XOFCurrency() {
            // C++ africa.cpp:94
            Data xofData = new Data("West African CFA franc", "XOF", 952, "XOF", "", 100, new Rounding(), "%3% %1$.2f");
            data = xofData;
        }
    }

    /**
     * Zambian kwacha
     * <p>
     * The ISO three-letter code is ZMW; the numeric code is 967. It is divided in 100 ngwee.
     *
     * @category currencies
     */
    public static class ZMWCurrency extends Currency {
        public ZMWCurrency() {
            // C++ africa.cpp:106
            Data zmwData = new Data("Zambian kwacha", "ZMW", 967, "ZMW", "", 100, new Rounding(), "%3% %1$.2f");
            data = zmwData;
        }
    }

}
