package com.screentranslate.app;

import com.google.mlkit.nl.translate.TranslateLanguage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Only offer source languages whose writing system our OCR libraries can read. */
final class Languages {
    enum Script { LATIN, CHINESE, DEVANAGARI, JAPANESE, KOREAN }

    private static final List<String> OCR_CODES = Arrays.asList(
            "af", "sq", "ca", "zh", "hr", "cs", "da", "nl", "en", "et", "tl", "fi", "fr", "de",
            "hi", "hu", "is", "id", "it", "ja", "ko", "lv", "lt", "ms", "mr", "ne", "no",
            "pl", "pt", "ro", "sk", "sl", "es", "sv", "tr", "vi");

    static List<String> sourceCodes() {
        List<String> codes = new ArrayList<>(OCR_CODES);
        codes.retainAll(TranslateLanguage.getAllLanguages());
        codes.sort((left, right) -> name(left).compareTo(name(right)));
        return Collections.unmodifiableList(codes);
    }

    static List<String> targetCodes() {
        List<String> codes = new ArrayList<>(TranslateLanguage.getAllLanguages());
        codes.sort((left, right) -> name(left).compareTo(name(right)));
        return Collections.unmodifiableList(codes);
    }

    static boolean supportsSource(String code) { return OCR_CODES.contains(code) && supportsTarget(code); }
    static boolean supportsTarget(String code) { return TranslateLanguage.getAllLanguages().contains(code); }
    static String name(String code) { return Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH); }
    static String shortName(String code) { return code.toUpperCase(Locale.ROOT); }
    static Script script(String code) {
        switch (code) {
            case "zh": return Script.CHINESE;
            case "hi": case "mr": case "ne": return Script.DEVANAGARI;
            case "ja": return Script.JAPANESE;
            case "ko": return Script.KOREAN;
            default: return Script.LATIN;
        }
    }
}
