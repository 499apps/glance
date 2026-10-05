package com.screentranslate.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;
import com.google.android.gms.tasks.Tasks;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class ScreenTranslationTest {
    @Test public void highlightsTranslateReplaceAndKeepChatUsable() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        File screenshots = context.getExternalFilesDir(null);
        context.stopService(new Intent(context, CaptureService.class));
        new LanguageSettings("es", "en").save(context);
        for (String[] pair : new String[][]{{"es", "en"}, {"fr", "de"}}) {
            TranslationEngine engine = new TranslationEngine(pair[0], pair[1]);
            try { Tasks.await(engine.prepare(), 180, TimeUnit.SECONDS); }
            finally { engine.close(); }
        }
        try {
        device.executeShellCommand("appops set " + context.getPackageName() + " SYSTEM_ALERT_WINDOW allow");
        device.executeShellCommand("pm grant " + context.getPackageName() + " android.permission.POST_NOTIFICATIONS");
        context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        assertTrue(device.wait(Until.hasObject(By.text("Start translating")), 10000));
        assertNotNull(device.findObject(By.desc("Source language")));
        assertNotNull(device.findObject(By.desc("Translation language")));
        device.takeScreenshot(new File(screenshots, "main-languages.png"));
        device.findObject(By.text("Start translating")).click();
        UiObject2 consent = device.wait(Until.findObject(By.text(Pattern.compile("(?i)start(?: (now|recording|sharing))?"))), 15000);
        assertNotNull("Android capture consent should appear", consent);
        consent.click();
        assertTrue(device.wait(Until.hasObject(By.text("Highlight")), 15000));
        device.executeShellCommand("am start -n " + context.getPackageName() + "/.ChatFixtureActivity");
        assertTrue(device.wait(Until.hasObject(By.desc("Spanish message one")), 10000));
        Rect first = device.findObject(By.desc("Spanish message one")).getVisibleBounds();
        select(device, first);
        boolean translated = device.wait(Until.hasObject(By.text(Pattern.compile("(?is).*hello.*how are you.*"))), 15000);
        device.takeScreenshot(new File(screenshots, "first-result.png"));
        device.dumpWindowHierarchy(new File(screenshots, "first-result.xml"));
        assertTrue("English should appear after releasing the highlight", translated);
        device.takeScreenshot(new File(screenshots, "first-translation.png"));
        Rect second = device.findObject(By.desc("Spanish message two")).getVisibleBounds();
        select(device, second);
        assertTrue("The next selection should replace the first translation", device.wait(Until.hasObject(By.text(Pattern.compile("(?is).*(see you|we.*see).*tomorrow.*"))), 15000));
        assertNull("Previous translation should be removed", device.findObject(By.text(Pattern.compile("(?is).*hello.*how are you.*"))));
        device.takeScreenshot(new File(screenshots, "second-translation.png"));

        UiObject2 header = device.findObject(By.desc("Drag translation card to move it."));
        Rect previous = header.getVisibleBounds();
        device.swipe(previous.centerX(), previous.centerY(), previous.centerX() + 50, previous.centerY() - 80, 25);
        Rect moved = device.findObject(By.desc("Drag translation card to move it.")).getVisibleBounds();
        assertNotEquals("The card should be movable", previous.top, moved.top);
        device.findObject(By.desc("Collapse translation card")).click();
        assertTrue(device.wait(Until.hasObject(By.text("EN")), 5000));
        UiObject2 reply = device.findObject(By.desc("Chat reply field"));
        reply.click();
        reply.setText("Reply stays usable");
        assertEquals("Reply stays usable", reply.getText());
        device.pressBack();
        device.findObject(By.text("EN")).click();
        assertTrue(device.wait(Until.hasObject(By.text(Pattern.compile("(?is).*(see you|we.*see).*tomorrow.*"))), 5000));
        device.findObject(By.text("Highlight")).click();
        assertTrue(device.wait(Until.hasObject(By.descContains("Drag a rectangle")), 5000));
        device.takeScreenshot(new File(screenshots, "selection.png"));
        device.pressBack();
        assertTrue("Cancelling should preserve the latest translation", device.wait(Until.hasObject(By.text(Pattern.compile("(?is).*(see you|we.*see).*tomorrow.*"))), 5000));
        device.takeScreenshot(new File(screenshots, "floating-chat.png"));

        device.findObject(By.desc("Open Glance settings")).click();
        assertTrue(device.wait(Until.hasObject(By.desc("Source language")), 10000));
        assertTrue("The card should hide while settings are open", device.wait(Until.gone(By.text("Highlight")), 5000));
        assertTrue("Settings must keep the capture session alive", CaptureService.active);
        chooseLanguage(device, "Source language", "French");
        chooseLanguage(device, "Translation language", "German");
        assertTrue(device.wait(Until.hasObject(By.text("Apply languages & return")), 5000));
        device.findObject(By.text("Apply languages & return")).click();
        assertTrue("Changing languages should reuse screen capture", device.wait(Until.hasObject(By.text("German  ·  FR → DE")), 15000));
        assertNull("Changing languages clears the old translation", device.findObject(By.text(Pattern.compile("(?is).*(see you|we.*see).*tomorrow.*"))));
        device.executeShellCommand("am start -n " + context.getPackageName() + "/.ChatFixtureActivity --es source fr");
        assertTrue(device.wait(Until.hasObject(By.desc("French message one")), 10000));
        select(device, device.findObject(By.desc("French message one")).getVisibleBounds());
        boolean german = device.wait(Until.hasObject(By.text(Pattern.compile("(?is).*hallo.*wie.*"))), 15000);
        device.takeScreenshot(new File(screenshots, "french-german.png"));
        assertTrue("The new pair must translate French into German", german);
        device.findObject(By.desc("Open Glance settings")).click();
        assertTrue(device.wait(Until.hasObject(By.text("Return to chat")), 10000));
        assertEquals("fr", LanguageSettings.load(context).source);
        assertEquals("de", LanguageSettings.load(context).target);
        assertNotNull(device.findObject(By.text("French")));
        assertNotNull(device.findObject(By.text("German")));
        device.takeScreenshot(new File(screenshots, "saved-languages.png"));
        device.findObject(By.text("Return to chat")).click();
        assertTrue(device.wait(Until.hasObject(By.text("Highlight")), 5000));

        context.stopService(new Intent(context, CaptureService.class));
        assertTrue("Stopping must remove the card", device.wait(Until.gone(By.text("Highlight")), 5000));
        } finally {
            context.stopService(new Intent(context, CaptureService.class));
            new LanguageSettings("es", "en").save(context);
        }
    }

    private void chooseLanguage(UiDevice device, String description, String language) throws Exception {
        device.findObject(By.desc(description)).click();
        UiScrollable dropdown = new UiScrollable(new UiSelector().className("android.widget.ListView"));
        assertTrue("The dropdown should offer " + language, dropdown.scrollIntoView(new UiSelector().text(language)));
        device.findObject(By.text(language)).click();
    }

    private void select(UiDevice device, Rect message) throws Exception {
        device.findObject(By.text("Highlight")).click();
        assertTrue(device.wait(Until.hasObject(By.descContains("Drag a rectangle")), 5000));
        device.takeScreenshot(new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "before-highlight.png"));
        // Start inside the message, outside Android's edge-back gesture strip.
        device.swipe(message.left + 32, message.top + 2, message.right - 32, message.bottom - 2, 35);
    }
}
