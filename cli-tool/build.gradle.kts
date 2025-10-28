plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":parser"))
}

application {
    mainClass = "com.example.emvnfc.cli.MainKt"
}
