/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pixload.app;

import com.squareup.gifencoder.Color;
import com.squareup.gifencoder.ColorQuantizer;
import com.squareup.gifencoder.HashMultiset;
import com.squareup.gifencoder.Multiset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Adapted from Square gifencoder 0.10.1 MedianCutQuantizer.
 * Modifications: exact RGB tie ordering, invocation-local cluster ordinals, counted splits,
 * canonical centroid accumulation and canonical output (including the small-palette bypass).
 */
final class DeterministicMedianCutQuantizer implements ColorQuantizer {
    static final DeterministicMedianCutQuantizer INSTANCE = new DeterministicMedianCutQuantizer();

    private static final Comparator<Color> RGB_ORDER = (a, b) -> {
        for (int component = 0; component < 3; component++) {
            int order = Double.compare(a.getComponent(component), b.getComponent(component));
            if (order != 0) return order;
        }
        return 0;
    };

    private DeterministicMedianCutQuantizer() {}

    @Override public Set<Color> quantize(Multiset<Color> originalColors, int maxColorCount) {
        if (originalColors.getDistinctElements().size() <= maxColorCount) {
            return canonicalPalette(originalColors.getDistinctElements());
        }
        TreeSet<Cluster> clusters = new TreeSet<>((a, b) -> {
            int spreadOrder = Double.compare(b.largestSpread, a.largestSpread);
            return spreadOrder != 0 ? spreadOrder : Long.compare(a.ordinal, b.ordinal);
        });
        long nextOrdinal = 0;
        clusters.add(new Cluster(originalColors, nextOrdinal++));
        while (clusters.size() < maxColorCount) {
            Cluster cluster = clusters.pollFirst();
            List<Color> ordered = new ArrayList<>(cluster.colors.getDistinctElements());
            ordered.sort((a, b) -> {
                int order = Double.compare(a.getComponent(cluster.componentWithLargestSpread),
                        b.getComponent(cluster.componentWithLargestSpread));
                return order != 0 ? order : RGB_ORDER.compare(a, b);
            });
            int remaining = cluster.colors.size() / 2;
            Multiset<Color> left = new HashMultiset<>();
            Multiset<Color> right = new HashMultiset<>();
            for (Color color : ordered) {
                int count = cluster.colors.count(color);
                int leftCount = Math.min(remaining, count);
                if (leftCount > 0) left.add(color, leftCount);
                if (count > leftCount) right.add(color, count - leftCount);
                remaining -= leftCount;
            }
            clusters.add(new Cluster(left, nextOrdinal++));
            clusters.add(new Cluster(right, nextOrdinal++));
        }
        List<Color> centroids = new ArrayList<>(clusters.size());
        for (Cluster cluster : clusters) {
            List<Color> ordered = new ArrayList<>(cluster.colors.getDistinctElements());
            ordered.sort(RGB_ORDER);
            Color sum = Color.BLACK;
            for (Color color : ordered) {
                // Keep Color.getCentroid's multiply, add and final normalization semantics.
                sum = sum.plus(color.scaled(cluster.colors.count(color)));
            }
            centroids.add(sum.scaled(1.0 / cluster.colors.size()));
        }
        return canonicalPalette(centroids);
    }

    private static Set<Color> canonicalPalette(Collection<Color> colors) {
        List<Color> ordered = new ArrayList<>(colors);
        ordered.sort(RGB_ORDER);
        return new LinkedHashSet<>(ordered);
    }

    private static final class Cluster {
        final Multiset<Color> colors;
        final long ordinal;
        double largestSpread = -1;
        int componentWithLargestSpread;

        Cluster(Multiset<Color> colors, long ordinal) {
            this.colors = colors;
            this.ordinal = ordinal;
            for (int component = 0; component < 3; component++) {
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;
                for (Color color : colors.getDistinctElements()) {
                    min = Math.min(min, color.getComponent(component));
                    max = Math.max(max, color.getComponent(component));
                }
                double spread = max - min;
                if (spread > largestSpread) {
                    largestSpread = spread;
                    componentWithLargestSpread = component;
                }
            }
        }
    }
}
