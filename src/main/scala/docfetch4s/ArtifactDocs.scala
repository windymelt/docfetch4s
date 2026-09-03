package docfetch4s

import cats.effect.IO
import cats.syntax.all.*

/** 開いた javadoc.jar 1 つ分。検索インデックスと HTML ページを同じ ZipFile から読む。
  *
  * `index` は初回の評価だけを行い、以降は同じ結果を返す（1 コマンド中に検索と ページ表示の両方でインデックスを引くため）。
  */
final class ArtifactDocs(
  val coords: Coordinates,
  reader: JarReader,
  memoizedIndex: IO[Vector[Entry]],
):
  def index: IO[Vector[Entry]] = memoizedIndex

  def page(pathInJar: String): IO[Option[String]] = reader.text(pathInJar)

object ArtifactDocs:
  def make(coords: Coordinates, reader: JarReader): IO[ArtifactDocs] =
    loadIndex(coords, reader).memoize.map(new ArtifactDocs(coords, reader, _))

  private def loadIndex(coords: Coordinates, reader: JarReader): IO[Vector[Entry]] =
    reader.text(SearchData.PathInJar).flatMap {
      case None =>
        IO.raiseError(
          new DocfetchError(
            s"$coords has no ${SearchData.PathInJar}; " +
              "its javadoc was likely not produced by the Scala 3 scaladoc " +
              "(Scala 2 scaladoc and Java Javadoc use different formats)",
          ),
        )
      case Some(js) => IO.fromEither(SearchData.parse(js).leftMap(new DocfetchError(_)))
    }
