package docfetch4s

/** Maven のバージョン文字列を比較可能にしたもの。
  *
  * Maven の正式な比較規則は複雑だが、実際のアーティファクト選びで効くのは 「数値の大小」と「正式版かどうか」の 2 点なので、そこに絞っている。
  */
final case class Version(raw: String) extends Ordered[Version]:
  private val parsed = Version.split(raw)

  /** 先頭から続く数値セグメント。`2.13.0` なら List(2, 13, 0)。 */
  def numeric: List[Int] = parsed._1

  /** 数値セグメントより後ろの部分。`2.13.0-RC1` なら "RC1"。 */
  def qualifier: String = parsed._2

  /** 正式リリースではない版か。
    *
    * RC / M / alpha のような明示的な印のほか、sbt-dynver が付けるコミットハッシュ （`3.7-8f2b497` や `3.7.0-15-0d069d3`）も正式版ではないものとして扱う。
    * これらはリポジトリ上に大量に並ぶため、除外しないと最新版を見失う。
    */
  def isPreRelease: Boolean =
    val q = qualifier.toLowerCase
    if q.isEmpty then false
    else Version.preReleaseWords.exists(q.startsWith) || Version.hasCommitHash(q)

  /** `2.13.0` に depth=2 を与えると "2.13"。数値が足りなければあるだけ返す。 */
  def series(depth: Int): String = numeric.take(depth).mkString(".")

  /** 数値セグメントの先頭が `prefix` と一致するか。`2.12` は 2.12.x に一致し 2.120.x には一致しない。 */
  def inSeries(prefix: List[Int]): Boolean =
    prefix.nonEmpty && numeric.take(prefix.length) == prefix

  def compare(that: Version): Int =
    val len   = math.max(numeric.length, that.numeric.length)
    val mine  = numeric.padTo(len, 0)
    val other = that.numeric.padTo(len, 0)
    mine.zip(other).collectFirst { case (a, b) if a != b => a.compare(b) } match
      case Some(c) => c
      case None    =>
        // 数値が同じなら、修飾子の無い版（正式版）が上。
        (qualifier.isEmpty, that.qualifier.isEmpty) match
          case (true, true)   => 0
          case (true, false)  => 1
          case (false, true)  => -1
          case (false, false) => Version.compareQualifiers(qualifier, that.qualifier)

  override def toString: String = raw

object Version:
  private val preReleaseWords =
    List("snapshot", "alpha", "beta", "milestone", "rc", "cr", "pre", "dev", "ea", "m")

  /** sbt-dynver などが付けるコミットハッシュらしい断片か。 */
  private def looksLikeCommitHash(s: String): Boolean =
    s.length >= 7 && s.length <= 40 && s.forall(c => c.isDigit || ('a' to 'f').contains(c))

  /** 修飾子はさらに区切られることがある（`3.7.0-15-0d069d3`）ので、断片ごとに見る。 */
  private def hasCommitHash(q: String): Boolean =
    q.split(Array('.', '-', '+', '_')).exists(looksLikeCommitHash)

  /** 先頭の数値セグメント列と、それ以降の修飾子に分ける。 */
  private def split(raw: String): (List[Int], String) =
    val dash          = raw.indexOf('-')
    val (head, tail)  = if dash < 0 then (raw, "") else (raw.substring(0, dash), raw.substring(dash + 1))
    val segments      = head.split('.').toList
    val numberStrings = segments.takeWhile(s => s.nonEmpty && s.forall(_.isDigit))
    val numbers       = numberStrings.map(_.toInt)
    val leftover      = segments.drop(numberStrings.length)
    val qualifier     = (leftover ++ (if tail.isEmpty then Nil else List(tail))).mkString(".")
    (numbers, qualifier)

  /** 修飾子の序列。CI が吐くスナップショットは、印のついたプレリリースより下に置く。 数値だけのビルド番号は正規のリリースに近いものとして上に置く。
    */
  private def rank(q: String): Int =
    val lower = q.toLowerCase
    if lower.startsWith("snapshot") || hasCommitHash(lower) then 0
    else if lower.startsWith("alpha") then 1
    else if lower.startsWith("beta") then 2
    else if lower.startsWith("milestone") || startsWithMarkerDigit(lower, "m") then 3
    else if lower.startsWith("rc") || lower.startsWith("cr") then 4
    else if lower.forall(c => c.isDigit || c == '.') then 6
    else 5

  /** "m47" のように 1 文字の印に数字が続く形か。"main" のような語を巻き込まないための判定。 */
  private def startsWithMarkerDigit(s: String, marker: String): Boolean =
    s.startsWith(marker) && s.length > marker.length && s.charAt(marker.length).isDigit

  private def compareQualifiers(a: String, b: String): Int =
    val byRank = rank(a).compare(rank(b))
    if byRank != 0 then byRank
    else
      // 同じ種類なら中の数字で比べる。そうしないと M47 が M5 より小さくなる。
      (firstNumber(a), firstNumber(b)) match
        case (Some(x), Some(y)) if x != y => x.compare(y)
        case _                            => a.compareToIgnoreCase(b)

  private def firstNumber(s: String): Option[Int] =
    val digits = s.dropWhile(!_.isDigit).takeWhile(_.isDigit)
    if digits.isEmpty then None else digits.toIntOption
