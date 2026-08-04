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

/*! \file asia.hpp
    \brief Asian currencies

    Data from http://fx.sauder.ubc.ca/currency_table.html
    and http://www.thefinancials.com/vortex/CurrencyFormats.html
*/

package org.jquantlib.currencies;

import org.jquantlib.math.Rounding;

public class Asia {

    /**
     * Bangladesh taka
     * <p>
     * The ISO three-letter code is BDT; the numeric code is 50. It is divided in 100 paisa.
     *
     * @category currencies
     */
    public static class BDTCurrency extends Currency {
        public BDTCurrency() {
            Data bdtData = new Data("Bangladesh taka", "BDT", 50, "Bt", "", 100, new Rounding(), "%3% %1$.2f");
            data = bdtData;
        }
    }

    /**
     * Chinese yuan
     * <p>
     * The ISO three-letter code is CNY; the numeric code is 156. It is divided in 100 fen.
     *
     * @category currencies
     */
    public static class CNYCurrency extends Currency {
        public CNYCurrency() {
            Data cnyData = new Data("Chinese yuan", "CNY", 156, "Y", "", 100, new Rounding(), "%3% %1$.2f");
            data = cnyData;
        }
    }

    /**
     * Honk Kong dollar
     * <p>
     * The ISO three-letter code is HKD; the numeric code is 344. It is divided in 100 cents.
     *
     * @category currencies
     */
    public static class HKDCurrency extends Currency {
        public HKDCurrency() {
            Data hkdData = new Data("Honk Kong dollar", "HKD", 344, "HK$", "", 100, new Rounding(), "%3% %1$.2f");
            data = hkdData;
        }
    }

    /**
     * Israeli shekel
     * <p>
     * The ISO three-letter code is ILS; the numeric code is 376. It is divided in 100 agorot.
     *
     * @category currencies
     */
    public static class ILSCurrency extends Currency {
        public ILSCurrency() {
            Data ilsData = new Data("Israeli shekel", "ILS", 376, "NIS", "", 100, new Rounding(), "%1$.2f %3%");
            data = ilsData;
        }
    }

    /**
     * Indian rupee
     * <p>
     * The ISO three-letter code is INR; the numeric code is 356. It is divided in 100 paise.
     *
     * @category currencies
     */
    public static class INRCurrency extends Currency {
        public INRCurrency() {
            Data inrData = new Data("Indian rupee", "INR", 356, "Rs", "", 100, new Rounding(), "%3% %1$.2f");
            data = inrData;
        }
    }

    /**
     * Iraqi dinar
     * <p>
     * The ISO three-letter code is IQD; the numeric code is 368. It is divided in 100 fils.
     *
     * @category currencies
     */
    public static class IQDCurrency extends Currency {
        public IQDCurrency() {
            Data iqdData = new Data("Iraqi dinar", "IQD", 368, "ID", "", 1000, new Rounding(), "%2% %1$.3f");
            data = iqdData;
        }
    }

    /**
     * Iranian rial
     * <p>
     * The ISO three-letter code is IRR; the numeric code is 364. It has no subdivisions.
     *
     * @category currencies
     */
    public static class IRRCurrency extends Currency {
        public IRRCurrency() {
            Data irrData = new Data("Iranian rial", "IRR", 364, "Rls", "", 1, new Rounding(), "%3% %1$.2f");
            data = irrData;
        }
    }

    /**
     * Japanese yen
     * <p>
     * The ISO three-letter code is JPY; the numeric code is 392. It is divided in 100 sen.
     *
     * @category currencies
     */
    public static class JPYCurrency extends Currency {
        public JPYCurrency() {
            Data jpyData = new Data("Japanese yen", "JPY", 392, "\\xA5", "", 100, new Rounding(), "%3% %1$.0f");
            data = jpyData;
        }
    }

    /**
     * South-Korean won
     * <p>
     * The ISO three-letter code is KRW; the numeric code is 410. It is divided in 100 chon.
     *
     * @category currencies
     */
    public static class KRWCurrency extends Currency {
        public KRWCurrency() {
            Data krwData = new Data("South-Korean won", "KRW", 410, "W", "", 100, new Rounding(), "%3% %1$.0f");
            data = krwData;
        }
    }

    /**
     * Kuwaiti dinar
     * <p>
     * The ISO three-letter code is KWD; the numeric code is 414. It is divided in 100 fils.
     *
     * @category currencies
     */
    public static class KWDCurrency extends Currency {
        public KWDCurrency() {
            Data kwdData = new Data("Kuwaiti dinar", "KWD", 414, "KD", "", 1000, new Rounding(), "%3% %1$.3f");
            data = kwdData;
        }
    }

    /**
     * Nepal rupee
     * <p>
     * The ISO three-letter code is NPR; the numeric code is 524. It is divided in 100 paise.
     *
     * @category currencies
     */
    public static class NPRCurrency extends Currency {
        public NPRCurrency() {
            Data nprData = new Data("Nepal rupee", "NPR", 524, "NRs", "", 100, new Rounding(), "%3% %1$.2f");
            data = nprData;
        }
    }

    /**
     * Pakistani rupee
     * <p>
     * The ISO three-letter code is PKR; the numeric code is 586. It is divided in 100 paisa.
     *
     * @category currencies
     */
    public static class PKRCurrency extends Currency {
        public PKRCurrency() {
            Data pkrData = new Data("Pakistani rupee", "PKR", 586, "Rs", "", 100, new Rounding(), "%3% %1$.2f");
            data = pkrData;
        }
    }

    /**
     * Saudi riyal
     * <p>
     * The ISO three-letter code is SAR; the numeric code is 682. It is divided in 100 halalat.
     *
     * @category currencies
     */
    public static class SARCurrency extends Currency {
        public SARCurrency() {
            Data sarData = new Data("Saudi riyal", "SAR", 682, "SRls", "", 100, new Rounding(), "%3% %1$.2f");
            data = sarData;
        }
    }

    /**
     * Singapore dollar
     * <p>
     * The ISO three-letter code is SGD; the numeric code is 702. It is divided in 100 cents.
     *
     * @category currencies
     */
    public static class SGDCurrency extends Currency {
        public SGDCurrency() {
            Data sgdData = new Data("Singapore dollar", "SGD", 702, "S$", "", 100, new Rounding(), "%3% %1$.2f");
            data = sgdData;
        }
    }

    /**
     * Thai baht
     * <p>
     * The ISO three-letter code is THB; the numeric code is 764. It is divided in 100 stang.
     *
     * @category currencies
     */
    public static class THBCurrency extends Currency {
        public THBCurrency() {
            Data thbData = new Data("Thai baht", "THB", 764, "Bht", "", 100, new Rounding(), "%1$.2f %3%");
            data = thbData;
        }
    }

    /**
     * Taiwan dollar
     * <p>
     * The ISO three-letter code is TWD; the numeric code is 901. It is divided in 100 cents.
     *
     * @category currencies
     */
    public static class TWDCurrency extends Currency {
        public TWDCurrency() {
            Data twdData = new Data("Taiwan dollar", "TWD", 901, "NT$", "", 100, new Rounding(), "%3% %1$.2f");
            data = twdData;
        }
    }

    /**
     * Indonesian Rupiah
     * <p>
     * The ISO three-letter code is IDR; the numeric code is 360. It is divided in 100 sen.
     *
     * @category currencies
     */
    public static class IDRCurrency extends Currency {
        public IDRCurrency() {
            // C++ asia.cpp:62
            Data idrData = new Data("Indonesian Rupiah", "IDR", 360, "Rp", "", 100, new Rounding(), "%3% %1$.2f");
            data = idrData;
        }
    }

    /**
     * Kazakstani Tenge
     * <p>
     * The ISO three-letter code is KZT; the numeric code is 398. It is divided in 100 tijin.
     *
     * @category currencies
     */
    public static class KZTCurrency extends Currency {
        public KZTCurrency() {
            // C++ asia.cpp:134 (name "Kazakstanti Tenge" mirrors the C++ literal exactly)
            Data kztData = new Data("Kazakstanti Tenge", "KZT", 398, "Kzt", "", 100, new Rounding(), "%3% %1$.2f");
            data = kztData;
        }
    }

    /**
     * Malaysian Ringgit
     * <p>
     * The ISO three-letter code is MYR; the numeric code is 458. It is divided in 100 sen.
     *
     * @category currencies
     */
    public static class MYRCurrency extends Currency {
        public MYRCurrency() {
            // C++ asia.cpp:143
            Data myrData = new Data("Malaysian Ringgit", "MYR", 458, "RM", "", 100, new Rounding(), "%3% %1$.2f");
            data = myrData;
        }
    }

    /**
     * Vietnamese Dong
     * <p>
     * The ISO three-letter code is VND; the numeric code is 704. It was divided in 100 xu.
     *
     * @category currencies
     */
    public static class VNDCurrency extends Currency {
        public VNDCurrency() {
            // C++ asia.cpp:206
            Data vndData = new Data("Vietnamese Dong", "VND", 704, "", "", 100, new Rounding(), "%3% %1$.2f");
            data = vndData;
        }
    }

    /**
     * Qatari riyal
     * <p>
     * The ISO three-letter code is QAR; the numeric code is 634. It is divided in 100 dirhams.
     *
     * @category currencies
     */
    public static class QARCurrency extends Currency {
        public QARCurrency() {
            // C++ asia.cpp:212
            Data qarData = new Data("Qatari riyal", "QAR", 634, "QAR", "", 100, new Rounding(), "%3% %1$.2f");
            data = qarData;
        }
    }

    /**
     * Bahraini dinar
     * <p>
     * The ISO three-letter code is BHD; the numeric code is 48. It is divided in 1000 fils.
     *
     * @category currencies
     */
    public static class BHDCurrency extends Currency {
        public BHDCurrency() {
            // C++ asia.cpp:218
            Data bhdData = new Data("Bahraini dinar", "BHD", 48, "BHD", "", 1000, new Rounding(), "%3% %1$.2f");
            data = bhdData;
        }
    }

    /**
     * Omani rial
     * <p>
     * The ISO three-letter code is OMR; the numeric code is 512. It is divided in 1000 baisa.
     *
     * @category currencies
     */
    public static class OMRCurrency extends Currency {
        public OMRCurrency() {
            // C++ asia.cpp:224
            Data omrData = new Data("Omani rial", "OMR", 512, "OMR", "", 1000, new Rounding(), "%3% %1$.2f");
            data = omrData;
        }
    }

    /**
     * Jordanian dinar
     * <p>
     * The ISO three-letter code is JOD; the numeric code is 400. It is divided in 1000 fils.
     *
     * @category currencies
     */
    public static class JODCurrency extends Currency {
        public JODCurrency() {
            // C++ asia.cpp:230
            Data jodData = new Data("Jordanian dinar", "JOD", 400, "JOD", "", 1000, new Rounding(), "%3% %1$.2f");
            data = jodData;
        }
    }

    /**
     * United Arab Emirates dirham
     * <p>
     * The ISO three-letter code is AED; the numeric code is 784. It is divided in 100 fils.
     *
     * @category currencies
     */
    public static class AEDCurrency extends Currency {
        public AEDCurrency() {
            // C++ asia.cpp:236
            Data aedData = new Data("United Arab Emirates dirham", "AED", 784, "AED", "", 100, new Rounding(), "%3% %1$.2f");
            data = aedData;
        }
    }

    /**
     * Philippine peso
     * <p>
     * The ISO three-letter code is PHP; the numeric code is 608. It is divided in 100 centavos.
     *
     * @category currencies
     */
    public static class PHPCurrency extends Currency {
        public PHPCurrency() {
            // C++ asia.cpp:242
            Data phpData = new Data("Philippine peso", "PHP", 608, "PHP", "", 100, new Rounding(), "%3% %1$.2f");
            data = phpData;
        }
    }

    /**
     * Chinese yuan (Hong Kong)
     * <p>
     * The ISO three-letter code is CNH; the numeric code is 156. It is divided in 100 fen.
     *
     * @category currencies
     */
    public static class CNHCurrency extends Currency {
        public CNHCurrency() {
            // C++ asia.cpp:248
            Data cnhData = new Data("Chinese yuan (Hong Kong)", "CNH", 156, "CNH", "", 100, new Rounding(), "%3% %1$.2f");
            data = cnhData;
        }
    }

    /**
     * Sri Lankan rupee
     * <p>
     * The ISO three-letter code is LKR; the numeric code is 144. It is divided in 100 cents.
     *
     * @category currencies
     */
    public static class LKRCurrency extends Currency {
        public LKRCurrency() {
            // C++ asia.cpp:254
            Data lkrData = new Data("Sri Lankan rupee", "LKR", 144, "LKR", "", 100, new Rounding(), "%3% %1$.2f");
            data = lkrData;
        }
    }

    /**
     * Uzbekistani som. The ISO three-letter code is UZS; the numeric code is 860. It is divided in 100 tiyin.
     * <p>
     * New in C++ QuantLib v1.43, alongside the Uzbekistan calendar.
     *
     * @category currencies
     */
    public static class UZSCurrency extends Currency {
        public UZSCurrency() {
            final Data uzsData = new Data("Uzbekistani Som", "UZS", 860, "UZS", "", 100, new Rounding(),
                    "%3% %1$.2f");
            data = uzsData;
        }
    }
}
