cmake_minimum_required(VERSION 3.20)
project(sqlitejdbc VERSION 3.53.4 LANGUAGES C)

add_library(sqlitejdbc SHARED
    src/main/c/sqlite3.c
    src/main/c/sqlite_jdbc.c
)
set_target_properties(sqlitejdbc PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED YES
    POSITION_INDEPENDENT_CODE YES
    VERSION ${PROJECT_VERSION}
    SOVERSION 0
    C_VISIBILITY_PRESET hidden
)

target_compile_definitions(sqlitejdbc PRIVATE
    SQLITE_THREADSAFE=1
    SQLITE_DEFAULT_MEMSTATUS=0
    SQLITE_DQS=0
    SQLITE_ENABLE_COLUMN_METADATA
    SQLITE_ENABLE_DBSTAT_VTAB
    SQLITE_ENABLE_FTS5
    SQLITE_ENABLE_MATH_FUNCTIONS
    SQLITE_ENABLE_RTREE
    SQLITE_OMIT_DEPRECATED
    SQLITE_OMIT_LOAD_EXTENSION
    SQLITE_USE_URI=1
)

if(MSVC)
    target_compile_options(sqlitejdbc PRIVATE /O2 /W4 /guard:cf)
    target_link_options(sqlitejdbc PRIVATE /guard:cf)
else()
    target_compile_definitions(sqlitejdbc PRIVATE _FORTIFY_SOURCE=2)
    target_compile_options(sqlitejdbc PRIVATE
        -O2
        -fstack-protector-strong
        -Wall
        -Wextra
        -Wno-implicit-fallthrough
        -Wno-unused-parameter
    )
    target_link_libraries(sqlitejdbc PRIVATE m)
    if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
        target_link_options(sqlitejdbc PRIVATE -Wl,-z,relro,-z,now)
    endif()
endif()


include(GNUInstallDirs)
install(TARGETS sqlitejdbc
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
    ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
)
