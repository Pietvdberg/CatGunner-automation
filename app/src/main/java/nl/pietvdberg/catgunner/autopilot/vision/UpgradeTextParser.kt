package nl.pietvdberg.catgunner.autopilot.vision

object UpgradeTextParser {
    fun level(text: String): Int? = Regex("^Lv[. ]*([0-9]{1,6})$", RegexOption.IGNORE_CASE)
        .matchEntire(text.trim())?.groupValues?.get(1)?.toIntOrNull()

    /** Preserve suffix spelling; its numeric magnitude has not been established. */
    fun cost(text: String): String? = text.replace(" ", "").takeIf {
        Regex("^[0-9]{1,12}(?:[.][0-9]{1,6})?[a-zA-Z]{0,3}$").matches(it)
    }
}
