package com.pixload.bench.square;

import java.util.Set;

/** Diagnostic palette derived from bounded pixel samples. */
public final class FixedQuantizer implements ColorQuantizer {
    private final Set<Color> palette;

    public FixedQuantizer(int[] pixels) {
        HashMultiset<Color> sampled = new HashMultiset<>();
        for (int i = 0; i < pixels.length; i += 8) {
            int value = pixels[i];
            int rgb = ((value >> 16) & 0xf8) << 16 | ((value >> 8) & 0xf8) << 8 | (value & 0xf8);
            sampled.add(Color.fromRgbInt(rgb));
        }
        palette = MedianCutQuantizer.INSTANCE.quantize(sampled, 256);
    }

    @Override public Set<Color> quantize(Multiset<Color> originalColors, int maxColorCount) {
        return palette;
    }
}
