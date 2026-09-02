package docfetch4s

import cats.effect.{IO, Resource}
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*

/** javadoc.jar を展開せずにエントリ単位で読み出す。 */
final class JarReader(private val zf: ZipFile):

  def bytes(name: String): IO[Option[Array[Byte]]] =
    IO.blocking {
      Option(zf.getEntry(name)).map { e =>
        val in = zf.getInputStream(e)
        try in.readAllBytes()
        finally in.close()
      }
    }

  def text(name: String): IO[Option[String]] =
    bytes(name).map(_.map(b => new String(b, "UTF-8")))

  def names: IO[List[String]] =
    IO.blocking(zf.entries().asScala.map(_.getName).toList)

object JarReader:
  def open(path: os.Path): Resource[IO, JarReader] =
    Resource
      .make(IO.blocking(new ZipFile(path.toIO)))(zf => IO.blocking(zf.close()).attempt.void)
      .map(new JarReader(_))
