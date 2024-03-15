# binary-resources ![Maven version](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.aliucord.com%2Fsnapshots%2Fcom%2Faliucord%2Fbinary-resources%2Fmaven-metadata.xml)

This is a fork of Android's tools/base/apkparser/binary-resources (as part of ArscBlamer)
made to be usable on Android devices directly as a lightweight library.

## Usage
`build.gradle.kts`:
```kt
respositories {
    maven("https://maven.aliucord.com/snapshots")
}
dependencies {
    implementation("com.aliucord:binary-resources:1.0.0")
}
```