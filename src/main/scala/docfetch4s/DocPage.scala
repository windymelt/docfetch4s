package docfetch4s

import scala.collection.mutable

/** ページ内の 1 メンバ（def / val / type など）。 */
final case class Member(
  anchor: String,
  name: String,
  signature: String,
  doc: String,
  origin: String,
)

/** scaladoc の 1 ページ（クラス・トレイト・オブジェクト・パッケージ）から取り出した内容。 */
final case class DocPage(
  summary: String,
  attributes: List[(String, String)],
  members: List[Member],
):
  def memberByAnchor(a: String): Option[Member] = members.find(_.anchor == a)

  def membersNamed(n: String): List[Member] =
    val nl = n.toLowerCase
    members.filter(_.name == n) match
      case Nil   => members.filter(_.name.toLowerCase == nl)
      case exact => exact

object DocPage:

  def parse(html: String): DocPage =
    val body  = Html.stripNonContent(html)
    val cover = Html.extractDivs(body, "cover").headOption

    val summary = cover
      .flatMap(c => Html.extractDivs(c, "doc").headOption)
      .map(Html.toText)
      .getOrElse("")

    val attributes = cover
      .flatMap(c => Html.extractSection(c, "attributes"))
      .map(parseDefinitionList)
      .getOrElse(Nil)

    val members = Html.extractDivs(body, "documentableElement").map(parseMember)

    DocPage(summary, attributes, members)

  private def parseMember(element: String): Member =
    val anchor = Html.attr(Html.startTagOf(element), "id").getOrElse("")

    val signature = Html
      .extractDivs(element, "signature")
      .headOption
      .map(Html.toInlineText)
      .getOrElse("")

    // メンバ名は signature 内のリンクに documentableName クラスが付いている。
    val name = Html
      .extractByClass(element, "a", "documentableName")
      .headOption
      .map(Html.toInlineText)
      .getOrElse("")

    val memberDoc = Html.extractDivs(element, "memberDocumentation").headOption

    // memberDocumentation は「短い要約 (documentableBrief) + 本文 (cover > doc) + Attributes」
    // という構成なので、本文だけを取る。本文が無いメンバでは要約にフォールバックする。
    val doc = memberDoc
      .flatMap { md =>
        val fullBody = Html
          .extractDivs(md, "cover")
          .headOption
          .flatMap(c => Html.extractDivs(c, "doc").headOption)
        fullBody.orElse(Html.extractDivs(md, "documentableBrief").headOption)
      }
      .map(Html.toText)
      .getOrElse("")

    // 継承元は memberDocumentation 内の Attributes に "Inherited from: Apply" として入る。
    // originInfo div も見るが、実際の scaladoc 出力では空であることが多い。
    val fromOriginInfo = Html
      .extractDivs(element, "originInfo")
      .headOption
      .map(Html.toInlineText)
      .filter(_.nonEmpty)

    val fromAttributes = memberDoc.flatMap { md =>
      parseDefinitionList(md).collectFirst {
        case (k, v) if k.toLowerCase.startsWith("inherited from") => s"Inherited from: $v"
      }
    }

    Member(anchor, name, signature, doc, fromOriginInfo.orElse(fromAttributes).getOrElse(""))

  /** `<dl>` の dt/dd を順に対応付ける。scaladoc の Attributes セクションはこの形。 */
  private def parseDefinitionList(section: String): List[(String, String)] =
    val terms = extractTags(section, "dt").map(Html.toInlineText)
    val defs  =
      extractTags(section, "dd").map(t => Html.toText(t).linesIterator.map(_.trim).filter(_.nonEmpty).mkString(" / "))
    terms.zip(defs).filter { case (k, v) => k.nonEmpty && v.nonEmpty }

  // Html の走査と同じく、局所的な位置送りで書いてある。
  // scalafix:off DisableSyntax.var, DisableSyntax.while
  private def extractTags(html: String, tag: String): List[String] =
    val open = s"<$tag"
    val out  = mutable.ListBuffer.empty[String]
    var i    = 0
    while i < html.length do
      val idx = html.indexOf(open, i)
      if idx < 0 then i = html.length
      else
        val after      = idx + open.length
        val isTagStart = after >= html.length || !Character.isLetterOrDigit(html.charAt(after))
        if !isTagStart then i = after
        else
          Html.sliceElement(html, idx, tag) match
            case Some((body, end)) => out += body; i = end
            case None              => i = after
    out.toList

  // scalafix:on DisableSyntax.var, DisableSyntax.while
