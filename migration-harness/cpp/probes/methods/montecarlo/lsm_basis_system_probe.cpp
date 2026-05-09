// migration-harness/cpp/probes/methods/montecarlo/lsm_basis_system_probe.cpp
// Phase 5h.5-MC — emit C++ v1.42.1 LsmBasisSystem reference values for
// every PolynomialType (Monomial, Laguerre, Hermite, Hyperbolic, Legendre,
// Chebyshev, Chebyshev2nd) at order=2..4 and several x values, so the
// Java port can verify weightedValue() bit-equivalence.
//
// Multi-path basis system is also probed at dim=2,3 with order=2 to verify
// tuple ordering and product evaluation.

#include <ql/version.hpp>
#include <ql/methods/montecarlo/lsmbasissystem.hpp>
#include <ql/math/array.hpp>
#include "../../common.hpp"

#include <cmath>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

const char* type_name(LsmBasisSystem::PolynomialType t) {
    switch (t) {
      case LsmBasisSystem::Monomial:    return "Monomial";
      case LsmBasisSystem::Laguerre:    return "Laguerre";
      case LsmBasisSystem::Hermite:     return "Hermite";
      case LsmBasisSystem::Hyperbolic:  return "Hyperbolic";
      case LsmBasisSystem::Legendre:    return "Legendre";
      case LsmBasisSystem::Chebyshev:   return "Chebyshev";
      case LsmBasisSystem::Chebyshev2nd: return "Chebyshev2nd";
    }
    return "?";
}

void emitPathBasis(ReferenceWriter& out, LsmBasisSystem::PolynomialType type, Size order) {
    auto basis = LsmBasisSystem::pathBasisSystem(order, type);
    // sample x in [-1,1] for Legendre/Chebyshev (where w(x) is defined);
    // for Laguerre x>=0; Hermite/Monomial/Hyperbolic accept any real.
    std::vector<double> xs;
    if (type == LsmBasisSystem::Legendre || type == LsmBasisSystem::Chebyshev || type == LsmBasisSystem::Chebyshev2nd) {
        xs = {-0.9, -0.4, 0.0, 0.4, 0.9};
    } else if (type == LsmBasisSystem::Laguerre) {
        xs = {0.1, 0.5, 1.0, 2.5, 5.0};
    } else { // Monomial, Hermite, Hyperbolic
        xs = {-1.5, -0.5, 0.0, 0.5, 1.5};
    }

    json values = json::array();
    for (double x : xs) {
        json row = json::object();
        row["x"] = x;
        json row_vals = json::array();
        for (Size i = 0; i < basis.size(); ++i) {
            row_vals.push_back(basis[i](x));
        }
        row["values"] = row_vals;
        values.push_back(row);
    }
    json inputs = json::object();
    inputs["type"] = type_name(type);
    inputs["order"] = order;
    out.addCase(std::string("path_") + type_name(type) + "_order" + std::to_string(order),
                inputs, json{{"size", basis.size()}, {"rows", values}});
}

void emitMultiPathBasis(ReferenceWriter& out, LsmBasisSystem::PolynomialType type,
                        Size dim, Size order) {
    auto basis = LsmBasisSystem::multiPathBasisSystem(dim, order, type);

    // Build a small set of test arrays of length dim
    std::vector<std::vector<double>> arrays;
    if (dim == 2) {
        arrays = {{0.5, 0.5}, {-0.5, 0.4}, {0.7, -0.3}};
    } else if (dim == 3) {
        arrays = {{0.5, 0.4, 0.3}, {-0.5, 0.4, 0.2}};
    } else {
        arrays = {std::vector<double>(dim, 0.5)};
    }

    json values = json::array();
    for (const auto& arr : arrays) {
        Array a(arr.begin(), arr.end());
        json row = json::object();
        json xs = json::array();
        for (double v : arr) xs.push_back(v);
        row["x"] = xs;
        json row_vals = json::array();
        for (Size i = 0; i < basis.size(); ++i) {
            row_vals.push_back(basis[i](a));
        }
        row["values"] = row_vals;
        values.push_back(row);
    }
    json inputs = json::object();
    inputs["type"] = type_name(type);
    inputs["dim"] = dim;
    inputs["order"] = order;
    out.addCase(std::string("multi_") + type_name(type)
                + "_dim" + std::to_string(dim)
                + "_order" + std::to_string(order),
                inputs, json{{"size", basis.size()}, {"rows", values}});
}

} // namespace

int main() {
    ReferenceWriter out("methods/montecarlo/lsm_basis_system", QL_VERSION,
                        "lsm_basis_system_probe");

    // Path basis: every type at order=2,3,4
    LsmBasisSystem::PolynomialType types[] = {
        LsmBasisSystem::Monomial,
        LsmBasisSystem::Laguerre,
        LsmBasisSystem::Hermite,
        LsmBasisSystem::Hyperbolic,
        LsmBasisSystem::Legendre,
        LsmBasisSystem::Chebyshev,
        LsmBasisSystem::Chebyshev2nd
    };
    for (auto t : types) {
        for (Size order = 2; order <= 4; ++order) {
            emitPathBasis(out, t, order);
        }
    }

    // Multi-path: just Monomial and Hermite at dim=2 order=2 and dim=3 order=2 (suffice for tuple-order verification)
    emitMultiPathBasis(out, LsmBasisSystem::Monomial, 2, 2);
    emitMultiPathBasis(out, LsmBasisSystem::Hermite, 2, 2);
    emitMultiPathBasis(out, LsmBasisSystem::Monomial, 3, 2);

    out.write();
    return 0;
}
