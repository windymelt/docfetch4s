package docfetch4s

import cats.data.{Validated, ValidatedNel}
import cats.effect.{ExitCode, IO}
import cats.syntax.all.*
import com.monovore.decline.*
import com.monovore.decline.effect.CommandIOApp

object Main
    extends CommandIOApp(
      name = AppInfo.name,
      header = """Fetch and search scaladoc for Scala libraries.
                 |
                 |Artifacts are given as org:artifact:version. The Scala suffix (_3, _2.13) may be
                 |omitted and is resolved against the repository.
                 |
                 |The version may be a literal version, `latest` for the newest stable release, or a
                 |series such as 2.13.+ for the newest stable 2.13.x. Pre-releases (-RC, -M, -alpha,
                 |-SNAPSHOT and CI commit hashes) are skipped unless a line has nothing else.
                 |Run `versions` to see what a library publishes.""".stripMargin,
      version = AppInfo.version
    ):

  // --- 共通のオプション -----------------------------------------------------

  private def validated(
      r: Either[String, (String, String, String)]
  ): ValidatedNel[String, (String, String, String)] =
    r match
      case Right(t) => Validated.valid(t)
      case Left(e)  => Validated.invalidNel(e)

  /** 座標は `org:artifact:version` の 1 引数、または `--org/--artifact/--version` で指定する。
    *
    * バージョンには `latest` を指定できる。アーティファクト名の Scala 接尾辞（`_3` など）は
    * 省略でき、その場合は `_3` → `_2.13` → 接尾辞なし の順に実在するものを探す。
    */
  private val coords: Opts[(String, String, String)] =
    Opts
      .argument[String]("org:artifact:version")
      .mapValidated(s => validated(Coordinates.parse(s)))
      .orElse(
        (
          Opts.option[String]("org", "Group ID, e.g. org.typelevel", metavar = "group-id"),
          Opts.option[String]("artifact", "Artifact name, e.g. cats-core", metavar = "name"),
          Opts.option[String](
            "version",
            "Version: 2.13.0, or latest, or 2.13.+ for the newest 2.13.x",
            metavar = "version"
          )
        ).tupled
          .mapValidated { case (o, a, v) => validated(Coordinates.validate(o, a, v)) }
      )

  /** 座標の書き方。サブコマンド単体のヘルプだけを読んでも分かるよう、各 header に添える。 */
  private val coordsNote =
    """
      |
      |Coordinates are org:artifact:version. The Scala suffix (_3, _2.13) may be omitted.
      |The version can be:
      |  2.13.0     a literal version
      |  latest     the newest stable release
      |  2.13.+     the newest stable release in the 2.13 line (2.13.* works too)
      |  2.+        the newest stable release in the 2.x line""".stripMargin

  private val jsonFlag  = Opts.flag("json", "Emit JSON instead of text").orFalse
  private val quietFlag = Opts.flag("quiet", "Suppress progress output on stderr").orFalse
  private val kindsOpt =
    Opts
      .options[String](
        "kind",
        "Filter by kind: def, val, class, trait, object, type, package",
        metavar = "kind"
      )
      .orEmpty
      .map(_.toList.toSet)

  // --- サブコマンド ---------------------------------------------------------

  private final case class SearchArgs(
      coords: (String, String, String),
      query: String,
      kinds: Set[String],
      limit: Int,
      searchDocs: Boolean,
      brief: Boolean,
      json: Boolean,
      quiet: Boolean
  )

  private val searchCmd: Opts[SearchArgs] =
    Opts.subcommand(Command("search", "Search for methods and types by name." + coordsNote) {
      (
        coords,
        Opts.argument[String]("query"),
        kindsOpt,
        Opts
          .option[Int]("limit", "Maximum number of results (default 20)", metavar = "n")
          .withDefault(20),
        Opts.flag("docs", "Also search documentation text").orFalse,
        Opts.flag("brief", "Print signatures only, without descriptions").orFalse,
        jsonFlag,
        quietFlag
      ).mapN(SearchArgs.apply)
    })

  private final case class ShowArgs(
      coords: (String, String, String),
      target: String,
      full: Boolean,
      inherited: Boolean,
      json: Boolean,
      quiet: Boolean
  )

  private val showCmd: Opts[ShowArgs] =
    Opts.subcommand(Command("show", "Show documentation for a type or member." + coordsNote) {
      (
        coords,
        Opts.argument[String]("fqcn-or-Type.member"),
        Opts.flag("full", "Print each member's full description").orFalse,
        Opts.flag("inherited", "Include inherited members").orFalse,
        jsonFlag,
        quietFlag
      ).mapN(ShowArgs.apply)
    })

  private final case class ListArgs(
      coords: (String, String, String),
      kinds: Set[String],
      json: Boolean,
      quiet: Boolean
  )

  private val listCmd: Opts[ListArgs] =
    Opts.subcommand(Command("list", "List the types an artifact documents." + coordsNote) {
      (coords, kindsOpt, jsonFlag, quietFlag).mapN(ListArgs.apply)
    })

  private final case class FetchArgs(
      coords: (String, String, String),
      force: Boolean,
      quiet: Boolean
  )

  private val fetchCmd: Opts[FetchArgs] =
    Opts.subcommand(
      Command("fetch", "Download an artifact's javadoc into the cache." + coordsNote) {
        (coords, Opts.flag("force", "Re-download even if already cached").orFalse, quietFlag)
          .mapN(FetchArgs.apply)
      }
    )

  private final case class VersionsArgs(
      orgArtifact: (String, String),
      matching: Option[String],
      all: Boolean,
      json: Boolean,
      quiet: Boolean
  )

  /** versions はバージョンを取らないので、`org artifact` と `org:artifact` の両方を受ける。 */
  private val orgArtifact: Opts[(String, String)] =
    (Opts.argument[String]("org"), Opts.argument[String]("artifact")).tupled
      .mapValidated { case (o, a) =>
        Coordinates.validateOrgArtifact(o, a) match
          case Right(t) => Validated.valid(t)
          case Left(e)  => Validated.invalidNel(e)
      }
      .orElse(
        Opts
          .argument[String]("org:artifact")
          .mapValidated { s =>
            Coordinates.parseOrgArtifact(s) match
              case Right(t) => Validated.valid(t)
              case Left(e)  => Validated.invalidNel(e)
          }
      )

  private val versionsHeader =
    """List the published versions of an artifact.
      |
      |By default this prints the latest stable version and the latest of each release line,
      |rather than every version, since libraries that publish from CI accumulate hundreds.
      |
      |A series is a version prefix matched on segment boundaries, so --matching 2.13 selects
      |2.13.x and --matching 2 selects 2.x, while --matching 2.13 never selects 2.130.x.
      |The wildcard forms used in coordinates, 2.13.+ and 2.13.*, are accepted here as well.
      |
      |Once you know the line you want, you can skip this command: any other subcommand takes
      |a version of 2.13.+ or latest directly, and resolves it the same way.
      |
      |Examples:
      |  docfetch4s versions org.typelevel cats-effect
      |  docfetch4s versions org.typelevel:cats-effect
      |  docfetch4s versions org.typelevel cats-effect --matching 3.5
      |  docfetch4s versions org.typelevel cats-effect --matching 3 --all""".stripMargin

  private val versionsCmd: Opts[VersionsArgs] =
    Opts.subcommand(
      Command("versions", versionsHeader) {
        (
          orgArtifact,
          Opts
            .option[String](
              "matching",
              "Restrict to one release line: 2.13 selects 2.13.x, 2 selects 2.x " +
                "(2.13.+ and 2.13.* are accepted too)",
              metavar = "series"
            )
            .orNone,
          Opts.flag("all", "List every version instead of a summary").orFalse,
          jsonFlag,
          quietFlag
        ).mapN(VersionsArgs.apply)
      }
    )

  private enum CacheAction:
    case List
    case Clear
    case Remove(coords: (String, String, String))

  private val cacheCmd: Opts[CacheAction] =
    Opts.subcommand("cache", "Inspect and manage the local cache") {
      Opts.subcommand("list", "List cached artifacts")(Opts(CacheAction.List)) orElse
        Opts.subcommand("clear", "Delete the entire cache")(Opts(CacheAction.Clear)) orElse
        Opts.subcommand(
          "remove",
          "Delete one artifact from the cache; needs a literal version, not latest or 2.13.+"
        )(coords.map(CacheAction.Remove.apply))
    }

  // --- 実行 -----------------------------------------------------------------

  private def withStore[A](quiet: Boolean)(f: DocStore => IO[A]): IO[A] =
    Cache.default.flatMap { cache =>
      MavenCentral.resource.use(m => f(new DocStore(cache, m, new Log(quiet))))
    }

  /** 座標を解決して javadoc.jar を 1 回だけ開く、search / show / list の共通部分。 */
  private def withDocs[A](c: (String, String, String), quiet: Boolean)(
      f: ArtifactDocs => IO[A]
  ): IO[A] =
    withStore(quiet) { store =>
      store.resolve(c._1, c._2, c._3).flatMap(coords => store.open(coords).use(f))
    }

  private def out(s: String): IO[Unit] = IO.println(s)

  private def runSearch(a: SearchArgs): IO[Unit] =
    withDocs(a.coords, a.quiet) { docs =>
      docs.index.flatMap { entries =>
        val hits = Search.query(entries, a.query, a.kinds, a.searchDocs, a.limit)
        out(
          if a.json then Render.searchJson(docs.coords, a.query, hits)
          else Render.search(docs.coords, a.query, hits, a.brief)
        )
      }
    }

  private def runShow(a: ShowArgs): IO[Unit] =
    withDocs(a.coords, a.quiet) { docs =>
      docs.index.flatMap(entries => showTarget(docs, entries, a))
    }

  /** target をまず型として、次に「型.メンバ」として解決する。どちらでもなければ候補を提示する。 */
  private def showTarget(docs: ArtifactDocs, entries: Vector[Entry], a: ShowArgs): IO[Unit] =
    val types = Search.resolveType(entries, a.target)
    if types.isEmpty then showMember(docs, entries, a)
    else
      types.toList.traverse_ { e =>
        docs.page(e.page).flatMap {
          case None => IO.blocking(System.err.println(s"page not found in jar: ${e.page}"))
          case Some(html) =>
            val page = DocPage.parse(html)
            out(
              if a.json then Render.typePageJson(docs.coords, e, page, a.inherited)
              else Render.typePage(docs.coords, e, page, a.full, a.inherited)
            )
        }
      }

  private def showMember(docs: ArtifactDocs, entries: Vector[Entry], a: ShowArgs): IO[Unit] =
    val idx = a.target.lastIndexOf('.')
    if idx <= 0 || idx == a.target.length - 1 then suggest(docs, entries, a)
    else
      val ownerName  = a.target.substring(0, idx)
      val memberName = a.target.substring(idx + 1)
      val owners     = Search.resolveType(entries, ownerName)
      if owners.isEmpty then suggest(docs, entries, a)
      else
        owners.toList
          .traverse(e => docs.page(e.page).map(_.map(html => e.fqn -> DocPage.parse(html))))
          .map(_.flatten)
          .flatMap { pages =>
            val found = pages.flatMap { case (owner, p) =>
              val named = p.membersNamed(memberName)
              val ms    = if a.inherited then named else named.filter(_.origin.isEmpty)
              if ms.isEmpty then Nil else List(owner -> ms)
            }
            if found.isEmpty then suggest(docs, entries, a)
            else
              found.traverse_ { case (owner, ms) =>
                out(
                  if a.json then Render.membersJson(docs.coords, owner, ms)
                  else Render.member(docs.coords, owner, ms)
                )
              }
          }

  private def suggest(docs: ArtifactDocs, entries: Vector[Entry], a: ShowArgs): IO[Unit] =
    val hits = Search.query(entries, a.target, Set.empty, searchDocs = false, limit = 10)
    if hits.isEmpty then
      IO.raiseError(new DocfetchError(s"'${a.target}' not found in ${docs.coords}"))
    else
      IO.blocking(System.err.println(s"'${a.target}' not found. Closest matches:")) *>
        out(Render.search(docs.coords, a.target, hits, brief = true))

  private def runList(a: ListArgs): IO[Unit] =
    withDocs(a.coords, a.quiet) { docs =>
      docs.index.flatMap { entries =>
        // 既定では型のみ。全件だと件数が多すぎて読めなくなる。
        val filtered =
          if a.kinds.nonEmpty then entries.filter(e => a.kinds.contains(e.kind))
          else entries.filter(_.isTypeLike)
        out(
          if a.json then Render.listJson(docs.coords, filtered)
          else Render.list(docs.coords, filtered)
        )
      }
    }

  private def runFetch(a: FetchArgs): IO[Unit] =
    withStore(a.quiet) { store =>
      val (org, art, ver) = a.coords
      store.resolve(org, art, ver).flatMap(c => store.ensure(c, a.force)).void
    }

  private def runVersions(a: VersionsArgs): IO[Unit] =
    withStore(a.quiet) { store =>
      val (org, artifact) = a.orgArtifact
      store.versions(org, artifact).flatMap { found =>
        val summaries = found.map { case (art, vs) => VersionSummary.of(art, vs, a.matching) }
        out(
          if a.json then Render.versionsJson(org, summaries)
          else Render.versions(org, artifact, summaries, a.all)
        )
      }
    }

  private def runCache(action: CacheAction): IO[Unit] =
    Cache.default.flatMap { cache =>
      action match
        case CacheAction.List =>
          cache.listCached.flatMap(items => out(Render.cacheList(cache.root, items)))
        case CacheAction.Clear =>
          cache.clearAll *> out(s"Cleared cache at ${cache.root}")
        // 削除はネットワークに問い合わせないので、latest や 2.13.+ は解決できない。
        // 黙って「該当なし」と答えると消えたように見えるため、はっきり断る。
        case CacheAction.Remove((_, _, ver)) if VersionQuery.isDynamic(ver) =>
          IO.raiseError(
            new DocfetchError(
              s"cache remove needs a literal version, not '$ver'; " +
                "run cache list to see what is cached"
            )
          )
        case CacheAction.Remove((org, art, ver)) =>
          // 接尾辞の候補すべてを対象にする。
          Coordinates
            .candidates(org, art, ver)
            .traverse(c => cache.remove(c).map(c -> _))
            .flatMap { results =>
              val removed = results.collect { case (c, true) => c }
              if removed.isEmpty then out(s"Nothing cached for $org:$art:$ver")
              else removed.traverse_(c => out(s"Removed $c"))
            }
    }

  def main: Opts[IO[ExitCode]] =
    (searchCmd.map(runSearch) orElse
      showCmd.map(runShow) orElse
      listCmd.map(runList) orElse
      fetchCmd.map(runFetch) orElse
      versionsCmd.map(runVersions) orElse
      cacheCmd.map(runCache)).map { action =>
      action.as(ExitCode.Success).handleErrorWith {
        case e: DocfetchError =>
          IO.blocking(System.err.println(s"error: ${e.getMessage}")).as(ExitCode.Error)
        case e =>
          IO.blocking(System.err.println(s"error: ${e.getClass.getSimpleName}: ${e.getMessage}"))
            .as(ExitCode.Error)
      }
    }
