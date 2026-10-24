package com.pixload.bench.square;

import java.util.Set;

/** Diagnostic lookup mapper: 5 bits/channel and optional Floyd-Steinberg diffusion. */
public final class LookupDitherer implements Ditherer {
    private final boolean diffuse;
    private Color[] table;
    private Set<Color> lastPalette;

    public LookupDitherer(boolean diffuse) { this.diffuse = diffuse; }

    @Override public Image dither(Image image, Set<Color> palette) {
        if (lastPalette != palette) {
            Color[] entries = palette.toArray(new Color[0]);
            table = new Color[32768];
            for (int i = 0; i < table.length; i++) {
                int r = ((i >> 10) & 31) * 8 + 4;
                int g = ((i >> 5) & 31) * 8 + 4;
                int b = (i & 31) * 8 + 4;
                int best = Integer.MAX_VALUE;
                for (Color color : entries) {
                    int dr = r - (int) (color.getComponent(0) * 255);
                    int dg = g - (int) (color.getComponent(1) * 255);
                    int db = b - (int) (color.getComponent(2) * 255);
                    int distance = dr * dr + dg * dg + db * db;
                    if (distance < best) { best = distance; table[i] = color; }
                }
            }
            lastPalette = palette;
        }
        int width = image.getWidth(), height = image.getHeight();
        Color[][] result = new Color[height][width];
        float[] current = new float[(width + 2) * 3], next = new float[(width + 2) * 3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color source = image.getColor(x, y);
                int pos = (x + 1) * 3;
                int red = clamp((int) (source.getComponent(0) * 255 + current[pos]));
                int green = clamp((int) (source.getComponent(1) * 255 + current[pos + 1]));
                int blue = clamp((int) (source.getComponent(2) * 255 + current[pos + 2]));
                Color chosen = table[((red >> 3) << 10) | ((green >> 3) << 5) | (blue >> 3)];
                result[y][x] = chosen;
                if (diffuse) for (int c = 0; c < 3; c++) {
                    float error = (c == 0 ? red : c == 1 ? green : blue)
                            - (float) chosen.getComponent(c) * 255;
                    if (x + 1 < width) current[pos + 3 + c] += error * 7 / 16;
                    if (y + 1 < height) {
                        if (x > 0) next[pos - 3 + c] += error * 3 / 16;
                        next[pos + c] += error * 5 / 16;
                        if (x + 1 < width) next[pos + 3 + c] += error / 16;
                    }
                }
            }
            float[] swap = current; current = next; next = swap;
            java.util.Arrays.fill(next, 0);
        }
        return Image.fromColors(result);
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
}
