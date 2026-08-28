
include VERSION

TARGET := target
PLATFORM_CLASS := io.github.idoly.sqlite.ffm.NativePlatform
PLATFORM_TOOL_DIR := $(TARGET)/tools
PLATFORM_PROG := $(PLATFORM_TOOL_DIR)/io/github/idoly/sqlite/ffm/NativePlatform.class
JEXTRACT ?= jextract
JEXTRACT_VERSION := 25
JEXTRACT_FUNCTIONS := src/main/jextract/sqlite3.functions
JEXTRACT_PACKAGE := io.github.idoly.sqlite.ffm.generated
JEXTRACT_PACKAGE_PATH := io/github/idoly/sqlite/ffm/generated
JEXTRACT_OUTPUT := src/main/java/$(JEXTRACT_PACKAGE_PATH)
JEXTRACT_STAGE := $(TARGET)/jextract-staging

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
known_targets := Linux-x86_64 Mac-x86_64 Mac-aarch64 Windows-x86_64 Windows-aarch64

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

Mac-x86_64_CC := $(CROSS_PREFIX)clang -arch x86_64
Mac-x86_64_STRIP := $(CROSS_PREFIX)strip -x
Mac-x86_64_CCFLAGS := $(COMMON_CCFLAGS) -mmacosx-version-min=11
Mac-x86_64_LINKFLAGS := -dynamiclib -lpthread -lm
Mac-x86_64_LIBNAME := libsqlite3.dylib

Mac-aarch64_CC := $(CROSS_PREFIX)clang -arch arm64
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
SQLITE_VERSION_CODE := $(shell printf '%s\n' '$(version)' | awk -F. '{ printf "%d%02d%02d%02d\n", $$1, $$2, $$3, $$4 }')
SQLITE_SRC_PREFIX := sqlite-src-$(SQLITE_VERSION_CODE)
SQLITE_AMAL_PREFIX := sqlite-amalgamation-$(SQLITE_VERSION_CODE)

RESOURCE_DIR = src/main/resources

.PHONY: all package native generate-bindings clean clean-native clean-java clean-tests

all: package

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
# limits defined here: https://www.sqlite.org/limits.html
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
	    $(SQLITE_SOURCE)/sqlite3.c

$(SQLITE_SOURCE)/sqlite3.h: $(SQLITE_UNPACKED)

generate-bindings: $(SQLITE_SOURCE)/sqlite3.h $(JEXTRACT_FUNCTIONS)
	@version="$$($(JEXTRACT) --version 2>&1 | head -n 1)"; \
	    test "$$version" = "jextract $(JEXTRACT_VERSION)" || { \
	        echo "Expected jextract $(JEXTRACT_VERSION), found: $$version" >&2; exit 1; \
	    }
	rm -rf $(JEXTRACT_STAGE)
	@set -eu; \
	    set -- --output "$(JEXTRACT_STAGE)" \
	        --target-package "$(JEXTRACT_PACKAGE)" \
	        --header-class-name SQLiteNative; \
	    while IFS= read -r function; do \
	        test -z "$$function" || set -- "$$@" --include-function "$$function"; \
	    done < "$(JEXTRACT_FUNCTIONS)"; \
	    "$(JEXTRACT)" "$$@" "$(SQLITE_SOURCE)/sqlite3.h"
	test -f $(JEXTRACT_STAGE)/$(JEXTRACT_PACKAGE_PATH)/SQLiteNative.java
	rm -rf $(JEXTRACT_OUTPUT)
	mkdir -p src/main/java/io/github/idoly/sqlite/ffm
	mv $(JEXTRACT_STAGE)/$(JEXTRACT_PACKAGE_PATH) $(JEXTRACT_OUTPUT)
	@header_sha="$$(sha256sum '$(SQLITE_SOURCE)/sqlite3.h' | awk '{print $$1}')"; \
	    printf '%s\n' \
	        '# Generated by jextract. Do not edit.' \
	        'jextract.version=$(JEXTRACT_VERSION)' \
	        'sqlite.version=$(version)' \
	        "sqlite.header.sha256=$$header_sha" \
	        "generator.os=$$(uname -s)" \
	        > $(JEXTRACT_OUTPUT)/GENERATED.properties

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

native: $(NATIVE_DLL)

$(NATIVE_DLL): $(SQLITE_OUT)/$(LIBNAME)
	@mkdir -p $(@D)
	cp $< $@
	@mkdir -p $(NATIVE_TARGET_DIR)
	cp $< $(NATIVE_TARGET_DIR)/$(LIBNAME)

package: native
	$(MVN) package

clean-native:
	rm -rf $(SQLITE_OUT)

clean-java:
	rm -rf $(TARGET)/*classes
	rm -rf $(TARGET)/sqlite-jdbc-*jar

clean-tests:
	rm -rf $(TARGET)/{surefire*,testdb.jar*}
