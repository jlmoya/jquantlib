// migration-harness/cpp/probes/math/matrixutilities/sparsematrix_probe.cpp
// Reference values for SparseMatrix (boost compressed_matrix) and
// SparseILUPreconditioner.  Phase 5b.5.
//
// Test strategy: cover the operations exercised by FdmLinearOpTest's
//   testBiCGstab, testGMRES, testSpareMatrixReference, testSparseMatrixZeroAssignment
// at small sizes to keep references compact.
//
// Tier: TIGHT (abs 1e-9, rel 1e-12) for ILU operations; EXACT for entry counts.

#include <ql/version.hpp>
#include <ql/math/matrixutilities/sparsematrix.hpp>
#include <ql/math/matrixutilities/sparseilupreconditioner.hpp>
#include <ql/math/array.hpp>
#include "../../common.hpp"

#include <cmath>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// 5x5 SPD tridiagonal — useful as a small reference for set/get + prod.
SparseMatrix small5x5SPD() {
    SparseMatrix A(5, 5);
    A(0, 0) =  5.0; A(0, 1) = -1.0;
    A(1, 0) = -1.0; A(1, 1) =  5.0; A(1, 2) = -1.0;
    A(2, 1) = -1.0; A(2, 2) =  5.0; A(2, 3) = -1.0;
    A(3, 2) = -1.0; A(3, 3) =  5.0; A(3, 4) = -1.0;
    A(4, 3) = -1.0; A(4, 4) =  5.0;
    return A;
}

// 5x5 asymmetric banded lower with explicit off-tridiagonal entries.
SparseMatrix small5x5Asym() {
    SparseMatrix A(5, 5);
    for (int i = 0; i < 5; ++i) {
        A(i, i) = i + 1.0;
        if (i > 0) A(i, i-1) = 0.5;
    }
    return A;
}

json sparseMatToJson(const SparseMatrix& A) {
    json j;
    j["rows"]    = static_cast<int>(A.size1());
    j["columns"] = static_cast<int>(A.size2());
    json entries = json::array();
    for (Size i = 0; i < A.size1(); ++i) {
        for (Size j2 = 0; j2 < A.size2(); ++j2) {
            const Real v = A(i, j2);
            if (std::fabs(v) > 0.0) {
                entries.push_back({{"i", static_cast<int>(i)},
                                   {"j", static_cast<int>(j2)},
                                   {"v", v}});
            }
        }
    }
    j["entries"] = entries;
    return j;
}

json arrayToJson(const Array& a) {
    json j = json::array();
    for (Size i = 0; i < a.size(); ++i) j.push_back(a[i]);
    return j;
}

} // anonymous namespace

int main() {
    ReferenceWriter out("math/matrixutilities/sparsematrix",
                        QL_VERSION,
                        "sparsematrix_probe");

    // ---- A * x for the 5x5 SPD ----
    {
        const SparseMatrix A = small5x5SPD();
        Array x = {1.0, 2.0, 3.0, 4.0, 5.0};
        Array y = prod(A, x);

        json inputs;
        inputs["matrix"] = sparseMatToJson(A);
        inputs["x"] = arrayToJson(x);

        json expected;
        expected["y"] = arrayToJson(y);
        out.addCase("prod_5x5_spd", inputs, expected);
    }

    // ---- A * x for the 5x5 asymmetric ----
    {
        const SparseMatrix A = small5x5Asym();
        Array x = {2.0, 1.0, 3.0, 1.0, 2.0};
        Array y = prod(A, x);

        json inputs;
        inputs["matrix"] = sparseMatToJson(A);
        inputs["x"] = arrayToJson(x);

        json expected;
        expected["y"] = arrayToJson(y);
        out.addCase("prod_5x5_asym", inputs, expected);
    }

    // ---- ILU(1) preconditioner factors L and U for the 5x5 SPD ----
    {
        const SparseMatrix A = small5x5SPD();
        SparseILUPreconditioner ilu(A, 1);

        json inputs;
        inputs["matrix"] = sparseMatToJson(A);
        inputs["lfil"] = 1;

        json expected;
        expected["L"] = sparseMatToJson(ilu.L());
        expected["U"] = sparseMatToJson(ilu.U());
        out.addCase("ilu1_5x5_spd_factors", inputs, expected);
    }

    // ---- ILU(1) apply on the 5x5 SPD ----
    {
        const SparseMatrix A = small5x5SPD();
        SparseILUPreconditioner ilu(A, 1);
        Array b = {4.0, 3.0, 3.0, 3.0, 4.0};   // matches solveResult in FdmLinearOp test path
        Array y = ilu.apply(b);

        json inputs;
        inputs["matrix"] = sparseMatToJson(A);
        inputs["b"] = arrayToJson(b);
        inputs["lfil"] = 1;

        json expected;
        expected["y"] = arrayToJson(y);
        out.addCase("ilu1_5x5_spd_apply", inputs, expected);
    }

    // ---- ILU(4) apply on the 5x5 SPD (matches test_BiCGStab/testGMRES lfil=4) ----
    {
        const SparseMatrix A = small5x5SPD();
        SparseILUPreconditioner ilu(A, 4);
        Array b = {4.0, 3.0, 3.0, 3.0, 4.0};
        Array y = ilu.apply(b);

        json inputs;
        inputs["matrix"] = sparseMatToJson(A);
        inputs["b"] = arrayToJson(b);
        inputs["lfil"] = 4;

        json expected;
        expected["y"] = arrayToJson(y);
        out.addCase("ilu4_5x5_spd_apply", inputs, expected);
    }

    // ---- ILU(1) apply on 5x5 asymmetric ----
    {
        const SparseMatrix A = small5x5Asym();
        SparseILUPreconditioner ilu(A, 1);
        // RHS chosen so that A*x = b for x = [1,1,1,1,1]
        Array b(5, 0.0);
        Array x = {1.0, 1.0, 1.0, 1.0, 1.0};
        for (Size i = 0; i < 5; ++i) {
            for (Size j = 0; j < 5; ++j) {
                b[i] += A(i, j) * x[j];
            }
        }
        Array y = ilu.apply(b);

        json inputs;
        inputs["matrix"] = sparseMatToJson(A);
        inputs["b"] = arrayToJson(b);
        inputs["lfil"] = 1;

        json expected;
        expected["y"] = arrayToJson(y);
        out.addCase("ilu1_5x5_asym_apply", inputs, expected);
    }

    // ---- testSparseMatrixZeroAssignment behavior ----
    // Mirrors fdmlinearop.cpp:1423 — record nrElementsOfSparseMatrix counts after
    // each set, including zero assignments.
    {
        SparseMatrix m(5, 5);
        // Helper to count entries the same way as nrElementsOfSparseMatrix().
        auto count = [](const SparseMatrix& mm) {
            Size c = 0;
            for (auto i1 = mm.begin1(); i1 != mm.end1(); ++i1) {
                c += std::distance(i1.begin(), i1.end());
            }
            return c;
        };

        json inputs = json::object();
        json expected;

        expected["count_initial"] = static_cast<int>(count(m));
        m(0, 0) = 0.0;
        m(1, 2) = 0.0;
        expected["count_after_two_zeros"] = static_cast<int>(count(m));
        m(1, 3) = 1.0;
        expected["count_after_one_value"] = static_cast<int>(count(m));
        m(1, 3) = 0.0;
        expected["count_after_overwrite_with_zero"] = static_cast<int>(count(m));

        out.addCase("zero_assignment", inputs, expected);
    }

    out.write();
    return 0;
}
