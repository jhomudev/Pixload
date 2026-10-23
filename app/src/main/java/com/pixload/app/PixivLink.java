package com.pixload.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PixivLink {
    private static final Pattern URL = Pattern.compile(
            "https?://(?:www\\.)?pixiv\\.net/[^\\s\\u3000<>\"'“”‘’]+",
            Pattern.CASE_INSENSITIVE);
    private static final String TRAILING = ".,;:!?)]}»、。？！」";

    private PixivLink() { }

    static String extract(String text) {
        if (text == null) return null;
        Matcher found = URL.matcher(text);
        if (!found.find()) return null;
        String url = found.group();
        while (!url.isEmpty() && TRAILING.indexOf(url.charAt(url.length() - 1)) >= 0)
            url = url.substring(0, url.length() - 1);
        return url;
    }
}
