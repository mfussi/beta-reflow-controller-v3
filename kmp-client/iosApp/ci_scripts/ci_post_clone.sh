#!/bin/sh
#build firebase file in iosApp directory
cd ..

echo "generate firebase.json"
echo "${FIREBASE_SERVICE_FILE}" | base64 -d >> firebase.json

# build files for gradle in parent directory
cd ..

echo "generate signing.properties"
echo "${SIGNING_FILE}" | base64 -d >> signing.properties

echo "generate secure.properties"
echo "${SECURE_FILE}" | base64 -d >> secure.properties

echo "append maven login to gradle.properties"
echo "${GRADLE_FILE}" | base64 -d >> gradle.properties

echo "installing java"
brew install openjdk@21
