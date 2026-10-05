package com.screentranslate.app;

import android.content.Context;
import android.content.SharedPreferences;

final class LanguageSettings {
    final String source;
    final String target;

    LanguageSettings(String source, String target) {
        if (!Languages.supportsSource(source) || !Languages.supportsTarget(target)) {
            throw new IllegalArgumentException("Unsupported language selection");
        }
        this.source = source;
        this.target = target;
    }

    static LanguageSettings load(Context context) {
        SharedPreferences saved = context.getSharedPreferences("languages", Context.MODE_PRIVATE);
        String source = saved.getString("source", "es");
        String target = saved.getString("target", "en");
        return new LanguageSettings(Languages.supportsSource(source) ? source : "es",
                Languages.supportsTarget(target) ? target : "en");
    }

    void save(Context context) {
        context.getSharedPreferences("languages", Context.MODE_PRIVATE).edit()
                .putString("source", source).putString("target", target).apply();
    }
    boolean samePair(LanguageSettings other) { return other != null && source.equals(other.source) && target.equals(other.target); }
    String label() { return Languages.name(source) + " → " + Languages.name(target); }
    String shortLabel() { return Languages.shortName(source) + " → " + Languages.shortName(target); }
}
