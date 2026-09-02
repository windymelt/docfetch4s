package docfetch4s

class VersionSummarySuite extends munit.FunSuite:

  private val versions = List(
    "3.5.7",
    "3.6.1",
    "3.6.3",
    "3.6.3-42-f8d9991",
    "3.7-4972921",
    "3.7.0",
    "3.7.0-RC1"
  )

  private val summary = VersionSummary.of("cats-effect_3", versions, None)

  test("sorts newest first regardless of the order published") {
    assertEquals(summary.sorted.head.raw, "3.7.0")
    assertEquals(summary.sorted.last.raw, "3.5.7")
  }

  test("identifies the latest stable version") {
    assertEquals(summary.latestStable.map(_.raw), Some("3.7.0"))
  }

  test("latestOverall matches latestStable when the newest is stable") {
    assertEquals(summary.latestOverall.map(_.raw), Some("3.7.0"))
    assert(!summary.hasPreReleaseOnly)
  }

  test("reports the newest per minor series") {
    val series = summary.series().map(e => e.series -> e.latest.raw)
    assertEquals(series, List("3.7" -> "3.7.0", "3.6" -> "3.6.3", "3.5" -> "3.5.7"))
  }

  test("counts every version in a series, snapshots included") {
    val s36 = summary.series().find(_.series == "3.6").get
    assertEquals(s36.count, 3)
  }

  test("--matching narrows to one series") {
    val only36 = VersionSummary.of("cats-effect_3", versions, Some("3.6"))
    assertEquals(only36.versions.size, 3)
    assertEquals(only36.latestStable.map(_.raw), Some("3.6.3"))
    assertEquals(only36.matching, Some("3.6"))
  }

  test("--matching on a major number keeps the whole line") {
    val all3 = VersionSummary.of("cats-effect_3", versions, Some("3"))
    assertEquals(all3.versions.size, versions.size)
  }

  test("flags an artifact that has only pre-releases") {
    val s = VersionSummary.of("http4s_3", List("1.0.0-M46", "1.0.0-M47"), None)
    assertEquals(s.latestStable, None)
    assertEquals(s.latestOverall.map(_.raw), Some("1.0.0-M47"))
    assert(s.hasPreReleaseOnly)
  }

  test("--matching accepts the wildcard forms too") {
    val plain    = VersionSummary.of("cats-effect_3", versions, Some("3.6"))
    val gradle   = VersionSummary.of("cats-effect_3", versions, Some("3.6.+"))
    val ivy      = VersionSummary.of("cats-effect_3", versions, Some("3.6.*"))
    assertEquals(gradle.versions.size, plain.versions.size)
    assertEquals(ivy.versions.size, plain.versions.size)
  }
