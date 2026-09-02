package docfetch4s

class HtmlSuite extends munit.FunSuite:

  test("decodes character entities") {
    assertEquals(Html.decodeEntities("A &amp;&amp; B &lt;: C &gt; D"), "A && B <: C > D")
    assertEquals(Html.decodeEntities("&#65;&#x42;"), "AB")
    // An unterminated '&' is left as-is.
    assertEquals(Html.decodeEntities("a & b"), "a & b")
  }

  test("drops script and svg along with their contents") {
    val h = """<div>keep<script>var x = "<div>";</script><svg><path/></svg>tail</div>"""
    assertEquals(Html.toText(h), "keeptail")
  }

  test("matches class attributes token by token") {
    val h =
      """<div class="documentableElement" id="a"><div class="documentableElement-expander">x</div></div>"""
    val found = Html.extractDivs(h, "documentableElement")
    assertEquals(found.size, 1)
    assertEquals(Html.attr(Html.startTagOf(found.head), "id"), Some("a"))
  }

  test("returns only the outer element when divs nest") {
    val h = """<div class="c"><div class="c">inner</div>outer</div><div class="c">second</div>"""
    val found = Html.extractDivs(h, "c")
    assertEquals(found.size, 2)
    assert(found.head.contains("inner"))
    assert(found(1).contains("second"))
  }

  test("preserves indentation and line breaks inside pre") {
    val h = "<div><p>Example:</p><pre><code>if (x) {\n    y\n}</code></pre></div>"
    val text = Html.toText(h)
    assert(text.contains("```"), text)
    assert(text.contains("    y"), text)
  }

  test("toInlineText collapses line breaks and runs of whitespace") {
    val h = "<div class=\"signature\">\n  <span>def </span>\n  <a>map</a>[A]\n</div>"
    assertEquals(Html.toInlineText(h), "def map[A]")
  }

  test("removeByClass drops the element along with its contents") {
    assertEquals(
      Html.removeByClass("""<div>a<div class="buttons">x</div>b</div>""", "div", "buttons"),
      "<div>ab</div>"
    )
  }
