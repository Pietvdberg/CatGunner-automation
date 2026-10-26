package nl.pietvdberg.catgunner.autopilot.vision

object UpgradeTextParser {
    // The handset reads the period after Lv as "o". Accept it only at that separator;
    // never replace letters within the numeric value or infer missing digits.
    private val levelPattern = Regex("^Lv(?:[. ]*|o *)([0-9]{1,6})$", RegexOption.IGNORE_CASE)

    fun level(text: String): Int? = levelPattern.matchEntire(text.trim())
        ?.groupValues?.get(1)?.toIntOrNull()

    /** Preserve suffix spelling; its numeric magnitude has not been established. */
    fun cost(text: String): String? = text.replace(" ", "").takeIf {
        Regex("^[0-9]{1,12}(?:[.][0-9]{1,6})?[a-zA-Z]{0,3}$").matches(it)
    }
}
