package com.screentranslate.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;

final class Ui {
    static final int INK = Color.rgb(27, 35, 59);
    static final int MUTED = Color.rgb(105, 113, 136);
    static final int BLUE = Color.rgb(70, 92, 224);
    static final int BACKGROUND = Color.rgb(247, 248, 252);
    static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable background(int color, float radius, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        return d;
    }
    static TextView text(Context c, String value, float size, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setLineSpacing(dp(c, 3), 1);
        return v;
    }
    static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(primary ? Color.WHITE : BLUE);
        b.setBackground(background(primary ? BLUE : Color.rgb(235, 238, 255), 16, c));
        b.setPadding(dp(c, 16), dp(c, 6), dp(c, 16), dp(c, 6));
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
        return b;
    }
    static Button compactButton(Context c, String label, boolean primary) {
        Button b = button(c, label, primary);
        b.setTextSize(12);
        b.setBackground(background(primary ? BLUE : Color.rgb(235, 238, 255), 10, c));
        b.setPadding(dp(c, 10), dp(c, 2), dp(c, 10), dp(c, 2));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(c, 32));
        b.setMinimumHeight(dp(c, 32));
        b.setStateListAnimator(null);
        b.setElevation(0);
        return b;
    }
    static void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(parent.getContext(), top);
        parent.addView(child, p);
    }
    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    static ImageView attribution(Context c) {
        ImageView image = new ImageView(c);
        image.setImageResource(R.drawable.google_translate_attribution);
        image.setContentDescription("Powered by Google Translate");
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_START);
        image.setMaxHeight(dp(c, 18));
        return image;
    }
}
