package com.pixload.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.VideoView;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ExecutorService;

import org.junit.Test;

/** Guards the static overloads used by MainActivity after the Kotlin translation. */
public class GalleryViewerApiTest {
    @Test public void javaCallersKeepBothShowOverloads() throws Exception {
        Class<?> viewer = GalleryViewer.class;
        Class<?>[] parameters = {
                Activity.class, LinearLayout.class, GalleryDocuments.Item.class, ExecutorService.class
        };
        for (Class<?>[] signature : new Class<?>[][] {parameters, {
                Activity.class, LinearLayout.class, GalleryDocuments.Item.class, ExecutorService.class, int.class
        }}) {
            Method show = viewer.getDeclaredMethod("show", signature);
            assertTrue(Modifier.isStatic(show.getModifiers()));
            assertEquals(VideoView.class, show.getReturnType());
            assertFalse(Modifier.isPrivate(show.getModifiers()));
        }
    }
}
