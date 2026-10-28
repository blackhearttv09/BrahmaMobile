#!/data/data/com.termux/files/usr/bin/bash
# One-shot local build inside Termux (needs: pkg install openjdk-17 aapt2 + an Android SDK, see README).
set -e
export JAVA_HOME=${JAVA_HOME:-$PREFIX/lib/jvm/java-17-openjdk}
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK folder first}"
echo "sdk.dir=$ANDROID_HOME" > local.properties
grep -q aapt2FromMavenOverride gradle.properties || echo "android.aapt2FromMavenOverride=$PREFIX/bin/aapt2" >> gradle.properties
chmod +x gradlew
./gradlew assembleDebug --no-daemon
mkdir -p ~/storage/downloads 2>/dev/null || true
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/BrahmaMobile.apk && echo "APK -> Downloads/BrahmaMobile.apk"
