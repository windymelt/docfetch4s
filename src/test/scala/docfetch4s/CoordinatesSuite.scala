package docfetch4s

class CoordinatesSuite extends munit.FunSuite:

  test("parses org:artifact:version") {
    assertEquals(
      Coordinates.parse("org.typelevel:cats-core:2.13.0"),
      Right(("org.typelevel", "cats-core", "2.13.0")),
    )
  }

  test("accepts the sbt-style org::artifact:version") {
    assertEquals(
      Coordinates.parse("org.typelevel::cats-core:2.13.0"),
      Right(("org.typelevel", "cats-core", "2.13.0")),
    )
  }

  test("rejects coordinates with missing parts") {
    assert(Coordinates.parse("org.typelevel:cats-core").isLeft)
    assert(Coordinates.parse("").isLeft)
    assert(Coordinates.parse("a::b:").isLeft)
  }

  test("orders suffix candidates when none is given") {
    val cs = Coordinates.candidates("org.typelevel", "cats-core", "2.13.0")
    assertEquals(cs.map(_.artifact), List("cats-core_3", "cats-core_2.13", "cats-core"))
  }

  test("keeps an explicit suffix as the only candidate") {
    val cs = Coordinates.candidates("org.typelevel", "cats-core_2.13", "2.13.0")
    assertEquals(cs.map(_.artifact), List("cats-core_2.13"))
  }

  test("expands the group ID into a directory path") {
    val c = Coordinates("org.typelevel", "cats-core_3", "2.13.0")
    assertEquals(c.basePath, "org/typelevel/cats-core_3/2.13.0")
    assertEquals(c.fileName(Some("javadoc"), "jar"), "cats-core_3-2.13.0-javadoc.jar")
  }

  test("rejects parts that would escape the cache directory") {
    assert(Coordinates.parse("org.typelevel:cats-core:..").isLeft)
    assert(Coordinates.parse("..:cats-core:1.0").isLeft)
    assert(Coordinates.validate("org", "..", "1.0").isLeft)
    assert(Coordinates.validate("a.b", "c/d", "1.0").isLeft)
    assert(Coordinates.validate("a.b", "c", "1.0 2.0").isLeft)
    // Ordinary coordinates still pass.
    assert(Coordinates.validate("org.typelevel", "cats-core_3", "2.13.0-RC1").isRight)
  }

  test("derives suffix candidates without needing a version") {
    assertEquals(
      Coordinates.artifactCandidates("cats-core"),
      List("cats-core_3", "cats-core_2.13", "cats-core"),
    )
    assertEquals(Coordinates.artifactCandidates("guava"), List("guava_3", "guava_2.13", "guava"))
  }
