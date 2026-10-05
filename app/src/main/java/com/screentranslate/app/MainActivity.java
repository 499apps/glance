package com.screentranslate.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int CAPTURE = 100, NOTIFICATIONS = 101;
    private Button start;
    private TextView status;
    private TextView permissions;
    private Spinner from, to;
    private List<String> sourceCodes, targetCodes;
    private TranslationEngine engine;
    private LanguageSettings pending;
    private boolean starting, waitingForOverlay, resumed, prepared;
    private int preparation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        waitingForOverlay = state != null && state.getBoolean("waitingForOverlay");
        LanguageSettings saved = LanguageSettings.load(this);
        sourceCodes = Languages.sourceCodes();
        targetCodes = Languages.targetCodes();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24));
        scroll.addView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                root.setPadding(Ui.dp(this, 24) + bars.left, Ui.dp(this, 24) + bars.top,
                        Ui.dp(this, 24) + bars.right, Ui.dp(this, 24) + bars.bottom);
                return insets;
            });
        }
        Ui.add(root, Ui.text(this, "GLANCE", 12, Ui.BLUE, true), 0);
        Ui.add(root, Ui.text(this, "Highlight a message.\nUnderstand at a glance.", 28, Ui.INK, true), 16);
        Ui.add(root, Ui.text(this, "Your translator floats over your chat. Select some text and the latest translation appears automatically.", 14, Ui.MUTED, false), 10);

        LinearLayout languages = Ui.column(this);
        languages.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        languages.setBackground(Ui.background(Color.WHITE, 16, this));
        Ui.add(languages, Ui.text(this, "Translation languages", 14, Ui.INK, true), 0);
        LinearLayout languageRow = new LinearLayout(this);
        LinearLayout sourceColumn = Ui.column(this);
        LinearLayout targetColumn = Ui.column(this);
        Ui.add(sourceColumn, Ui.text(this, "From", 12, Ui.MUTED, false), 0);
        Ui.add(targetColumn, Ui.text(this, "To", 12, Ui.MUTED, false), 0);
        from = spinner(sourceCodes, saved.source, "Source language");
        to = spinner(targetCodes, saved.target, "Translation language");
        Ui.add(sourceColumn, from, 4);
        Ui.add(targetColumn, to, 4);
        languageRow.addView(sourceColumn, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams targetLayout = new LinearLayout.LayoutParams(0, -2, 1);
        targetLayout.leftMargin = Ui.dp(this, 12);
        languageRow.addView(targetColumn, targetLayout);
        Ui.add(languages, languageRow, 14);
        Ui.add(languages, Ui.text(this, "Your choices are remembered. From lists languages this app can read from screenshots.", 11, Ui.MUTED, false), 10);
        Ui.add(root, languages, 22);

        Ui.add(root, Ui.text(this, "First use of a language downloads its model (about 30 MB each) over your current connection. Wi-Fi is recommended. Once prepared, translation works offline.", 12, Ui.MUTED, false), 16);
        status = Ui.text(this, "Ready to translate", 13, Ui.BLUE, true);
        Ui.add(root, status, 18);
        start = Ui.button(this, "Start translating", true);
        start.setOnClickListener(v -> begin());
        Ui.add(root, start, 10);
        Button stop = Ui.button(this, "Stop translator", false);
        stop.setOnClickListener(v -> {
            preparation++;
            starting = false;
            prepared = false;
            stopService(new Intent(this, CaptureService.class));
            enableControls(true);
            status.setText("Translator stopped");
            start.setText("Start translating");
        });
        Ui.add(root, stop, 8);
        permissions = Ui.text(this, "", 12, Ui.MUTED, false);
        Ui.add(root, permissions, 14);
        AdapterView.OnItemSelectedListener selection = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selected().save(MainActivity.this);
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
        from.setOnItemSelectedListener(selection);
        to.setOnItemSelectedListener(selection);

        Ui.add(root, Ui.text(this, "Made for conversations", 18, Ui.INK, true), 22);
        Ui.add(root, Ui.text(this, "1   Tap Highlight on the floating card.\n2   Drag a box around a message.\n3   Release. Read the latest translation.\n\nDrag the card's header to move it, tap − to collapse, or use the gear to open these settings.", 13, Ui.MUTED, false), 10);
        Ui.add(root, Ui.text(this, "Screenshots and selected text stay in memory on your phone. Glance does not save chat history. Opening Google Translate sends the selected text to its website.\n\nAndroid asks for display-over-apps and screen-capture permission. Protected screens may appear blank.", 11, Ui.MUTED, false), 16);
        android.widget.ImageView credit = Ui.attribution(this);
        credit.setOnClickListener(v -> openLink("https://translate.google.com"));
        Ui.add(root, credit, 16);
        Button about = Ui.button(this, "About & privacy", false);
        about.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("About Glance")
                .setMessage("Glance is an independent, open-source screen translator by 499apps. App source is licensed under MIT. Google Translate powers the on-device translation through Google ML Kit.\n\nNo account or API key is needed. Glance does not save screenshots, selected text, or translation history. Google's SDK may send diagnostics and download language models. Opening Google Translate sends your selected text to Google in your browser.\n\nAutomatic translations can lose context or nuance. Google disclaims warranties related to these translations, including accuracy, reliability, merchantability, fitness for a particular purpose, and noninfringement.\n\nGoogle ML Kit terms: developers.google.com/ml-kit/terms\nGoogle privacy policy: policies.google.com/privacy\n\nVersion 1.1.0")
                .setPositiveButton("Close", null).setNeutralButton("Source code", (dialog, which) -> openLink("https://github.com/499apps/glance")).show());
        Ui.add(root, about, 14);
        setContentView(scroll);
        refresh();
    }

    private Spinner spinner(List<String> codes, String selected, String description) {
        Spinner spinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        List<String> labels = new ArrayList<>();
        for (String code : codes) labels.add(Languages.name(code));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setContentDescription(description);
        spinner.setMinimumHeight(Ui.dp(this, 48));
        spinner.setSelection(codes.indexOf(selected));
        return spinner;
    }

    private LanguageSettings selected() {
        return new LanguageSettings(sourceCodes.get(from.getSelectedItemPosition()), targetCodes.get(to.getSelectedItemPosition()));
    }

    private void begin() {
        if (starting) return;
        pending = selected();
        pending.save(this);
        if (CaptureService.active && pending.samePair(CaptureService.currentLanguages)) { moveTaskToBack(true); return; }
        starting = true;
        enableControls(false);
        status.setText("Preparing " + pending.label() + "… First download may take a minute.");
        if (engine != null) engine.close();
        engine = new TranslationEngine(pending.source, pending.target);
        int request = ++preparation;
        engine.prepare().addOnSuccessListener(unused -> {
            if (isDestroyed() || request != preparation) return;
            prepared = true;
            status.setText("Language models ready · works offline");
            if (resumed) continueStart();
        }).addOnFailureListener(e -> {
            if (isDestroyed() || request != preparation) return;
            starting = false;
            enableControls(true);
            status.setText("Could not download the model. Connect to the internet and tap Start again.");
        });
    }

    private void continueStart() {
        prepared = false;
        if (CaptureService.active) {
            startService(new Intent(this, CaptureService.class).setAction(CaptureService.UPDATE_LANGUAGES)
                    .putExtra("source", pending.source).putExtra("target", pending.target));
            starting = false;
            enableControls(true);
            moveTaskToBack(true);
        } else requestOverlay();
    }

    private void requestOverlay() {
        if (Settings.canDrawOverlays(this)) { requestNotifications(); return; }
        waitingForOverlay = true;
        status.setText("Enable “Display over other apps”, then return here.");
        try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
        catch (RuntimeException e) {
            waitingForOverlay = false;
            starting = false;
            enableControls(true);
            status.setText("Open Android Settings → Apps → Glance → Display over other apps.");
        }
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATIONS);
        } else requestCapture();
    }

    private void requestCapture() {
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        status.setText("Allow screen capture to enable the floating translator.");
        Intent capture = Build.VERSION.SDK_INT >= 34
                ? manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                : manager.createScreenCaptureIntent();
        startActivityForResult(capture, CAPTURE);
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == NOTIFICATIONS) requestCapture();
    }

    @Override public void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != CAPTURE) return;
        starting = false;
        enableControls(true);
        if (result == RESULT_OK && data != null) {
            LanguageSettings languages = pending == null ? LanguageSettings.load(this) : pending;
            Intent service = new Intent(this, CaptureService.class).putExtra("resultCode", result).putExtra("captureData", data)
                    .putExtra("source", languages.source).putExtra("target", languages.target);
            startForegroundService(service);
            status.setText("Translator is running. Tap Highlight in any app.");
            moveTaskToBack(true);
        } else status.setText("Screen capture cancelled. Tap Start when you're ready.");
    }

    @Override public void onResume() {
        super.onResume();
        resumed = true;
        if (permissions == null) return;
        if (CaptureService.active) setFloaterVisible(false);
        refresh();
        if (prepared) { continueStart(); return; }
        if (waitingForOverlay) {
            waitingForOverlay = false;
            if (Settings.canDrawOverlays(this)) requestNotifications();
            else {
                starting = false;
                enableControls(true);
                status.setText("Display-over-apps permission is needed for the floating card.");
            }
        }
    }

    @Override public void onPause() {
        resumed = false;
        if (CaptureService.active) setFloaterVisible(true);
        super.onPause();
    }

    private void setFloaterVisible(boolean visible) {
        startService(new Intent(this, CaptureService.class).setAction(CaptureService.SET_VISIBILITY).putExtra("visible", visible));
    }

    private void enableControls(boolean enabled) { start.setEnabled(enabled); from.setEnabled(enabled); to.setEnabled(enabled); }

    private void refresh() {
        LanguageSettings languages = selected();
        permissions.setText("Floating card: " + (Settings.canDrawOverlays(this) ? "enabled" : "permission needed") + "  ·  " + languages.label());
        if (starting) return;
        boolean running = CaptureService.active;
        start.setText(!running ? "Start translating" : languages.samePair(CaptureService.currentLanguages) ? "Return to chat" : "Apply languages & return");
        if (running && CaptureService.currentLanguages != null) status.setText("Running · " + CaptureService.currentLanguages.label());
    }

    private void openLink(String url) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }

    @Override public void onSaveInstanceState(Bundle state) {
        state.putBoolean("waitingForOverlay", waitingForOverlay);
        super.onSaveInstanceState(state);
    }
    @Override public void onDestroy() {
        preparation++;
        if (engine != null) engine.close();
        super.onDestroy();
    }
}
