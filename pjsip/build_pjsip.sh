#!/bin/bash

#  build_pjsip.sh
#  Script to build pjsip for plivo-ios-sdk
#  This script build for these architectures:
#   - armv7
#   - arm64
#   - x86_64
#
#  Copyright (c) 2013 Plivo Inc. All rights reserved.

#TODO: Read for input after every arch compilation
# Check if the library exists in the individual folder.

PJPROJECT_FILE_NAME="pjproject-2.7.2.tar.bz2"
PJPROJECT_SRC_DIR="pjproject-2.7.2"

CONFIG_SITE_H="$PJPROJECT_SRC_DIR/pjlib/include/pj/config_site.h"
CWD=`pwd`
SSLPATH=$2

export PJSIP_DIR=$CWD/$PJPROJECT_SRC_DIR/
export PJDIR=$CWD/$PJPROJECT_SRC_DIR/

#copy our config_site.h
copy_config_site_h()
{
    echo "Copy $CONFIG_SITE_H"
    cp -v config_site.h $CONFIG_SITE_H
}

prep_pjsip_source()
{
    echo "Untar $PJPROJECT_FILE_NAME"
    tar xvjf $PJPROJECT_FILE_NAME
}

apply_patches_for_2_7()
{
    # https://trac.pjsip.org/repos/ticket/2085
    cp ./patches/pjsua_acc.patch $CWD/$PJPROJECT_SRC_DIR/pjsip/src/pjsua-lib/
    cp ./patches/sip_util.patch $CWD/$PJPROJECT_SRC_DIR/pjsip/src/pjsip/
    cp ./patches/sip_config.patch $CWD/$PJPROJECT_SRC_DIR/pjsip/include/pjsip/
    cp ./patches/stream_info.patch $CWD/$PJPROJECT_SRC_DIR/pjmedia/src/pjmedia/

    cd $CWD/$PJPROJECT_SRC_DIR/pjsip/src/pjsua-lib/
    patch < pjsua_acc.patch

    cd $CWD/$PJPROJECT_SRC_DIR/pjsip/src/pjsip/
    patch < sip_util.patch

    cd $CWD/$PJPROJECT_SRC_DIR/pjsip/include/pjsip/
    patch < sip_config.patch

    cd $CWD/$PJPROJECT_SRC_DIR/pjmedia/src/pjmedia/
    patch < stream_info.patch

    cd $CWD
}

build_pjsip_armeabi()
{
    echo $SSLPATH
    rm -rf $CWD/$PJPROJECT_SRC_DIR
    prep_pjsip_source
    apply_patches_for_2_7
    copy_config_site_h

    echo "Building armeabi libs..."
    cd ./$PJPROJECT_SRC_DIR
    #android_ndk_r9 should be present in path environment variable
    if [ ! -z `printenv | grep ANDROID_NDK_ROOT` ]
    then
        ./configure-android --with-ssl=$1 --with-opus=$2
        make dep && make clean && make
        cd $CWD
    else
        echo "ndk path not provided" 
     fi
}


build_pjsip_armv7()
{
    rm -rf $CWD/$PJPROJECT_SRC_DIR
    prep_pjsip_source
    apply_patches_for_2_7
    copy_config_site_h

    echo "Building armv7 libs..."
    export TARGET_ABI="armeabi-v7a"
    cd ./$PJPROJECT_SRC_DIR
    #android_ndk_r9 should be present in path environment variable
    if [ ! -z `printenv | grep ANDROID_NDK_ROOT` ]
    then
        TARGET_ABI=armeabi-v7a ./configure-android --use-ndk-cflags --with-ssl=$1 --with-opus=$2
        make dep && make clean && make
        cd $CWD
    else
        echo "ndk path not provided" 
     fi
}

build_pjsip_arm64v8a()
{
    rm -rf $CWD/$PJPROJECT_SRC_DIR
    prep_pjsip_source
    apply_patches_for_2_7
    copy_config_site_h

    echo "Building arm64v8a libs..."
    export TARGET_ABI="arm64-v8a"
    cd ./$PJPROJECT_SRC_DIR
    if [ ! -z `printenv | grep ANDROID_NDK_ROOT` ]
    then
        TARGET_ABI=arm64-v8a ./configure-android --use-ndk-cflags --with-ssl=$1 --with-opus=$2
        make dep && make clean && make
        cd $CWD
    else
        echo "ndk path not provided" 
     fi
}


build_pjsip_x86()
{
    rm -rf $CWD/$PJPROJECT_SRC_DIR
    prep_pjsip_source
    apply_patches_for_2_7
    copy_config_site_h

    echo "Building x86 libs..."
    export TARGET_ABI="x86"
    cd ./$PJPROJECT_SRC_DIR
    
    if [ ! -z `printenv | grep ANDROID_NDK_ROOT` ]
    then
        TARGET_ABI=x86 ./configure-android --use-ndk-cflags --with-ssl=$1 --with-opus=$2
        make dep && make clean && make
        cd $CWD
    else
        echo "ndk path not provided" 
     fi
}


build_pjsip_x86_64()
{
    rm -rf $CWD/$PJPROJECT_SRC_DIR
    prep_pjsip_source
    apply_patches_for_2_7
    copy_config_site_h

    echo "Building x86_64 libs..."
    export TARGET_ABI="x86_64"
    cd ./$PJPROJECT_SRC_DIR
    
    if [ ! -z `printenv | grep ANDROID_NDK_ROOT` ]
    then
        TARGET_ABI=x86_64 ./configure-android --use-ndk-cflags --with-ssl=$1 --with-opus=$2
        make dep && make clean && make
        cd $CWD
    else
        echo "ndk path not provided" 
     fi
}


echo "PJSIP library builder"

#build_pjsip_armv7
#build_pjsip_arm64
#build_pjsip_x86_64



if [ "$1" == "armeabi-v7a" ]; then
	echo "Building ARM V7"
	build_pjsip_armv7 $2 $3
elif [ "$1" == "x86" ]; then
	echo "Building x86"
	build_pjsip_x86 $2 $3
elif [ "$1" == "x86_64" ]; then
	echo "Building X86 64"
	build_pjsip_x86_64 $2 $3
elif [ "$1" == "arm64-v8a" ]; then
	echo "Building arm64v8a"
	build_pjsip_arm64v8a $2 $3
elif [ "$1" == "armeabi" ]; then
	echo "Building armeabi"
	build_pjsip_armeabi $2 $3
fi
