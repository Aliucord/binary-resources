plugins {
    id("maven-publish")
    id("com.android.library") version "8.2.1"
}

group = "com.aliucord.apkparser"
version = "1.0.0"

android {
    compileSdk = 34
    namespace = "com.aliucord.apkparser.binaryresources"

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
        repositories {
            val sonatypeUsername = System.getenv("SONATYPE_USERNAME")
            val sonatypePassword = System.getenv("SONATYPE_PASSWORD")

            if (sonatypeUsername == null || sonatypePassword == null)
                mavenLocal()
            else {
                maven {
                    name = "sonatype"
                    credentials {
                        username = sonatypeUsername
                        password = sonatypePassword
                    }
                    setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                }
            }
        }

        publications {
            register(project.name, MavenPublication::class.java) {
                from(components["release"])
            }
        }
    }
}
