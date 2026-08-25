
include VERSION

TARGET := target
PLATFORM_CLASS := io.github.idoly.sqlite.ffm.NativePlatform
PLATFORM_TOOL_DIR := $(TARGET)/tools
PLATFORM_PROG := $(PLATFORM_TOOL_DIR)/io/github/idoly/sqlite/ffm/NativePlatform.class

ifndef OS_NAME
NEED_PLATFORM := 1
endif
ifndef OS_ARCH
NEED_PLATFORM := 1
endif

ifdef NEED_PLATFORM
ifndef JAVA_HOME
$(error JAVA_HOME must point to JDK 25 or newer)
endif
JAVA := "$(JAVA_HOME)/bin/java"
JAVAC := "$(JAVA_HOME)/bin/javac"
ifeq ("$(wildcard $(PLATFORM_PROG))","")
$(info Building NativePlatform tool)
$(shell mkdir -p $(PLATFORM_TOOL_DIR) && $(JAVAC) -d $(PLATFORM_TOOL_DIR) src/main/java/io/github/idoly/sqlite/ffm/NativePlatform.java)
endif
ifndef OS_NAME
OS_NAME := $(shell $(JAVA) -cp $(PLATFORM_TOOL_DIR) $(PLATFORM_CLASS) --os)
endif
ifndef OS_ARCH
OS_ARCH := $(shell $(JAVA) -cp $(PLATFORM_TOOL_DIR) $(PLATFORM_CLASS) --arch)
endif
endif

sqlite := sqlite-$(version)
target := $(OS_NAME)-$(OS_ARCH)
known_targets := Linux-x86_64 Linux-aarch64 Linux-Musl-x86_64 Linux-Musl-aarch64 Mac-x86_64 Mac-aarch64 Windows-x86_64 Windows-aarch64

ifeq (,$(filter $(target),$(known_targets)))
$(error Unsupported native target: $(target))
endif

$(info Building native SQLite for $(target))

CROSS_PREFIX ?=
COMMON_CCFLAGS := -Os -fPIC
UNIX_LINKFLAGS := -shared -static-libgcc -pthread -lm

Linux-x86_64_CC := $(CROSS_PREFIX)gcc
Linux-x86_64_STRIP := $(CROSS_PREFIX)strip
Linux-x86_64_CCFLAGS := $(COMMON_CCFLAGS) -m64
Linux-x86_64_LINKFLAGS := $(UNIX_LINKFLAGS)
Linux-x86_64_LIBNAME := libsqlite3.so

Linux-aarch64_CC := $(CROSS_PREFIX)gcc
Linux-aarch64_STRIP := $(CROSS_PREFIX)strip
Linux-aarch64_CCFLAGS := $(COMMON_CCFLAGS)
Linux-aarch64_LINKFLAGS := $(UNIX_LINKFLAGS)
Linux-aarch64_LIBNAME := libsqlite3.so

Linux-Musl-x86_64_CC := $(CROSS_PREFIX)gcc
Linux-Musl-x86_64_STRIP := $(CROSS_PREFIX)strip
Linux-Musl-x86_64_CCFLAGS := $(COMMON_CCFLAGS) -m64
Linux-Musl-x86_64_LINKFLAGS := $(UNIX_LINKFLAGS)
Linux-Musl-x86_64_LIBNAME := libsqlite3.so

Linux-Musl-aarch64_CC := $(CROSS_PREFIX)gcc
Linux-Musl-aarch64_STRIP := $(CROSS_PREFIX)strip
Linux-Musl-aarch64_CCFLAGS := $(COMMON_CCFLAGS)
Linux-Musl-aarch64_LINKFLAGS := $(UNIX_LINKFLAGS)
Linux-Musl-aarch64_LIBNAME := libsqlite3.so

Mac-x86_64_CC := $(CROSS_PREFIX)clang -arch x86_64
Mac-x86_64_STRIP := $(CROSS_PREFIX)strip -x
Mac-x86_64_CCFLAGS := $(COMMON_CCFLAGS) -mmacosx-version-min=11
Mac-x86_64_LINKFLAGS := -dynamiclib -lpthread -lm
Mac-x86_64_LIBNAME := libsqlite3.dylib

Mac-aarch64_CC := $(CROSS_PREFIX)clang
Mac-aarch64_STRIP := $(CROSS_PREFIX)strip -x
Mac-aarch64_CCFLAGS := $(COMMON_CCFLAGS) -mmacosx-version-min=11
Mac-aarch64_LINKFLAGS := -dynamiclib -lpthread -lm
Mac-aarch64_LIBNAME := libsqlite3.dylib

Windows-x86_64_CC := $(CROSS_PREFIX)gcc
Windows-x86_64_STRIP := $(CROSS_PREFIX)strip
Windows-x86_64_CCFLAGS := -Os
Windows-x86_64_LINKFLAGS := -Wl,--kill-at -shared -static-libgcc
Windows-x86_64_LIBNAME := sqlite3.dll

Windows-aarch64_CC := $(CROSS_PREFIX)clang
Windows-aarch64_STRIP := $(CROSS_PREFIX)strip
Windows-aarch64_CCFLAGS := -Os
Windows-aarch64_LINKFLAGS := -Wl,--kill-at -shared -static-libgcc
Windows-aarch64_LIBNAME := sqlite3.dll

CC := $($(target)_CC)
STRIP := $($(target)_STRIP)
CCFLAGS := $($(target)_CCFLAGS)
LINKFLAGS := $($(target)_LINKFLAGS)
LIBNAME := $($(target)_LIBNAME)
SQLITE_FLAGS := $($(target)_SQLITE_FLAGS)
SQLITE_SRC_PREFIX := sqlite-src-$(shell ./amalgamation_version.sh $(version))
SQLITE_AMAL_PREFIX := sqlite-amalgamation-$(shell ./amalgamation_version.sh $(version))

RESOURCE_DIR = src/main/resources

.PHONY: all package native native-all clean clean-native clean-java clean-tests \
        win64 win-arm64 mac64 mac-arm64 \
        linux64 linux-arm64 linux-musl64 linux-musl-arm64

all: package

CONTAINER_ENGINE ?= docker
DOCKER_RUN_OPTS := --rm
MVN := mvn
SQLITE_OUT:=$(TARGET)/$(sqlite)-$(OS_NAME)-$(OS_ARCH)
SQLITE_OBJ?=$(SQLITE_OUT)/sqlite3.o
SQLITE_SRC_ARCHIVE:=$(TARGET)/$(sqlite)-src.zip
SQLITE_SRC:=$(TARGET)/sqlite-src.log
SQLITE_SRC_TMP:=$(TARGET)/tmp-src.$(version)/$(SQLITE_SRC_PREFIX)
SQLITE_AMALGAMATION_FROM_SRC:=$(TARGET)/tmp-src.$(version)/$(SQLITE_AMAL_PREFIX)
SQLITE_AMALGAMATION_ZIP_FROM_SRC:=$(SQLITE_AMALGAMATION_FROM_SRC).zip
SQLITE_ARCHIVE:=$(TARGET)/$(sqlite)-amal.zip
SQLITE_UNPACKED:=$(TARGET)/sqlite-unpack.log
SQLITE_SOURCE?=$(TARGET)/$(SQLITE_AMAL_PREFIX)
SQLITE_HEADER?=$(SQLITE_SOURCE)/sqlite3.h
SQLITE_EXTENSION_SOURCES := $(wildcard src/main/c/*.c)

SQLITE_INCLUDE := $(shell dirname "$(SQLITE_HEADER)")

CCFLAGS:= -I$(SQLITE_OUT) -I$(SQLITE_INCLUDE) $(CCFLAGS)

$(SQLITE_SRC_ARCHIVE):
	mkdir -p $(@D)
	curl -L --max-redirs 0 -f -o$@ https://www.sqlite.org/$(sqlite_year)/$(SQLITE_SRC_PREFIX).zip
	if command -v sha256sum >/dev/null; then \
		test "$$(sha256sum '$@' | awk '{print $$1}')" = '$(sqlite_src_sha256)'; \
	else \
		test "$$(shasum -a 256 '$@' | awk '{print $$1}')" = '$(sqlite_src_sha256)'; \
	fi

$(SQLITE_SRC): $(SQLITE_SRC_ARCHIVE)
	unzip -qo $< -d $(TARGET)/tmp-src.$(version)
	((cd $(SQLITE_SRC_TMP) && ./configure --update-limit && make sqlite3.c) | tee $@)

$(SQLITE_AMALGAMATION_ZIP_FROM_SRC): $(SQLITE_SRC)
	mkdir -p $(SQLITE_AMALGAMATION_FROM_SRC)
	cp $(SQLITE_SRC_TMP)/sqlite3.c $(SQLITE_SRC_TMP)/sqlite3.h $(SQLITE_SRC_TMP)/sqlite3ext.h $(SQLITE_AMALGAMATION_FROM_SRC)/
	(cd $(SQLITE_AMALGAMATION_FROM_SRC)/.. && zip -r $(SQLITE_AMAL_PREFIX).zip $(SQLITE_AMAL_PREFIX))

$(SQLITE_ARCHIVE): $(SQLITE_AMALGAMATION_ZIP_FROM_SRC)
	@mkdir -p $(@D)
	cp -v $< $@

$(SQLITE_UNPACKED): $(SQLITE_ARCHIVE)
	unzip -qo $< -d $(TARGET)/tmp.$(version)
	(mv $(TARGET)/tmp.$(version)/$(SQLITE_AMAL_PREFIX) $(TARGET) && rmdir $(TARGET)/tmp.$(version)) || mv $(TARGET)/tmp.$(version)/ $(TARGET)/$(SQLITE_AMAL_PREFIX)
	touch $@

test:
	mvn test

clean: clean-native clean-java clean-tests


$(SQLITE_OUT)/sqlite3.o : $(SQLITE_UNPACKED)
	@mkdir -p $(@D)
	perl -p -e "s/sqlite3_api;/sqlite3_api = 0;/g" \
	    $(SQLITE_SOURCE)/sqlite3ext.h > $(SQLITE_OUT)/sqlite3ext.h
# insert a code for loading extension functions
	perl -p -e "s/^static int openDatabase\(/int RegisterExtensionFunctions(sqlite3 *db);\nstatic int openDatabase(/; s/^opendb_out:/  if(!db->mallocFailed \&\& rc==SQLITE_OK){ rc = RegisterExtensionFunctions(db); }\nopendb_out:/;" \
	    $(SQLITE_SOURCE)/sqlite3.c > $(SQLITE_OUT)/sqlite3.c.tmp
# register compile option 'JDBC_EXTENSIONS'
# limits defined here: https://www.sqlite.org/limits.html
	perl -p -e "s/^(static const char \* const sqlite3azCompileOpt.+)$$/\1\n\n\/* This has been automatically added by sqlite-jdbc *\/\n  \"JDBC_EXTENSIONS\",/;" \
	    $(SQLITE_OUT)/sqlite3.c.tmp > $(SQLITE_OUT)/sqlite3.c
	cat $(SQLITE_EXTENSION_SOURCES) >> $(SQLITE_OUT)/sqlite3.c
	$(CC) -o $@ -c $(CCFLAGS) \
	    -DSQLITE_ENABLE_LOAD_EXTENSION=1 \
	    -DSQLITE_HAVE_ISNAN \
	    -DHAVE_USLEEP=1 \
	    -DSQLITE_ENABLE_COLUMN_METADATA \
	    -DSQLITE_CORE \
	    -DSQLITE_ENABLE_FTS3 \
	    -DSQLITE_ENABLE_FTS3_PARENTHESIS \
	    -DSQLITE_ENABLE_FTS5 \
	    -DSQLITE_ENABLE_RTREE \
	    -DSQLITE_ENABLE_PERCENTILE \
	    -DSQLITE_ENABLE_STAT4 \
	    -DSQLITE_ENABLE_DBSTAT_VTAB \
	    -DSQLITE_ENABLE_MATH_FUNCTIONS \
	    -DSQLITE_THREADSAFE=1 \
	    -DSQLITE_DEFAULT_MEMSTATUS=0 \
	    -DSQLITE_DEFAULT_FILE_PERMISSIONS=0666 \
	    -DSQLITE_MAX_VARIABLE_NUMBER=250000 \
	    -DSQLITE_MAX_MMAP_SIZE=1099511627776 \
	    -DSQLITE_MAX_LENGTH=2147483647 \
	    -DSQLITE_MAX_COLUMN=32767 \
	    -DSQLITE_MAX_SQL_LENGTH=1073741824 \
	    -DSQLITE_MAX_FUNCTION_ARG=127 \
	    -DSQLITE_MAX_ATTACHED=125 \
	    -DSQLITE_MAX_PAGE_COUNT=4294967294 \
	    -DSQLITE_DISABLE_PAGECACHE_OVERFLOW_STATS \
	    -DSQLITE_ENABLE_UPDATE_DELETE_LIMIT \
	    $(SQLITE_FLAGS) \
	    $(SQLITE_OUT)/sqlite3.c

$(SQLITE_SOURCE)/sqlite3.h: $(SQLITE_UNPACKED)

$(SQLITE_OUT)/$(LIBNAME): $(SQLITE_HEADER) $(SQLITE_OBJ)
	@mkdir -p $(@D)
	$(CC) $(CCFLAGS) -o $@ $(SQLITE_OBJ) $(LINKFLAGS)
# Workaround for strip Protocol error when using VirtualBox on Mac
	cp $@ /tmp/$(@F)
	$(STRIP) /tmp/$(@F)
	cp /tmp/$(@F) $@

NATIVE_DIR=src/main/resources/io/github/idoly/sqlite/native/$(OS_NAME)/$(OS_ARCH)
NATIVE_TARGET_DIR:=$(TARGET)/classes/io/github/idoly/sqlite/native/$(OS_NAME)/$(OS_ARCH)
NATIVE_DLL:=$(NATIVE_DIR)/$(LIBNAME)

native-all: win64 win-arm64 mac64 mac-arm64 linux64 linux-arm64 linux-musl64 linux-musl-arm64

native: $(NATIVE_DLL)

$(NATIVE_DLL): $(SQLITE_OUT)/$(LIBNAME)
	@mkdir -p $(@D)
	cp $< $@
	@mkdir -p $(NATIVE_TARGET_DIR)
	cp $< $(NATIVE_TARGET_DIR)/$(LIBNAME)

win64: $(SQLITE_UNPACKED)
	OCI_EXE=$(CONTAINER_ENGINE) ./docker/dockcross-windows-x64 -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=x86_64-w64-mingw32.static- OS_NAME=Windows OS_ARCH=x86_64'

win-arm64: $(SQLITE_UNPACKED)
	OCI_EXE=$(CONTAINER_ENGINE) ./docker/dockcross-windows-arm64 -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=aarch64-w64-mingw32- OS_NAME=Windows OS_ARCH=aarch64'

linux64: $(SQLITE_UNPACKED)
	$(CONTAINER_ENGINE) run $(DOCKER_RUN_OPTS) -v $$PWD:/work -w /work docker.io/almalinux:8 sh -c 'dnf install -y gcc make perl && make clean-native native OS_NAME=Linux OS_ARCH=x86_64'

linux-arm64: $(SQLITE_UNPACKED)
	OCI_EXE=$(CONTAINER_ENGINE) ./docker/dockcross-arm64-lts -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=aarch64-unknown-linux-gnu- OS_NAME=Linux OS_ARCH=aarch64'

linux-musl64: $(SQLITE_UNPACKED)
	$(CONTAINER_ENGINE) run $(DOCKER_RUN_OPTS) -v $$PWD:/work -w /work alpine:3.22 sh -c 'apk add --no-cache build-base make perl && make clean-native native OS_NAME=Linux-Musl OS_ARCH=x86_64'

linux-musl-arm64: $(SQLITE_UNPACKED)
	OCI_EXE=$(CONTAINER_ENGINE) ./docker/dockcross-musl-arm64 -a $(DOCKER_RUN_OPTS) bash -c 'make clean-native native CROSS_PREFIX=aarch64-linux-musl- OS_NAME=Linux-Musl OS_ARCH=aarch64'

mac64: $(SQLITE_UNPACKED)
	$(CONTAINER_ENGINE) run $(DOCKER_RUN_OPTS) -v $$PWD:/workdir docker.io/gotson/crossbuild make clean-native native OS_NAME=Mac OS_ARCH=x86_64 CROSS_PREFIX="/usr/osxcross/bin/x86_64-apple-darwin20.4-"

mac-arm64: $(SQLITE_UNPACKED)
	$(CONTAINER_ENGINE) run $(DOCKER_RUN_OPTS) -v $$PWD:/workdir -e CROSS_TRIPLE=aarch64-apple-darwin docker.io/gotson/crossbuild make clean-native native OS_NAME=Mac OS_ARCH=aarch64 CROSS_PREFIX="/usr/osxcross/bin/aarch64-apple-darwin20.4-"


package: native
	$(MVN) package

clean-native:
	rm -rf $(SQLITE_OUT)

clean-java:
	rm -rf $(TARGET)/*classes
	rm -rf $(TARGET)/sqlite-jdbc-*jar

clean-tests:
	rm -rf $(TARGET)/{surefire*,testdb.jar*}
