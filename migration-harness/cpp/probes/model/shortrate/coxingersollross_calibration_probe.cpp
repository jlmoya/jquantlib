// migration-harness/cpp/probes/model/shortrate/coxingersollross_calibration_probe.cpp
// Reference values for CoxIngersollRoss discountBond fingerprint
// across three (t, T, r0) tuples; verifies the arguments_-indirection
// wiring carries through the Parameter system. Uses discountBond
// (closed-form A(t,T)*exp(-B(t,T)*r0)) which depends only on Math
// arithmetic; avoids NonCentralChiSquaredDistribution divergence.

#include <ql/version.hpp>
#include <ql/models/shortrate/onefactormodels/coxingersollross.hpp>
#include "../../common.hpp"

#include <vector>
#include <tuple>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("model/shortrate/coxingersollross_calibration", QL_VERSION,
                        "coxingersollross_calibration_probe");

    const Real r0 = 0.05, theta = 0.06, k = 0.5, sigma = 0.04;
    CoxIngersollRoss model(r0, theta, k, sigma);

    json sampleArr = json::array();
    // Three (t, T) pairs; CIR.discountBond(t, T, rate) is the affine
    // form, fully closed-form via A(t,T) and B(t,T).
    for (auto pair : {std::make_tuple(0.0, 1.0),
                      std::make_tuple(0.5, 2.0),
                      std::make_tuple(1.0, 5.0)}) {
        const Time t = std::get<0>(pair);
        const Time T = std::get<1>(pair);
        sampleArr.push_back({{"t", t}, {"T", T},
                             {"discountBond", model.discountBond(t, T, r0)}});
    }

    out.addCase("cir_discountbond_fingerprint",
        json{{"r0", r0}, {"theta", theta}, {"k", k}, {"sigma", sigma}},
        json{{"samples", sampleArr}});

    out.write();
    return 0;
}
