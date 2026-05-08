cmake_minimum_required(VERSION 3.18)
project(jquantlib-migration-harness CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# --- QuantLib (as a subdirectory submodule) ---
# Disable QL's own examples/tests/benchmarks to keep our build lean.
set(QL_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(QL_BUILD_TEST_SUITE OFF CACHE BOOL "" FORCE)
set(QL_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
add_subdirectory(quantlib EXCLUDE_FROM_ALL)
# Alias the internal ql_library target to the namespaced form used by probes.
# add_subdirectory does not export QuantLib::QuantLib into parent scope; this does.
add_library(QuantLib::QuantLib ALIAS ql_library)

# --- Submodule SHA for reference-file provenance ---
# Capture the pinned QuantLib submodule's commit SHA at configure time so
# probes embed the actual pinned SHA in every reference JSON. Avoids a
# hardcoded constant in common.hpp that can silently drift from the pin.
execute_process(
    COMMAND git -C "${CMAKE_CURRENT_SOURCE_DIR}/quantlib" rev-parse HEAD
    OUTPUT_VARIABLE JQML_QL_COMMIT_SHA
    OUTPUT_STRIP_TRAILING_WHITESPACE
    RESULT_VARIABLE _ql_sha_result
)
if(NOT _ql_sha_result EQUAL 0)
    message(FATAL_ERROR "Failed to read QuantLib submodule SHA. Run `git submodule update --init --recursive` first.")
endif()
message(STATUS "QuantLib pinned SHA: ${JQML_QL_COMMIT_SHA}")

# --- Probes ---
# Each probe is a self-contained .cpp under probes/ (may be nested in subdirs).
# Output binaries go to build/probes/<name>_probe for easy scripting.

file(GLOB_RECURSE PROBE_SOURCES CONFIGURE_DEPENDS probes/*_probe.cpp)

foreach(src IN LISTS PROBE_SOURCES)
    get_filename_component(probe_name "${src}" NAME_WE)   # strips .cpp
    add_executable(${probe_name} "${src}")
    target_link_libraries(${probe_name} PRIVATE QuantLib::QuantLib)
    target_include_directories(${probe_name} PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party"
        "${CMAKE_CURRENT_SOURCE_DIR}/probes")
    target_compile_definitions(${probe_name} PRIVATE
        JQML_QL_COMMIT_SHA="${JQML_QL_COMMIT_SHA}")
    set_target_properties(${probe_name} PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/probes")
endforeach()

# --- Probe-specific extensions ---
# qint64_probe needs the qint64_shim.c compiled as C (to dodge the C++
# tokenizer rejection of pre-C99 string concatenation in qint.h's
# print_qint debug helper). Link the shim's .o into qint64_probe.
if(TARGET qint64_probe)
    enable_language(C)
    target_sources(qint64_probe PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/probes/transcendental/qint64_shim.c")
    set_source_files_properties(
        "${CMAKE_CURRENT_SOURCE_DIR}/probes/transcendental/qint64_shim.c"
        PROPERTIES LANGUAGE C COMPILE_FLAGS "-std=c11")
endif()

# pow_probe needs pow_shim.c (which includes pow.c via pow_shim_body.c, with
# pow_dint.h as the dint backend) compiled as C — same reasons as qint64_shim.
if(TARGET pow_probe)
    enable_language(C)
    target_sources(pow_probe PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/probes/transcendental/pow_shim.c")
    set_source_files_properties(
        "${CMAKE_CURRENT_SOURCE_DIR}/probes/transcendental/pow_shim.c"
        PROPERTIES LANGUAGE C COMPILE_FLAGS "-std=c11")
endif()

message(STATUS "Migration harness: ${CMAKE_CURRENT_SOURCE_DIR}")
message(STATUS "Probes discovered: ${PROBE_SOURCES}")
