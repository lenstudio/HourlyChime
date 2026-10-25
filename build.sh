#!/bin/sh
# HourlyChime 构建脚本（aapt2 + javac + d8 + zipalign + apksigner，无 Gradle）
# 依赖：JDK 17、Android SDK build-tools 34、platform android-33
set -e
cd "$(dirname "$0")"

SDK=${SDK:-$HOME/android/android-sdk}
BT=$SDK/build-tools/34.0.0
AJ=$SDK/platforms/android-33/android.jar
export JAVA_HOME=${JAVA_HOME:-$HOME/android/jdk17/Contents/Home}
export PATH=$JAVA_HOME/bin:$PATH

KS=${KS:-$HOME/android/boot-to-tv/debug.keystore}
KSPASS=${KSPASS:-android}
KSALIAS=${KSALIAS:-boottotv}

rm -rf classes dexout res.zip unsigned.apk aligned.apk HourlyChime.apk
mkdir -p classes dexout

# 1. 编译资源（仅启动器图标）
$BT/aapt2 compile --dir res -o res.zip

# 2. 链接生成基础 APK（注意 --min/--target 必须显式指定）
$BT/aapt2 link -o unsigned.apk -I $AJ --manifest AndroidManifest.xml \
    -A assets --min-sdk-version 30 --target-sdk-version 33 res.zip

# 3. 编译 Java（无 support 库，纯 framework API）
$JAVA_HOME/bin/javac -source 8 -target 8 -classpath $AJ -d classes *.java

# 4. dex 打包（min-api 30）
$JAVA_HOME/bin/java -cp $BT/lib/d8.jar com.android.tools.r8.D8 --min-api 30 --output dexout $(find classes -name '*.class')

# 5. 合包、对齐、签名
(cd dexout && zip -q -j ../unsigned.apk classes.dex)
$BT/zipalign -f 4 unsigned.apk aligned.apk
$BT/apksigner sign --ks "$KS" --ks-pass pass:$KSPASS --ks-key-alias $KSALIAS --out HourlyChime.apk aligned.apk

ls -la HourlyChime.apk
echo "done. 安装: adb install -r HourlyChime.apk"
