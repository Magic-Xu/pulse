plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(11)
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

tasks.named<Test>("test") {
    failOnNoDiscoveredTests = false
}
