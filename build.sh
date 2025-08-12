#!/bin/bash

./gradlew setupCarpetmod
./gradlew createRelease

rm -rf build/tmp/fullRelease
mkdir -p build/tmp/fullRelease

cd build/tmp/fullRelease || exit
cp ~/.gradle/caches/minecraft/net/minecraft/minecraft_server/1.12.2/minecraft_server-1.12.2.jar ./base.jar

mkdir -p carpetmod

echo "📦 Extracting CARPET1122-CTEC zip to carpetmod directory..."
unzip -qo ../../distributions/Carpetmod_dev.zip -d carpetmod

echo "🔄 Updating base.jar with Carpetmod files..."
zip -ur base.jar carpetmod/*

echo "✅ base.jar update completed! Preparing proxy folder..."

mkdir -p proxy/in proxy/out

cd proxy || exit

cp ../../../distributions/Carpetmod_dev.zip ./out/patcher.zip
cp ../base.jar ./in/carpet1122_ctec_"${VERSION}"_proxy.jar
cp ../base.jar ./out/carpet1122_ctec_"${VERSION}".jar
cp ../base.jar ./out/base.zip

echo "🚀 Running VanillaCord patcher..."
java -jar ../../../../.github/workflows/scripts/VanillaCord.jar carpet1122_ctec_"${VERSION}"_proxy

mkdir ../../../out
cp ./out/* ../../../out
