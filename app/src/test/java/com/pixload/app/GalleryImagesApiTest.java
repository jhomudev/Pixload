package com.pixload.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.util.LruCache;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ExecutorService;

import org.junit.Test;

/** Guards the Java-callable surface MainActivity needs from the Kotlin GalleryImages helper. */
public class GalleryImagesApiTest {
    @Test
    public void keepsJavaCallableSurface() throws Exception {
        Class<?> type = Class.forName("com.pixload.app.GalleryImages");
        Constructor<?> constructor = type.getDeclaredConstructor(Activity.class);
        assertFalse(Modifier.isStatic(constructor.getModifiers()));
        Method tile = type.getDeclaredMethod("tile", String.class, String.class, int.class);
        assertEquals(View.class, tile.getReturnType());
        assertFalse(Modifier.isStatic(tile.getModifiers()));
        for (String name : new String[] {"clear", "loadVisible", "shutdown"}) {
            Method method = type.getDeclaredMethod(name);
            assertEquals(void.class, method.getReturnType());
            assertFalse(Modifier.isStatic(method.getModifiers()));
        }
        for (Method method : type.getDeclaredMethods()) {
            // Internal Kotlin members would be mangled; lambda bodies are private implementation detail.
            if (method.isSynthetic() || Modifier.isPrivate(method.getModifiers())) continue;
            assertFalse(method.getName(), method.getName().contains("$"));
        }
    }

    @Test
    public void keepsExecutorAndByteSizedCacheFields() throws Exception {
        Class<?> type = Class.forName("com.pixload.app.GalleryImages");
        Field executor = type.getDeclaredField("executor");
        assertTrue(ExecutorService.class.isAssignableFrom(executor.getType()));
        Field cache = type.getDeclaredField("cache");
        assertTrue(LruCache.class.isAssignableFrom(cache.getType()));
    }
}
