package docfetch4s

import cats.effect.IO

/** ローカルキャッシュのレイアウトと入出力。
  *
  * 配置は `<root>/<org>/<artifact>/<version>/` で、javadoc.jar を展開せずそのまま置く。 cats-core の javadoc.jar は 14MB だが展開すると 195MB
  * になるため、 読み出しは ZipFile によるランダムアクセスで行い展開しない。
  */
final class Cache(val root: os.Path):

  def dirFor(c: Coordinates): os.Path =
    root / os.SubPath(c.org.replace('.', '/')) / c.artifact / c.version

  def javadocJar(c: Coordinates): os.Path = dirFor(c) / "javadoc.jar"

  def isCached(c: Coordinates): IO[Boolean] =
    IO.blocking(os.exists(javadocJar(c)))

  /** キャッシュ済みの座標を列挙する。org はディレクトリ階層に展開されているので復元する。 */
  def listCached: IO[List[(Coordinates, Long)]] =
    IO.blocking {
      if !os.exists(root) then Nil
      else
        os.walk(root)
          .filter(_.last == "javadoc.jar")
          .toList
          .flatMap { jar =>
            val dir      = jar / os.up
            val version  = dir.last
            val artifact = (dir / os.up).last
            val orgPath  = (dir / os.up / os.up).relativeTo(root)
            val org      = orgPath.segments.mkString(".")
            if orgPath.ups > 0 || org.isEmpty then None
            else Some(Coordinates(org, artifact, version) -> os.size(jar))
          }
          .sortBy(_._1.toString)
    }

  def remove(c: Coordinates): IO[Boolean] =
    IO.blocking {
      val d = dirFor(c)
      if os.exists(d) then { os.remove.all(d); true }
      else false
    }

  def clearAll: IO[Unit] =
    IO.blocking(if os.exists(root) then os.remove.all(root) else ())

object Cache:
  /** `DOCFETCH4S_CACHE` > `XDG_CACHE_HOME/docfetch4s` > `~/.cache/docfetch4s` の順に決める。 */
  def default: IO[Cache] =
    IO.blocking {
      val root = sys.env.get("DOCFETCH4S_CACHE").filter(_.nonEmpty) match
        case Some(p) => os.Path(p, os.pwd)
        case None    =>
          val base = sys.env.get("XDG_CACHE_HOME").filter(_.nonEmpty) match
            case Some(x) => os.Path(x, os.pwd)
            case None    => os.home / ".cache"
          base / "docfetch4s"
      new Cache(root)
    }
