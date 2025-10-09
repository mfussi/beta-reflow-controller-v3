#!/bin/sh

cd ../fastlane

bundle install --path vendor/bundle --verbose

bundle exec fastlane firebase_upload FIREBASE_APP_ID:$FIREBASE_APP_ID service_credentials_file:firebase.json TEST_GROUPS:$TEST_GROUPS
