package com.screentranslate.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.os.Build;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Debug-only chat for exercising capture, OCR, and replacement without real chat data. */
public final class ChatFixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 28), Ui.dp(this, 20), Ui.dp(this, 20));
        root.setBackgroundColor(Ui.BACKGROUND);
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(Ui.dp(this, 20) + bars.left, Ui.dp(this, 28) + bars.top,
                    Ui.dp(this, 20) + bars.right, Ui.dp(this, 20) + bars.bottom);
            return insets;
        });
        boolean french = "fr".equals(getIntent().getStringExtra("source"));
        Ui.add(root, Ui.text(this, "Chat preview", 25, Ui.INK, true), 0);
        TextView first = Ui.text(this, french ? "Bonjour, comment allez-vous ?" : "Hola, ¿cómo estás?", 24, Ui.INK, false);
        first.setContentDescription(french ? "French message one" : "Spanish message one");
        first.setBackground(Ui.background(Color.WHITE, 16, this));
        first.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        Ui.add(root, first, 250);
        TextView second = Ui.text(this, french ? "À demain." : "Nos vemos mañana.", 24, Ui.INK, false);
        second.setContentDescription(french ? "French message two" : "Spanish message two");
        second.setBackground(Ui.background(Color.WHITE, 16, this));
        second.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        Ui.add(root, second, 20);
        EditText input = new EditText(this);
        input.setHint("Write a reply…");
        input.setContentDescription("Chat reply field");
        Ui.add(root, input, 20);
        setContentView(root);
    }
}
