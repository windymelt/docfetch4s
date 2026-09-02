package docfetch4s

final case class Hit(entry: Entry, score: Int, matchedIn: String)

/** 検索インデックスに対する名前・シグネチャ・説明文の照合。 */
object Search:

  /** クエリを owner ヒントと名前に分ける。
    *
    * `Functor.map` や `cats.Functor.map` のように書かれた場合、最後のセグメントを名前、
    * それより前を owner の部分一致条件として扱う。ドットが無ければ全体が名前。
    */
  private def split(q: String): (Option[String], String) =
    val i = q.lastIndexOf('.')
    if i <= 0 || i == q.length - 1 then (None, q)
    else (Some(q.substring(0, i)), q.substring(i + 1))

  private def scoreName(name: String, needle: String): Option[(Int, String)] =
    val n  = name
    val nl = n.toLowerCase
    val ql = needle.toLowerCase
    if n == needle then Some((100, "exact name match"))
    else if nl == ql then Some((90, "exact name match, ignoring case"))
    else if n.startsWith(needle) then Some((72, "name prefix match"))
    else if nl.startsWith(ql) then Some((68, "name prefix match, ignoring case"))
    else if nl.contains(ql) then Some((50, "name substring match"))
    else None

  /** 種別による優先度。型そのものの方が探索対象として指定されやすい。 */
  private def kindBonus(kind: String): Int =
    kind match
      case "class" | "trait" | "object" | "enum" => 6
      case "type" | "given"                      => 4
      case "package"                             => 3
      case _                                     => 0

  def query(
      entries: Vector[Entry],
      rawQuery: String,
      kinds: Set[String],
      searchDocs: Boolean,
      limit: Int
  ): Vector[Hit] =
    val (ownerHint, needle) = split(rawQuery)
    val hintLower           = ownerHint.map(_.toLowerCase)
    val queryLower          = rawQuery.toLowerCase

    val hits = entries.iterator.flatMap { e =>
      if e.name.isEmpty then None
      else if kinds.nonEmpty && !kinds.contains(e.kind) then None
      else
        val byName = scoreName(e.name, needle).flatMap { case (s, why) =>
          // owner ヒントが指定された場合、owner に含まれないものは名前が一致しても除外する。
          hintLower match
            case None => Some((s, why))
            case Some(h) =>
              if e.owner.toLowerCase.contains(h) then Some((s + 10, s"$why, owner matches"))
              else None
        }

        val byFqn =
          if byName.nonEmpty then None
          else if e.fqn.toLowerCase.contains(queryLower) then
            Some((34, "fully-qualified name substring match"))
          else None

        val bySignature =
          if byName.nonEmpty || byFqn.nonEmpty then None
          else if e.signature.toLowerCase.contains(queryLower) then
            Some((22, "signature substring match"))
          else None

        val byDoc =
          if !searchDocs || byName.nonEmpty || byFqn.nonEmpty || bySignature.nonEmpty then None
          else if e.description.toLowerCase.contains(queryLower) then
            Some((12, "documentation substring match"))
          else None

        byName.orElse(byFqn).orElse(bySignature).orElse(byDoc).map { case (s, why) =>
          Hit(e, s + kindBonus(e.kind), why)
        }
    }.toVector

    hits
      .sortBy(h => (-h.score, h.entry.fqn.length, h.entry.fqn, h.entry.signature))
      .take(limit)

  /** `show` 用に、指定名に対応する型ページを探す。 */
  def resolveType(entries: Vector[Entry], target: String): Vector[Entry] =
    val tl = target.toLowerCase
    val exact = entries.filter(e =>
      e.isTypeLike && (e.fqn == target || e.name == target)
    )
    if exact.nonEmpty then exact
    else
      entries
        .filter(e => e.isTypeLike && (e.fqn.toLowerCase == tl || e.name.toLowerCase == tl))

