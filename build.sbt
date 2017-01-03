organization := "com.lightbend.lagom"

name := "lagom-service-locator-consul"

version := "1.0.1-SNAPSHOT"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.lightbend.lagom" %% "lagom-javadsl-api" % "1.2.0",
  "com.ecwid.consul"     % "consul-api"        % "1.1.10",
  "org.scalatest"       %% "scalatest"         % "2.2.4" % Test
)
