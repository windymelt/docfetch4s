package docfetch4s

class DocPageSuite extends munit.FunSuite:

  // A minimal reproduction of the structure scaladoc emits.
  private val html =
    """<div class="cover">
      | <div class="doc"><p>Functor.</p><p>The name is short for "covariant functor".</p></div>
      | <section id="attributes">
      |  <h2 class="h500">Attributes</h2>
      |  <dl class="attributes">
      |   <dt class="body-small">Companion</dt>
      |   <dd class="body-medium"><a href="Functor$.html">object</a></dd>
      |   <dt class="body-small">Supertypes</dt>
      |   <dd class="body-medium"><div>trait Invariant[F]</div><div>class Any</div></dd>
      |  </dl>
      | </section>
      |</div>
      |<div class="documentableElement" id="map-fffffdbf">
      | <div class="documentableElement-expander">
      |  <div class="header monospace mono-medium">
      |   <div class="signature"><span class="kind"><span t="k">def </span></span><a class="documentableName " t="n">map</a>[A, B](fa: F[A])(f: A =&gt; B): F[B]</div>
      |  </div>
      | </div>
      | <div class="docs">
      |  <div class="memberDocumentation">
      |   <div class="documentableBrief doc"><p>Applies f to the content.</p></div>
      |   <div class="cover">
      |    <div class="doc"><p>Applies f to the content.</p><p>Example:</p>
      |     <div class="snippet mono-small-block"><pre><code>scala&gt; List(1).map(_ + 1)
      |res0: List[Int] = List(2)</code></pre><div class="buttons"><button>copy</button></div></div>
      |    </div>
      |    <h2 class="h200">Attributes</h2>
      |    <dl class="attributes attributes-small"><dt class="body-small">Source</dt><dd class="body-medium">Functor.scala</dd></dl>
      |   </div>
      |  </div>
      | </div>
      |</div>
      |<div class="documentableElement" id="imap-1">
      | <div class="signature"><span class="kind"><span t="k">def </span></span><a class="documentableName " t="n">imap</a>[A, B]: F[B]</div>
      | <div class="docs"><div class="originInfo">Inherited from:<a>Invariant</a></div></div>
      |</div>
      |<div class="documentableElement" id="ap-2">
      | <div class="signature"><span class="kind"><span t="k">def </span></span><a class="documentableName " t="n">ap</a>[A, B]: F[B]</div>
      | <div class="docs">
      |  <div class="originInfo"></div>
      |  <div class="memberDocumentation">
      |   <div class="cover">
      |    <div class="doc"><p>Applies a function.</p></div>
      |    <h2 class="h200">Attributes</h2>
      |    <dl class="attributes attributes-small">
      |     <dt class="body-small">Inherited from:</dt>
      |     <dd class="body-medium"><a href="Apply.html#ap">Apply</a></dd>
      |     <dt class="body-small">Source</dt>
      |     <dd class="body-medium">Apply.scala</dd>
      |    </dl>
      |   </div>
      |  </div>
      | </div>
      |</div>""".stripMargin

  private val page = DocPage.parse(html)

  test("extracts the summary") {
    assert(page.summary.startsWith("Functor."), page.summary)
    assert(page.summary.contains("covariant functor"), page.summary)
  }

  test("pairs up the Attributes dt/dd entries") {
    assertEquals(page.attributes.toMap.get("Companion"), Some("object"))
    assertEquals(page.attributes.toMap.get("Supertypes"), Some("trait Invariant[F] / class Any"))
  }

  test("extracts member names and signatures") {
    assertEquals(page.members.size, 3)
    val m = page.members.head
    assertEquals(m.name, "map")
    assertEquals(m.anchor, "map-fffffdbf")
    assertEquals(m.signature, "def map[A, B](fa: F[A])(f: A => B): F[B]")
  }

  test("takes only the body, without the duplicated brief or the Attributes") {
    val doc = page.members.head.doc
    assertEquals(doc.split("Applies f to the content").length - 1, 1, doc)
    assert(!doc.contains("Functor.scala"), doc)
  }

  test("keeps code examples inside a fence") {
    val doc = page.members.head.doc
    assert(doc.contains("```"), doc)
    assert(doc.contains("res0: List[Int] = List(2)"), doc)
    // The copy button is dropped.
    assert(!doc.contains("copy"), doc)
  }

  test("reads the origin from an originInfo div") {
    val m = page.members(1)
    assertEquals(m.name, "imap")
    assert(m.origin.contains("Inherited from"), m.origin)
  }

  test("falls back to the Attributes when originInfo is empty") {
    // Real scaladoc output leaves originInfo empty and puts the origin in Attributes instead.
    val m = page.members(2)
    assertEquals(m.name, "ap")
    assert(m.origin.contains("Inherited from"), m.origin)
    assert(m.origin.contains("Apply"), m.origin)
    // Source must not be mistaken for an origin.
    assert(!m.origin.contains("Apply.scala"), m.origin)
  }

  test("keeps the Attributes out of the member body") {
    val m = page.members(2)
    assertEquals(m.doc, "Applies a function.")
  }

  test("does not mark a locally declared member as inherited") {
    assertEquals(page.members.head.name, "map")
    assertEquals(page.members.head.origin, "")
  }

  test("looks up members by name and by anchor") {
    assertEquals(page.membersNamed("map").map(_.anchor), List("map-fffffdbf"))
    assertEquals(page.memberByAnchor("imap-1").map(_.name), Some("imap"))
  }
