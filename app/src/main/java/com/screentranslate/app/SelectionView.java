package com.screentranslate.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

final class SelectionView extends View {
    interface Listener { void onSelected(Bitmap crop); void onCancelled(); }
    private final Bitmap image;
    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private float startX, startY, endX, endY;
    private boolean dragging;
    private boolean finished;
    private final RectF cancel = new RectF();
    private final RectF imageBounds = new RectF();
    private final RectF selected = new RectF();
    private final RectF instruction = new RectF();

    SelectionView(Context c, Bitmap screenshot, Listener listener) {
        super(c);
        image = screenshot;
        this.listener = listener;
        setFocusableInTouchMode(true);
        setContentDescription("Drag a rectangle around text. Release to translate with Google. Back cancels.");
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (image.isRecycled()) return;
        float scale = Math.min((float) getWidth() / image.getWidth(), (float) getHeight() / image.getHeight());
        float left = (getWidth() - image.getWidth() * scale) / 2;
        float top = (getHeight() - image.getHeight() * scale) / 2;
        canvas.drawColor(Color.BLACK);
        imageBounds.set(left, top, left + image.getWidth() * scale, top + image.getHeight() * scale);
        canvas.drawBitmap(image, null, imageBounds, paint);
        selected.set(Math.min(startX, endX), Math.min(startY, endY), Math.max(startX, endX), Math.max(startY, endY));
        canvas.save();
        if (dragging) canvas.clipOutRect(selected);
        canvas.drawColor(0x700A1025);
        canvas.restore();
        if (dragging) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(getContext(), 2));
            paint.setColor(0xFF9FB2FF);
            canvas.drawRoundRect(selected, Ui.dp(getContext(), 6), Ui.dp(getContext(), 6), paint);
            paint.setStyle(Paint.Style.FILL);
        }
        float margin = Ui.dp(getContext(), 16);
        float width = Math.min(getWidth() - margin * 2, Ui.dp(getContext(), 420));
        float pillLeft = (getWidth() - width) / 2;
        float pillTop = Ui.dp(getContext(), 48);
        paint.setColor(Color.WHITE);
        instruction.set(pillLeft, pillTop, pillLeft + width, pillTop + Ui.dp(getContext(), 76));
        canvas.drawRoundRect(instruction, Ui.dp(getContext(), 18), Ui.dp(getContext(), 18), paint);
        paint.setColor(Ui.INK);
        paint.setTextSize(Ui.dp(getContext(), 15));
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        canvas.drawText("Drag over the text", pillLeft + margin, pillTop + Ui.dp(getContext(), 29), paint);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(Ui.dp(getContext(), 12));
        paint.setColor(Ui.MUTED);
        canvas.drawText("Release to translate with Google", pillLeft + margin, pillTop + Ui.dp(getContext(), 53), paint);
        float cancelTop = getHeight() - Ui.dp(getContext(), 104);
        cancel.set(getWidth() / 2f - Ui.dp(getContext(), 60), cancelTop, getWidth() / 2f + Ui.dp(getContext(), 60), cancelTop + Ui.dp(getContext(), 48));
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(cancel, Ui.dp(getContext(), 24), Ui.dp(getContext(), 24), paint);
        paint.setColor(Ui.INK);
        paint.setTextSize(Ui.dp(getContext(), 14));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Cancel", getWidth() / 2f, cancelTop + Ui.dp(getContext(), 30), paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (finished) return true;
        float x = event.getX(), y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (cancel.contains(x, y)) { finished = true; listener.onCancelled(); return true; }
                startX = endX = x;
                startY = endY = y;
                dragging = true;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                endX = x; endY = y; invalidate(); return true;
            case MotionEvent.ACTION_UP:
                endX = x; endY = y;
                if (Math.abs(endX - startX) < Ui.dp(getContext(), 12) || Math.abs(endY - startY) < Ui.dp(getContext(), 12)) {
                    dragging = false; invalidate(); return true;
                }
                int[] bounds = SelectionGeometry.crop(startX, startY, endX, endY, getWidth(), getHeight(), image.getWidth(), image.getHeight());
                if (bounds == null) { dragging = false; invalidate(); return true; }
                Bitmap crop = Bitmap.createBitmap(image, bounds[0], bounds[1], bounds[2], bounds[3]);
                // createBitmap can return the original when the whole image is selected.
                if (crop == image) crop = image.copy(Bitmap.Config.ARGB_8888, false);
                finished = true;
                listener.onSelected(crop);
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false; invalidate(); return true;
            default: return true;
        }
    }
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && !finished) {
            finished = true; listener.onCancelled(); return true;
        }
        return super.onKeyUp(keyCode, event);
    }
}
