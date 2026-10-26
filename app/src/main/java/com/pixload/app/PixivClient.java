package com.pixload.app;

import android.net.Uri;
import android.webkit.CookieManager;

import com.pixload.shared.PixivWorkLinks;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class PixivClient {
    private static final String BASE = "https://www.pixiv.net";
    private static final String REFERER = BASE + "/";

    static boolean isLoggedIn() {
        try {
            HttpURLConnection connection = open(BASE + "/ajax/user/extra");
            try {
                if (connection.getResponseCode() != 200) return false;
                try (InputStream stream = connection.getInputStream()) {
                    return !new JSONObject(new String(read(stream), StandardCharsets.UTF_8)).optBoolean("error", true);
                }
            } finally {
                connection.disconnect();
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    static final class Work {
        String id;
        String title;
        String author;
        String type;
        String thumbnail;
        String text;
        String animationUrl;
        JSONArray frames;
        final List<String> pages = new ArrayList<>();
        final List<String> pageThumbnails = new ArrayList<>();
    }

    static Work load(String input) throws Exception {
        Uri uri = Uri.parse(input.trim());
        // Retain Android Uri's parsing and query decoding before pure classification.
        // The original order checks host and scheme before reading the query parameter.
        PixivWorkLinks.Result validation = PixivWorkLinks.INSTANCE.classify(
                uri.getHost(), uri.getScheme(), uri.getPath(), null);
        if (validation instanceof PixivWorkLinks.Rejected) {
            PixivWorkLinks.Reason reason = ((PixivWorkLinks.Rejected) validation).getReason();
            if (reason == PixivWorkLinks.Reason.HOST) throw new IOException("Introduce un enlace de pixiv.net.");
            if (reason == PixivWorkLinks.Reason.SCHEME) throw new IOException("El enlace debe usar HTTP o HTTPS.");
        }
        validation = PixivWorkLinks.INSTANCE.classify(
                uri.getHost(), uri.getScheme(), uri.getPath(), uri.getQueryParameter("id"));
        if (validation instanceof PixivWorkLinks.Rejected) throw new IOException("Enlace de obra no reconocido.");
        PixivWorkLinks.Work link = (PixivWorkLinks.Work) validation;
        boolean novel = link.getNovel();
        String id = link.getId();

        String prefix = novel ? "/ajax/novel/" : "/ajax/illust/";
        JSONObject body = api(prefix + id);
        Work work = new Work();
        work.id = id;
        work.title = body.optString("title", "obra-" + id);
        work.author = body.optString("userName", "");
        JSONObject previewUrls = body.optJSONObject("urls");
        work.thumbnail = previewUrls == null || previewUrls.isNull("regular") ? "" : previewUrls.optString("regular", "");
        // Pixiv still exposes metadata for age-restricted works to anonymous visitors,
        // but withholds their image URLs and returns 404 for their original files.
        if (!novel && body.optInt("xRestrict", 0) > 0 && work.thumbnail.isEmpty()) {
            throw new IOException("Esta obra es R-18. Pixiv no permite ver sus archivos sin una sesión " +
                    "autorizada y con el contenido R-18 habilitado en la cuenta.");
        }
        if (novel) {
            work.type = "novel";
            work.thumbnail = body.optString("coverUrl", "");
            work.text = body.optString("content", "");
            if (work.text.isEmpty()) throw new IOException("Pixiv no ha proporcionado el texto de esta novela.");
        } else if (body.optInt("illustType", 0) == 2) {
            work.type = "ugoira";
            JSONObject meta = api("/ajax/illust/" + id + "/ugoira_meta");
            work.animationUrl = meta.getString("originalSrc");
            work.frames = meta.getJSONArray("frames");
        } else {
            work.type = "image";
            JSONArray pages = apiArray("/ajax/illust/" + id + "/pages");
            for (int i = 0; i < pages.length(); i++) {
                JSONObject urls = pages.getJSONObject(i).getJSONObject("urls");
                work.pages.add(urls.getString("original"));
                String small = urls.optString("small", "");
                work.pageThumbnails.add(small.isEmpty() ? urls.optString("regular", "") : small);
                if (work.thumbnail.isEmpty()) work.thumbnail = urls.optString("regular", "");
            }
            if (work.pages.isEmpty()) throw new IOException("La obra no tiene páginas disponibles.");
        }
        return work;
    }

    private static JSONObject api(String path) throws Exception {
        return response(path).getJSONObject("body");
    }

    private static JSONArray apiArray(String path) throws Exception {
        return response(path).getJSONArray("body");
    }

    private static JSONObject response(String path) throws Exception {
        HttpURLConnection connection = open(BASE + path);
        try {
            int code = connection.getResponseCode();
            if (code == 401 || code == 403) throw new IOException("Esta obra requiere iniciar sesión o no está disponible para tu cuenta.");
            if (code != 200) throw new IOException("Pixiv respondió con HTTP " + code + ".");
            try (InputStream stream = connection.getInputStream()) {
                JSONObject json = new JSONObject(new String(read(stream), StandardCharsets.UTF_8));
                if (json.optBoolean("error")) throw new IOException(json.optString("message", "Pixiv denegó el acceso."));
                return json;
            }
        } finally {
            connection.disconnect();
        }
    }

    static HttpURLConnection open(String address) throws IOException {
        URL url = new URL(address);
        String host = url.getHost().toLowerCase();
        if (!"https".equalsIgnoreCase(url.getProtocol()) ||
                !(host.equals("www.pixiv.net") || host.equals("pixiv.net") || host.equals("i.pximg.net"))) {
            throw new IOException("Origen de archivo no permitido.");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Referer", REFERER);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
        if (host.equals("www.pixiv.net") || host.equals("pixiv.net")) {
            String cookie = CookieManager.getInstance().getCookie(BASE);
            if (cookie != null) connection.setRequestProperty("Cookie", cookie);
        }
        return connection;
    }

    static byte[] read(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    static String extension(String address) {
        String path = Uri.parse(address).getPath();
        String name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "jpg";
        return ext.matches("jpg|jpeg|png|gif|webp|zip") ? ext : "jpg";
    }
}
