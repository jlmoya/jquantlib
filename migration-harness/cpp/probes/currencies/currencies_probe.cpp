// migration-harness/cpp/probes/currencies/currencies_probe.cpp
// Cross-validation reference for the 42 currencies that exist in C++ QuantLib
// v1.42.1 but were previously missing in JQuantLib (see coverage-gaps.csv).
//
// Constructs each currency and emits its
//   (name, code, numericCode, symbol, fractionSymbol, fractionsPerUnit)
// as one JSON case. Currency Data is static literal data (no computation), so
// these values are the EXACT-tier ground truth consumed by
// org.jquantlib.testsuite.currencies.MissingCurrenciesTest.
//
// Auto-discovered by CMake (file(GLOB_RECURSE ... probes/*_probe.cpp)); no
// manual registration needed. Run via generate-references.sh from the harness
// root → references/currencies/missing_currencies.json.

#include <ql/version.hpp>
#include <ql/currencies/africa.hpp>
#include <ql/currencies/america.hpp>
#include <ql/currencies/asia.hpp>
#include <ql/currencies/europe.hpp>
#include <ql/currencies/crypto.hpp>
#include "../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {
void emit(ReferenceWriter& out, const Currency& c) {
    out.addCase(c.code(),
                json{{"code", c.code()}},
                json{{"name", c.name()},
                     {"code", c.code()},
                     {"numericCode", c.numericCode()},
                     {"symbol", c.symbol()},
                     {"fractionSymbol", c.fractionSymbol()},
                     {"fractionsPerUnit", c.fractionsPerUnit()}});
}
} // namespace

int main() {
    ReferenceWriter out("currencies/missing_currencies", QL_VERSION, "currencies_probe");

    // Africa (africa.cpp) — 13
    emit(out, AOACurrency());
    emit(out, BWPCurrency());
    emit(out, EGPCurrency());
    emit(out, ETBCurrency());
    emit(out, GHSCurrency());
    emit(out, KESCurrency());
    emit(out, MADCurrency());
    emit(out, MURCurrency());
    emit(out, NGNCurrency());
    emit(out, TNDCurrency());
    emit(out, UGXCurrency());
    emit(out, XOFCurrency());
    emit(out, ZMWCurrency());

    // America (america.cpp) — 4
    emit(out, MXVCurrency());
    emit(out, COUCurrency());
    emit(out, CLFCurrency());
    emit(out, UYUCurrency());

    // Asia / Mideast (asia.cpp) — 12
    emit(out, IDRCurrency());
    emit(out, KZTCurrency());
    emit(out, MYRCurrency());
    emit(out, VNDCurrency());
    emit(out, QARCurrency());
    emit(out, BHDCurrency());
    emit(out, OMRCurrency());
    emit(out, JODCurrency());
    emit(out, AEDCurrency());
    emit(out, PHPCurrency());
    emit(out, CNHCurrency());
    emit(out, LKRCurrency());

    // Europe (europe.cpp) — 5
    emit(out, UAHCurrency());
    emit(out, RSDCurrency());
    emit(out, HRKCurrency());
    emit(out, BGNCurrency());
    emit(out, GELCurrency());

    // Crypto (crypto.cpp) — 8
    emit(out, BTCCurrency());
    emit(out, ETHCurrency());
    emit(out, ETCCurrency());
    emit(out, BCHCurrency());
    emit(out, XRPCurrency());
    emit(out, LTCCurrency());
    emit(out, DASHCurrency());
    emit(out, ZECCurrency());

    // New in v1.43, alongside the North Macedonia and Uzbekistan calendars.
    // The calendars were ported and tested before their currencies existed,
    // which is exactly the gap the coverage audit caught.
    emit(out, MKDCurrency());
    emit(out, UZSCurrency());

    out.write();
    return 0;
}
