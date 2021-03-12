import Dependencies._

ThisBuild / scalaVersion     := "2.13.4"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "uk.gov.nationalarchives"
ThisBuild / organizationName := "db.users"

lazy val root = (project in file("."))
  .settings(
    name := "tdr-create-db-users",
    libraryDependencies ++= Seq(
      pureConfig,
      postgres,
      scalikeJdbc,
      ssm,
      scalaTest % Test,
      wiremock % Test,
      h2 % Test
    )
  )

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

assemblyJarName in assembly := "createdbuser.jar"