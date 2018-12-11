val lagomVersion = "1.4.9"

val typesafeConfig = "com.typesafe" % "config" % "1.3.3"
val lagomJavadslClient = "com.lightbend.lagom" %% "lagom-javadsl-client" % lagomVersion
val lagomScaladslClient = "com.lightbend.lagom" %% "lagom-scaladsl-client" % lagomVersion
val consulApi = "com.ecwid.consul" % "consul-api" % "1.4.2"
val scalatest = "org.scalatest" %% "scalatest" % "3.0.3" % Test

lazy val `lagom-service-locator-consul` = (project in file("."))
  .aggregate(
    `lagom-service-locator-javadsl-consul`,
    `lagom-service-locator-scaladsl-consul`
  )

def common = Seq(
  organization := "com.lightbend.lagom",
  version := "1.4.0-SNAPSHOT",
  scalaVersion := "2.12.7"
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
