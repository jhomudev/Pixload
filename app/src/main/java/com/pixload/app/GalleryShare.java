package com.pixload.app;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

final class GalleryShare {
    private GalleryShare() { }

    static Intent intent(List<GalleryDocuments.Item> items) {
        return intent(items, "Descarga de Pixload");
    }

    static Intent intent(List<GalleryDocuments.Item> items, String label) {
        if (items.isEmpty()) throw new IllegalArgumentException("No hay archivos para compartir.");
        ArrayList<Uri> uris = new ArrayList<>(items.size());
        String mime = items.get(0).mime;
        if (mime == null || mime.isEmpty()) mime = "*/*";
        ClipData clip = ClipData.newRawUri(label, items.get(0).uri);
        for (int i = 0; i < items.size(); i++) {
            GalleryDocuments.Item item = items.get(i);
            uris.add(item.uri);
            if (i > 0) {
                clip.addItem(new ClipData.Item(item.uri));
                if (!mime.equals(item.mime)) mime = "*/*";
            }
        }
        Intent send = new Intent(items.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        send.setType(mime);
        if (items.size() == 1) send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        send.setClipData(clip);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return send;
    }
}
