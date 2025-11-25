#!/bin/bash
VERSION="1.3"

REPOROOT=$(pwd)
SRCDIR="${REPOROOT}/src"
mkdir -p $SRCDIR

if [ ! -e "${SRCDIR}/opus-${VERSION}.tar.gz" ]; then
	echo "Downloading opus-${VERSION}.tar.gz"
	curl -LO http://downloads.xiph.org/releases/opus/opus-${VERSION}.tar.gz
fi
echo "Using opus-${VERSION}.tar.gz"

tar zxf opus-${VERSION}.tar.gz -C $SRCDIR

echo "Creating folder ${SRCDIR}/opus-${VERSION}/jni"
mkdir -p ${SRCDIR}/opus-${VERSION}/jni

cp ./Android.mk ${SRCDIR}/opus-${VERSION}/jni/

cd ${SRCDIR}/opus-${VERSION}

${ANDROID_NDK_ROOT}ndk-build

cd ${REPOROOT}

for ARCH in "x86" "x86_64" "arm64-v8a" "armeabi-v7a"
do
    if [ -e "${ARCH}" ]; then
      echo "Clearing old ${ARCH} build"
      rm -rf ${ARCH}
    fi
    mkdir -p ${ARCH}/include
    mkdir -p ${ARCH}/lib
    cp -rf ${SRCDIR}/opus-${VERSION}/include ${ARCH}/include/opus
    cp ${SRCDIR}/opus-${VERSION}/obj/local/${ARCH}/libopus.a ${ARCH}/lib/
done