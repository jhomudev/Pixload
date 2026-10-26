package com.pixload.app;

import com.pixload.shared.PixivLinks;

final class PixivLink {
    private PixivLink() { }

    static String extract(String text) {
        return PixivLinks.INSTANCE.extract(text);
    }
}
