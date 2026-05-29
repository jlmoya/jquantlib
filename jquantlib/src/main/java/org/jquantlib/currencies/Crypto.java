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

/*! \file crypto.hpp
    \brief Crypto currencies

    Data from http://fx.sauder.ubc.ca/currency_table.html
    and http://www.thefinancials.com/vortex/CurrencyFormats.html
*/

package org.jquantlib.currencies;

import org.jquantlib.math.Rounding;

/**
 * Crypto currencies.
 * <p>
 * Mirrors C++ QuantLib {@code ql/currencies/crypto.hpp} / {@code crypto.cpp}
 * (v1.42.1 @ 099987f0). Each crypto unit is sub-divided into 100000 fractions.
 *
 * @category currencies
 */
public class Crypto {

    /**
     * Bitcoin
     * <p>
     * The pseudo ISO three-letter code is BTC; the numeric code is 10000. It is divided in 100000 fractions.
     * See https://bitcoin.org/
     *
     * @category currencies
     */
    public static class BTCCurrency extends Currency {
        public BTCCurrency() {
            // C++ crypto.cpp:33
            Data btcData = new Data("Bitcoin", "BTC", 10000, "BTC", "", 100000, new Rounding(), "%3% %1$.2f");
            data = btcData;
        }
    }

    /**
     * Ethereum
     * <p>
     * The pseudo ISO three-letter code is ETH; the numeric code is 10001. It is divided in 100000 fractions.
     * See https://www.ethereum.org/
     *
     * @category currencies
     */
    public static class ETHCurrency extends Currency {
        public ETHCurrency() {
            // C++ crypto.cpp:41
            Data ethData = new Data("Ethereum", "ETH", 10001, "ETH", "", 100000, new Rounding(), "%3% %1$.2f");
            data = ethData;
        }
    }

    /**
     * Ethereum Classic
     * <p>
     * The pseudo ISO three-letter code is ETC; the numeric code is 10002. It is divided in 100000 fractions.
     * See https://ethereumclassic.github.io/
     *
     * @category currencies
     */
    public static class ETCCurrency extends Currency {
        public ETCCurrency() {
            // C++ crypto.cpp:49
            Data etcData = new Data("Ethereum Classic", "ETC", 10002, "ETC", "", 100000, new Rounding(), "%3% %1$.2f");
            data = etcData;
        }
    }

    /**
     * Bitcoin Cash
     * <p>
     * The pseudo ISO three-letter code is BCH; the numeric code is 10003. It is divided in 100000 fractions.
     * See https://www.bitcoincash.org/
     *
     * @category currencies
     */
    public static class BCHCurrency extends Currency {
        public BCHCurrency() {
            // C++ crypto.cpp:57
            Data bchData = new Data("Bitcoin Cash", "BCH", 10003, "BCH", "", 100000, new Rounding(), "%3% %1$.2f");
            data = bchData;
        }
    }

    /**
     * Ripple
     * <p>
     * The pseudo ISO three-letter code is XRP; the numeric code is 10004. It is divided in 100000 fractions.
     * See https://ripple.com/
     *
     * @category currencies
     */
    public static class XRPCurrency extends Currency {
        public XRPCurrency() {
            // C++ crypto.cpp:65
            Data xrpData = new Data("Ripple", "XRP", 10004, "XRP", "", 100000, new Rounding(), "%3% %1$.2f");
            data = xrpData;
        }
    }

    /**
     * Litecoin
     * <p>
     * The pseudo ISO three-letter code is LTC; the numeric code is 10005. It is divided in 100000 fractions.
     * See https://litecoin.com/
     *
     * @category currencies
     */
    public static class LTCCurrency extends Currency {
        public LTCCurrency() {
            // C++ crypto.cpp:73
            Data ltcData = new Data("Litecoin", "LTC", 10005, "LTC", "", 100000, new Rounding(), "%3% %1$.2f");
            data = ltcData;
        }
    }

    /**
     * Dash coin
     * <p>
     * The pseudo ISO three-letter code is DASH; the numeric code is 10006. It is divided in 100000 fractions.
     * See https://www.dash.org/
     *
     * @category currencies
     */
    public static class DASHCurrency extends Currency {
        public DASHCurrency() {
            // C++ crypto.cpp:81
            Data dashData = new Data("Dash coin", "DASH", 10006, "DASH", "", 100000, new Rounding(), "%3% %1$.2f");
            data = dashData;
        }
    }

    /**
     * Zcash
     * <p>
     * The pseudo ISO three-letter code is ZEC; the numeric code is 10007. It is divided in 100000 fractions.
     * See https://z.cash/
     *
     * @category currencies
     */
    public static class ZECCurrency extends Currency {
        public ZECCurrency() {
            // C++ crypto.cpp:89
            Data zecData = new Data("Zcash", "ZEC", 10007, "ZEC", "", 100000, new Rounding(), "%3% %1$.2f");
            data = zecData;
        }
    }

}
