package docfetch4s

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.io.file.{Files as Fs2Files, Path as Fs2Path}
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.client.Client
import org.http4s.client.middleware.FollowRedirect
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.`User-Agent`
import scala.concurrent.duration.*

/** Maven リポジトリからの取得。既定は Maven Central。 */
final class MavenCentral(client: Client[IO], base: Uri):

  private def uriFor(path: String): Uri =
    path.split('/').filter(_.nonEmpty).foldLeft(base)(_ / _)

  private def request(uri: Uri, method: Method): Request[IO] =
    Request[IO](method = method, uri = uri)
      .putHeaders(`User-Agent`(ProductId(AppInfo.name, Some(AppInfo.version))))

  /** 存在確認。接尾辞違いのアーティファクト候補を絞るのに使う。 */
  def exists(path: String): IO[Boolean] =
    client
      .run(request(uriFor(path), Method.HEAD))
      .use(r => IO.pure(r.status.isSuccess))
      .handleError(_ => false)

  /** 本文を文字列で取得する。404 は None。 */
  def getText(path: String): IO[Option[String]] =
    client
      .run(request(uriFor(path), Method.GET))
      .use { r =>
        if r.status.isSuccess then r.bodyText.compile.string.map(Some(_))
        else if r.status == Status.NotFound then IO.pure(None)
        else IO.raiseError(new DocfetchError(s"${r.status.code} ${r.status.reason}: ${uriFor(path)}"))
      }

  /** 本文をファイルへ直接流し込む。javadoc.jar は数十 MB になりうるためメモリに載せない。 */
  def download(path: String, dest: os.Path, onSize: Option[Long] => IO[Unit]): IO[Unit] =
    val uri = uriFor(path)
    val tmp = dest / os.up / s".${dest.last}.tmp"

    val fetch = client.run(request(uri, Method.GET)).use { r =>
      if !r.status.isSuccess then
        IO.raiseError(
          new DocfetchError(
            if r.status == Status.NotFound then s"not found: $uri"
            else s"${r.status.code} ${r.status.reason}: $uri"
          )
        )
      else
        onSize(r.contentLength) *>
          r.body.through(Fs2Files[IO].writeAll(Fs2Path(tmp.toString))).compile.drain
    }

    IO.blocking(os.makeDir.all(dest / os.up)) *>
      // 中断や失敗で書きかけのファイルが残らないようにし、完了したものだけを本来の名前に移す。
      fetch.onError(_ => IO.blocking(os.remove(tmp, checkExists = false)).void) *>
      IO.blocking(os.move(tmp, dest, replaceExisting = true, atomicMove = true))

  /** maven-metadata.xml を読む。存在しなければ None。 */
  def metadata(org: String, artifact: String): IO[Option[ArtifactMetadata]] =
    val path = s"${org.replace('.', '/')}/$artifact/maven-metadata.xml"
    getText(path).map(_.map(MavenCentral.parseMetadata))

object MavenCentral:
  private val versionPattern = "<version>([^<]+)</version>".r
  private val releasePattern = "<release>([^<]+)</release>".r
  private val latestPattern  = "<latest>([^<]+)</latest>".r

  /** maven-metadata.xml の解析。
    *
    * Scala Native では javax.xml が使えないため要素を正規表現で拾う。maven-metadata.xml は
    * 構造が単純で、これらの要素が他の意味で現れることはない。
    */
  def parseMetadata(xml: String): ArtifactMetadata =
    def one(r: scala.util.matching.Regex): Option[String] =
      r.findFirstMatchIn(xml).map(_.group(1).trim).filter(_.nonEmpty)

    ArtifactMetadata(
      versions = versionPattern.findAllMatchIn(xml).map(_.group(1).trim).filter(_.nonEmpty).toList,
      release = one(releasePattern),
      latest = one(latestPattern)
    )

  val defaultBase: Uri = uri"https://repo1.maven.org/maven2"

  /** `DOCFETCH4S_REPO` で参照先リポジトリを差し替えられる。 */
  def resolveBase: IO[Uri] =
    IO.blocking(sys.env.get("DOCFETCH4S_REPO").filter(_.nonEmpty)).flatMap {
      case None => IO.pure(defaultBase)
      case Some(s) =>
        IO.fromEither(
          Uri.fromString(s).leftMap(e => new DocfetchError(s"invalid DOCFETCH4S_REPO: ${e.message}"))
        )
    }

  def resource: Resource[IO, MavenCentral] =
    for
      base <- Resource.eval(resolveBase)
      raw <- EmberClientBuilder
        .default[IO]
        .withTimeout(60.seconds)
        .withIdleConnectionTime(30.seconds)
        .build
    yield new MavenCentral(FollowRedirect(maxRedirects = 5)(raw), base)
