# docfetch4s

A CLI that fetches and searches scaladoc for Scala libraries. It is meant to be called by an
AI agent that needs to look up a library's API with real type signatures. It builds to a single
native binary with Scala Native.

## What it does

- Fetches scaladoc for a Maven triple (org / artifact / version).
- Caches the javadoc jar locally, so later lookups do not touch the network.
- Searches classes, traits and methods by name.
- Prints the documentation for a specific type or member, including its prose.

## Requirements

To build you need:

- A JDK and sbt 2.x (the build pins sbt 2.0.6)
- Clang, for Scala Native's native compilation
- s2n-tls development files (`libs2n.so` and `s2n.h`) — fs2-io's TLS links against them for
  HTTPS. On openSUSE install `s2n-devel`; use the equivalent package elsewhere.
- zlib development files (`zlib.h`), used to read jars

## Build

```
sbt nativeLink                              # development build (fast to link)
sbt -Ddocfetch4s.release=true nativeLink    # release build (releaseFast + thin LTO)
```

The binary lands at `target/out/native0.5/scala-3.8.4/docfetch4s/docfetch4s`.

It is not static: it links dynamically against `libs2n`, `libz`, `libstdc++`, `libm` and `libc`.
The s2n SONAME differs between distributions (on openSUSE it is `libs2n.so.0unstable`), so a
binary built here cannot simply be shipped to a different distribution. Build it per target.

```
# check the actual dependencies
readelf -d target/out/native0.5/scala-3.8.4/docfetch4s/docfetch4s | grep NEEDED
```

## Usage

Pass coordinates as a single `org:artifact:version` argument, or with `--org` / `--artifact` /
`--version`.

The Scala binary suffix (`_3` and friends) may be omitted. When it is, the tool tries
`_3`, then `_2.13`, then no suffix, and uses the first that exists.

The version may be a dynamic spec instead of a literal version:

| Spec | Resolves to |
| --- | --- |
| `latest` | the newest stable version |
| `2.13.+` | the newest stable version in the 2.13 line |
| `2.+` | the newest stable version in the 2.x line |

`*` works wherever `+` does, so `2.13.*` and `2.13.+` are the same thing.

"Stable" excludes pre-releases: `-RC`, `-M`, `-alpha`, `-beta`, `-SNAPSHOT`, and the commit
hashes sbt-dynver publishes from CI (`3.7-8f2b497`, `3.7.0-15-0d069d3`). When a line has no
stable version at all — http4s 1.x, for instance — the newest pre-release is used instead.

Resolution reads the version list from maven-metadata.xml and compares versions numerically,
rather than trusting the `<release>` tag: some libraries declare a snapshot-like version there.
cats-effect declares `3.7-4972921` as `<release>` while the actual latest release is `3.7.0`.

### Search

```
docfetch4s search org.typelevel:cats-core:2.13.0 flatMap
docfetch4s search org.typelevel:cats-core:latest Functor.map
docfetch4s search org.typelevel:cats-core:2.13.0 traverse --kind def --limit 5
docfetch4s search org.typelevel:cats-core:2.13.0 monadic --docs
```

Writing the query as `Owner.member` restricts matches to members of a matching owner.
`--docs` extends the search to documentation text. `--json` switches to machine-readable output.

### Showing documentation

```
docfetch4s show org.typelevel:cats-core:2.13.0 cats.Functor
docfetch4s show org.typelevel:cats-core:2.13.0 cats.Functor.map
docfetch4s show org.typelevel:cats-core:2.13.0 cats.Monad --full --inherited
```

Naming a type prints its summary, its attributes (companion, supertypes, known subtypes and so
on) and its members. Naming `Type.member` prints that member's full description. Inherited
members are hidden by default; `--inherited` includes them.

### Versions

```
docfetch4s versions org.typelevel cats-effect              # summary
docfetch4s versions org.typelevel:cats-effect               # same, single argument
docfetch4s versions org.typelevel cats-effect --matching 3.5
docfetch4s versions org.typelevel cats-effect --all
```

Libraries that publish from CI accumulate hundreds of versions — cats-effect has over 300 across
its Scala binary versions — so a raw list is hard to choose from. The default output answers the
two questions that usually matter:

```
org.typelevel:cats-effect_3 — 125 versions

  latest stable:   3.7.0

  latest per series:
    3.7  3.7.0
    3.6  3.6.3
    3.5  3.5.7
    ...
```

Add `--matching <series>` to narrow to one line, or `--all` to list every version with
pre-releases marked. `--json` reports the same data, including the `preRelease` flag per version.

`--matching` takes a bare series (`3.5`) or either wildcard form (`3.5.+`, `3.5.*`).

If an artifact has no stable release the summary says so, and shows the newest pre-release.

You often do not need this command at all: every other subcommand accepts `2.13.+` or `latest`
as the version and resolves it the same way. Reach for `versions` when you want to see what
lines exist, or which versions a line contains.

`cache remove` is the exception — it does not query the repository, so it needs a literal
version and rejects `latest` and `2.13.+`.

### Other commands

```
docfetch4s list org.typelevel:cats-core:2.13.0            # types the artifact documents
docfetch4s fetch org.typelevel:cats-core:2.13.0           # populate the cache only
docfetch4s cache list
docfetch4s cache remove org.typelevel:cats-core:2.13.0
docfetch4s cache clear
```

## Cache

The cache lives in `$XDG_CACHE_HOME/docfetch4s`, or `~/.cache/docfetch4s` when `XDG_CACHE_HOME`
is unset. Set `DOCFETCH4S_CACHE` to put it somewhere else.

Entries are stored as `<org>/<artifact>/<version>/javadoc.jar`, kept as jars rather than
unpacked: cats-core's javadoc jar is 14MB but expands to 195MB. Reads go through random access
into the jar instead.

Set `DOCFETCH4S_REPO` to fetch from a repository other than Maven Central.

## Output

Documentation goes to stdout; progress and warnings go to stderr, so an agent can consume stdout
as-is. `--quiet` silences the progress output.

## Limitations

- Only javadoc produced by the Scala 3 scaladoc is supported, because the search index comes from
  `scripts/searchData.js`. Javadoc without that file cannot be used — that means Scala 2 scaladoc
  (which uses a different `index.js` format) and Java's Javadoc. Such artifacts are downloaded and
  then rejected: telling them apart requires looking inside the jar, so it cannot be detected
  beforehand.
- Artifacts that publish no javadoc jar cannot be used.

## Development

```
sbt test            # runs the munit suites on Scala Native
sbt "testOnly *"    # sbt 2 selects tests incrementally; this forces the whole suite
sbt compile
```

Metals generates `project/metals.sbt` to enable sbt-bloop, which has no sbt 2 build published.
If that file reappears, sbt 2 will refuse to load the build; delete it and let Metals use sbt's
own BSP server instead.

## License

BSD 3-Clause. See [LICENSE](LICENSE).
