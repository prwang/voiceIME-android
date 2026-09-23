# The Android Gradle plugin generates keep rules for manifest components.
# The accessibility InputMethod is created directly by the service, so its
# reachable implementation is retained by R8 without keeping the entire package.
