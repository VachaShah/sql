project(itodbc_execution)

# Source, headers, and include dirs
set(SOURCE_FILES test_odbc_execution.cpp)
include_directories(	${UT_HELPER}
						${IT_HELPER}
						${OPENSEARCHODBC_SRC}
						${VLD_SRC}  )

# Generate executable
add_executable(itodbc_execution ${SOURCE_FILES})

# Library dependencies
target_link_libraries(itodbc_execution sqlodbc itodbc_helper ut_helper gtest_main)
target_compile_definitions(itodbc_execution PUBLIC _UNICODE UNICODE)
