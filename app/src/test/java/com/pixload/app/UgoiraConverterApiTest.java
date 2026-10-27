package com.pixload.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.pixload.shared.GifPreset;

import org.json.JSONArray;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Guards the Java-callable surface DownloadService and UgoiraConverterTest compile against. */
public class UgoiraConverterApiTest {
    @Test
    public void keepsStaticEntryPointsDownloadServiceCalls() throws Exception {
        Class<?> type = Class.forName("com.pixload.app.UgoiraConverter");
        assertDescriptor(type, "gif", new Class<?>[] {File.class, JSONArray.class, OutputStream.class,
                progressClass()});
        assertDescriptor(type, "gif", new Class<?>[] {File.class, JSONArray.class, OutputStream.class,
                progressClass(), GifPreset.class});
        assertDescriptor(type, "mp4", new Class<?>[] {File.class, JSONArray.class, File.class, progressClass()});
    }

    @Test
    public void progressStaysAnImplementableThrowingSam() throws Exception {
        Class<?> progress = progressClass();
        assertTrue(progress.isInterface());
        Method[] abstractMethods = progress.getMethods();
        assertEquals(1, abstractMethods.length);
        assertEquals("update", abstractMethods[0].getName());
        assertArrayEquals(new Class<?>[] {int.class, int.class}, abstractMethods[0].getParameterTypes());
        assertEquals(void.class, abstractMethods[0].getReturnType());
        assertArrayEquals(new Class<?>[] {IOException.class}, abstractMethods[0].getExceptionTypes());

        // DownloadService's lambda declares IOException inside its body, so the throws clause is load-bearing.
        UgoiraConverter.Progress lambda = (done, total) -> {
            throw new IOException("Conversión interrumpida");
        };
        try {
            lambda.update(1, 2);
        } catch (IOException expected) {
            assertEquals("Conversión interrumpida", expected.getMessage());
        }
    }

    private static Class<?> progressClass() throws ClassNotFoundException {
        return Class.forName("com.pixload.app.UgoiraConverter$Progress");
    }

    private static void assertDescriptor(Class<?> type, String name, Class<?>[] parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        assertTrue(name, Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
        assertArrayEquals(new Class<?>[] {Exception.class}, method.getExceptionTypes());
    }
}
