import Dependencies._

ThisBuild / scalaVersion := "2.12.10"
ThisBuild / version := "0.1.0-SNAPSHOT"

//-XX:MetaspaceSize=
scalacOptions ++= Seq("-Xfatal-warnings")

lazy val root = (project in file("."))
  .settings(
    name := "zio-akka-slick-sample",
    libraryDependencies ++= Seq(
        akkaHttp,
        akkaStream,
        sprayJson,
        scalazZio,
        scalazZioRS,
        scalazZioIntRS,
        slick,
        h2,
        logback,
        akkaSlf4j,
        scalaTest        % Test,
        scalaTestMockito % Test,
        akkaTestkit      % Test,
        akkaHttpTestkit  % Test
      )
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
