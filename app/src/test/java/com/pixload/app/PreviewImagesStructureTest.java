package com.pixload.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.List;

import org.junit.Test;

/**
 * Pins the byte-sized cache override and the thumbnail state that PreviewImages keeps. Both are
 * reached through the declared field types so a refactor to a named class does not break the guard
 * for a reason unrelated to behavior. No Activity instance is required.
 */
public class PreviewImagesStructureTest {
    @Test
    public void sizesCacheByBitmapBytes() throws Exception {
        Field cache = Class.forName("com.pixload.app.PreviewImages").getDeclaredField("cache");
        Method sizeOf = cache.getType().getDeclaredMethod("sizeOf", String.class, Bitmap.class);
        assertEquals(int.class, sizeOf.getReturnType());
        assertTrue(Modifier.isProtected(sizeOf.getModifiers()));
    }

    @Test
    public void thumbnailKeepsViewReferencesAndStartedFlag() throws Exception {
        Field visible = Class.forName("com.pixload.app.PreviewImages").getDeclaredField("visibleRequests");
        ParameterizedType list = (ParameterizedType) visible.getGenericType();
        assertTrue(List.class.isAssignableFrom((Class<?>) list.getRawType()));
        Class<?> thumbnail = (Class<?>) list.getActualTypeArguments()[0];
        assertTrue(Modifier.isFinal(thumbnail.getModifiers()));
        for (String name : new String[] {"frame", "image", "skeleton", "url", "targetPixels", "started"}) {
            thumbnail.getDeclaredField(name);
        }
    }
}
