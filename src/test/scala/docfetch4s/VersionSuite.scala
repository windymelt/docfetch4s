package docfetch4s

class VersionSuite extends munit.FunSuite:

  test("splits a version into numbers and a qualifier") {
    assertEquals(Version("2.13.0").numeric, List(2, 13, 0))
    assertEquals(Version("2.13.0").qualifier, "")
    assertEquals(Version("1.0.0-M47").numeric, List(1, 0, 0))
    assertEquals(Version("1.0.0-M47").qualifier, "M47")
    assertEquals(Version("3.7-4972921").numeric, List(3, 7))
    assertEquals(Version("3.7-4972921").qualifier, "4972921")
  }

  test("orders by numeric segments") {
    assert(Version("2.13.0") > Version("2.12.0"))
    assert(Version("2.2.0") > Version("2.11.0") == false)
    assert(Version("0.23.36") > Version("0.23.5"))
    assert(Version("3.7.0") > Version("3.6.3"))
  }

  test("treats a missing trailing segment as zero") {
    assertEquals(Version("3.7").compare(Version("3.7.0")), 0)
    assert(Version("3.7.1") > Version("3.7"))
  }

  test("ranks a release above any pre-release of the same number") {
    assert(Version("2.13.0") > Version("2.13.0-RC1"))
    assert(Version("1.0.0") > Version("1.0.0-M47"))
    assert(Version("3.7.0") > Version("3.7.0-RC1"))
  }

  test("compares pre-release markers in the conventional order") {
    assert(Version("1.0.0-RC1") > Version("1.0.0-M1"))
    assert(Version("1.0.0-M1") > Version("1.0.0-beta1"))
    assert(Version("1.0.0-beta1") > Version("1.0.0-alpha1"))
    assert(Version("1.0.0-alpha1") > Version("1.0.0-SNAPSHOT"))
  }

  test("ranks a release candidate above a CI snapshot") {
    assert(Version("3.7.0-RC1") > Version("3.7-8f2b497"))
    assert(Version("3.7.0-RC1") > Version("3.7.0-15-0d069d3"))
    assert(Version("3.7.0-RC1") > Version("3.7-4972921"))
  }

  test("compares the number inside a marker numerically") {
    // Plain string ordering would put M47 below M5.
    assert(Version("1.0.0-M47") > Version("1.0.0-M5"))
    assert(Version("2.0.0-RC10") > Version("2.0.0-RC9"))
  }

  test("recognises explicit pre-release markers") {
    assert(Version("2.13.0-RC1").isPreRelease)
    assert(Version("1.0.0-M47").isPreRelease)
    assert(Version("1.0.0-SNAPSHOT").isPreRelease)
    assert(Version("2.0.0-alpha3").isPreRelease)
    assert(!Version("2.13.0").isPreRelease)
    assert(!Version("0.23.36").isPreRelease)
  }

  test("treats sbt-dynver commit hashes as pre-releases") {
    // These flood the version list of libraries that publish from CI.
    assert(Version("3.7-8f2b497").isPreRelease)
    assert(Version("3.7.0-15-0d069d3").isPreRelease)
    assert(Version("3.6-623178c").isPreRelease)
    // An all-digit hash of the same shape counts too.
    assert(Version("3.7-4972921").isPreRelease)
    // A short build number is not a hash.
    assert(!Version("1.2.3-1").isPreRelease)
  }

  test("reports the series a version belongs to") {
    assertEquals(Version("2.13.5").series(2), "2.13")
    assertEquals(Version("2.13.5").series(1), "2")
    assertEquals(Version("1.0.0-M47").series(2), "1.0")
  }

  test("matches a series prefix on segment boundaries") {
    assert(Version("2.12.0").inSeries(List(2, 12)))
    assert(Version("2.12.15").inSeries(List(2)))
    assert(!Version("2.120.0").inSeries(List(2, 12)))
    assert(!Version("3.0.0").inSeries(List(2)))
  }

  test("sorts a mixed list newest first") {
    val vs = List("3.6.3", "3.7.0", "3.7-4972921", "3.7.0-RC1", "3.5.7")
      .map(Version(_))
      .sorted
      .reverse
      .map(_.raw)
    assertEquals(vs.head, "3.7.0")
    assertEquals(vs.last, "3.5.7")
  }
