Prerequisites
-------------
- Java 1.8
- Android SDK API level 24
- [android_ndk_r12b](https://developer.android.com/ndk/downloads/older_releases)
- [swig](http://prdownloads.sourceforge.net/swig/swig-3.0.12.tar.gz)

Componnets
-------------
The project is divided into 4 parts
- OpenSSL
- PJSIP 2.7.2
- PJSIP-JNI
- Plivoendpoint


Build SDK
-------------
## Envrionment varibles

`cp temp.env .env`

Change the env paths in the .env file to matche the paths in you machine. Don't change temp.env file.

### your android sdk path
`export ANDROID_HOME=~/Library/Android/sdk`

### your android ndk path
`export ANDROID_NDK_ROOT=~/Library/Android/sdk/android-ndk-r12b/`

`export ANDROID_NDK=~/Library/Android/sdk/android-ndk-r12b/`

`export NDK_TOOLCHAIN_VERSION=4.9`

### your swig path
`export SWIG_PATH=/usr/local/bin/swig`

### build all (openssl, pjsip, pjsip-jni, PlivoEndpoint)
```
source .env
./allbuild.sh
```

## If you want to build individual components
### build openssl
```
source .env
./gen_openssl.sh
```

### build pjsip
Build openssl before building pjsip
```
source .env
./gen_pjsip.sh
```

### build pjsip-jni
Build pjsip before building pjsip-jni
```
source .env
./gen_jni.sh
```


### build PlivoEndpoint
Build pjsip before building pjsip-jni
For prod
```
ENV=prod
./gen_endpoint.sh $ENV
```
For Stage
```
ENV=stage
./gen_endpoint.sh $ENV
```
For QA
```
ENV=qa
./gen_endpoint.sh $ENV
```

### build outputs (.so files)
`plivo-android-sdk/build/outputs/libs`

### build outputs (.aar file)
`plivo-android-sdk/build/outputs/aar`


### Deployment to Bintray

Add the following lines to local.properties:

```
bintray.user=BINTRAY_USER_HERE
bintray.apikey=API_KEY_HERE
```

To deploy to jCenter:

`./deployJCenter.sh`
