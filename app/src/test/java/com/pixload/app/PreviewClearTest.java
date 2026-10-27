package com.pixload.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the clear rule the queue used to break: the preview and the link belong to the work on
 * screen, so they clear even when another job wrote the last finished status.
 */
public class PreviewClearTest {
    @Test
    public void previewClearsForTheLastQueuedWorkEvenWhenAnotherJobFinishedLast() {
        assertTrue(PreviewClear.preview("100", "100"));
    }

    @Test
    public void previewStaysWhenTheWorkOnScreenWasNotTheQueuedOne() {
        assertFalse(PreviewClear.preview("100", "200"));
        assertFalse(PreviewClear.preview("", "200"));
        assertFalse(PreviewClear.preview(null, null));
        assertFalse(PreviewClear.preview("100", null));
    }

    @Test
    public void linkClearsOnlyWhileTheBoxHoldsTheQueuedLink() {
        assertTrue(PreviewClear.link("https://www.pixiv.net/artworks/1", "https://www.pixiv.net/artworks/1"));
        assertFalse(PreviewClear.link("https://www.pixiv.net/artworks/1", "https://www.pixiv.net/artworks/2"));
        assertFalse(PreviewClear.link("", ""));
        assertFalse(PreviewClear.link(null, "https://www.pixiv.net/artworks/2"));
    }
}
