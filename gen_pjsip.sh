#!/bin/bash
PLIVOWD=`pwd`
clear_build_folder() {
  cd ./pjsip
  rm -rf pjsip-src-bin
  mkdir pjsip-src-bin
  cd $PLIVOWD
}

build_pjsip() {
  for ARCH in "x86" "x86_64" "arm64-v8a" "armeabi-v7a"
  do
        SSLPATH=$PLIVOWD/openssl/$ARCH
        OPUSPATH=$PLIVOWD/opus/$ARCH
        echo $SSLPATH
        cd ./pjsip
        export TARGET_ABI=$ARCH
        ./build_pjsip.sh $ARCH $SSLPATH $OPUSPATH
        echo $TARGET_ABI

        cd ./pjsip-src-bin
        rm -r $ARCH
        mkdir $ARCH
        cd ./$ARCH
        cp -r ../../pjproject-2.7.2 .
	      echo $TARGET_ABI "done"
        cd $PLIVOWD
  done
}

clear_build_folder
build_pjsip
