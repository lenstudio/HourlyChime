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

# 签名密钥：优先用脚本同目录的 release.keystore + release.keystore.pass（不入库），
# 否则回退 boot-to-tv debug.keystore；可用 KS/KSPASS/KSALIAS 环境变量覆盖
if [ -z "$KS" ]; then
    if [ -f release.keystore ] && [ -f release.keystore.pass ]; then
        KS=release.keystore
        KSPASS=$(cat release.keystore.pass)
        KSALIAS=${KSALIAS:-hourlychime}
    else
        KS=$HOME/android/boot-to-tv/debug.keystore
        KSPASS=android
        KSALIAS=boottotv
    fi
fi

rm -rf classes dexout gen res.zip unsigned.apk aligned.apk HourlyChime.apk
mkdir -p classes dexout gen

# 1. 编译资源（启动器图标 + 多语言 strings）
$BT/aapt2 compile --dir res -o res.zip

# 2. 链接生成基础 APK（注意 --min/--target 必须显式指定；--java 生成 R.java）
$BT/aapt2 link -o unsigned.apk -I $AJ --manifest AndroidManifest.xml \
    -A assets --min-sdk-version 30 --target-sdk-version 33 \
    --java gen res.zip

# 3. 编译 Java（无 support 库，纯 framework API）
$JAVA_HOME/bin/javac -encoding UTF-8 -source 8 -target 8 -classpath $AJ -d classes \
    $(find gen classes -name '*.java' 2>/dev/null) *.java

# 4. dex 打包（min-api 30）
$JAVA_HOME/bin/java -cp $BT/lib/d8.jar com.android.tools.r8.D8 --min-api 30 --output dexout $(find classes -name '*.class')

# 5. 合包、对齐、签名
(cd dexout && zip -q -j ../unsigned.apk classes.dex)
$BT/zipalign -f 4 unsigned.apk aligned.apk
APKSIGNER=$BT/apksigner
[ -f "$APKSIGNER.bat" ] && APKSIGNER=$APKSIGNER.bat   # Windows
$APKSIGNER sign --ks "$KS" --ks-pass pass:$KSPASS --ks-key-alias $KSALIAS --out HourlyChime.apk aligned.apk

ls -la HourlyChime.apk
echo "done. 安装: adb install -r HourlyChime.apk"
