// migration-harness/cpp/probes/heston-slv/fdm_square_root_fwd_op_probe.cpp
//
// Reference values for FdmSquareRootFwdOp (Phase 5h.5-SLV).
//
// Apply, lowerBoundaryFactor, upperBoundaryFactor for the three transforms:
// Plain, Power, Log.

#include <ql/version.hpp>
#include <ql/methods/finitedifferences/operators/fdmsquarerootfwdop.hpp>
#include <ql/methods/finitedifferences/meshers/fdmmeshercomposite.hpp>
#include <ql/methods/finitedifferences/meshers/uniform1dmesher.hpp>
#include <ql/math/array.hpp>
#include <vector>
#include "../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

ext::shared_ptr<FdmMesher> makeVMesher(double vMin, double vMax, Size n) {
    auto m = ext::make_shared<Uniform1dMesher>(vMin, vMax, n);
    return ext::make_shared<FdmMesherComposite>(m);
}

json arrayToJson(const Array& a) {
    json j = json::array();
    for (Size i = 0; i < a.size(); ++i) j.push_back(a[i]);
    return j;
}

void addCases(ReferenceWriter& out,
              double kappa, double theta, double sigma,
              double vMin, double vMax, Size n,
              FdmSquareRootFwdOp::TransformationType type,
              const std::string& typeName) {
    auto mesher = makeVMesher(vMin, vMax, n);
    FdmSquareRootFwdOp op(mesher, kappa, theta, sigma, 0, type);

    // 1) lower / upper boundary factors
    {
        json inputs = {
            {"kappa", kappa}, {"theta", theta}, {"sigma", sigma},
            {"vMin", vMin}, {"vMax", vMax}, {"n", n},
            {"transform", typeName}
        };
        json expected = {
            {"lowerBoundaryFactor", op.lowerBoundaryFactor(type)},
            {"upperBoundaryFactor", op.upperBoundaryFactor(type)}
        };
        out.addCase(typeName + "_boundary_factors", inputs, expected);
    }

    // 2) apply on a few input vectors:
    //    a) constant vector (=1)
    //    b) linear v (v[i] = i / (n-1))
    //    c) gaussian-like exp(-2*(v-mid)^2)
    {
        Array p1(n, 1.0);
        Array p2(n);
        Array p3(n);
        const double mid = 0.5 * (vMin + vMax);
        for (Size i = 0; i < n; ++i) {
            const double v = mesher->locations(0)[i];
            p2[i] = v;
            p3[i] = std::exp(-2.0 * (v - mid) * (v - mid));
        }
        const Array r1 = op.apply(p1);
        const Array r2 = op.apply(p2);
        const Array r3 = op.apply(p3);
        json inputs = {
            {"kappa", kappa}, {"theta", theta}, {"sigma", sigma},
            {"vMin", vMin}, {"vMax", vMax}, {"n", n},
            {"transform", typeName}
        };
        json expected = {
            {"apply_constant", arrayToJson(r1)},
            {"apply_linear",   arrayToJson(r2)},
            {"apply_gauss",    arrayToJson(r3)}
        };
        out.addCase(typeName + "_apply", inputs, expected);
    }

    // 3) v(0), v(n), v(n+1) — ghost cells
    {
        json inputs = {
            {"kappa", kappa}, {"theta", theta}, {"sigma", sigma},
            {"vMin", vMin}, {"vMax", vMax}, {"n", n},
            {"transform", typeName}
        };
        json expected = {
            {"v0",   op.v(0)},
            {"vN",   op.v(n)},
            {"vNp1", op.v(n + 1)}
        };
        out.addCase(typeName + "_v_ghost", inputs, expected);
    }
}

} // namespace

int main() {
    ReferenceWriter out("heston-slv/fdm_square_root_fwd_op",
                        QL_VERSION,
                        "fdm_square_root_fwd_op_probe");

    // Cherry-pick parameters that satisfy the Feller condition (2*kappa*theta > sigma^2)
    // for the Power transformation to make physical sense:
    //   kappa=2.5, theta=0.04, sigma=0.2 → 2*2.5*0.04 = 0.20 > 0.04
    addCases(out, 2.5, 0.04, 0.2, 0.005, 0.5, 25, FdmSquareRootFwdOp::Plain, "plain");
    addCases(out, 2.5, 0.04, 0.2, 0.005, 0.5, 25, FdmSquareRootFwdOp::Power, "power");
    addCases(out, 2.5, 0.04, 0.2, std::log(0.005), std::log(0.5), 25,
             FdmSquareRootFwdOp::Log, "log");

    out.write();
    return 0;
}
