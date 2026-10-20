# Android resource-linking fix

The accessibility service description must reference a string resource. The service config now uses:

`android:description="@string/accessibility_service_description"`

The resource is defined in `app/src/main/res/values/strings.xml`.

This addresses the previously reported AAPT resource-linking error. It does not prove that the project compiles; run a clean Android build with Android SDK Platform 35 and Java 17, then review any subsequent errors.
