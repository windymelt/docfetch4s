package docfetch4s

class SearchSuite extends munit.FunSuite:

  private val js =
    """pages = [{"l":"cats/Functor.html#","e":false,"i":"","n":"Functor","t":"Functor[F[_]]","d":"cats","k":"trait","x":"Covariant functor."},
      |{"l":"cats/Functor.html#map-fffffdbf","e":false,"i":"","n":"map","t":"map[A, B](fa: F[A])(f: A => B): F[B]","d":"cats.Functor","k":"def","x":""},
      |{"l":"cats/FlatMap.html#flatMap-abc","e":false,"i":"","n":"flatMap","t":"flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]","d":"cats.FlatMap","k":"def","x":"Monadic bind."}];""".stripMargin

  private val entries = SearchData.parse(js).fold(e => fail(e), identity)

  test("parses searchData.js") {
    assertEquals(entries.size, 3)
    val f = entries.head
    assertEquals(f.name, "Functor")
    assertEquals(f.kind, "trait")
    assertEquals(f.fqn, "cats.Functor")
    assertEquals(f.page, "cats/Functor.html")
    assertEquals(f.anchor, "")
    assert(f.isTypeLike)
  }

  test("splits a member link into page and anchor") {
    val m = entries(1)
    assertEquals(m.fqn, "cats.Functor.map")
    assertEquals(m.page, "cats/Functor.html")
    assertEquals(m.anchor, "map-fffffdbf")
    assert(!m.isTypeLike)
  }

  test("ranks an exact name match above a substring match") {
    val hits = Search.query(entries, "map", Set.empty, searchDocs = false, limit = 10)
    assertEquals(hits.head.entry.fqn, "cats.Functor.map")
    assert(hits.map(_.entry.fqn).contains("cats.FlatMap.flatMap"))
  }

  test("an owner-qualified query excludes members of other types") {
    val hits = Search.query(entries, "FlatMap.flatMap", Set.empty, searchDocs = false, limit = 10)
    assertEquals(hits.size, 1)
    assertEquals(hits.head.entry.fqn, "cats.FlatMap.flatMap")
  }

  test("filters by kind") {
    val hits = Search.query(entries, "Functor", Set("trait"), searchDocs = false, limit = 10)
    assertEquals(hits.map(_.entry.kind).distinct, Vector("trait"))
  }

  test("searches documentation text only under --docs") {
    val without = Search.query(entries, "Monadic", Set.empty, searchDocs = false, limit = 10)
    assertEquals(without.size, 0)
    val withDocs = Search.query(entries, "Monadic", Set.empty, searchDocs = true, limit = 10)
    assertEquals(withDocs.head.entry.fqn, "cats.FlatMap.flatMap")
  }

  test("resolving a type prefers an exact match") {
    val r = Search.resolveType(entries, "cats.Functor")
    assertEquals(r.map(_.fqn), Vector("cats.Functor"))
  }
