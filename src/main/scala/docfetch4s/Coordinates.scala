package docfetch4s

/** Maven の 3 つ組。`artifact` は Scala バイナリ接尾辞を含んだ実際のアーティファクト名を指す。 */
final case class Coordinates(org: String, artifact: String, version: String):
  /** repo1.maven.org 上のディレクトリパス。 */
  def basePath: String = s"${org.replace('.', '/')}/$artifact/$version"

  def fileName(classifier: Option[String], ext: String): String =
    classifier match
      case Some(c) => s"$artifact-$version-$c.$ext"
      case None    => s"$artifact-$version.$ext"

  override def toString: String = s"$org:$artifact:$version"

object Coordinates:
  /** 既知の Scala バイナリ接尾辞。 */
  private val knownSuffixes = List("_3", "_2.13", "_2.12", "_2.11")

  /** 解決を試す接尾辞の優先順。Scala 3 を最優先し、次に 2.13 系、最後に接尾辞なし（Java ライブラリ）。 */
  private val probeSuffixes = List("_3", "_2.13", "")

  def hasScalaSuffix(artifact: String): Boolean =
    knownSuffixes.exists(artifact.endsWith)

  /** 接尾辞が明示されていればそれのみ、無ければ探索候補を優先順に返す。 */
  def candidates(org: String, artifact: String, version: String): List[Coordinates] =
    artifactCandidates(artifact).map(a => Coordinates(org, a, version))

  /** バージョンに依存しない、接尾辞違いのアーティファクト名候補。 */
  def artifactCandidates(artifact: String): List[String] =
    if hasScalaSuffix(artifact) then List(artifact)
    else probeSuffixes.map(artifact + _)

  /** 座標の各要素はキャッシュのディレクトリ名とリポジトリ上のパスの両方になる。
    * 上位ディレクトリ参照や区切り文字を通すと意図しないパスを指すため、ここで弾く。
    */
  private def isSafeSegment(s: String): Boolean =
    val separators = Set('/', '\\', ' ')
    s.nonEmpty && s != "." && s != ".." &&
    !s.exists(c => separators.contains(c) || Character.isISOControl(c))

  def validateOrgArtifact(org: String, artifact: String): Either[String, (String, String)] =
    // org はドット区切りでディレクトリ階層に展開されるため、各要素を個別に検証する。
    val orgSegments = org.split('.').toList
    if org.isEmpty || orgSegments.isEmpty || !orgSegments.forall(isSafeSegment) then
      Left(s"invalid group ID: '$org'")
    else if !isSafeSegment(artifact) then Left(s"invalid artifact name: '$artifact'")
    else Right((org, artifact))

  def validate(
      org: String,
      artifact: String,
      version: String
  ): Either[String, (String, String, String)] =
    validateOrgArtifact(org, artifact).flatMap { case (o, a) =>
      if !isSafeSegment(version) then Left(s"invalid version: '$version'")
      else Right((o, a, version))
    }

  /** `org:artifact`（バージョンなし）を受け付ける。versions コマンド用。 */
  def parseOrgArtifact(s: String): Either[String, (String, String)] =
    s.replace("::", ":").split(':').toList match
      case org :: artifact :: Nil => validateOrgArtifact(org, artifact)
      case _ => Left(s"invalid coordinates: '$s' (expected org:artifact)")

  /** `org:artifact:version` および sbt 風の `org::artifact:version` を受け付ける。
    *
    * `::` は sbt では Scala 接尾辞の自動付与を意味する。ここでは接尾辞が無ければ
    * どちらの書き方でも自動解決するため、挙動は同じになる。sbt からの貼り付けを
    * そのまま通すために受け付けている。
    */
  def parse(s: String): Either[String, (String, String, String)] =
    s.replace("::", ":").split(':').toList match
      case org :: artifact :: version :: Nil => validate(org, artifact, version)
      case _ => Left(s"invalid coordinates: '$s' (expected org:artifact:version)")
