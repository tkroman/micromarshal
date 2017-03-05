name := "upickle-akka"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

lazy val commonSettings = Seq(
  scalaVersion in ThisBuild := "2.12.1",
  scalaOrganization in ThisBuild := "org.typelevel",
  version in ThisBuild := "0.0.1",
  organization in ThisBuild := "com.tkroman"
)

lazy val akkaHttpV = "10.0.4"
lazy val upickleV = "0.4.4"
lazy val scalametaV = "1.6.0"

lazy val metaMacroSettings: Seq[Def.Setting[_]] = Seq(
  resolvers += Resolver.sonatypeRepo("releases"),
  resolvers += Resolver.bintrayIvyRepo("scalameta", "maven"),
  addCompilerPlugin("org.scalameta" % "paradise" % "3.0.0-M7" cross CrossVersion.full),
  scalacOptions += "-Xplugin-require:macroparadise",
  scalacOptions in (Compile, console) := Seq(), // macroparadise plugin doesn't work in repl yet.
  sources in (Compile, doc) := Nil // macroparadise doesn't work with scaladoc yet.
)

lazy val macros = (project in file("macros")).settings(
  commonSettings,
  metaMacroSettings,
  libraryDependencies += "org.scalameta" %% "scalameta" % scalametaV,
  libraryDependencies += "com.typesafe.akka" %% "akka-http" % akkaHttpV,
  libraryDependencies += "com.lihaoyi" %% "upickle" % upickleV
)

lazy val core = (project in file("core"))
  .settings(metaMacroSettings)
  .dependsOn(macros)

lazy val root = project
  .in(file("."))
  .aggregate(core, macros)
  .dependsOn(core)
  .settings(
    publishMavenStyle := false
  )
