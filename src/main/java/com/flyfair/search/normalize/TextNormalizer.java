package com.flyfair.search.normalize;

import com.ibm.icu.text.Normalizer2;

/**
 * Folds a raw query or a stored name into a canonical comparison key so that matching works
 * across scripts, cases, accents and punctuation. This is the single point where multilingual
 * and accent handling lives.
 *
 * <p>Examples (input &rarr; key):
 * <pre>
 *   "São Paulo"  &rarr; "sao paulo"     (NFKD + strip combining marks)
 *   "MÜNCHEN"    &rarr; "munchen"
 *   "St. Louis"  &rarr; "st louis"      (punctuation &rarr; space, collapsed)
 *   "東京"        &rarr; "東京"           (CJK preserved, fullwidth normalised)
 *   "دُبَيّ"       &rarr; "دبي"            (Arabic harakat + tatweel removed)
 * </pre>
 *
 * <p>It deliberately does NOT transliterate (東京 stays 東京): we resolve scripts by indexing the
 * real names, not by converting the query.
 */
public final class TextNormalizer {

    private static final Normalizer2 NFKD = Normalizer2.getNFKDInstance();
    private static final int TATWEEL = 0x0640; // Arabic kashida; a non-combining decorator we drop

    private TextNormalizer() {}

    public static String normalize(String input) {
        if (input == null) return "";
        String decomposed = NFKD.normalize(input);

        StringBuilder sb = new StringBuilder(decomposed.length());
        boolean lastWasSpace = true; // trims leading space
        int i = 0;
        while (i < decomposed.length()) {
            int cp = decomposed.codePointAt(i);
            i += Character.charCount(cp);

            if (cp == TATWEEL || Character.getType(cp) == Character.NON_SPACING_MARK) {
                continue; // strip accents / Arabic harakat
            }
            if (Character.isLetterOrDigit(cp)) {
                sb.appendCodePoint(Character.toLowerCase(cp));
                lastWasSpace = false;
            } else if (!lastWasSpace) {
                sb.append(' '); // any separator/punctuation collapses to a single space
                lastWasSpace = true;
            }
        }
        // trim trailing space
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == ' ') end--;
        return sb.substring(0, end);
    }
}
