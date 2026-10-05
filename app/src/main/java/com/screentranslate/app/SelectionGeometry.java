package com.screentranslate.app;

/** Pure coordinate mapping, also used by the tests. Rectangles may be drawn in any direction. */
final class SelectionGeometry {
    static int[] crop(float x1, float y1, float x2, float y2, int viewWidth, int viewHeight, int imageWidth, int imageHeight) {
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return null;
        float scale = Math.min((float) viewWidth / imageWidth, (float) viewHeight / imageHeight);
        float offsetX = (viewWidth - imageWidth * scale) / 2f;
        float offsetY = (viewHeight - imageHeight * scale) / 2f;
        int left = clamp((int) Math.floor((Math.min(x1, x2) - offsetX) / scale), imageWidth);
        int top = clamp((int) Math.floor((Math.min(y1, y2) - offsetY) / scale), imageHeight);
        int right = clamp((int) Math.ceil((Math.max(x1, x2) - offsetX) / scale), imageWidth);
        int bottom = clamp((int) Math.ceil((Math.max(y1, y2) - offsetY) / scale), imageHeight);
        return right > left && bottom > top ? new int[]{left, top, right - left, bottom - top} : null;
    }
    private static int clamp(int value, int maximum) { return Math.max(0, Math.min(maximum, value)); }
}
