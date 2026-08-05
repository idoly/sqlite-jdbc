# SQLite native build

This module vendors the SQLite 3.50.4 amalgamation. `src/main/c/SQLITE_SHA256` records the checksum of the upstream ZIP used to populate the source files.

`sqlite_jdbc.c` is the exported ABI. SQLite's original `sqlite3_*` symbols are hidden; only the prefixed `sqlitejdbc_*` functions used by the FFM binding are visible. This avoids collisions when another native component in the same process embeds SQLite.

The Maven `package-native` profile attaches a platform classifier JAR when these properties are supplied:

```text
sqlite.native.platform
sqlite.native.inputDirectory
sqlite.native.inputFile
```

The JAR stores the mapped shared library under `META-INF/native/<platform-id>/`. Supported platform IDs and build commands are documented in the project README.
