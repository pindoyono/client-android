# Fix Gradle JVM Incompatibility

The project is currently using Gradle 8.9, which is incompatible with the selected JVM version 25 in your Android Studio. Gradle 8.9 supports Java versions up to 22.

## User Review Required

> [!IMPORTANT]
> To fully resolve this issue, you MUST click **"Use JVM 21"** in the notification banner shown in your Android Studio. This will configure the IDE to use a compatible JDK for running Gradle.

## Proposed Changes

### Build Configuration

#### [MODIFY] [gradle-wrapper.properties](file:///D:/Project Absensi/client-android/gradle/wrapper/gradle-wrapper.properties)
- Update Gradle version from `8.9` to `8.10.2` to ensure better compatibility with newer JDKs and fix potential bugs.

## Verification Plan

### Manual Verification
- After I update the wrapper and you click "Use JVM 21", run **Sync Project with Gradle Files** to verify that the project imports successfully.
