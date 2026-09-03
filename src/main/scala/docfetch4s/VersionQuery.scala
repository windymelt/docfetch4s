package docfetch4s

/** バージョン指定の解決。
  *
  * 座標のバージョン欄には実際の版のほか `latest` と `2.13.+` のような系列指定を書ける。 どちらもリポジトリのバージョン一覧が要るため、解決は取得後に行う。
  */
object VersionQuery:

  private val wildcards = Set("+", "*")

  /** リポジトリへの問い合わせが必要な指定か。 */
  def isDynamic(spec: String): Boolean =
    spec == "latest" || spec == "release" || wildcards.contains(spec) ||
      seriesPrefix(spec).nonEmpty

  /** `2.13.+` や `2.13.*` から List(2, 13) を取り出す。系列指定でなければ空。
    *
    * `+` は Gradle 系、`*` は Ivy 系の書き方で、どちらで書かれても同じに扱う。
    */
  private def seriesPrefix(spec: String): List[Int] =
    if !wildcards.exists(spec.endsWith) then Nil
    else
      val body = spec.dropRight(1).stripSuffix(".")
      if body.isEmpty then Nil else Version(body).numeric

  /** `--matching` に渡された系列。`2.13` でも `2.13.+` でも同じ意味に取る。 */
  def matchPrefix(spec: String): List[Int] =
    val explicit = seriesPrefix(spec)
    if explicit.nonEmpty then explicit else Version(spec).numeric

  /** 指定に合う版を選ぶ。合うものが無ければ None。 */
  def resolve(versions: List[String], spec: String): Option[String] =
    val prefix     = seriesPrefix(spec)
    val candidates =
      if prefix.nonEmpty then versions.filter(v => Version(v).inSeries(prefix))
      else versions
    best(candidates)

  /** 正式版のうち最大のもの。正式版が無ければプレリリースも含めて最大のもの。 */
  def best(versions: List[String]): Option[String] =
    val parsed = versions.map(Version(_))
    val stable = parsed.filterNot(_.isPreRelease)
    (if stable.nonEmpty then stable else parsed).maxOption.map(_.raw)
