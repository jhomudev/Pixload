package com.pixload.app;

import static org.junit.Assert.assertNotNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Android instantiates Activity fields before attaching its Context. */
@RunWith(AndroidJUnit4.class)
public class MainActivityStartupTest {
    @Test public void mainActivityCanBeConstructedBeforeContextAttachment() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> assertNotNull(new MainActivity()));
    }
}
