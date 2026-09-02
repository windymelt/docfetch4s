import scala.scalanative.build.*

val scala3Version = "3.8.4"

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaNativePlugin)
  .settings(
    name         := "docfetch4s",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all"),
    libraryDependencies ++= Seq(
      "org.http4s"    %% "http4s-ember-client" % "0.23.36",
      "org.typelevel" %% "cats-effect"         % "3.7.0",
      "com.monovore"  %% "decline-effect"      % "2.6.2",
      "com.lihaoyi"   %% "upickle"             % "4.4.3",
      "com.lihaoyi"   %% "os-lib"              % "0.11.8",
      "org.scalameta" %% "munit"               % "1.3.5" % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    // debug モードは起動が速くビルドも短い。配布用は `sbt -Ddocfetch4s.release=true nativeLink`
    nativeConfig ~= { c =>
      val release = sys.props.get("docfetch4s.release").contains("true")
      c.withLTO(if (release) LTO.thin else LTO.none)
        .withMode(if (release) Mode.releaseFast else Mode.debug)
        .withGC(GC.immix)
    }
  )
