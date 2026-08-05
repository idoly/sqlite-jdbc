#!/usr/bin/env sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
version=${1:-0.1.0-SNAPSHOT}
platform=${2:-linux-x86_64-glibc}
output="$root/integration-tests/target/jpms-test"
ffm="$root/sqlite-ffm/target/sqlite-ffm-${version}.jar"
driver="$root/sqlite-driver/target/sqlite-driver-${version}.jar"
native="$root/sqlite-native/target/sqlite-native-${version}-${platform}.jar"

rm -rf "$output"
mkdir -p "$output"
javac \
  --module-path "$ffm:$driver" \
  -d "$output" \
  "$root/integration-tests/src/jpms/java/module-info.java" \
  "$root/integration-tests/src/jpms/java/io/github/idoly/sqlite/jpms/Main.java"
java \
  --enable-native-access=io.github.idoly.sqlite.ffm \
  --add-modules io.github.idoly.sqlite.nativelib \
  --module-path "$ffm:$driver:$native:$output" \
  -m io.github.idoly.sqlite.jpms.test/io.github.idoly.sqlite.jpms.Main
