#!/bin/bash
set -euo pipefail

# ==============================
# Settings
# ==============================
BUILD_ROOT_DIR="$(pwd)/build"
BUILD_TMP_DIR="$BUILD_ROOT_DIR/tmp/fullRelease"
PROXY_DIR="$BUILD_TMP_DIR/proxy"
CARPETMOD_ZIP="$BUILD_ROOT_DIR/distributions/Carpetmod_dev.zip"
MINECRAFT_SERVER_JAR="$HOME/.gradle/caches/minecraft/net/minecraft/minecraft_server/1.12.2/minecraft_server-1.12.2.jar"
VERSION="${VERSION:-1_0}"  # default version if not set

# ==============================
# Prerequisite checks
# ==============================
command -v zip >/dev/null 2>&1 || {
  echo "⚠️ zip command not found. Installing..."
  sudo apt update && sudo apt install zip -y
}

command -v unzip >/dev/null 2>&1 || {
  echo "⚠️ unzip command not found. Installing..."
  sudo apt update && sudo apt install unzip -y
}

# ==============================
# Gradle build
# ==============================
./gradlew setupCarpetmod
./gradlew createRelease

# ==============================
# Create temporary directories
# ==============================
rm -rf "$BUILD_TMP_DIR"
mkdir -p "$BUILD_TMP_DIR"
cd "$BUILD_TMP_DIR" || exit

# Copy the vanilla Minecraft Server JAR
cp "$MINECRAFT_SERVER_JAR" ./base.jar
cp ./base.jar ./base2.jar

# ==============================
# Extract Carpetmod
# ==============================
rm -rf carpetmod
mkdir -p carpetmod
echo "📦 Extracting Carpetmod zip..."
unzip -qo "$CARPETMOD_ZIP" -d carpetmod

# ==============================
# Update base.jar
# ==============================
echo "🔄 Updating base.jar with Carpetmod files..."
(cd carpetmod && zip -r ../base.jar .)

# Recompress and optimize the JAR
unzip -qo base.jar -d tmp
rm -rf base.jar
(cd tmp && zip -r -FS ../base.jar .)
rm -rf tmp

echo "✅ base.jar update completed!"

# ==============================
# Prepare proxy folder
# ==============================
rm -rf "$PROXY_DIR"
mkdir -p "$PROXY_DIR/in" "$PROXY_DIR/out"

cd "$PROXY_DIR" || exit
cp "$CARPETMOD_ZIP" ./out/patcher.zip
cp ../base.jar ./in/carpet1122_ctec_"${VERSION}"_proxy.jar
cp ../base.jar ./out/carpet1122_ctec_"${VERSION}".jar
cp ../base.jar ./out/base.zip

# ==============================
# Run VanillaCord patcher
# ==============================
echo "🚀 Running VanillaCord patcher..."
java -jar ../../../../.github/workflows/scripts/VanillaCord.jar carpet1122_ctec_"${VERSION}"_proxy

# ==============================
# Copy output
# ==============================
rm -rf "$BUILD_ROOT_DIR/out"
mkdir -p "$BUILD_ROOT_DIR/out"
cp ./out/* "$BUILD_ROOT_DIR/out"

echo "🎉 Build completed! Output is in $BUILD_ROOT_DIR/out"
