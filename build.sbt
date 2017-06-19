import sbt.Keys.version

organization := "com.lightbend.lagom"

name := "lagom-service-locator-consul"


val lagomVersion = "1.3.5"

val typesafeConfig = "com.typesafe" % "config" % "1.3.1"
val lagomJavadslClient = "com.lightbend.lagom" %% "lagom-javadsl-client" % lagomVersion
val lagomScaladslClient = "com.lightbend.lagom" %% "lagom-scaladsl-client" % lagomVersion
val consulApi = "com.ecwid.consul" % "consul-api" % "1.1.10"
val scalatest = "org.scalatest" %% "scalatest" % "3.0.1" % Test


lazy val `lagom-service-locator-consul` = (project in file("."))
  .aggregate(
    `lagom-service-locator-javadsl-consul`,
    `lagom-service-locator-scaladsl-consul`
  )

def common = Seq(
  scalaVersion := "2.11.11",
  version := "1.4.0-SNAPSHOT"
)

lazy val `lagom-service-locator-javadsl-consul` = (project in file("lagom-service-locator-javadsl-consul"))
  .settings(common)
  .settings(
    libraryDependencies ++= Seq(
      lagomJavadslClient,
      consulApi,
      scalatest
    )
  )

lazy val `lagom-service-locator-scaladsl-consul` = (project in file("lagom-service-locator-scaladsl-consul"))
  .settings(common)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslClient,
      consulApi,
      scalatest
    )
  )

