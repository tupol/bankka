import Dependencies._

lazy val commonSettings = Seq(
  organization := "org.tupol",
  name := "takkagotchi",
  scalaVersion := Versions.scala,
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-unchecked",
    "-Ywarn-unused:imports"
  )
)

lazy val server = (project in file("server"))
  .settings(commonSettings: _*)
  .settings(
    name := "takkagotchi-server",
    version := "0.0.1",
    libraryDependencies ++= ServerCoreDependencies,
    libraryDependencies ++= ServerTestDependencies,
  )

lazy val client = (project in file("client"))
  .settings(commonSettings: _*)
  .settings(
    name := "takkagotchi-client"
  )

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(client)
  .aggregate(server)
  .settings(name := "takkagotchi")
