// migration-harness/cpp/probes/math/matrixutilities/bicgstab_gmres_probe.cpp
// Reference values for BiCGStab and GMRES iterative linear solvers.
// Solves Ax = b for small dense systems with known solutions.
// Phase 2l Track A.
//
// Test strategy:
//   - 2x2, 3x3, 4x4, 5x5 SPD systems (diagonal + small perturbation)
//   - Asymmetric systems (GMRES handles; BiCGStab also handles non-symmetric)
//   - Near-singular (high condition number) to stress iteration counts
//   - Zero RHS -> trivial solution
//   - x0 initial guess provided vs zero start
//   - Preconditioning: diagonal (Jacobi) preconditioner for selected cases
//
// Tolerance: solver relTol=1e-10, report solution components to full double.
// Cross-validation tier: TIGHT (abs 1e-14 + rel 1e-12).

#include <ql/version.hpp>
#include <ql/math/matrixutilities/bicgstab.hpp>
#include <ql/math/matrixutilities/gmres.hpp>
#include <ql/math/matrix.hpp>
#include <ql/math/array.hpp>
#include "../../common.hpp"

#include <vector>
#include <cmath>
#include <functional>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// Dense matrix-vector product: y = A*x
// A stored row-major as vector<vector<double>>.
Array matvec(const std::vector<std::vector<double>>& A, const Array& x) {
    const Size n = x.size();
    Array y(n, 0.0);
    for (Size i = 0; i < n; ++i) {
        for (Size j = 0; j < n; ++j) {
            y[i] += A[i][j] * x[j];
        }
    }
    return y;
}

// Diagonal (Jacobi) preconditioner: M^{-1} * x = x_i / A_{ii}
Array diagPrecond(const std::vector<double>& diag, const Array& x) {
    const Size n = x.size();
    Array y(n, 0.0);
    for (Size i = 0; i < n; ++i) {
        y[i] = x[i] / diag[i];
    }
    return y;
}

// Build result JSON for a single solve
json solveResult(const std::vector<std::vector<double>>& A,
                 const Array& b,
                 const Array& xExpected,
                 bool withPrecond,
                 const std::string& solver) {
    const Size n = b.size();
    const Size maxIter = 500;
    const Real relTol  = 1.0e-10;

    // Extract diagonal for Jacobi preconditioner
    std::vector<double> diag(n);
    for (Size i = 0; i < n; ++i) diag[i] = A[i][i];

    auto Afunc = [&A](const Array& x) { return matvec(A, x); };
    auto Mfunc = [&diag](const Array& x) { return diagPrecond(diag, x); };

    json result;
    result["n"] = n;

    // solution components
    json xExpArr = json::array();
    for (Size i = 0; i < n; ++i) xExpArr.push_back(xExpected[i]);
    result["x_expected"] = xExpArr;

    if (solver == "bicgstab") {
        BiCGstab::MatrixMult Af = Afunc;
        BiCGstab::MatrixMult Mf = withPrecond ? BiCGstab::MatrixMult(Mfunc) : BiCGstab::MatrixMult();
        BiCGstab solver_obj(Af, maxIter, relTol, Mf);
        BiCGStabResult res = solver_obj.solve(b);
        result["iterations"] = static_cast<int>(res.iterations);
        result["error"]      = res.error;
        json xArr = json::array();
        for (Size i = 0; i < n; ++i) xArr.push_back(res.x[i]);
        result["x"] = xArr;
    } else {
        // gmres
        GMRES::MatrixMult Af = Afunc;
        GMRES::MatrixMult Mf = withPrecond ? GMRES::MatrixMult(Mfunc) : GMRES::MatrixMult();
        GMRES solver_obj(Af, maxIter, relTol, Mf);
        GMRESResult res = solver_obj.solve(b);
        json errArr = json::array();
        for (Real e : res.errors) errArr.push_back(e);
        result["errors"] = errArr;
        result["final_error"] = res.errors.back();
        json xArr = json::array();
        for (Size i = 0; i < n; ++i) xArr.push_back(res.x[i]);
        result["x"] = xArr;
    }
    return result;
}

} // anonymous namespace

// Helper: check if zero RHS gives trivial result (no solve needed)
static json zeroRhsResult(const std::string& solver) {
    std::vector<std::vector<double>> A = {{3.0,1.0},{1.0,2.0}};
    Array b(2, 0.0);
    Array xExact(2, 0.0);

    const Size n = 2;
    const Size maxIter = 500;
    const Real relTol  = 1.0e-10;
    auto Afunc = [&A](const Array& x) { return matvec(A, x); };

    json result;
    result["n"] = n;
    result["x_expected"] = json::array({0.0, 0.0});

    if (solver == "bicgstab") {
        BiCGstab s(Afunc, maxIter, relTol);
        BiCGStabResult res = s.solve(b);
        result["iterations"] = static_cast<int>(res.iterations);
        result["error"] = res.error;
        json xArr = json::array();
        for (Size i = 0; i < n; ++i) xArr.push_back(res.x[i]);
        result["x"] = xArr;
    } else {
        GMRES s(Afunc, maxIter, relTol);
        GMRESResult res = s.solve(b);
        result["final_error"] = res.errors.back();
        json xArr = json::array();
        for (Size i = 0; i < n; ++i) xArr.push_back(res.x[i]);
        result["x"] = xArr;
    }
    return result;
}

// With initial guess x0 (3x3 tridiagonal)
static json solveWithX0Result(const std::string& solver) {
    std::vector<std::vector<double>> A = {{4.0,-1.0,0.0},{-1.0,4.0,-1.0},{0.0,-1.0,4.0}};
    Array b = {3.0, 2.0, 3.0};
    Array xExact = {1.0, 1.0, 1.0};
    Array x0 = {0.5, 0.5, 0.5};

    const Size n = 3;
    const Size maxIter = 500;
    const Real relTol  = 1.0e-10;
    auto Afunc = [&A](const Array& x) { return matvec(A, x); };

    json result;
    result["n"] = n;
    json xExpArr = json::array();
    for (Size i = 0; i < n; ++i) xExpArr.push_back(xExact[i]);
    result["x_expected"] = xExpArr;

    if (solver == "bicgstab") {
        BiCGstab s(Afunc, maxIter, relTol);
        BiCGStabResult res = s.solve(b, x0);
        result["iterations"] = static_cast<int>(res.iterations);
        result["error"] = res.error;
        json xArr = json::array();
        for (Size i = 0; i < n; ++i) xArr.push_back(res.x[i]);
        result["x"] = xArr;
    } else {
        GMRES s(Afunc, maxIter, relTol);
        GMRESResult res = s.solve(b, x0);
        result["final_error"] = res.errors.back();
        json xArr = json::array();
        for (Size i = 0; i < n; ++i) xArr.push_back(res.x[i]);
        result["x"] = xArr;
    }
    return result;
}

// GMRES with restart
static json gmresWithRestartResult() {
    std::vector<std::vector<double>> A = {
        { 5.0,-1.0, 0.0, 0.0, 0.0},
        {-1.0, 5.0,-1.0, 0.0, 0.0},
        { 0.0,-1.0, 5.0,-1.0, 0.0},
        { 0.0, 0.0,-1.0, 5.0,-1.0},
        { 0.0, 0.0, 0.0,-1.0, 5.0}
    };
    Array b = {4.0, 3.0, 3.0, 3.0, 4.0};
    Array xExact = {1.0, 1.0, 1.0, 1.0, 1.0};

    const Size n = 5;
    const Size maxIter = 50;
    const Real relTol  = 1.0e-10;
    auto Afunc = [&A](const Array& x) { return matvec(A, x); };

    GMRES s(Afunc, maxIter, relTol);
    GMRESResult res = s.solveWithRestart(3, b);

    json result;
    result["n"] = n;
    json xExpArr = json::array();
    for (Size i = 0; i < n; ++i) xExpArr.push_back(xExact[i]);
    result["x_expected"] = xExpArr;
    result["final_error"] = res.errors.back();
    json xArr = json::array();
    for (Size i = 0; i < n; ++i) xArr.push_back(res.x[i]);
    result["x"] = xArr;
    return result;
}

int main() {
    ReferenceWriter out("math/matrixutilities/bicgstab_gmres",
                        QL_VERSION,
                        "bicgstab_gmres_probe");

    // ---- BiCGStab cases ----

    // 2x2 diagonal SPD: A=diag(2,3), x=[1,1], b=[2,3]
    {
        std::vector<std::vector<double>> A = {{2.0,0.0},{0.0,3.0}};
        Array b = {2.0, 3.0};
        Array xExact = {1.0, 1.0};
        out.addCase("bicgstab_2x2_diag", json{{"solver","bicgstab"},{"n",2}},
                    solveResult(A, b, xExact, false, "bicgstab"));
    }
    // 2x2 symmetric: A=[[4,1],[1,3]], x=[1,-1], b=[3,-2]
    {
        std::vector<std::vector<double>> A = {{4.0,1.0},{1.0,3.0}};
        Array b = {3.0, -2.0};
        Array xExact = {1.0, -1.0};
        out.addCase("bicgstab_2x2_sym", json{{"solver","bicgstab"},{"n",2}},
                    solveResult(A, b, xExact, false, "bicgstab"));
    }
    // 2x2 asymmetric: A=[[3,1],[0,2]], x=[2,1], b=[7,2]
    {
        std::vector<std::vector<double>> A = {{3.0,1.0},{0.0,2.0}};
        Array b = {7.0, 2.0};
        Array xExact = {2.0, 1.0};
        out.addCase("bicgstab_2x2_asym", json{{"solver","bicgstab"},{"n",2}},
                    solveResult(A, b, xExact, false, "bicgstab"));
    }
    // 3x3 diagonal SPD: A=diag(1,2,3), x=[3,2,1], b=[3,4,3]
    {
        std::vector<std::vector<double>> A = {{1.0,0.0,0.0},{0.0,2.0,0.0},{0.0,0.0,3.0}};
        Array b = {3.0, 4.0, 3.0};
        Array xExact = {3.0, 2.0, 1.0};
        out.addCase("bicgstab_3x3_diag", json{{"solver","bicgstab"},{"n",3}},
                    solveResult(A, b, xExact, false, "bicgstab"));
    }
    // 3x3 tridiagonal SPD: A=[[4,-1,0],[-1,4,-1],[0,-1,4]], x=[1,1,1], b=[3,2,3]
    {
        std::vector<std::vector<double>> A = {{4.0,-1.0,0.0},{-1.0,4.0,-1.0},{0.0,-1.0,4.0}};
        Array b = {3.0, 2.0, 3.0};
        Array xExact = {1.0, 1.0, 1.0};
        out.addCase("bicgstab_3x3_tridiag", json{{"solver","bicgstab"},{"n",3}},
                    solveResult(A, b, xExact, false, "bicgstab"));
    }
    // 3x3 tridiagonal SPD with Jacobi preconditioner
    {
        std::vector<std::vector<double>> A = {{4.0,-1.0,0.0},{-1.0,4.0,-1.0},{0.0,-1.0,4.0}};
        Array b = {3.0, 2.0, 3.0};
        Array xExact = {1.0, 1.0, 1.0};
        out.addCase("bicgstab_3x3_tridiag_precond", json{{"solver","bicgstab"},{"n",3},{"precond","jacobi"}},
                    solveResult(A, b, xExact, true, "bicgstab"));
    }
    // 3x3 upper triangular: A=[[2,1,0],[0,3,1],[0,0,4]], x=[1,2,3], b=[4,9,12]
    {
        std::vector<std::vector<double>> A = {{2.0,1.0,0.0},{0.0,3.0,1.0},{0.0,0.0,4.0}};
        Array b = {4.0, 9.0, 12.0};
        Array xExact = {1.0, 2.0, 3.0};
        out.addCase("bicgstab_3x3_uppertri", json{{"solver","bicgstab"},{"n",3}},
                    solveResult(A, b, xExact, false, "bicgstab"));
    }
    // 4x4 diagonal
    {
        std::vector<std::vector<double>> A = {{5.0,0.0,0.0,0.0},{0.0,4.0,0.0,0.0},{0.0,0.0,3.0,0.0},{0.0,0.0,0.0,2.0}};
        Array b = {5.0, 8.0, 9.0, 8.0};
        Array xExact = {1.0, 2.0, 3.0, 4.0};
        out.addCase("bicgstab_4x4_diag", json{{"solver","bicgstab"},{"n",4}},
                    solveResult(A, b, xExact, false, "bicgstab"));
    }
    // 4x4 SPD: A=[[10,1,0,0],[1,10,1,0],[0,1,10,1],[0,0,1,10]], x=[1,2,3,4], b=[12,24,36,43]
    {
        std::vector<std::vector<double>> A = {{10.0,1.0,0.0,0.0},{1.0,10.0,1.0,0.0},{0.0,1.0,10.0,1.0},{0.0,0.0,1.0,10.0}};
        Array b = {12.0, 24.0, 36.0, 43.0};
        Array xExact = {1.0, 2.0, 3.0, 4.0};
        out.addCase("bicgstab_4x4_spd", json{{"solver","bicgstab"},{"n",4}},
                    solveResult(A, b, xExact, false, "bicgstab"));
    }
    // 4x4 asymmetric: A=[[3,1,0,0],[0,2,1,0],[0,0,4,1],[0,0,0,5]], x=[1,2,3,4], b=[5,7,16,20]
    {
        std::vector<std::vector<double>> A = {{3.0,1.0,0.0,0.0},{0.0,2.0,1.0,0.0},{0.0,0.0,4.0,1.0},{0.0,0.0,0.0,5.0}};
        Array b = {5.0, 7.0, 16.0, 20.0};
        Array xExact = {1.0, 2.0, 3.0, 4.0};
        out.addCase("bicgstab_4x4_asym", json{{"solver","bicgstab"},{"n",4}},
                    solveResult(A, b, xExact, false, "bicgstab"));
    }
    // 5x5 diagonal: A=diag(1..5), x=[1,2,3,4,5], b=[1,4,9,16,25]
    {
        std::vector<std::vector<double>> A = {
            {1.0,0.0,0.0,0.0,0.0},{0.0,2.0,0.0,0.0,0.0},{0.0,0.0,3.0,0.0,0.0},
            {0.0,0.0,0.0,4.0,0.0},{0.0,0.0,0.0,0.0,5.0}};
        Array b = {1.0, 4.0, 9.0, 16.0, 25.0};
        Array xExact = {1.0, 2.0, 3.0, 4.0, 5.0};
        out.addCase("bicgstab_5x5_diag", json{{"solver","bicgstab"},{"n",5}},
                    solveResult(A, b, xExact, false, "bicgstab"));
    }
    // 5x5 SPD tridiagonal: x=[1,1,1,1,1], b=[4,3,3,3,4]
    {
        std::vector<std::vector<double>> A = {
            { 5.0,-1.0, 0.0, 0.0, 0.0},
            {-1.0, 5.0,-1.0, 0.0, 0.0},
            { 0.0,-1.0, 5.0,-1.0, 0.0},
            { 0.0, 0.0,-1.0, 5.0,-1.0},
            { 0.0, 0.0, 0.0,-1.0, 5.0}
        };
        Array b = {4.0, 3.0, 3.0, 3.0, 4.0};
        Array xExact = {1.0, 1.0, 1.0, 1.0, 1.0};
        out.addCase("bicgstab_5x5_spd", json{{"solver","bicgstab"},{"n",5}},
                    solveResult(A, b, xExact, false, "bicgstab"));
    }
    // 5x5 SPD tridiagonal with Jacobi preconditioner
    {
        std::vector<std::vector<double>> A = {
            { 5.0,-1.0, 0.0, 0.0, 0.0},
            {-1.0, 5.0,-1.0, 0.0, 0.0},
            { 0.0,-1.0, 5.0,-1.0, 0.0},
            { 0.0, 0.0,-1.0, 5.0,-1.0},
            { 0.0, 0.0, 0.0,-1.0, 5.0}
        };
        Array b = {4.0, 3.0, 3.0, 3.0, 4.0};
        Array xExact = {1.0, 1.0, 1.0, 1.0, 1.0};
        out.addCase("bicgstab_5x5_spd_precond", json{{"solver","bicgstab"},{"n",5},{"precond","jacobi"}},
                    solveResult(A, b, xExact, true, "bicgstab"));
    }
    // 5x5 asymmetric (banded lower): A[i][i]=i+1, A[i][i-1]=0.5
    // x=[2,1,3,1,2], b computed from A*x
    {
        std::vector<std::vector<double>> A(5, std::vector<double>(5, 0.0));
        for (int i = 0; i < 5; ++i) {
            A[i][i] = i+1;
            if (i > 0) A[i][i-1] = 0.5;
        }
        Array xExact = {2.0, 1.0, 3.0, 1.0, 2.0};
        Array b(5, 0.0);
        for (int i = 0; i < 5; ++i) {
            for (int j = 0; j < 5; ++j) b[i] += A[i][j] * xExact[j];
        }
        out.addCase("bicgstab_5x5_asym", json{{"solver","bicgstab"},{"n",5}},
                    solveResult(A, b, xExact, false, "bicgstab"));
    }
    // zero RHS
    out.addCase("bicgstab_zero_rhs", json{{"solver","bicgstab"},{"n",2}},
                zeroRhsResult("bicgstab"));
    // with x0
    out.addCase("bicgstab_with_x0", json{{"solver","bicgstab"},{"n",3}},
                solveWithX0Result("bicgstab"));

    // ---- GMRES cases ----

    // 2x2 diagonal SPD
    {
        std::vector<std::vector<double>> A = {{2.0,0.0},{0.0,3.0}};
        Array b = {2.0, 3.0};
        Array xExact = {1.0, 1.0};
        out.addCase("gmres_2x2_diag", json{{"solver","gmres"},{"n",2}},
                    solveResult(A, b, xExact, false, "gmres"));
    }
    // 2x2 symmetric
    {
        std::vector<std::vector<double>> A = {{4.0,1.0},{1.0,3.0}};
        Array b = {3.0, -2.0};
        Array xExact = {1.0, -1.0};
        out.addCase("gmres_2x2_sym", json{{"solver","gmres"},{"n",2}},
                    solveResult(A, b, xExact, false, "gmres"));
    }
    // 2x2 asymmetric
    {
        std::vector<std::vector<double>> A = {{3.0,1.0},{0.0,2.0}};
        Array b = {7.0, 2.0};
        Array xExact = {2.0, 1.0};
        out.addCase("gmres_2x2_asym", json{{"solver","gmres"},{"n",2}},
                    solveResult(A, b, xExact, false, "gmres"));
    }
    // 3x3 diagonal SPD
    {
        std::vector<std::vector<double>> A = {{1.0,0.0,0.0},{0.0,2.0,0.0},{0.0,0.0,3.0}};
        Array b = {3.0, 4.0, 3.0};
        Array xExact = {3.0, 2.0, 1.0};
        out.addCase("gmres_3x3_diag", json{{"solver","gmres"},{"n",3}},
                    solveResult(A, b, xExact, false, "gmres"));
    }
    // 3x3 tridiagonal SPD
    {
        std::vector<std::vector<double>> A = {{4.0,-1.0,0.0},{-1.0,4.0,-1.0},{0.0,-1.0,4.0}};
        Array b = {3.0, 2.0, 3.0};
        Array xExact = {1.0, 1.0, 1.0};
        out.addCase("gmres_3x3_tridiag", json{{"solver","gmres"},{"n",3}},
                    solveResult(A, b, xExact, false, "gmres"));
    }
    // 3x3 tridiagonal SPD with Jacobi preconditioner
    {
        std::vector<std::vector<double>> A = {{4.0,-1.0,0.0},{-1.0,4.0,-1.0},{0.0,-1.0,4.0}};
        Array b = {3.0, 2.0, 3.0};
        Array xExact = {1.0, 1.0, 1.0};
        out.addCase("gmres_3x3_tridiag_precond", json{{"solver","gmres"},{"n",3},{"precond","jacobi"}},
                    solveResult(A, b, xExact, true, "gmres"));
    }
    // 3x3 upper triangular
    {
        std::vector<std::vector<double>> A = {{2.0,1.0,0.0},{0.0,3.0,1.0},{0.0,0.0,4.0}};
        Array b = {4.0, 9.0, 12.0};
        Array xExact = {1.0, 2.0, 3.0};
        out.addCase("gmres_3x3_uppertri", json{{"solver","gmres"},{"n",3}},
                    solveResult(A, b, xExact, false, "gmres"));
    }
    // 4x4 diagonal
    {
        std::vector<std::vector<double>> A = {{5.0,0.0,0.0,0.0},{0.0,4.0,0.0,0.0},{0.0,0.0,3.0,0.0},{0.0,0.0,0.0,2.0}};
        Array b = {5.0, 8.0, 9.0, 8.0};
        Array xExact = {1.0, 2.0, 3.0, 4.0};
        out.addCase("gmres_4x4_diag", json{{"solver","gmres"},{"n",4}},
                    solveResult(A, b, xExact, false, "gmres"));
    }
    // 4x4 SPD
    {
        std::vector<std::vector<double>> A = {{10.0,1.0,0.0,0.0},{1.0,10.0,1.0,0.0},{0.0,1.0,10.0,1.0},{0.0,0.0,1.0,10.0}};
        Array b = {12.0, 24.0, 36.0, 43.0};
        Array xExact = {1.0, 2.0, 3.0, 4.0};
        out.addCase("gmres_4x4_spd", json{{"solver","gmres"},{"n",4}},
                    solveResult(A, b, xExact, false, "gmres"));
    }
    // 4x4 asymmetric
    {
        std::vector<std::vector<double>> A = {{3.0,1.0,0.0,0.0},{0.0,2.0,1.0,0.0},{0.0,0.0,4.0,1.0},{0.0,0.0,0.0,5.0}};
        Array b = {5.0, 7.0, 16.0, 20.0};
        Array xExact = {1.0, 2.0, 3.0, 4.0};
        out.addCase("gmres_4x4_asym", json{{"solver","gmres"},{"n",4}},
                    solveResult(A, b, xExact, false, "gmres"));
    }
    // 5x5 diagonal
    {
        std::vector<std::vector<double>> A = {
            {1.0,0.0,0.0,0.0,0.0},{0.0,2.0,0.0,0.0,0.0},{0.0,0.0,3.0,0.0,0.0},
            {0.0,0.0,0.0,4.0,0.0},{0.0,0.0,0.0,0.0,5.0}};
        Array b = {1.0, 4.0, 9.0, 16.0, 25.0};
        Array xExact = {1.0, 2.0, 3.0, 4.0, 5.0};
        out.addCase("gmres_5x5_diag", json{{"solver","gmres"},{"n",5}},
                    solveResult(A, b, xExact, false, "gmres"));
    }
    // 5x5 SPD tridiagonal
    {
        std::vector<std::vector<double>> A = {
            { 5.0,-1.0, 0.0, 0.0, 0.0},
            {-1.0, 5.0,-1.0, 0.0, 0.0},
            { 0.0,-1.0, 5.0,-1.0, 0.0},
            { 0.0, 0.0,-1.0, 5.0,-1.0},
            { 0.0, 0.0, 0.0,-1.0, 5.0}
        };
        Array b = {4.0, 3.0, 3.0, 3.0, 4.0};
        Array xExact = {1.0, 1.0, 1.0, 1.0, 1.0};
        out.addCase("gmres_5x5_spd", json{{"solver","gmres"},{"n",5}},
                    solveResult(A, b, xExact, false, "gmres"));
    }
    // 5x5 SPD tridiagonal with Jacobi preconditioner
    {
        std::vector<std::vector<double>> A = {
            { 5.0,-1.0, 0.0, 0.0, 0.0},
            {-1.0, 5.0,-1.0, 0.0, 0.0},
            { 0.0,-1.0, 5.0,-1.0, 0.0},
            { 0.0, 0.0,-1.0, 5.0,-1.0},
            { 0.0, 0.0, 0.0,-1.0, 5.0}
        };
        Array b = {4.0, 3.0, 3.0, 3.0, 4.0};
        Array xExact = {1.0, 1.0, 1.0, 1.0, 1.0};
        out.addCase("gmres_5x5_spd_precond", json{{"solver","gmres"},{"n",5},{"precond","jacobi"}},
                    solveResult(A, b, xExact, true, "gmres"));
    }
    // 5x5 asymmetric (banded lower)
    {
        std::vector<std::vector<double>> A(5, std::vector<double>(5, 0.0));
        for (int i = 0; i < 5; ++i) {
            A[i][i] = i+1;
            if (i > 0) A[i][i-1] = 0.5;
        }
        Array xExact = {2.0, 1.0, 3.0, 1.0, 2.0};
        Array b(5, 0.0);
        for (int i = 0; i < 5; ++i) {
            for (int j = 0; j < 5; ++j) b[i] += A[i][j] * xExact[j];
        }
        out.addCase("gmres_5x5_asym", json{{"solver","gmres"},{"n",5}},
                    solveResult(A, b, xExact, false, "gmres"));
    }
    // zero RHS
    out.addCase("gmres_zero_rhs", json{{"solver","gmres"},{"n",2}},
                zeroRhsResult("gmres"));
    // with x0
    out.addCase("gmres_with_x0", json{{"solver","gmres"},{"n",3}},
                solveWithX0Result("gmres"));
    // solveWithRestart
    out.addCase("gmres_with_restart", json{{"solver","gmres"},{"n",5},{"restart",3}},
                gmresWithRestartResult());

    out.write();
    return 0;
}
