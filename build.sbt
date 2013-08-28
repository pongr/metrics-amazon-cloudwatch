name := "metrics-amazon-cloudwatch"

organization := "com.pongr"

version := "0.1-SNAPSHOT"

scalaVersion := "2.9.1"

resolvers ++= Seq(
  "Sonatype" at "https://oss.sonatype.org/content/groups/public",
  "typesafe repo"   at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2" % "1.12.4" % "test",
  "org.mockito" % "mockito-all" % "1.9.5" % "test",
  "nl.grons" %% "metrics-scala" % "3.0.3",
  "org.clapper" %% "grizzled-slf4j" % "0.6.10",
  "com.amazonaws" % "aws-java-sdk" % "1.5.4"
)

publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots/")
  else                             Some("releases" at nexus + "service/local/staging/deploy/maven2/")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("Apache-2.0" -> url("http://opensource.org/licenses/Apache-2.0"))

homepage := Some(url("http://github.com/pongr/metrics-amazon-cloudwatch"))

organizationName := "Pongr"

organizationHomepage := Some(url("http://pongr.com"))

description := "Amazon Cloud watch reporter for metrics"
