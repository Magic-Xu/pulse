plugins {
    id("org.jetbrains.kotlin.jvm")
}

description = "Pulse contract definitions: Intent/State/Effect/Reducer/Store."

kotlin {
    jvmToolchain(11)
}

dependencies {
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}

val testSourceSet = sourceSets.named("test")

tasks.register<JavaExec>("contractSelfCheck") {
    group = "verification"
    description = "Runs contract invariants without external test frameworks."
    classpath = testSourceSet.get().runtimeClasspath
    mainClass.set("com.magic.mvicore.contract.ContractSelfCheckKt")
}

tasks.named("check") {
    dependsOn("contractSelfCheck")
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
