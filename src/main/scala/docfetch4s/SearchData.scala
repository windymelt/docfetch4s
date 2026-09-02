package docfetch4s

/** scaladoc の検索インデックス 1 件。 */
final case class Entry(
    name: String,
    signature: String,
    owner: String,
    kind: String,
    description: String,
    link: String,
    isExtension: Boolean
):
  /** 完全修飾名。owner が空なのはルートパッケージやトップページの場合。 */
  def fqn: String = if owner.isEmpty then name else s"$owner.$name"

  /** javadoc.jar 内の HTML パス。 */
  def page: String =
    val i = link.indexOf('#')
    if i < 0 then link else link.substring(0, i)

  /** ページ内アンカー。メンバでない場合は空。 */
  def anchor: String =
    val i = link.indexOf('#')
    if i < 0 then "" else link.substring(i + 1)

  /** 型やクラスそのものを指すエントリか（メンバではなく）。 */
  def isTypeLike: Boolean =
    kind match
      case "class" | "trait" | "object" | "enum" | "package" | "given" => true
      case _                                                           => false

/** javadoc.jar 内の `scripts/searchData.js` を読む。
  *
  * 実体は `pages = [ {...}, ... ];` という JS 代入文で、値は素の JSON 配列。
  * 各要素のキーは scaladoc が短縮しており、l=link, n=name, t=signature(title),
  * d=owner(declaring), k=kind, x=description, e=extension を表す。
  */
object SearchData:
  val PathInJar = "scripts/searchData.js"

  def parse(js: String): Either[String, Vector[Entry]] =
    val start = js.indexOf('[')
    val end   = js.lastIndexOf(']')
    if start < 0 || end < start then Left("no JSON array found in searchData.js")
    else
      try
        val arr = ujson.read(js.substring(start, end + 1)).arr
        Right(arr.iterator.map(fromJson).toVector)
      catch case t: Throwable => Left(s"failed to parse searchData.js: ${t.getMessage}")

  private def str(v: ujson.Value, key: String): String =
    v.obj.get(key).flatMap(_.strOpt).getOrElse("")

  private def fromJson(v: ujson.Value): Entry =
    Entry(
      name        = str(v, "n"),
      signature   = str(v, "t"),
      owner       = str(v, "d"),
      kind        = str(v, "k"),
      description = str(v, "x"),
      link        = str(v, "l"),
      isExtension = v.obj.get("e").flatMap(_.boolOpt).getOrElse(false)
    )
