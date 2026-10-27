package com.pixload.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.Test;

public class MainActivityApiTest {
    @Test public void launcherActivityRetainsJavaLifecycleSurface() throws Exception {
        assertEquals(Activity.class, MainActivity.class.getSuperclass());
        assertTrue(!Modifier.isFinal(MainActivity.class.getModifiers()));
        assertTrue(Modifier.isPublic(MainActivity.class.getConstructor().getModifiers()));
        assertMethod("attachBaseContext", void.class, Context.class);
        assertMethod("onCreate", void.class, Bundle.class);
        assertMethod("onSaveInstanceState", void.class, Bundle.class);
        assertMethod("onNewIntent", void.class, Intent.class);
        assertMethod("onActivityResult", void.class, int.class, int.class, Intent.class);
        assertMethod("onResume", void.class);
        assertMethod("onPause", void.class);
        assertMethod("onDestroy", void.class);
        assertEquals(Object.class, MainActivity.class.getMethod("onRetainNonConfigurationInstance").getReturnType());
        assertEquals(boolean.class, MainActivity.class.getMethod("dispatchTouchEvent", MotionEvent.class).getReturnType());
        MainActivity.class.getMethod("onBackPressed");
    }

    private static void assertMethod(String name, Class<?> result, Class<?>... arguments) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, arguments);
        assertEquals(result, method.getReturnType());
        assertTrue(Modifier.isProtected(method.getModifiers()) || Modifier.isPublic(method.getModifiers()));
    }
}
