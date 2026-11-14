// 순수 Kotlin 도메인 모듈: 승차 지점 데이터, 추천 알고리즘, 음성 문장 해석, 픽업 상태 머신
plugins {
    id("org.jetbrains.kotlin.jvm")
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { jvmToolchain(17) }
dependencies {
    testImplementation("junit:junit:4.13.2")
}
