package com.screentranslate.app;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** A consented screen-capture session; only selected regions reach OCR and translation. */
public final class CaptureService extends Service {
    static volatile boolean active;
    static volatile LanguageSettings currentLanguages;
    static final String UPDATE_LANGUAGES = "com.screentranslate.app.UPDATE_LANGUAGES";
    static final String SET_VISIBILITY = "com.screentranslate.app.SET_VISIBILITY";
    private static final String CHANNEL = "screen_translate";
    private static final String STOP = "com.screentranslate.app.STOP";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private Image latestFrame;
    private long frameReceived;
    private int captureWidth, captureHeight;
    private TranslationEngine engine;
    private View floating;
    private SelectionView selection;
    private Bitmap screenshot;
    private WindowManager.LayoutParams floatingParams;
    private String recognizedText = "";
    private String translatedText = "";
    private LanguageSettings languages;
    private String message;
    private boolean hiddenForSettings;
    private boolean busy;
    private boolean destroyed;
    private int generation;
    private int cardX, cardY;
    private boolean collapsed;

    @Override public void onCreate() {
        super.onCreate();
        windows = getSystemService(WindowManager.class);
        languages = LanguageSettings.load(this);
        message = readyMessage();
        cardX = getSharedPreferences("position", MODE_PRIVATE).getInt("x", Ui.dp(this, 16));
        cardY = getSharedPreferences("position", MODE_PRIVATE).getInt("y", Ui.dp(this, 120));
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(
                CHANNEL, "Floating translator", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null || STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (SET_VISIBILITY.equals(intent.getAction())) {
            hiddenForSettings = !intent.getBooleanExtra("visible", true);
            if (hiddenForSettings) removeFloating(); else if (selection == null) showFloating();
            if (projection == null) stopSelf();
            return START_NOT_STICKY;
        }
        if (UPDATE_LANGUAGES.equals(intent.getAction())) {
            if (projection == null) { stopSelf(); return START_NOT_STICKY; }
            generation++;
            busy = false;
            removeSelection();
            configureLanguages(intent);
            recognizedText = translatedText = "";
            message = readyMessage();
            getSystemService(NotificationManager.class).notify(1, notification());
            showFloating();
            return START_NOT_STICKY;
        }
        if (projection != null) { showFloating(); return START_NOT_STICKY; }
        try {
            configureLanguages(intent);
            startForeground(1, notification());
            if (!Settings.canDrawOverlays(this)) throw new IllegalStateException("Floating permission is missing");
            Intent consent = Build.VERSION.SDK_INT >= 33 ? intent.getParcelableExtra("captureData", Intent.class) : intent.getParcelableExtra("captureData");
            int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
            if (consent == null || resultCode != Activity.RESULT_OK) throw new IllegalStateException("Screen capture was not granted");
            projection = getSystemService(MediaProjectionManager.class).getMediaProjection(resultCode, consent);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSelf(); }
                @Override public void onCapturedContentResize(int width, int height) {
                    if (!destroyed && display != null && (width != captureWidth || height != captureHeight)) resizeCapture(width, height);
                }
            }, handler);
            Rect bounds = screenBounds();
            captureWidth = bounds.width();
            captureHeight = bounds.height();
            reader = newReader(captureWidth, captureHeight);
            display = projection.createVirtualDisplay("Glance", captureWidth, captureHeight,
                    getResources().getConfiguration().densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, handler);
            active = true;
            showFloating();
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not start screen capture. Reopen Glance and tap Start.", Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void configureLanguages(Intent intent) {
        String source = intent.getStringExtra("source"), target = intent.getStringExtra("target");
        languages = new LanguageSettings(source == null ? languages.source : source, target == null ? languages.target : target);
        if (engine != null) engine.close();
        engine = new TranslationEngine(languages.source, languages.target);
        currentLanguages = languages;
        message = readyMessage();
    }

    private String readyMessage() { return "Tap Highlight, then drag over a " + Languages.name(languages.source) + " message."; }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, CaptureService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Glance · " + languages.label())
                .setContentText("Highlight a message on the floating card. Tap Stop to finish.")
                .setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build()).build();
    }

    private Rect screenBounds() {
        if (Build.VERSION.SDK_INT >= 30) return windows.getMaximumWindowMetrics().getBounds();
        DisplayMetrics metrics = new DisplayMetrics();
        windows.getDefaultDisplay().getRealMetrics(metrics);
        return new Rect(0, 0, metrics.widthPixels, metrics.heightPixels);
    }

    private ImageReader newReader(int width, int height) {
        ImageReader result = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        result.setOnImageAvailableListener(source -> {
            if (destroyed || source != reader) return;
            try {
                Image image = source.acquireLatestImage();
                if (image == null) return;
                if (latestFrame != null) latestFrame.close();
                latestFrame = image;
                frameReceived = SystemClock.uptimeMillis();
            } catch (IllegalStateException ignored) { /* Reader may close during a resize. */ }
        }, handler);
        return result;
    }

    private void resizeCapture(int width, int height) {
        if (width <= 0 || height <= 0 || display == null) return;
        generation++;
        busy = false;
        removeSelection();
        ImageReader old = reader;
        if (latestFrame != null) { latestFrame.close(); latestFrame = null; }
        captureWidth = width; captureHeight = height;
        reader = newReader(width, height);
        display.setSurface(null);
        display.resize(width, height, getResources().getConfiguration().densityDpi);
        display.setSurface(reader.getSurface());
        old.close();
        showFloating();
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (display != null && Build.VERSION.SDK_INT < 34) {
            Rect bounds = screenBounds();
            if (bounds.width() != captureWidth || bounds.height() != captureHeight) resizeCapture(bounds.width(), bounds.height());
        } else if (active && selection == null && !busy) showFloating();
    }

    private void showFloating() {
        if (destroyed || !active || hiddenForSettings) return;
        removeFloating();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        floatingParams = new WindowManager.LayoutParams(collapsed ? Ui.dp(this, 40) : Math.min(Ui.dp(this, 280), screenBounds().width() - Ui.dp(this, 24)),
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        floatingParams.gravity = Gravity.TOP | Gravity.LEFT;
        floatingParams.x = cardX; floatingParams.y = cardY;
        if (collapsed) {
            TextView bubble = Ui.text(this, Languages.shortName(languages.target), 12, Color.WHITE, true);
            bubble.setGravity(Gravity.CENTER);
            bubble.setMinHeight(Ui.dp(this, 40));
            bubble.setBackground(Ui.background(Ui.BLUE, 20, this));
            bubble.setContentDescription("Expand translation card. Drag to move.");
            bubble.setOnClickListener(v -> { collapsed = false; showFloating(); });
            enableDrag(bubble, true);
            floating = bubble;
        } else {
            LinearLayout card = Ui.column(this);
            card.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 8));
            card.setBackground(Ui.background(Color.WHITE, 14, this));
            card.setElevation(Ui.dp(this, 6));
            LinearLayout header = new LinearLayout(this);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = Ui.text(this, Languages.name(languages.target) + "  ·  " + languages.shortLabel(), 11, Ui.BLUE, true);
            title.setMinHeight(Ui.dp(this, 32));
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setContentDescription("Drag translation card to move it.");
            enableDrag(title, false);
            header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            android.widget.ImageButton settings = new android.widget.ImageButton(this);
            settings.setImageResource(R.drawable.ic_settings);
            settings.setBackground(Ui.background(Color.rgb(235, 238, 255), 10, this));
            settings.setPadding(Ui.dp(this, 7), Ui.dp(this, 7), Ui.dp(this, 7), Ui.dp(this, 7));
            settings.setContentDescription("Open Glance settings");
            settings.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
            LinearLayout.LayoutParams settingsLayout = new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32));
            settingsLayout.rightMargin = Ui.dp(this, 6);
            header.addView(settings, settingsLayout);
            Button minimize = Ui.compactButton(this, "−", false);
            minimize.setContentDescription("Collapse translation card");
            minimize.setOnClickListener(v -> { collapsed = true; showFloating(); });
            header.addView(minimize, new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)));
            card.addView(header);

            TextView translation = Ui.text(this, message, 13, Ui.INK, false);
            translation.setTextIsSelectable(true);
            translation.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
            translation.setLineSpacing(Ui.dp(this, 1), 1);
            ScrollView scroll = new ScrollView(this);
            scroll.addView(translation);
            // Measure text first, then cap the card so long chat messages can scroll.
            int textWidth = floatingParams.width - Ui.dp(this, 24);
            translation.measure(View.MeasureSpec.makeMeasureSpec(textWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int maxHeight = Math.min(Ui.dp(this, 180), Math.max(Ui.dp(this, 56), screenBounds().height() / 3));
            card.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(translation.getMeasuredHeight(), maxHeight)));
            Ui.add(card, Ui.attribution(this), 4);

            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            Button highlight = Ui.compactButton(this, "Highlight", true);
            highlight.setContentDescription("Highlight text to translate with Google");
            highlight.setEnabled(!busy);
            highlight.setAlpha(busy ? 0.5f : 1f);
            highlight.setOnClickListener(v -> beginSelection());
            actions.addView(highlight, new LinearLayout.LayoutParams(-2, Ui.dp(this, 32)));
            Button copy = Ui.compactButton(this, "Copy", false);
            copy.setEnabled(!translatedText.isEmpty() && !busy);
            copy.setOnClickListener(v -> {
                getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Glance translation", translatedText));
                Toast.makeText(this, "Translation copied", Toast.LENGTH_SHORT).show();
            });
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-2, Ui.dp(this, 32));
            copyParams.leftMargin = Ui.dp(this, 6);
            actions.addView(copy, copyParams);
            Ui.add(card, actions, 6);
            TextView google = Ui.text(this, "Open in Google Translate  ↗", 10, Ui.MUTED, false);
            google.setGravity(Gravity.CENTER);
            google.setMinHeight(Ui.dp(this, 24));
            google.setEnabled(!recognizedText.isEmpty());
            google.setOnClickListener(v -> openGoogleTranslate());
            Ui.add(card, google, 2);
            floating = card;
        }
        floating.measure(View.MeasureSpec.makeMeasureSpec(floatingParams.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        clampPosition(floating.getMeasuredHeight());
        try { windows.addView(floating, floatingParams); }
        catch (RuntimeException e) { floating = null; stopSelf(); }
    }

    private void enableDrag(View handle, boolean clickWhenStationary) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int initialX, initialY;
            boolean moved;
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (floating == null || floatingParams == null) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX(); downY = event.getRawY();
                        initialX = floatingParams.x; initialY = floatingParams.y;
                        moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                        if (Math.abs(dx) + Math.abs(dy) > Ui.dp(CaptureService.this, 6)) moved = true;
                        if (moved) {
                            floatingParams.x = initialX + Math.round(dx);
                            floatingParams.y = initialY + Math.round(dy);
                            clampPosition(floating.getHeight());
                            windows.updateViewLayout(floating, floatingParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        cardX = floatingParams.x; cardY = floatingParams.y;
                        getSharedPreferences("position", MODE_PRIVATE).edit().putInt("x", cardX).putInt("y", cardY).apply();
                        if (!moved && clickWhenStationary) v.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL: return true;
                    default: return true;
                }
            }
        });
    }

    private void clampPosition(int height) {
        Rect bounds = screenBounds();
        floatingParams.x = Math.max(Ui.dp(this, 8), Math.min(floatingParams.x, bounds.width() - floatingParams.width - Ui.dp(this, 8)));
        floatingParams.y = Math.max(Ui.dp(this, 32), Math.min(floatingParams.y, bounds.height() - height - Ui.dp(this, 64)));
    }

    private void beginSelection() {
        if (busy || destroyed) return;
        busy = true;
        generation++;
        int request = generation;
        removeFloating();
        long hiddenAt = SystemClock.uptimeMillis();
        handler.postDelayed(() -> takeScreenshot(request, hiddenAt, 0), 250);
    }

    private void takeScreenshot(int request, long hiddenAt, int attempts) {
        if (destroyed || request != generation) return;
        if (latestFrame == null || frameReceived < hiddenAt) {
            if (attempts < 12) { handler.postDelayed(() -> takeScreenshot(request, hiddenAt, attempts + 1), 100); return; }
            fail("Could not capture this screen. Try Highlight again, or restart the translator.");
            return;
        }
        try {
            Image.Plane plane = latestFrame.getPlanes()[0];
            int width = latestFrame.getWidth(), height = latestFrame.getHeight();
            int pixelStride = plane.getPixelStride();
            int paddedWidth = width + (plane.getRowStride() - pixelStride * width) / pixelStride;
            Bitmap padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
            plane.getBuffer().rewind();
            padded.copyPixelsFromBuffer(plane.getBuffer());
            screenshot = Bitmap.createBitmap(padded, 0, 0, width, height);
            if (padded != screenshot) padded.recycle();
            latestFrame.close(); latestFrame = null;
            selection = new SelectionView(this, screenshot, new SelectionView.Listener() {
                @Override public void onSelected(Bitmap crop) {
                    removeSelection();
                    readAndTranslate(crop, request);
                }
                @Override public void onCancelled() {
                    removeSelection(); busy = false; showFloating();
                }
            });
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            if (Build.VERSION.SDK_INT >= 28) params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            if (Build.VERSION.SDK_INT >= 30) params.setFitInsetsTypes(0);
            windows.addView(selection, params);
            selection.requestFocus();
        } catch (RuntimeException e) {
            removeSelection();
            fail("Could not read this screen. Try Highlight again. Protected apps cannot be captured.");
        }
    }

    private void readAndTranslate(Bitmap crop, int request) {
        message = "Reading " + Languages.name(languages.source) + "…";
        showFloating();
        engine.read(crop).addOnCompleteListener(task -> {
            crop.recycle();
            if (destroyed || request != generation) return;
            if (!task.isSuccessful()) { fail("Couldn't read the text. Try a clearer or larger selection."); return; }
            String text = task.getResult().getText().trim();
            if (text.isEmpty()) { fail("No text found. Highlight a message; protected screens may be blank."); return; }
            recognizedText = text;
            translatedText = "";
            message = "Translating to " + Languages.name(languages.target) + "…";
            showFloating();
            engine.translate(text).addOnSuccessListener(result -> {
                if (destroyed || request != generation) return;
                translatedText = result;
                message = result;
                busy = false;
                showFloating();
            }).addOnFailureListener(e -> {
                if (!destroyed && request == generation) fail("Translation unavailable. Reopen the app to prepare the language model, or use Google Translate below.", true);
            });
        });
    }

    private void fail(String detail) {
        fail(detail, false);
    }

    private void fail(String detail, boolean preserveSource) {
        message = detail;
        translatedText = "";
        if (!preserveSource) recognizedText = "";
        busy = false;
        showFloating();
    }

    private void openGoogleTranslate() {
        if (recognizedText.isEmpty()) return;
        Uri link = Uri.parse("https://translate.google.com/").buildUpon()
                .appendQueryParameter("sl", languages.source).appendQueryParameter("tl", languages.target)
                .appendQueryParameter("text", recognizedText).appendQueryParameter("op", "translate").build();
        try { startActivity(new Intent(Intent.ACTION_VIEW, link).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (RuntimeException e) { Toast.makeText(this, "Install or enable a browser to open Google Translate.", Toast.LENGTH_LONG).show(); }
    }

    private void removeFloating() {
        if (floating != null) {
            if (floating.isAttachedToWindow()) windows.removeViewImmediate(floating);
            floating = null;
        }
    }

    private void removeSelection() {
        if (selection != null) {
            if (selection.isAttachedToWindow()) windows.removeViewImmediate(selection);
            selection = null;
        }
        if (screenshot != null) { screenshot.recycle(); screenshot = null; }
    }

    @Override public void onDestroy() {
        destroyed = true;
        active = false;
        currentLanguages = null;
        generation++;
        handler.removeCallbacksAndMessages(null);
        removeFloating(); removeSelection();
        if (latestFrame != null) { latestFrame.close(); latestFrame = null; }
        if (display != null) { display.release(); display = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (projection != null) { projection.stop(); projection = null; }
        if (engine != null) engine.close();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
