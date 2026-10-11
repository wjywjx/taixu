plugins {
    alias(libs.plugins.taixu.android.feature)
}

android {
    namespace = "top.wkbin.taixu.feature.custom_iteration"
}

dependencies {
    testImplementation(libs.junit)
}

// [临时·仅本分支] 让 CI 日志逐条打印单元测试结果。
// 注意：接收者必须是 Test 类型，tasks.matching 只能给出 Task，无法解析 testLogging 扩展。
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    if (name == "testDebugUnitTest") {
        testLogging {
            events("passed", "failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
