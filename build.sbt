import scala.scalanative.build.*

val scala3Version = "3.8.4"

// scalafix の OrganizeImports が SemanticDB を必要とする。
semanticdbEnabled := true

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
      "org.scalameta" %% "munit"               % "1.3.5" % Test,
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    // debug モードは起動が速くビルドも短い。配布用は `sbt -Ddocfetch4s.release=true nativeLink`
    nativeConfig ~= { c =>
      val release = sys.props.get("docfetch4s.release").contains("true")
      // fs2-io の TLS は s2n を要求するが、Debian/Ubuntu には s2n のパッケージが無い。
      // S2N_LIB_DIR に libs2n.a のあるディレクトリを渡すと静的リンクし、配布先に
      // s2n の導入を求めずに済む。未設定なら通常どおり libs2n.so を動的リンクする。
      // linkingOptions は fs2 の @link が足す -ls2n より前に置かれる。静的版の
      // libs2n.a は libs2n.so と違って OpenSSL 依存を自分で持たないので、
      // ここで s2n と crypto をこの順に並べて未定義シンボルを解決させる。
      // 後から来る -ls2n も同じ -L から libs2n.a を掴むため、二重指定は害にならない。
      val s2nStatic = sys.env
        .get("S2N_LIB_DIR")
        .toSeq
        .flatMap(dir => Seq(s"-L$dir", "-l:libs2n.a", "-lcrypto"))
      c.withLTO(if (release) LTO.thin else LTO.none)
        .withMode(if (release) Mode.releaseFast else Mode.debug)
        .withGC(GC.immix)
        .withLinkingOptions(s2nStatic ++ c.linkingOptions)
    },
  )
