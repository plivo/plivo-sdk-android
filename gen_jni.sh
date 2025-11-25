#!/bin/bash
PLIVOWD=`pwd`
export PJSIP_DIR=$PLIVOWD/pjsip/pjproject-2.7.2
export PJDIR=$PLIVOWD/pjsip/pjproject-2.7.2


clean_build_folder() {
  rm -rf ./pjsip-jni/jni/src
  mkdir -p ./pjsip-jni/jni/src/jni
}

build_pjsip_jni() {
  for VARIABLE in "x86" "x86_64" "arm64-v8a" "armeabi-v7a"
  do
	rm -rf pjsip/pjproject-2.7.2
	cp -r pjsip/pjsip-src-bin/$VARIABLE/pjproject-2.7.2 pjsip/

	cd ./pjsip-jni/jni
	mkdir ../src/libs
	make clean
	make
	cd $PLIVOWD

	#stat pjsip-jni/src/libs/libpjplivo.so
	rm -rf build/outputs/libs/$VARIABLE
	mkdir -p build/outputs/libs/$VARIABLE
	mv pjsip-jni/src/libs/*.so build/outputs/libs/$VARIABLE
	stat build/outputs/libs/$VARIABLE/libpjplivo.so
  done
}

clean_build_folder
build_pjsip_jni
