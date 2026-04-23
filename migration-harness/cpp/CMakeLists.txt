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
    set_target_properties(${probe_name} PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/probes")
endforeach()

message(STATUS "Migration harness: ${CMAKE_CURRENT_SOURCE_DIR}")
message(STATUS "Probes discovered: ${PROBE_SOURCES}")
