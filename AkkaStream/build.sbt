name := "AkkaStream"

version := "0.1"

scalaVersion := "2.13.3"

val AkkaVersion = "2.6.10"

libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream-kafka" % "2.0.5"