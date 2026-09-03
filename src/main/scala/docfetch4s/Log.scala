package docfetch4s

import cats.effect.IO

/** 進捗は標準エラーに出す。標準出力はドキュメント本文だけに使い、 エージェントがそのまま取り込めるようにする。
  */
final class Log(quiet: Boolean):
  def info(msg: String): IO[Unit] =
    if quiet then IO.unit else IO.blocking(System.err.println(msg))
