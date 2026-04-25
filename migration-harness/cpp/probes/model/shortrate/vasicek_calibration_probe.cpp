// migration-harness/cpp/probes/model/shortrate/vasicek_calibration_probe.cpp
// Reference values for Vasicek discountBondOption fingerprint at three
// (strike, maturity, bondMaturity) tuples; verifies that the
// arguments_-indirection wiring carries through the Parameter system.

#include <ql/version.hpp>
#include <ql/models/shortrate/onefactormodels/vasicek.hpp>
#include "../../common.hpp"

#include <vector>
#include <tuple>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("model/shortrate/vasicek_calibration", QL_VERSION,
                        "vasicek_calibration_probe");

    const Real r0 = 0.05, a = 0.1, b = 0.05, sigma = 0.01, lambda = 0.0;
    Vasicek model(r0, a, b, sigma, lambda);

    json sampleArr = json::array();
    for (auto t : {std::make_tuple(0.95, 0.5, 1.0),
                   std::make_tuple(1.00, 1.0, 2.0),
                   std::make_tuple(1.05, 2.0, 5.0)}) {
        const Real strike = std::get<0>(t);
        const Time mat = std::get<1>(t);
        const Time bMat = std::get<2>(t);
        sampleArr.push_back({{"strike", strike}, {"maturity", mat},
                             {"bondMaturity", bMat},
                             {"call", model.discountBondOption(Option::Call, strike, mat, bMat)},
                             {"put",  model.discountBondOption(Option::Put,  strike, mat, bMat)}});
    }

    out.addCase("vasicek_round_trip",
        json{{"r0", r0}, {"a", a}, {"b", b},
             {"sigma", sigma}, {"lambda", lambda}},
        json{{"samples", sampleArr}});

    out.write();
    return 0;
}
