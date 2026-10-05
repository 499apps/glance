package com.screentranslate.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class LanguagesTest {
    @Test public void sourceMenuOnlyOffersSupportedWritingSystems() {
        assertTrue(Languages.sourceCodes().contains("es"));
        assertTrue(Languages.sourceCodes().contains("fr"));
        assertTrue(Languages.sourceCodes().contains("zh"));
        assertTrue(Languages.sourceCodes().contains("ja"));
        assertTrue(Languages.sourceCodes().contains("ko"));
        assertTrue(Languages.sourceCodes().contains("hi"));
        assertFalse(Languages.sourceCodes().contains("ar"));
        assertTrue(Languages.targetCodes().contains("ar"));
    }
    @Test public void recognitionRoutesLanguagesToTheirWritingSystems() {
        assertEquals(Languages.Script.LATIN, Languages.script("es"));
        assertEquals(Languages.Script.CHINESE, Languages.script("zh"));
        assertEquals(Languages.Script.JAPANESE, Languages.script("ja"));
        assertEquals(Languages.Script.KOREAN, Languages.script("ko"));
        for (String language : new String[]{"hi", "mr", "ne"}) assertEquals(Languages.Script.DEVANAGARI, Languages.script(language));
    }
    @Test public void settingsRejectUnreadableSourceLanguages() {
        assertThrows(IllegalArgumentException.class, () -> new LanguageSettings("ar", "en"));
        assertThrows(IllegalArgumentException.class, () -> new LanguageSettings("es", "invalid"));
        assertEquals("Spanish → English", new LanguageSettings("es", "en").label());
    }
}
