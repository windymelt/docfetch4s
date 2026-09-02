package docfetch4s

class MetadataSuite extends munit.FunSuite:

  private val xml =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<metadata>
      |  <groupId>org.typelevel</groupId>
      |  <artifactId>cats-core_3</artifactId>
      |  <versioning>
      |    <latest>2.13.0</latest>
      |    <release>2.12.0</release>
      |    <versions>
      |      <version>2.11.0</version>
      |      <version>2.12.0</version>
      |      <version>2.13.0</version>
      |    </versions>
      |  </versioning>
      |</metadata>""".stripMargin

  test("extracts versions, release and latest") {
    val m = MavenCentral.parseMetadata(xml)
    assertEquals(m.versions, List("2.11.0", "2.12.0", "2.13.0"))
    assertEquals(m.release, Some("2.12.0"))
    assertEquals(m.latest, Some("2.13.0"))
  }

  test("resolves 'latest' from the version list, not from the release tag") {
    // Some libraries publish a snapshot-like version as <release>, so the list wins.
    assertEquals(MavenCentral.parseMetadata(xml).newest, Some("2.13.0"))
  }

  test("skips pre-releases when resolving 'latest'") {
    val withRc = xml.replace(
      "<version>2.13.0</version>",
      "<version>2.13.0</version>\n      <version>2.14.0-RC1</version>"
    )
    assertEquals(MavenCentral.parseMetadata(withRc).newest, Some("2.13.0"))
  }

  test("falls back to the declared release when no versions are listed") {
    val noVersions = xml.replaceAll("(?s)<versions>.*</versions>", "")
    assertEquals(MavenCentral.parseMetadata(noVersions).newest, Some("2.12.0"))

    val noRelease = noVersions.replaceAll("<release>[^<]+</release>", "")
    assertEquals(MavenCentral.parseMetadata(noRelease).newest, Some("2.13.0"))
  }

  test("returns nothing for empty metadata") {
    val m = MavenCentral.parseMetadata("<metadata></metadata>")
    assertEquals(m.versions, Nil)
    assertEquals(m.newest, None)
  }
