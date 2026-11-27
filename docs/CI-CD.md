# CI and release automation

Pull requests to main, develop, sprint and release branches run the Android/JVM and native iOS gates. A maintainer can also run the workflow manually. The macOS job runs shared Kotlin simulator tests and builds the SwiftUI app. The Android job runs tests and assembles debug and minified APKs.

Android verification APKs stay in the Actions run for 14 days. The current release build configuration uses debug signing. These artifacts are for verification.

A push of a version tag on a commit already contained in main runs both jobs and creates a GitHub draft release with generated notes after they pass. The workflow checks the tag against the commit and leaves an existing release intact. Review the draft before publication. Signed distribution artifacts, Play internal testing and TestFlight require separate store access, signing assets and an approved publishing configuration.

Require Android build and JVM tests and iOS SwiftUI build and simulator tests in GitHub rules after both checks have passed on the active PR paths. Keep production publication under owner approval.
