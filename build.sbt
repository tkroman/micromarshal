lazy val akkaHttpV = "10.0.4"
lazy val upickleV = "0.4.4"
lazy val scalametaV = "1.6.0"

val settings = Seq(
  name := "micromarshal",
  organization := "com.tkroman",
  version := "0.0.6",

  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.11.8", "2.12.1"),

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

  resolvers += Resolver.sonatypeRepo("releases"),
  resolvers += Resolver.bintrayIvyRepo("scalameta", "maven"),

  libraryDependencies += "org.scalameta" %% "scalameta" % scalametaV % Provided,
  libraryDependencies += "com.typesafe.akka" %% "akka-http" % akkaHttpV,
  libraryDependencies += "com.lihaoyi" %% "upickle" % upickleV,

  addCompilerPlugin("org.scalameta" % "paradise" % "3.0.0-M7" cross CrossVersion.full),

  scalacOptions += "-Xplugin-require:macroparadise",
  scalacOptions in (Compile, console) := Seq(),

  sources in (Compile, doc) := Nil,

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
  }
)

settings

lazy val micromarshal = (project in file("."))
  .settings(settings: _*)
