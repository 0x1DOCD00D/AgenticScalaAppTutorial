// Packages the app as a Docker image (JavaAppPackaging + DockerPlugin).
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.1")

// Deterministic formatting — enforced by a Claude Code PostToolUse hook.
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.5")
