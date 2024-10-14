repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))

    api("net.dv8tion:JDA:5.1.2") {
        exclude(module = "opus-java")
    }
    api("xyz.artrinix:aviation:7d1dcef7")
    api("club.minnced:jda-ktx:0.12.0")
}