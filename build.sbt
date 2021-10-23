name := "akka-typed"

version := "0.1"

scalaVersion := "2.13.6"

val AkkaVersion = "2.6.16"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
 "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.4" % Test

)
