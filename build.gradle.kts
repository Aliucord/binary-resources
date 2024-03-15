plugins {
    id("maven-publish")
    id("com.android.library") version "8.3.0"
}

version = "1.0.0"

android {
    compileSdk = 34
    namespace = "com.aliucord.binaryresources"

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = false
        resValues = false
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    api("com.google.guava:guava:33.0.0-android")
    implementation("androidx.collection:collection:1.4.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

afterEvaluate {
    publishing {
        publications {
            register(project.name, MavenPublication::class.java) {
                groupId = "com.aliucord"
                artifactId = "binary-resources"

                from(components["release"])
            }
        }

        repositories {
            val username = System.getenv("MAVEN_USERNAME")
            val password = System.getenv("MAVEN_PASSWORD")

            if (username != null && password != null) {
                maven {
                    credentials {
                        this.username = username
                        this.password = password
                    }
                    setUrl("https://maven.aliucord.com/snapshots")
                }
            } else {
                mavenLocal()
            }
        }
    }
}
