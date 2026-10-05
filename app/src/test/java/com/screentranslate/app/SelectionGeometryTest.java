package com.screentranslate.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class SelectionGeometryTest {
    @Test public void reverseDragSelectsSamePixels() {
        assertArrayEquals(new int[]{100, 200, 300, 100}, SelectionGeometry.crop(400, 300, 100, 200, 1080, 1920, 1080, 1920));
    }
    @Test public void mapsScaledScreenshots() {
        assertArrayEquals(new int[]{200, 400, 600, 200}, SelectionGeometry.crop(100, 200, 400, 300, 540, 960, 1080, 1920));
    }
    @Test public void letterboxDoesNotOffsetSelectedText() {
        assertArrayEquals(new int[]{100, 0, 200, 100}, SelectionGeometry.crop(100, 50, 300, 150, 400, 400, 400, 300));
    }
    @Test public void boundsNeverExtendOutsideScreenshot() {
        assertArrayEquals(new int[]{0, 0, 1080, 1920}, SelectionGeometry.crop(-10, -20, 1200, 2100, 1080, 1920, 1080, 1920));
    }
    @Test public void emptyAndInvalidSelectionsAreRejected() {
        assertNull(SelectionGeometry.crop(100, 100, 100, 200, 1080, 1920, 1080, 1920));
        assertNull(SelectionGeometry.crop(10, 10, 20, 20, 0, 1920, 1080, 1920));
    }
}
