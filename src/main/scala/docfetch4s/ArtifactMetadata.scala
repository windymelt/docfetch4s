package docfetch4s

/** maven-metadata.xml の内容。 */
final case class ArtifactMetadata(
    versions: List[String],
    release: Option[String],
    latest: Option[String]
):
  /** `latest` 指定で選ぶバージョン。
    *
    * 一覧から正式版の最大を選ぶ。`<release>` を優先しないのは、そこに
    * スナップショット相当の版が入っているライブラリが実在するため
    * （cats-effect の `<release>` は `3.7-4972921` で、正式版の 3.7.0 より前のもの）。
    * 一覧が空のときだけメタデータの申告を使う。
    */
  def newest: Option[String] =
    VersionQuery.best(versions).orElse(release).orElse(latest)
