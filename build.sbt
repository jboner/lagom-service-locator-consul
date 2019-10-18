val lagomVersion = "1.5.4"

val typesafeConfig = "com.typesafe" % "config" % "1.4.0"
val lagomJavadslClient = "com.lightbend.lagom" %% "lagom-javadsl-client" % lagomVersion
val lagomScaladslClient = "com.lightbend.lagom" %% "lagom-scaladsl-client" % lagomVersion
val consulApi = "com.ecwid.consul" % "consul-api" % "1.4.2"
val scalatest = "org.scalatest" %% "scalatest" % "3.0.3" % Test

lazy val `lagom-service-locator-consul` = (project in file("."))
  .aggregate(
    `lagom-service-locator-javadsl-consul`,
    `lagom-service-locator-scaladsl-consul`
  )

organization in ThisBuild := "com.lightbend.lagom"
version in ThisBuild := "1.4.0-SNAPSHOT"
scalaVersion in ThisBuild := "2.12.7"
credentials in ThisBuild += Credentials(Path.userHome / ".ivy2" / "gv-credentials")
publishTo in ThisBuild := Some("Getvisibility artefacts" at "https://registry2.getvisibility.com/artifactory/ivy-dev/")


lazy val `lagom-service-locator-scaladsl-consul` = (project in file("lagom-service-locator-scaladsl-consul"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslClient,
      consulApi,
      scalatest
    )
  )

lazy val `lagom-service-locator-javadsl-consul` = (project in file("lagom-service-locator-javadsl-consul"))
  .settings(
    libraryDependencies ++= Seq(
      lagomJavadslClient,
      consulApi,
      scalatest
    )
  )

