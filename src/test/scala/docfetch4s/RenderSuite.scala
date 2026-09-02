package docfetch4s

class RenderSuite extends munit.FunSuite:

  private val coords = Coordinates("org.typelevel", "cats-core_3", "2.13.0")

  private def hit(name: String, owner: String, kind: String, desc: String): Hit =
    Hit(Entry(name, s"$name: Int", owner, kind, desc, s"$owner.html#$name", false), 100, "test")

  test("reports when a search found nothing") {
    val s = Render.search(coords, "nope", Vector.empty, brief = false)
    assert(s.contains("No matches for 'nope'"), s)
  }

  test("uses singular and plural match counts") {
    val one = Render.search(coords, "f", Vector(hit("f", "cats", "def", "")), brief = false)
    assert(one.contains("1 match for"), one)
    val two = Render.search(
      coords,
      "f",
      Vector(hit("f", "cats", "def", ""), hit("f", "cats.data", "def", "")),
      brief = false
    )
    assert(two.contains("2 matches for"), two)
  }

  test("flattens line breaks in a summary onto one line") {
    val s = Render.search(coords, "f", Vector(hit("f", "cats", "def", "one\ntwo\nthree")), false)
    assert(s.contains("one two three"), s)
  }

  test("drops code examples and their lead-in from a summary") {
    val doc = "Replaces the value.\n\nExample:\n\n```\nscala> x\n```"
    val s   = Render.search(coords, "f", Vector(hit("f", "cats", "def", doc)), false)
    assert(s.contains("Replaces the value."), s)
    assert(!s.contains("Example"), s)
    assert(!s.contains("scala>"), s)
  }

  test("omits descriptions under --brief") {
    val s = Render.search(coords, "f", Vector(hit("f", "cats", "def", "a description")), brief = true)
    assert(!s.contains("a description"), s)
  }

  test("truncates a long description with an ellipsis") {
    val long = "a" * 300
    val s    = Render.search(coords, "f", Vector(hit("f", "cats", "def", long)), false)
    assert(s.contains("…"), s)
    assert(s.linesIterator.forall(_.length < 200), s)
  }

  test("emits parseable JSON carrying the expected fields") {
    val s = Render.searchJson(coords, "f", Vector(hit("f", "cats", "def", "d")))
    val j = ujson.read(s)
    assertEquals(j("artifact").str, coords.toString)
    assertEquals(j("count").num.toInt, 1)
    assertEquals(j("results")(0)("fqn").str, "cats.f")
  }

  test("hides inherited members by default and says so") {
    val page = DocPage(
      "summary",
      List("Source" -> "X.scala"),
      List(
        Member("a", "a", "def a: Int", "", ""),
        Member("b", "b", "def b: Int", "", "Inherited from: Base")
      )
    )
    val e = Entry("X", "X", "pkg", "trait", "", "pkg/X.html#", false)
    val s = Render.typePage(coords, e, page, full = false, inherited = false)
    assert(s.contains("def a: Int"), s)
    assert(!s.contains("def b: Int"), s)
    assert(s.contains("--inherited"), s)

    val all = Render.typePage(coords, e, page, full = false, inherited = true)
    assert(all.contains("def b: Int"), all)
  }

  test("prints the total size of the cache listing") {
    val s = Render.cacheList(os.root / "tmp" / "c", List(coords -> (2 * 1024 * 1024L)))
    assert(s.contains("2.0 MB"), s)
    assert(s.contains("Total: 1 artifact"), s)
  }

  test("reports an empty cache") {
    val s = Render.cacheList(os.root / "tmp" / "c", Nil)
    assert(s.contains("Cache is empty"), s)
  }
