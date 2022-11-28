ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.1"

lazy val root = (project in file("."))
  .settings(
    name := "fs2-training",
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % "3.4.0",
      "co.fs2" %% "fs2-io"   % "3.4.0"
    )
  )
