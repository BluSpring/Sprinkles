
dependencies {
    implementation(project(":common"))

    api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    //api("org.kitteh.irc:client-lib:8.0.0")
    // fork of the IRC lib that fixes the UUID error
    api("com.github.rakambda:kittehircclientlib:d40278091e")

    // i thought it would be incredibly funny if a Twitch chatbot
    // used a Minecraft command dispatcher
    api("com.mojang:brigadier:1.1.8")
}