package docfetch4s

import scala.collection.mutable

/** scaladoc が出力する HTML の切り出しとテキスト化。
  *
  * 汎用の HTML パーサではなく、scaladoc の規則的な出力に対象を絞っている。 属性値の中にタグ様の文字列が現れる箇所（継承グラフの dot スクリプト）は `stripNonContent`
  * で先に落としてから要素を数える前提。
  *
  * 走査は文字位置を進める while ループで書いてある。該当箇所では DisableSyntax の var と while を個別に切ってあり、いずれも局所変数がメソッドの外に出ない範囲に限っている。
  */
object Html:

  private val entities = Map(
    "amp"    -> "&",
    "lt"     -> "<",
    "gt"     -> ">",
    "quot"   -> "\"",
    "apos"   -> "'",
    "nbsp"   -> " ",
    "hellip" -> "…",
    "mdash"  -> "—",
    "ndash"  -> "–",
    "rarr"   -> "→",
    "larr"   -> "←",
    "harr"   -> "↔",
    "times"  -> "×",
    "copy"   -> "©",
    "laquo"  -> "«",
    "raquo"  -> "»",
  )

  // scalafix:off DisableSyntax.var, DisableSyntax.while
  def decodeEntities(s: String): String =
    if !s.contains('&') then s
    else
      val sb = new mutable.StringBuilder(s.length)
      var i  = 0
      while i < s.length do
        val c = s.charAt(i)
        if c != '&' then { sb.append(c); i += 1 }
        else
          val semi = s.indexOf(';', i + 1)
          // 実体参照が閉じていない、または不自然に長い場合は素の '&' として扱う。
          if semi < 0 || semi - i > 12 then { sb.append(c); i += 1 }
          else
            val body    = s.substring(i + 1, semi)
            val decoded =
              if body.startsWith("#x") || body.startsWith("#X") then
                try Some(new String(Character.toChars(Integer.parseInt(body.substring(2), 16))))
                catch case _: Throwable => None
              else if body.startsWith("#") then
                try Some(new String(Character.toChars(Integer.parseInt(body.substring(1)))))
                catch case _: Throwable => None
              else entities.get(body)
            decoded match
              case Some(d) => sb.append(d); i = semi + 1
              case None    => sb.append(c); i += 1
      sb.result()

  // scalafix:on DisableSyntax.var, DisableSyntax.while

  private val nonContentTags = List("script", "style", "svg", "button")

  /** 表示に不要かつタグの数え上げを乱す要素を中身ごと落とす。 */
  def stripNonContent(html: String): String =
    nonContentTags.foldLeft(html)((acc, tag) => removeElement(acc, tag))

  // scalafix:off DisableSyntax.var, DisableSyntax.while
  private def removeElement(html: String, tag: String): String =
    val open  = s"<$tag"
    val close = s"</$tag>"
    val sb    = new mutable.StringBuilder(html.length)
    var i     = 0
    while i < html.length do
      val s = html.indexOf(open, i)
      // タグ名の続きが英数字なら別のタグ（<s> と <span> など）なので読み飛ばす。
      val isTagStart = s >= 0 && {
        val after = s + open.length
        after >= html.length || !Character.isLetterOrDigit(html.charAt(after))
      }
      if s < 0 then { sb.append(html.substring(i)); i = html.length }
      else if !isTagStart then { sb.append(html.substring(i, s + open.length)); i = s + open.length }
      else
        sb.append(html.substring(i, s))
        val e = html.indexOf(close, s)
        i = if e < 0 then html.length else e + close.length
    sb.result()

  // scalafix:on DisableSyntax.var, DisableSyntax.while

  /** 開始タグ 1 個分の文字列から属性値を取り出す。 */
  def attr(startTag: String, name: String): Option[String] =
    val needle = s"$name=\""
    val i      = startTag.indexOf(needle)
    if i < 0 then None
    else
      val from = i + needle.length
      val end  = startTag.indexOf('"', from)
      if end < 0 then None else Some(startTag.substring(from, end))

  /** 要素の開始タグ部分（`<div ...>`）を返す。 */
  def startTagOf(element: String): String =
    val gt = element.indexOf('>')
    if gt < 0 then element else element.substring(0, gt + 1)

  // scalafix:off DisableSyntax.var, DisableSyntax.while
  /** class 属性を空白区切りのトークンとして厳密に照合し、該当する要素を切り出す。
    *
    * 前方一致で判定すると `documentableElement` が `documentableElement-expander` を 拾ってしまうため、トークン単位で比較する。ネストしている場合は外側のみを返す。
    */
  def extractByClass(html: String, tag: String, className: String): List[String] =
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
          val gt      = html.indexOf('>', idx)
          val classes = if gt < 0 then None else attr(html.substring(idx, gt + 1), "class")
          val matches = classes.exists(_.split("\\s+").contains(className))
          if matches then
            sliceElement(html, idx, tag) match
              case Some((body, end)) => out += body; i = end
              case None              => i = after
          else i = after
    out.toList

  // scalafix:on DisableSyntax.var, DisableSyntax.while

  def extractDivs(html: String, className: String): List[String] =
    extractByClass(html, "div", className)

  /** `<section id="X">` を切り出す。 */
  def extractSection(html: String, id: String): Option[String] =
    val idx = html.indexOf(s"""<section id="$id"""")
    if idx < 0 then None else sliceElement(html, idx, "section").map(_._1)

  // scalafix:off DisableSyntax.var, DisableSyntax.while
  /** `start` にある `<tag ...>` から対応する `</tag>` までを返す。戻り値は (要素全体, 終端の次位置)。 */
  def sliceElement(html: String, start: Int, tag: String): Option[(String, Int)] =
    val open   = s"<$tag"
    val close  = s"</$tag>"
    var depth  = 0
    var i      = start
    var result = Option.empty[(String, Int)]
    while result.isEmpty && i < html.length do
      val nextOpen  = html.indexOf(open, i)
      val nextClose = html.indexOf(close, i)
      if nextClose < 0 then i = html.length
      else if nextOpen >= 0 && nextOpen < nextClose then
        val after     = nextOpen + open.length
        val isSameTag = after >= html.length || !Character.isLetterOrDigit(html.charAt(after))
        if isSameTag then depth += 1
        i = after
      else
        depth -= 1
        val after = nextClose + close.length
        if depth == 0 then result = Some((html.substring(start, after), after))
        i = after
    result

  // scalafix:on DisableSyntax.var, DisableSyntax.while

  private val blockEnd =
    List(
      "</p>",
      "</div>",
      "</li>",
      "</dt>",
      "</dd>",
      "</tr>",
      "</h1>",
      "</h2>",
      "</h3>",
      "</h4>",
      "</h5>",
      "</h6>",
      "</blockquote>",
      "</section>",
    )

  // コードブロックを退避するための私用領域文字。空白を含まないため normalize を通しても壊れない。
  private val codeMarkStart = '\uE000'
  private val codeMarkEnd   = '\uE001'

  // 以降のテキスト化はすべて文字位置を進める走査で書いてある。
  // scalafix:off DisableSyntax.var, DisableSyntax.while

  /** `<pre>` の中身は行頭の空白に意味があるため、テキスト化の前に退避する。 */
  private def stashCodeBlocks(html: String): (String, Vector[String]) =
    val sb     = new mutable.StringBuilder(html.length)
    val blocks = mutable.ArrayBuffer.empty[String]
    var i      = 0
    while i < html.length do
      val idx = html.indexOf("<pre", i)
      if idx < 0 then { sb.append(html.substring(i)); i = html.length }
      else
        sb.append(html.substring(i, idx))
        sliceElement(html, idx, "pre") match
          case Some((body, end)) =>
            // pre の中身だけを取り出し、タグを落として改行とインデントを保つ。
            val inner = body.indexOf('>') match
              case -1 => body
              case g  => body.substring(g + 1, body.lastIndexOf("</pre>") max (g + 1))
            blocks += stripTagsKeepingLayout(inner)
            sb.append(s"\n$codeMarkStart${blocks.size - 1}$codeMarkEnd\n")
            i = end
          case None =>
            sb.append(html.substring(idx, idx + 4))
            i = idx + 4
    (sb.result(), blocks.toVector)

  /** 改行をそのまま残してタグだけを落とす。 */
  private def stripTagsKeepingLayout(fragment: String): String =
    val sb = new mutable.StringBuilder(fragment.length)
    var i  = 0
    while i < fragment.length do
      val c = fragment.charAt(i)
      if c != '<' then { sb.append(c); i += 1 }
      else
        val gt = fragment.indexOf('>', i)
        if gt < 0 then { sb.append(c); i += 1 }
        else i = gt + 1
    decodeEntities(sb.result()).stripTrailing()

  private def restoreCodeBlocks(text: String, blocks: Vector[String]): String =
    if blocks.isEmpty then text
    else
      val sb = new mutable.StringBuilder(text.length)
      var i  = 0
      while i < text.length do
        val c = text.charAt(i)
        if c != codeMarkStart then { sb.append(c); i += 1 }
        else
          val end = text.indexOf(codeMarkEnd.toInt, i + 1)
          if end < 0 then { sb.append(c); i += 1 }
          else
            val n = text.substring(i + 1, end).toIntOption
            n.flatMap(blocks.lift) match
              case Some(code) => sb.append(s"```\n$code\n```")
              case None       => sb.append(text.substring(i, end + 1))
            i = end + 1
      sb.result()

  /** タグを落として読めるテキストにする。ブロック要素の切れ目で改行し、コード例は保護する。 */
  def toText(fragment: String): String =
    val cleaned           = stripNonContent(removeByClass(fragment, "div", "buttons"))
    val (stashed, blocks) = stashCodeBlocks(cleaned)
    val sb                = new mutable.StringBuilder(stashed.length)
    var i                 = 0
    while i < stashed.length do
      val c = stashed.charAt(i)
      if c != '<' then { sb.append(c); i += 1 }
      else
        val gt = stashed.indexOf('>', i)
        if gt < 0 then { sb.append(c); i += 1 }
        else
          val low = stashed.substring(i, gt + 1).toLowerCase
          if low.startsWith("<br") then sb.append('\n')
          else if low.startsWith("<li") then sb.append("\n- ")
          else if blockEnd.exists(low.startsWith) then sb.append('\n')
          i = gt + 1
    restoreCodeBlocks(normalize(decodeEntities(sb.result())), blocks)

  /** 指定 class を持つ要素を中身ごと落とす。 */
  def removeByClass(html: String, tag: String, className: String): String =
    val open = s"<$tag"
    val sb   = new mutable.StringBuilder(html.length)
    var i    = 0
    while i < html.length do
      val idx = html.indexOf(open, i)
      if idx < 0 then { sb.append(html.substring(i)); i = html.length }
      else
        val after      = idx + open.length
        val isTagStart = after >= html.length || !Character.isLetterOrDigit(html.charAt(after))
        val gt         = html.indexOf('>', idx)
        val matches    = isTagStart && gt >= 0 &&
          attr(html.substring(idx, gt + 1), "class").exists(_.split("\\s+").contains(className))
        if !matches then { sb.append(html.substring(i, after)); i = after }
        else
          sb.append(html.substring(i, idx))
          sliceElement(html, idx, tag) match
            case Some((_, end)) => i = end
            case None           => i = after
    sb.result()

  /** シグネチャなど 1 行に収めたい断片用。改行と連続空白を潰す。 */
  def toInlineText(fragment: String): String =
    val cleaned = stripNonContent(fragment)
    val sb      = new mutable.StringBuilder(cleaned.length)
    var i       = 0
    while i < cleaned.length do
      val c = cleaned.charAt(i)
      if c != '<' then { sb.append(c); i += 1 }
      else
        val gt = cleaned.indexOf('>', i)
        if gt < 0 then { sb.append(c); i += 1 }
        else i = gt + 1
    decodeEntities(sb.result()).replaceAll("\\s+", " ").trim

  /** 行末空白を落とし、連続する空行を 1 行にまとめる。 */
  private def normalize(text: String): String =
    val lines = text.split("\n", -1).map(_.replaceAll("[ \t]+", " ").trim)
    val out   = mutable.ListBuffer.empty[String]
    var blank = 0
    lines.foreach { l =>
      if l.isEmpty then blank += 1
      else
        if blank > 0 && out.nonEmpty then out += ""
        blank = 0
        out += l
    }
    out.mkString("\n").trim

  // scalafix:on DisableSyntax.var, DisableSyntax.while
