package com.pixload.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.webkit.WebView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ExecutorService;

import org.junit.Test;

/** Guards the Activity identity the manifest and MainActivity resolve, plus the login guards. */
public class LoginActivityApiTest {
    @Test
    public void keepsTheActivityIdentityTheManifestNeeds() throws Exception {
        Class<?> type = Class.forName("com.pixload.app.LoginActivity");
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertEquals(Activity.class, type.getSuperclass());
        Constructor<?> constructor = type.getConstructor();
        assertTrue(Modifier.isPublic(constructor.getModifiers()));
        for (Method method : type.getDeclaredMethods()) {
            // Lambda bodies and bridges are implementation detail, not the resolved lifecycle surface.
            if (method.isSynthetic() || Modifier.isPrivate(method.getModifiers())) continue;
            assertFalse(method.getName(), method.getName().contains("$"));
        }
    }

    @Test
    public void keepsTheLifecycleOverrides() throws Exception {
        Class<?> type = Class.forName("com.pixload.app.LoginActivity");
        assertEquals(void.class, type.getDeclaredMethod("attachBaseContext", Context.class).getReturnType());
        assertTrue(Modifier.isProtected(type.getDeclaredMethod("attachBaseContext", Context.class).getModifiers()));
        assertEquals(void.class, type.getDeclaredMethod("onCreate", Bundle.class).getReturnType());
        assertTrue(Modifier.isPublic(type.getDeclaredMethod("onCreate", Bundle.class).getModifiers()));
        assertEquals(void.class, type.getDeclaredMethod("onBackPressed").getReturnType());
        assertTrue(Modifier.isPublic(type.getDeclaredMethod("onBackPressed").getModifiers()));
        for (String name : new String[] {"onPause", "onDestroy"}) {
            Method method = type.getDeclaredMethod(name);
            assertEquals(void.class, method.getReturnType());
            assertTrue(Modifier.isProtected(method.getModifiers()));
        }
    }

    @Test
    public void keepsTheSessionCheckState() throws Exception {
        Class<?> type = Class.forName("com.pixload.app.LoginActivity");
        Field checks = type.getDeclaredField("checks");
        assertTrue(ExecutorService.class.isAssignableFrom(checks.getType()));
        assertTrue(Modifier.isFinal(checks.getModifiers()));
        assertEquals(WebView.class, type.getDeclaredField("webView").getType());
        // The two booleans gate the single in-flight session check and the terminal finish.
        assertEquals(boolean.class, type.getDeclaredField("checking").getType());
        assertEquals(boolean.class, type.getDeclaredField("finishing").getType());
    }
}
