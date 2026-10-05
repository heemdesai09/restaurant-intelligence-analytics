name := "restaurant-intelligence-analytics"
version := "1.0.0"
scalaVersion := "3.3.3"

// Enable standard input in sbt run for menu-driven CLI
run / connectInput := true
fork := true

libraryDependencies ++= Seq(
  // MongoDB Official Java/Scala Sync Driver
  "org.mongodb" % "mongodb-driver-sync" % "4.11.1",
  
  // Fast, lightweight JSON serialization
  "com.lihaoyi" %% "upickle" % "3.3.0"
)

assembly / mainClass := Some("restaurant.Main")
assembly / assemblyJarName := "app.jar"

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.defaultMergeStrategy(x)
}

