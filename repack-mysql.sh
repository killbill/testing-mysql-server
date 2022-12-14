#!/bin/bash

set -eu

VERSION=8.0.31
BASEURL="https://dev.mysql.com/get/Downloads/MySQL-8.0"

LINUX_BASE=mysql-$VERSION-linux-glibc2.12-x86_64
OSX_BASE=mysql-$VERSION-macos12-x86_64

TAR=tar
command -v gtar >/dev/null && TAR=gtar

if ! $TAR --version | grep -q "GNU tar"
then
    echo "GNU tar is required."
    echo "Hint: brew install gnu-tar"
    $TAR --version
    exit 100
fi

STRIP=strip
command -v gstrip >/dev/null && STRIP=gstrip

if ! $STRIP --version | grep -q "GNU strip"
then
    echo "GNU strip is required."
    echo "Hint: brew install binutils"
    exit 100
fi

set -x

cd $(dirname $0)

RESOURCES=target/generated-resources

mkdir -p dist $RESOURCES

LINUX_NAME=$LINUX_BASE.tar.xz
LINUX_DIST=dist/$LINUX_NAME

OSX_NAME=$OSX_BASE.tar.gz
OSX_DIST=dist/$OSX_NAME

test -e $LINUX_DIST || curl -L -o $LINUX_DIST "$BASEURL/$LINUX_NAME"
test -e $OSX_DIST || curl -L -o $OSX_DIST "$BASEURL/$OSX_NAME"

PACKDIR=$(mktemp -d "${TMPDIR:-/tmp}/mysql.XXXXXXXXXX")
$TAR -xf $LINUX_DIST -C "$PACKDIR"
pushd "$PACKDIR"/$LINUX_BASE
# libprotobuf<XXX> needed since 8.0.31
ln -s "$PACKDIR"/$LINUX_BASE/lib/private/libprotobuf.so.3.19.4 "$PACKDIR"/$LINUX_BASE/bin
ln -s "$PACKDIR"/$LINUX_BASE/lib/private/libprotobuf-lite.so.3.19.4 "$PACKDIR"/$LINUX_BASE/bin
$STRIP bin/mysqld
$TAR -czf "$OLDPWD"/$RESOURCES/mysql-Linux-amd64.tar.gz \
  LICENSE \
  README \
  docs/INFO* \
  share/*.sql \
  share/*.txt \
  share/charsets \
  share/english \
  lib/plugin/component_reference_cache.so \
  lib/private/libcrypto.* \
  lib/private/libssl.* \
  lib/private/libprotobuf.* \
  lib/private/libprotobuf-lite.* \
  bin/libprotobuf.* \
  bin/libprotobuf-lite.* \
  bin/mysqld
popd
rm -rf "$PACKDIR"

PACKDIR=$(mktemp -d "${TMPDIR:-/tmp}/mysql.XXXXXXXXXX")
$TAR -xf $OSX_DIST -C "$PACKDIR"
pushd "$PACKDIR"/$OSX_BASE
$TAR -czf "$OLDPWD"/$RESOURCES/mysql-Mac_OS_X-x86_64.tar.gz \
  LICENSE \
  README \
  docs/INFO* \
  share/*.sql \
  share/*.txt \
  share/charsets \
  share/english \
  lib/plugin/component_reference_cache.so \
  lib/libcrypto.* \
  lib/libssl.* \
  lib/libprotobuf.* \
  lib/libprotobuf-lite.* \
  bin/libprotobuf.* \
  bin/libprotobuf-lite.* \
  bin/mysqld
popd
rm -rf "$PACKDIR"
