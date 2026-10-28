package com.pixload.app;

import static org.junit.Assert.assertNotNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Android instantiates Activity fields before attaching its Context. */
@RunWith(AndroidJUnit4.class)
public class MainActivityStartupTest {
    @Test public void mainActivityCanBeConstructedBeforeContextAttachment() {
        assertNotNull(new MainActivity());
    }
}
