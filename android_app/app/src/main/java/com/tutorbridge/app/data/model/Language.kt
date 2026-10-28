package com.tutorbridge.app.data.model

/**
 * The three languages the app is offered in. There were three separate
 * mappings between the picker's names, the codes the API stores (`EN`, `ZU`,
 * `AF`) and Android's language tags, each written out by hand where it was
 * needed; this is the one place they are tied together.
 *
 * [displayName] is the language's own name for itself ("isiZulu", not "Zulu"),
 * which is what a language picker should show: someone who cannot read the
 * current language must still be able to find their own.
 */
enum class Language(val tag: String, val serverCode: String, val displayName: String) {
    ENGLISH("en", "EN", "English"),
    ZULU("zu", "ZU", "isiZulu"),
    AFRIKAANS("af", "AF", "Afrikaans");

    companion object {
        /** The language the API has stored for the account. Anything it does not know is English, its default. */
        fun fromServerCode(code: String?): Language =
            entries.firstOrNull { it.serverCode.equals(code, ignoreCase = true) } ?: ENGLISH

        /** From the picker's own label. */
        fun fromDisplayName(name: String?): Language =
            entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: ENGLISH

        /** From an Android language tag such as "zu" or "af-ZA"; null for a language the app is not offered in. */
        fun fromTag(tag: String?): Language? {
            val language = tag?.substringBefore('-')?.substringBefore('_')
            return entries.firstOrNull { it.tag.equals(language, ignoreCase = true) }
        }
    }
}

/** What to do about language after signing in: change it on this phone, change it on the account, both or neither. */
data class LanguagePlan(val applyOnThisPhone: Language?, val saveToAccount: Language?)

/**
 * Settles a disagreement between the language saved on the account and the one
 * chosen on this phone, when settings are loaded after sign-in.
 *
 *  - **A language was chosen on this phone:** that choice wins. Someone who
 *    picked isiZulu here does not expect an old setting from another device to
 *    flip the app back. The account is brought into line with it.
 *  - **Nothing chosen here, the account has a language:** use it. This is a
 *    new phone or a fresh install, and the account remembers what they want.
 *  - **Nothing chosen anywhere:** leave it alone. The account's English is
 *    only the default for someone who never picked, and applying it would
 *    override a phone that is set to Afrikaans, which Android already honours.
 *
 * Pure, so each of those cases is a unit test.
 */
fun reconcileLanguage(account: Language, chosenOnPhone: Language?): LanguagePlan = when {
    chosenOnPhone != null -> LanguagePlan(applyOnThisPhone = null, saveToAccount = chosenOnPhone.takeIf { it != account })
    account != Language.ENGLISH -> LanguagePlan(applyOnThisPhone = account, saveToAccount = null)
    else -> LanguagePlan(applyOnThisPhone = null, saveToAccount = null)
}
