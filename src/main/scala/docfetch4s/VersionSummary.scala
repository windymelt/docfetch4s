package docfetch4s

/** ある系列（`2.13` など）に属する版のまとめ。 */
final case class SeriesEntry(series: String, latest: Version, count: Int)

/** 1 アーティファクトのバージョン一覧を、選ぶのに必要な形に整理したもの。
  *
  * 生の一覧はリポジトリの並び順のままで、スナップショットが大量に混ざることがある。 どれを指定すればよいかを一目で決められるよう、新しい順に並べ替えたうえで 正式版の最新と系列ごとの最新を添える。
  */
final case class VersionSummary(
  artifact: String,
  versions: List[Version],
  matching: Option[String],
):
  /** 新しい順。 */
  val sorted: List[Version] = versions.sorted.reverse

  val latestStable: Option[Version] = sorted.find(!_.isPreRelease)

  /** プレリリースを含めた最新。正式版しか無ければ latestStable と同じ。 */
  val latestOverall: Option[Version] = sorted.headOption

  def hasPreReleaseOnly: Boolean = latestStable.isEmpty && latestOverall.nonEmpty

  /** メジャー.マイナー単位の最新。新しい系列が先。 */
  def series(depth: Int = 2): List[SeriesEntry] =
    sorted
      .groupBy(_.series(depth))
      .toList
      .flatMap { case (name, vs) =>
        val pick = vs.filterNot(_.isPreRelease).maxOption.orElse(vs.maxOption)
        pick.map(SeriesEntry(name, _, vs.size))
      }
      .sortBy(_.latest)
      .reverse

object VersionSummary:
  def of(artifact: String, versions: List[String], matching: Option[String]): VersionSummary =
    val filtered = matching match
      case None         => versions
      case Some(prefix) =>
        // `--matching 2.13` が基本形だが、座標と同じ `2.13.+` で書かれても通す。
        val wanted = VersionQuery.matchPrefix(prefix)
        versions.filter(v => Version(v).inSeries(wanted))
    VersionSummary(artifact, filtered.map(Version(_)), matching)
