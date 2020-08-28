import com.typesafe.sbt.packager.docker.{ Cmd, CmdLike, ExecCmd }

val akkaHttpVersion   = "10.1.12"
val akkaVersion       = "2.6.8"
val slickVersion      = "3.3.2"
val zioVersion        = "1.0.0"
val zioLoggingVersion = "0.4.0"
val zioConfigVersion  = "1.0.0-RC26"

val dockerReleaseSettings = Seq(
  dockerExposedPorts := Seq(8080),
  dockerExposedVolumes := Seq("/opt/docker/logs"),
  dockerBaseImage := "adoptopenjdk/openjdk12:x86_64-ubuntu-jre-12.0.2_10"
)

val root = (project in file("."))
  .settings(
    inThisBuild(
      List(
        organization := "com.example",
        scalaVersion := "2.13.2"
      )
    ),
    name := "zio-akka-slick-sample",
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka"     %% "akka-http"                   % akkaHttpVersion,
      "de.heikoseeberger"     %% "akka-http-play-json"         % "1.33.0",
      "com.typesafe.akka"     %% "akka-actor-typed"            % akkaVersion,
      "com.typesafe.akka"     %% "akka-stream"                 % akkaVersion,
      "com.typesafe.slick"    %% "slick"                       % slickVersion,
      "com.typesafe.slick"    %% "slick-hikaricp"              % slickVersion,
      "dev.zio"               %% "zio"                         % zioVersion,
      "dev.zio"               %% "zio-config"                  % zioConfigVersion,
      "dev.zio"               %% "zio-config-magnolia"         % zioConfigVersion,
      "dev.zio"               %% "zio-config-typesafe"         % zioConfigVersion,
      "io.scalac"             %% "zio-akka-http-interop"       % "0.2.0",
      "io.scalac"             %% "zio-slick-interop"           % "0.2.0",
      "dev.zio"               %% "zio-interop-reactivestreams" % "1.0.3.5",
      "ch.qos.logback"        % "logback-classic"              % "1.2.3",
      "dev.zio"               %% "zio-logging"                 % zioLoggingVersion,
      "dev.zio"               %% "zio-logging-slf4j"           % zioLoggingVersion,
      "com.h2database"        % "h2"                           % "1.4.200",
      "com.typesafe.akka"     %% "akka-http-testkit"           % akkaHttpVersion % Test,
      "com.typesafe.akka"     %% "akka-stream-testkit"         % akkaVersion % Test,
      "com.typesafe.akka"     %% "akka-actor-testkit-typed"    % akkaVersion %  Test,
      "dev.zio"               %% "zio-test-sbt"                % zioVersion % Test
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    dockerReleaseSettings
  )
  .enablePlugins(DockerPlugin, JavaAppPackaging)
