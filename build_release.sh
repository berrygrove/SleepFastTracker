#!/bin/bash

# SleepFastTracker Release Build Script
# This script builds a release APK for the SleepFastTracker application
# Note: This app only supports Android 14 (API level 34) and newer
# The app is signed with the release key from keystore/sleepfasttracker.jks

echo "===== SleepFastTracker Release Build Script ====="
echo "Starting build process for Android 14+ only app..."

# Display usage information
usage() {
    echo "Usage: $0 [OPTION]"
    echo "Options:"
    echo "  --install     Build and install the app on a connected device"
    echo "  --store       Build and store a timestamped APK in the apk directory"
    echo "  --help        Display this help message"
    exit 1
}

# Check for help flag
if [ "$1" == "--help" ]; then
    usage
fi

# Navigate to the project directory (in case the script is run from elsewhere)
cd "$(dirname "$0")"

# Check and set up Android SDK path
setup_android_sdk() {
    # Check if ANDROID_HOME is set
    if [ -z "$ANDROID_HOME" ]; then
        # Try common SDK locations
        if [ -d "$HOME/Android/Sdk" ]; then
            export ANDROID_HOME="$HOME/Android/Sdk"
        elif [ -d "/usr/lib/android-sdk" ]; then
            export ANDROID_HOME="/usr/lib/android-sdk"
        else
            echo "ERROR: Android SDK not found!"
            echo "Please set ANDROID_HOME environment variable or install Android Studio"
            exit 1
        fi
    fi

    # Create or update local.properties
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    echo "Using Android SDK at: $ANDROID_HOME"
}

# Verify keystore exists
verify_keystore() {
    if [ ! -f "keystore/sleepfasttracker.jks" ]; then
        echo "ERROR: Keystore file not found at keystore/sleepfasttracker.jks"
        echo "Please ensure you have created the keystore correctly."
        exit 1
    fi
    echo "Found signing keystore at keystore/sleepfasttracker.jks"
}

# Ensure apk directory exists
ensure_apk_dir() {
    if [ ! -d "apk" ]; then
        echo "Creating apk directory..."
        mkdir -p apk
    fi
}

# Set up Android SDK path
setup_android_sdk

# Verify keystore exists
verify_keystore

# Ensure apk directory exists
ensure_apk_dir

# Clean the project
echo "Cleaning project..."
./gradlew clean

# Build the signed release APK
echo "Building signed release APK for Android 14+ (API 34+)..."
./gradlew assembleRelease

# Check if the build was successful
if [ $? -eq 0 ]; then
    echo "Build completed successfully!"
    
    # Find the APK file
    APK_PATH=$(find app/build/outputs/apk/release -name "app-release.apk" | head -n 1)
    
    if [ -n "$APK_PATH" ]; then
        echo "APK created at: $APK_PATH"
        echo "APK size: $(du -h "$APK_PATH" | cut -f1)"
        
        # Display app signing info
        echo "App signing information:"
        echo "  Keystore: keystore/sleepfasttracker.jks"
        echo "  Alias: sft"
        
        # Store the APK in the apk directory with timestamp
        if [ "$1" == "--store" ]; then
            TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
            VERSION=$(grep -E "versionName" app/build.gradle | sed -E 's/.*versionName "([^"]+)".*/\1/')
            APK_FILENAME="SleepFastTracker_v${VERSION}_${TIMESTAMP}.apk"
            
            echo "Storing signed APK in apk directory..."
            cp "$APK_PATH" "apk/$APK_FILENAME"
            
            if [ $? -eq 0 ]; then
                echo "APK stored successfully at: apk/$APK_FILENAME"
            else
                echo "Failed to store APK in the apk directory."
            fi
        fi
        
        # Auto-install the APK on a connected device
        if [ "$1" == "--install" ]; then
        echo "Attempting to install APK on connected device..."
        
        # Check if adb is available
        if ! command -v adb &> /dev/null; then
            echo "ERROR: ADB (Android Debug Bridge) is not installed or not in your PATH."
            echo "Please install Android SDK Platform Tools:"
            echo "  - Ubuntu/Debian: sudo apt install android-tools-adb"
            echo "  - Fedora: sudo dnf install android-tools"
            echo "  - Arch Linux: sudo pacman -S android-tools"
            echo ""
            echo "After installing, make sure:"
            echo "1. Developer options are enabled on your phone (tap Build number 7 times in Settings > About phone)"
            echo "2. USB debugging is enabled in Developer options"
            echo "3. Your phone is connected via USB"
            echo "4. You've accepted the USB debugging prompt on your phone"
            exit 1
        fi
        
        # Check if any devices are connected
        DEVICES=$(adb devices | grep -v "List" | grep -v "^$" | wc -l)
        if [ "$DEVICES" -eq 0 ]; then
            echo "ERROR: No Android devices connected or authorized."
            echo "Please make sure:"
            echo "1. Your phone is connected via USB"
            echo "2. USB debugging is enabled in Developer options"
            echo "3. You've accepted the USB debugging prompt on your phone"
            echo "4. Your phone is in file transfer mode (not just charging)"
            exit 1
        fi
        
        # Check Android version before installing
        echo "Checking device Android version..."
        SDK_VERSION=$(adb shell getprop ro.build.version.sdk)
        if [ "$SDK_VERSION" -lt 34 ]; then
            echo "WARNING: Connected device is running Android $(adb shell getprop ro.build.version.release) (API $SDK_VERSION)"
            echo "This app requires Android 14 (API 34) or newer. Installation may fail."
        else
            echo "Device is running Android $(adb shell getprop ro.build.version.release) (API $SDK_VERSION), which is compatible."
        fi
        
        # Install the APK
        echo "Installing signed APK on device..."
        adb install -r "$APK_PATH"
        
        if [ $? -eq 0 ]; then
            echo "APK installed successfully!"
            # Launch the app
            echo "Launching SleepFastTracker..."
            adb shell am start -n "nl.berrygrove.sft/.MainActivity"
        else
            echo "Failed to install APK. This might be because your device is not running Android 14 or newer."
            fi        
        fi
    else
        echo "Error: APK file not found!"
    fi
else
    echo "Build failed! Check the logs for errors."
fi

echo "===== Build process completed =====" 
