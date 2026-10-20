# Lailaba AI Device Agent for Android (starter project)

A native Android Studio project for an Android companion to Lailaba CLI. This is source code, not a prebuilt APK. Build it with Android Studio or Gradle on a machine with Android SDK platform 35 installed.

## Build a debug APK

1. Install Android Studio and Android SDK Platform 35 / Build Tools.
2. Open this directory (`lailaba-android`) in Android Studio and allow Gradle sync.
3. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. The debug APK will normally be at `app/build/outputs/apk/debug/app-debug.apk`.

Alternatively, with Gradle installed and Android SDK configured:

```bash
gradle assembleDebug
```

## What this starter does

- Native Android chat screen with black `#000000`, charcoal `#2b2b2b`, and white styling.
- OpenAI-compatible API configuration, defaulting to Lailaba AI. API keys are encrypted using an AES-GCM key held in Android Keystore.
- `/models` validation and automatic first-model discovery when no model is configured.
- Agent tool-calling loop for contact search, inbox SMS search, dialing, composing SMS, opening Android settings, launching an app by package name, and optional visible-label tapping through Accessibility.
- Explicit approval dialog before each requested device tool action.
- Contacts and SMS permissions are requested by the user through separate buttons.
- Calling opens the system dialer; it does not place a call automatically. Sending SMS opens the user's messaging app with a draft; the user reviews and taps Send.
- Optional Accessibility service is not active unless the user explicitly enables it in Android Accessibility Settings.

## Important Android limitations

No ordinary Android app can obtain unrestricted access to every app or setting. Android's app sandbox, permission system, protected screens, OEM variations, and Play Store policy apply. Accessibility can interact only with UI exposed by the active app and is not guaranteed to work in every app. Secure windows and protected content may be inaccessible.

Reading SMS is sensitive and can be restricted by Google Play distribution rules. For a public Play Store release, review the current SMS/Call Log permissions policy and consider omitting direct SMS reading in favor of user-selected sharing or notification access. This project is a development starter and has not been security-reviewed or tested on physical devices.

Before publishing, add a privacy policy, clear disclosure, data minimization, stronger API endpoint allow-listing or certificate strategy, comprehensive error handling, and device/OEM testing. Never store API keys in source control. Do not use Accessibility to bypass another app's security controls or automate sensitive actions without user approval.

## Current scope

This is a functional starter for permissioned device actions, not a claim of full Android device control or full Hermes Agent parity. The agent API must support OpenAI-compatible `/chat/completions` tool calling for autonomous tool routing. The current implementation requires online connectivity to the configured provider.

## Codemagic build

A `codemagic.yaml` workflow is included at the project root. Commit the project contents to the repository root, connect the repository in Codemagic, and run the `android-debug` workflow. Download `app-debug.apk` from the build artifacts. The build must be verified in Codemagic; the current working environment does not have Gradle or the Android SDK installed.

## Resource-linking fix

The accessibility service XML uses `@string/accessibility_service_description`, defined in `app/src/main/res/values/strings.xml`, to avoid the AAPT error caused by a literal `android:description` value.

## Expanded permission manifest

The manifest declares every unique `android.permission.*` name in the supplied 28-category reference. This is intentionally a broad development reference, not a guarantee that every capability is available. Android can ignore, deny, or restrict permissions based on OS version, app role, installer, signing certificate, device-owner provisioning, and system privileges. Runtime and special-access permissions still require appropriate user approval. Service-binding permissions are effective only when used on the relevant service component; declaring them as `uses-permission` does not grant the app the ability to bind to privileged system services.

This project does not silently enable Accessibility, device-admin, VPN, notification-listener, or other special access. Users must enable supported services in Android Settings. Do not use these capabilities without clear disclosure and consent.
