val settings = Seq(
  name := "micromarshal",
  organization := "com.tkroman",
  scalaVersion := "2.12.1",
  scalaOrganization := "org.typelevel",
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := {
    <url>http://github.com/tkroman/micromarshal</url>
    <licenses>
      <license>
        <name>MIT</name>
        <url>http://opensource.org/licenses/MIT</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:github.com/tkroman/micromarshal.git</connection>
      <developerConnection>scm:git:git@github.com:tkroman/micromarshal.git</developerConnection>
      <url>https://github.com/tkroman/micromarshal</url>
    </scm>
    <developers>
      <developer>
        <id>tkroman</id>
        <name>Roman Tkalenko</name>
        <email>rmn.tk.ml@gmail.com</email>
        <url>https://github.com/tkroman</url>
      </developer>
    </developers>
  },
  libraryDependencies += "org.scalameta" %% "scalameta" % scalametaV % Provided,
  libraryDependencies += "com.typesafe.akka" %% "akka-http" % akkaHttpV,
  libraryDependencies += "com.lihaoyi" %% "upickle" % upickleV,
  resolvers += Resolver.sonatypeRepo("releases"),
  resolvers += Resolver.bintrayIvyRepo("scalameta", "maven"),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.patch),
  libraryDependencies += scalaOrganization.value % "scala-compiler" % scalaVersion.value % "provided",
  libraryDependencies += scalaOrganization.value % "scala-reflect" % scalaVersion.value % "provided",
  addCompilerPlugin("org.scalameta" % "paradise" % "3.0.0-M7" cross CrossVersion.full),
  scalacOptions += "-Xplugin-require:macroparadise",
  scalacOptions in (Compile, console) := Seq(),
  sources in (Compile, doc) := Nil
)

settings

lazy val akkaHttpV = "10.0.4"
lazy val upickleV = "0.4.4"
lazy val scalametaV = "1.6.0"

lazy val micromarshal = (project in file("."))
  .settings(settings: _*)
