package docfetch4s

import cats.effect.{IO, Resource}
import cats.syntax.all.*

/** キャッシュと取得を束ねた入口。座標の解決から javadoc.jar を開くまでを担う。 */
final class DocStore(cache: Cache, maven: MavenCentral, log: Log):

  private def javadocPath(c: Coordinates): String =
    s"${c.basePath}/${c.fileName(Some("javadoc"), "jar")}"

  /** `latest` や `2.13.+` のような指定を、リポジトリ上の実在する版に読み替える。 */
  private def resolveVersion(org: String, artifact: String, spec: String): IO[String] =
    if !VersionQuery.isDynamic(spec) then IO.pure(spec)
    else
      Coordinates.artifactCandidates(artifact)
        .foldLeftM(Option.empty[(String, String)]) { (found, art) =>
          found match
            case Some(_) => IO.pure(found)
            case None =>
              maven.metadata(org, art).map(_.flatMap(select(_, spec)).map(art -> _))
        }
        .flatMap {
          case Some((art, v)) => log.info(s"Resolved $spec to $org:$art:$v").as(v)
          case None =>
            IO.raiseError(
              new DocfetchError(
                s"could not resolve version '$spec' for $org:$artifact " +
                  "(no matching version published; try the versions command)"
              )
            )
        }

  private def select(m: ArtifactMetadata, spec: String): Option[String] =
    if spec == "latest" || spec == "release" || spec == "+" then m.newest
    else VersionQuery.resolve(m.versions, spec)

  /** Scala バイナリ接尾辞を解決して実在する座標を返す。
    *
    * キャッシュ済みの候補があればネットワークに触れずにそれを使う。
    */
  def resolve(org: String, artifact: String, version: String): IO[Coordinates] =
    resolveVersion(org, artifact, version).flatMap { v =>
      val cands = Coordinates.candidates(org, artifact, v)
      cands.findM(cache.isCached).flatMap {
        case Some(c) => IO.pure(c)
        case None =>
          cands.findM(c => maven.exists(javadocPath(c))).flatMap {
            case Some(c) => IO.pure(c)
            case None =>
              IO.raiseError(
                new DocfetchError(
                  s"no javadoc found for $org:$artifact:$v " +
                    s"(tried artifact names: ${cands.map(_.artifact).mkString(", ")})"
                )
              )
          }
      }
    }

  /** javadoc.jar をキャッシュに用意する。`force` で再取得する。 */
  def ensure(c: Coordinates, force: Boolean = false): IO[os.Path] =
    val dest = cache.javadocJar(c)
    cache.isCached(c).flatMap { cached =>
      if cached && !force then log.info(s"Using cached $c").as(dest)
      else
        log.info(s"Fetching $c") *>
          maven.download(
            javadocPath(c),
            dest,
            size => size.fold(IO.unit)(s => log.info(f"  size: ${s.toDouble / 1024 / 1024}%.1f MB"))
          ) *> log.info(s"Cached to $dest").as(dest)
    }

  /** javadoc.jar を 1 回だけ開いて、インデックスとページの両方をそこから読む。 */
  def open(c: Coordinates): Resource[IO, ArtifactDocs] =
    for
      jar    <- Resource.eval(ensure(c))
      reader <- JarReader.open(jar)
      docs   <- Resource.eval(ArtifactDocs.make(c, reader))
    yield docs

  def versions(org: String, artifact: String): IO[List[(String, List[String])]] =
    Coordinates.artifactCandidates(artifact)
      .traverse(art => maven.metadata(org, art).map(art -> _.map(_.versions).getOrElse(Nil)))
      .map(_.filter(_._2.nonEmpty))
