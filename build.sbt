import Dependencies._

lazy val commonSettings = Seq(
  organization := "org.tupol",
  name := "bankka",
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
    name := "bankka-server",
    version := "0.0.1",
    libraryDependencies ++= ServerCoreDependencies,
    libraryDependencies ++= ServerTestDependencies,
  )

lazy val client = (project in file("client"))
  .settings(commonSettings: _*)
  .settings(
    name := "bankka-client"
  )

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(client)
  .aggregate(server)
  .settings(name := "bankka")
