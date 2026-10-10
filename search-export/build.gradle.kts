plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.dublikunt.dmclient.searchexport.MainKt")
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlin.serialization)
    implementation(libs.json.jvm)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation(libs.junit4)
}
