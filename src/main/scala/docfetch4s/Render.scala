package docfetch4s

/** 標準出力に出す文字列の組み立て。
  *
  * 想定する読み手は AI エージェントなので、装飾より構造の一貫性を優先し、 折り返しは行わない。`--json` を選べば同じ内容を機械可読な形で出す。
  */
object Render:

  /** 一覧に載せる 1 行の要約。原文の改行位置で切れて見えないよう空白に潰し、長ければ省略する。 コード例は 1 行に収まらないためフェンスの手前で打ち切り、その導入句だけが残らないようにする。
    */
  private def oneLine(s: String, max: Int = 150): String =
    val flat = s.split("```").head.linesIterator.map(_.trim).filter(_.nonEmpty).mkString(" ")
    // コード例を落とすと "... Example:" のような導入句が末尾に残るため、コロンで終わる節を除く。
    val text =
      if !flat.endsWith(":") then flat
      else
        val cut = flat.lastIndexOf(". ")
        if cut > 0 then flat.substring(0, cut + 1) else flat
    if text.length <= max then text else text.take(max).trim + "…"

  private def indent(s: String, prefix: String): String =
    s.linesIterator.map(l => if l.isEmpty then "" else prefix + l).mkString("\n")

  private def plural(n: Int, singular: String, pluralForm: String = ""): String =
    if n == 1 then s"$n $singular"
    else s"$n ${if pluralForm.nonEmpty then pluralForm else singular + "s"}"

  // --- search ---------------------------------------------------------------

  def search(coords: Coordinates, query: String, hits: Vector[Hit], brief: Boolean): String =
    if hits.isEmpty then s"No matches for '$query' in $coords."
    else
      val body = hits.zipWithIndex
        .map { case (h, i) =>
          val e       = h.entry
          val sig     = if e.signature.isEmpty then e.name else e.signature
          val summary =
            if brief || e.description.isEmpty then None else Some(s"    ${oneLine(e.description)}")
          (List(f"${i + 1}%2d. ${e.kind} ${e.fqn}", s"    $sig") ++ summary).mkString("\n")
        }
        .mkString("\n\n")
      s"$coords — ${plural(hits.size, "match", "matches")} for '$query'\n\n$body"

  def searchJson(coords: Coordinates, query: String, hits: Vector[Hit]): String =
    ujson.write(
      ujson.Obj(
        "artifact" -> coords.toString,
        "query"    -> query,
        "count"    -> hits.size,
        "results"  -> ujson.Arr.from(hits.map { h =>
          ujson.Obj(
            "name"        -> h.entry.name,
            "fqn"         -> h.entry.fqn,
            "kind"        -> h.entry.kind,
            "signature"   -> h.entry.signature,
            "owner"       -> h.entry.owner,
            "description" -> h.entry.description,
            "page"        -> h.entry.page,
            "anchor"      -> h.entry.anchor,
            "score"       -> h.score,
            "matchedIn"   -> h.matchedIn,
          )
        }),
      ),
      indent = 2,
    )

  // --- show -----------------------------------------------------------------

  def typePage(
    coords: Coordinates,
    entry: Entry,
    page: DocPage,
    full: Boolean,
    inherited: Boolean,
  ): String =
    val sb = new StringBuilder

    sb ++= s"${entry.kind} ${entry.fqn}\n"
    if entry.signature.nonEmpty && entry.signature != entry.name then sb ++= s"  ${entry.signature}\n"
    sb ++= s"  [$coords]\n"

    if page.summary.nonEmpty then sb ++= s"\n${page.summary}\n"

    if page.attributes.nonEmpty then
      sb ++= "\n## Attributes\n"
      page.attributes.foreach { case (k, v) => sb ++= s"  $k: $v\n" }

    val members = if inherited then page.members else page.members.filter(_.origin.isEmpty)
    val skipped = page.members.size - members.size

    sb ++= s"\n## Members (${members.size}"
    if skipped > 0 then sb ++= s", $skipped inherited hidden — use --inherited to show"
    sb ++= ")\n"

    members.foreach { m =>
      sb ++= s"\n  ${if m.signature.nonEmpty then m.signature else m.name}\n"
      if m.origin.nonEmpty then sb ++= s"    (${m.origin})\n"
      if m.doc.nonEmpty then
        sb ++= (if full then indent(m.doc, "    ") else s"    ${oneLine(m.doc)}")
        sb ++= "\n"
    }

    sb.result().stripTrailing()

  def member(coords: Coordinates, owner: String, members: List[Member]): String =
    members
      .map { m =>
        val sb = new StringBuilder
        sb ++= s"$owner.${m.name}\n"
        sb ++= s"  ${m.signature}\n"
        sb ++= s"  [$coords]\n"
        if m.origin.nonEmpty then sb ++= s"  ${m.origin}\n"
        if m.doc.nonEmpty then sb ++= s"\n${m.doc}"
        sb.result().stripTrailing()
      }
      .mkString("\n\n")

  private def memberJson(m: Member): ujson.Obj =
    ujson.Obj(
      "name"      -> m.name,
      "signature" -> m.signature,
      "doc"       -> m.doc,
      "origin"    -> m.origin,
      "anchor"    -> m.anchor,
    )

  def typePageJson(coords: Coordinates, entry: Entry, page: DocPage, inherited: Boolean): String =
    val members = if inherited then page.members else page.members.filter(_.origin.isEmpty)
    ujson.write(
      ujson.Obj(
        "artifact"   -> coords.toString,
        "fqn"        -> entry.fqn,
        "kind"       -> entry.kind,
        "signature"  -> entry.signature,
        "summary"    -> page.summary,
        "attributes" -> ujson.Obj.from(page.attributes.map { case (k, v) => k -> ujson.Str(v) }),
        "members"    -> ujson.Arr.from(members.map(memberJson)),
      ),
      indent = 2,
    )

  def membersJson(coords: Coordinates, owner: String, members: List[Member]): String =
    ujson.write(
      ujson.Obj(
        "artifact" -> coords.toString,
        "owner"    -> owner,
        "members"  -> ujson.Arr.from(members.map(memberJson)),
      ),
      indent = 2,
    )

  // --- list / versions / cache ---------------------------------------------

  def list(coords: Coordinates, entries: Vector[Entry]): String =
    val body = entries
      .sortBy(e => (e.owner, e.name))
      .map(e => s"  ${e.kind} ${e.fqn}")
      .mkString("\n")
    s"$coords — ${plural(entries.size, "entry", "entries")}\n$body"

  def listJson(coords: Coordinates, entries: Vector[Entry]): String =
    ujson.write(
      ujson.Obj(
        "artifact" -> coords.toString,
        "count"    -> entries.size,
        "entries"  -> ujson.Arr.from(entries.map { e =>
          ujson.Obj("fqn" -> e.fqn, "kind" -> e.kind, "signature" -> e.signature)
        }),
      ),
      indent = 2,
    )

  def versions(
    org: String,
    artifact: String,
    found: List[VersionSummary],
    all: Boolean,
  ): String =
    if found.isEmpty || found.forall(_.versions.isEmpty) then
      val scope = found.flatMap(_.matching).headOption.fold("")(m => s" matching $m")
      s"No versions found for $org:$artifact$scope."
    else found.map(one(org, _, all)).mkString("\n\n")

  private def one(org: String, s: VersionSummary, all: Boolean): String =
    val sb    = new StringBuilder
    val scope = s.matching.fold("")(m => s" matching $m")
    sb ++= s"$org:${s.artifact} — ${plural(s.versions.size, "version")}$scope\n"

    s.latestStable.foreach(v => sb ++= s"\n  latest stable:   $v")
    s.latestOverall.foreach { v =>
      // 正式版と一致するなら繰り返さない。
      if !s.latestStable.contains(v) then sb ++= s"\n  latest any:      $v (pre-release)"
    }
    if s.hasPreReleaseOnly then sb ++= "\n  (no stable release published)"
    sb ++= "\n"

    if all then
      sb ++= "\n  all versions, newest first:\n"
      s.sorted.foreach { v =>
        sb ++= s"    $v${if v.isPreRelease then "  (pre-release)" else ""}\n"
      }
    else
      val entries = s.series()
      if entries.nonEmpty then
        sb ++= "\n  latest per series:\n"
        val width = entries.map(_.series.length).maxOption.getOrElse(0)
        entries.foreach { e =>
          val pre = if e.latest.isPreRelease then "  (pre-release)" else ""
          sb ++= s"    ${e.series.padTo(width, ' ')}  ${e.latest}$pre\n"
        }
      sb ++= "\n  --all lists every version; --matching <series> narrows to one line\n"

    sb.result().stripTrailing()

  def versionsJson(org: String, found: List[VersionSummary]): String =
    ujson.write(
      ujson.Obj(
        "org"       -> org,
        "artifacts" -> ujson.Arr.from(found.map { s =>
          ujson.Obj(
            "artifact"      -> s.artifact,
            "count"         -> s.versions.size,
            "matching"      -> s.matching.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
            "latestStable"  -> s.latestStable.fold[ujson.Value](ujson.Null)(v => ujson.Str(v.raw)),
            "latestOverall" -> s.latestOverall.fold[ujson.Value](ujson.Null)(v => ujson.Str(v.raw)),
            "series"        -> ujson.Arr.from(s.series().map { e =>
              ujson.Obj("series" -> e.series, "latest" -> e.latest.raw, "count" -> e.count)
            }),
            "versions" -> ujson.Arr.from(s.sorted.map { v =>
              ujson.Obj("version" -> v.raw, "preRelease" -> v.isPreRelease)
            }),
          )
        }),
      ),
      indent = 2,
    )

  def cacheList(root: os.Path, items: List[(Coordinates, Long)]): String =
    if items.isEmpty then s"Cache is empty ($root)"
    else
      val total = items.map(_._2).sum
      val body  = items.map { case (c, s) => f"  ${s.toDouble / 1024 / 1024}%7.1f MB  $c" }.mkString("\n")
      f"$root\n$body\n\n  Total: ${plural(items.size, "artifact")}, ${total.toDouble / 1024 / 1024}%.1f MB"
