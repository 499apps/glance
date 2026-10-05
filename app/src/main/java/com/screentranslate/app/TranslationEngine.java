package com.screentranslate.app;

import android.graphics.Bitmap;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

final class TranslationEngine implements AutoCloseable {
    private final TextRecognizer recognizer;
    private final Translator translator;

    TranslationEngine() { this("es", "en"); }

    TranslationEngine(String source, String target) {
        new LanguageSettings(source, target); // Validate before opening native clients.
        switch (Languages.script(source)) {
            case CHINESE:
                recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build()); break;
            case DEVANAGARI:
                recognizer = TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build()); break;
            case JAPANESE:
                recognizer = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build()); break;
            case KOREAN:
                recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build()); break;
            default:
                recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        }
        translator = source.equals(target) ? null : Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(source).setTargetLanguage(target).build());
    }
    // The setup screen explains the one-time download over the user's current connection.
    Task<Void> prepare() { return translator == null ? Tasks.forResult(null) : translator.downloadModelIfNeeded(new DownloadConditions.Builder().build()); }
    Task<Text> read(Bitmap bitmap) { return recognizer.process(InputImage.fromBitmap(bitmap, 0)); }
    Task<String> translate(String text) { return translator == null ? Tasks.forResult(text) : translator.translate(text); }
    @Override public void close() { recognizer.close(); if (translator != null) translator.close(); }
}
