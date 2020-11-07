import sbt._

object Dependencies {

  object Versions {
    val scala           = "2.13.3"
    val akkaVersion     = "2.6.10"
    val akkaHttpVersion = "10.2.1"
    val akkaJson4s      = "1.35.0"
    val json4s          = "3.6.9"
    val logback         = "1.2.3"
    val mockito         = "1.14.+"
    val scalacheck      = "1.14.+"
    val scalatest       = "3.1.+"
    val scalamock       = "4.4.+"
    val pureconfig      = "0.14.+"
  }

  val ServerCoreDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka"     %% "akka-actor-typed"            % Versions.akkaVersion,
    "com.typesafe.akka"     %% "akka-cluster-sharding-typed" % Versions.akkaVersion,
    "com.typesafe.akka"     %% "akka-serialization-jackson"  % Versions.akkaVersion,
    "com.typesafe.akka"     %% "akka-slf4j"                  % Versions.akkaVersion,
    "com.typesafe.akka"     %% "akka-http"                   % Versions.akkaHttpVersion,
    "org.json4s"            %% "json4s-jackson"              % Versions.json4s,
    "org.json4s"            %% "json4s-ext"                  % Versions.json4s,
    "de.heikoseeberger"     %% "akka-http-json4s"            % Versions.akkaJson4s,
    "ch.qos.logback"        % "logback-classic"              % Versions.logback,
    "com.github.pureconfig" %% "pureconfig"                  % Versions.pureconfig
  )
  val ServerTestDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % Versions.akkaVersion % Test,
    "com.typesafe.akka" %% "akka-multi-node-testkit"  % Versions.akkaVersion % Test,
    "org.scalatest"     %% "scalatest"                % Versions.scalatest   % Test,
    "org.scalamock"     %% "scalamock"                % Versions.scalamock   % Test,
    "org.scalacheck"    %% "scalacheck"               % Versions.scalacheck  % Test,
    "org.mockito"       %% "mockito-scala"            % Versions.mockito     % Test,
    "org.mockito"       %% "mockito-scala-scalatest"  % Versions.mockito     % Test
  )

}
