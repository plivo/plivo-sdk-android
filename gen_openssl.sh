#!/bin/bash
PLIVOWD=`pwd`
ANDROID_SDK_API_LEVEL=24
for VARIABLE in "x86" "x86_64" "arm64-v8a" "armeabi-v7a"
do
   ./opensslbuild.sh $ANDROID_NDK $PLIVOWD/openssl/openssl-1.0.2k $ANDROID_SDK_API_LEVEL $VARIABLE $NDK_TOOLCHAIN_VERSION $PLIVOWD/openssl/$VARIABLE
done
