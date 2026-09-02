package docfetch4s

/** 利用者に見せる想定のエラー。スタックトレースではなくメッセージだけを出す。 */
final class DocfetchError(message: String) extends RuntimeException(message)
