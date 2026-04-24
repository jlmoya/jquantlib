// migration-harness/cpp/probes/currencies/exchangerate_manager_probe.cpp
// Reference values for ExchangeRateManager behavior — captures v1.42.1
// output for direct lookup of populated known rates, custom-added rates,
// and single-entry lookup (the case where Java's fetch() incorrectly
// returns null for i == rates.size() - 1).

#include <ql/version.hpp>
#include <ql/currencies/exchangeratemanager.hpp>
#include <ql/currencies/europe.hpp>
#include <ql/currencies/america.hpp>
#include <ql/currencies/asia.hpp>
#include <ql/time/date.hpp>
#include "../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("currencies/exchangerate_manager", QL_VERSION, "exchangerate_manager_probe");

    auto& erm = ExchangeRateManager::instance();

    // Case 1: known-rate lookup — EUR/ATS was added by addKnownRates() at
    // construction, valid from 1-Jan-1999. Direct lookup on 1-Jan-2002
    // should return the fixed rate 13.7603.
    {
        ExchangeRate r = erm.lookup(EURCurrency(), ATSCurrency(),
                                    Date(1, January, 2002),
                                    ExchangeRate::Direct);
        out.addCase("knownRateEurAts",
                    json{{"source", "EUR"}, {"target", "ATS"},
                         {"date", "2002-01-01"}},
                    json(r.rate()));
    }

    // Case 2: add-then-direct-lookup — add a custom USD/EUR rate, look it
    // up via Direct on the same date. Must return the added rate exactly.
    // This case also exposes the Java fetch() bug: with a single entry in
    // the list, the buggy Java condition (i == size-1) rejects a valid
    // index-0 match.
    {
        erm.add(ExchangeRate(USDCurrency(), EURCurrency(), 0.92),
                Date(1, January, 2020), Date(31, December, 2020));
        ExchangeRate r = erm.lookup(USDCurrency(), EURCurrency(),
                                    Date(15, June, 2020),
                                    ExchangeRate::Direct);
        out.addCase("customAddedUsdEur",
                    json{{"source", "USD"}, {"target", "EUR"},
                         {"rate_added", 0.92},
                         {"date", "2020-06-15"}},
                    json(r.rate()));
    }

    // Case 3: multiple entries for the same pair — add two GBP/JPY rates
    // with non-overlapping date ranges; direct lookup must return the one
    // whose range contains the query date. This exposes the Java fetch()
    // bug differently: when the matching entry is at the end of the list
    // (i.e., the oldest entry), the buggy `i == size-1` branch returns
    // null. In C++, each non-overlapping query returns the correct rate.
    {
        erm.add(ExchangeRate(GBPCurrency(), JPYCurrency(), 150.0),
                Date(1, January, 2020), Date(31, December, 2020));
        erm.add(ExchangeRate(GBPCurrency(), JPYCurrency(), 140.0),
                Date(1, January, 2019), Date(31, December, 2019));
        // Lookup in 2019 range (second-added entry, but older date range)
        ExchangeRate r2019 = erm.lookup(GBPCurrency(), JPYCurrency(),
                                       Date(1, July, 2019),
                                       ExchangeRate::Direct);
        ExchangeRate r2020 = erm.lookup(GBPCurrency(), JPYCurrency(),
                                       Date(1, July, 2020),
                                       ExchangeRate::Direct);
        out.addCase("multipleEntries2019",
                    json{{"source", "GBP"}, {"target", "JPY"},
                         {"date", "2019-07-01"}},
                    json(r2019.rate()));
        out.addCase("multipleEntries2020",
                    json{{"source", "GBP"}, {"target", "JPY"},
                         {"date", "2020-07-01"}},
                    json(r2020.rate()));
    }

    // Case 4: clear() + addKnownRates — after clear(), the known rates
    // should still be available (C++ clear() re-populates).
    {
        erm.clear();
        ExchangeRate r = erm.lookup(EURCurrency(), DEMCurrency(),
                                    Date(1, January, 2001),
                                    ExchangeRate::Direct);
        out.addCase("knownRateEurDemAfterClear",
                    json{{"source", "EUR"}, {"target", "DEM"},
                         {"date", "2001-01-01"},
                         {"after", "clear()"}},
                    json(r.rate()));
    }

    out.write();
    return 0;
}
