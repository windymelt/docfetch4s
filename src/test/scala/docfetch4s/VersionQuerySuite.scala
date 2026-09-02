package docfetch4s

class VersionQuerySuite extends munit.FunSuite:

  // Shaped after cats-effect, which publishes many CI snapshots alongside releases.
  private val catsEffect = List(
    "3.5.7",
    "3.6-623178c",
    "3.6.1",
    "3.6.3",
    "3.6.3-42-f8d9991",
    "3.7-4972921",
    "3.7-8f2b497",
    "3.7.0",
    "3.7.0-15-0d069d3",
    "3.7.0-RC1"
  )

  test("recognises which specs need the repository") {
    assert(VersionQuery.isDynamic("latest"))
    assert(VersionQuery.isDynamic("release"))
    assert(VersionQuery.isDynamic("2.13.+"))
    assert(VersionQuery.isDynamic("2.+"))
    assert(!VersionQuery.isDynamic("2.13.0"))
    assert(!VersionQuery.isDynamic("1.0.0-M47"))
  }

  test("latest picks the newest stable, ignoring snapshots") {
    assertEquals(VersionQuery.best(catsEffect), Some("3.7.0"))
  }

  test("a series spec picks the newest stable in that series") {
    assertEquals(VersionQuery.resolve(catsEffect, "3.6.+"), Some("3.6.3"))
    assertEquals(VersionQuery.resolve(catsEffect, "3.5.+"), Some("3.5.7"))
    assertEquals(VersionQuery.resolve(catsEffect, "3.+"), Some("3.7.0"))
  }

  test("a series with no published version resolves to nothing") {
    assertEquals(VersionQuery.resolve(catsEffect, "4.+"), None)
    assertEquals(VersionQuery.resolve(Nil, "latest"), None)
  }

  test("falls back to a pre-release when a series has no stable version") {
    val onlyMilestones = List("1.0.0-M45", "1.0.0-M46", "1.0.0-M47")
    assertEquals(VersionQuery.best(onlyMilestones), Some("1.0.0-M47"))
    assertEquals(VersionQuery.resolve(onlyMilestones, "1.+"), Some("1.0.0-M47"))
  }

  test("picks the stable release over a newer milestone") {
    // http4s publishes 0.23.x as stable while 1.0.0 is still milestones.
    val http4s = List("0.23.35", "0.23.36", "1.0.0-M47")
    assertEquals(VersionQuery.best(http4s), Some("0.23.36"))
    // Asking for the 1.x line still gives the milestone.
    assertEquals(VersionQuery.resolve(http4s, "1.+"), Some("1.0.0-M47"))
  }

  test("a series prefix does not match a longer number") {
    val vs = List("2.12.0", "2.120.0")
    assertEquals(VersionQuery.resolve(vs, "2.12.+"), Some("2.12.0"))
  }

  test("accepts the Ivy-style wildcard as well as the Gradle one") {
    assert(VersionQuery.isDynamic("2.13.*"))
    assert(VersionQuery.isDynamic("2.*"))
    assertEquals(VersionQuery.resolve(catsEffect, "3.6.*"), VersionQuery.resolve(catsEffect, "3.6.+"))
    assertEquals(VersionQuery.resolve(catsEffect, "3.*"), Some("3.7.0"))
  }

  test("--matching takes a bare series or a wildcard form") {
    assertEquals(VersionQuery.matchPrefix("2.13"), List(2, 13))
    assertEquals(VersionQuery.matchPrefix("2.13.+"), List(2, 13))
    assertEquals(VersionQuery.matchPrefix("2.13.*"), List(2, 13))
    assertEquals(VersionQuery.matchPrefix("2"), List(2))
    assertEquals(VersionQuery.matchPrefix("nonsense"), Nil)
  }
