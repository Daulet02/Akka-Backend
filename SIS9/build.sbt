name := "SIS9"

version := "0.1"

scalaVersion := "2.13.3"

val AkkaVersion = "2.6.10"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream-kafka" % "2.0.5",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)
