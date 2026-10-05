package com.screentranslate.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class ScriptRecognitionTest {
    @Test public void bundledRecognizersReadTheirSupportedScripts() throws Exception {
        String[][] examples = {{"zh", "你好世界", "你好"}, {"ja", "こんにちは世界", "こんにちは"},
                {"ko", "안녕하세요 세계", "안녕"}, {"hi", "नमस्ते दुनिया", "नमस्ते"}};
        for (String[] example : examples) {
            Bitmap bitmap = Bitmap.createBitmap(1000, 220, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.BLACK);
            paint.setTextSize(72);
            canvas.drawText(example[1], 40, 140, paint);
            try (TranslationEngine engine = new TranslationEngine(example[0], example[0])) {
                String text = Tasks.await(engine.read(bitmap), 30, TimeUnit.SECONDS).getText();
                assertTrue(example[0] + " recognizer returned: " + text, text.contains(example[2]));
                assertEquals("Same-language mode returns recognized text", text, Tasks.await(engine.translate(text)));
            } finally { bitmap.recycle(); }
        }
    }
}
