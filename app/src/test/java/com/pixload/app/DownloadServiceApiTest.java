package com.pixload.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/** Pins the manifest and Java-callable service surface during the Kotlin migration. */
public class DownloadServiceApiTest {
    @Test public void keepsServiceLifecycleAndQueuedWorkFields() throws Exception {
        Class<?> type = DownloadService.class;
        assertEquals(Service.class, type.getSuperclass());
        assertTrue(Modifier.isPublic(type.getConstructor().getModifiers()));
        assertEquals(void.class, type.getDeclaredMethod("onCreate").getReturnType());
        assertEquals(int.class, type.getDeclaredMethod("onStartCommand", Intent.class, int.class, int.class).getReturnType());
        assertEquals(IBinder.class, type.getDeclaredMethod("onBind", Intent.class).getReturnType());
        assertEquals(void.class, type.getDeclaredMethod("onDestroy").getReturnType());
        Field queue = type.getDeclaredField("queue");
        assertTrue(ExecutorService.class.isAssignableFrom(queue.getType()));
        assertTrue(Modifier.isFinal(queue.getModifiers()));
        assertEquals(AtomicInteger.class, type.getDeclaredField("pending").getType());
        assertTrue(ConcurrentHashMap.class.isAssignableFrom(type.getDeclaredField("jobs").getType()));
    }

    @Test public void javaCallersKeepNamingAndNotificationMethods() throws Exception {
        Class<?> type = DownloadService.class;
        for (String name : new String[] {"startOrdinal", "folderName", "collisionName", "flatFileName", "titleName", "notificationDetail", "cancel"}) {
            Method method = null;
            for (Method candidate : type.getDeclaredMethods()) {
                if (candidate.getName().equals(name) && Modifier.isStatic(candidate.getModifiers())) method = candidate;
            }
            assertTrue(name, method != null);
            assertFalse(name, Modifier.isPrivate(method.getModifiers()));
        }
    }
}
