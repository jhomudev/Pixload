package com.pixload.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_FOLDER = 11;
    private static final int PIXIV_LOGIN = 12;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService galleryIO = Executors.newSingleThreadExecutor();
    private final PreviewImages images = new PreviewImages(this);
    private final GalleryImages galleryImages = new GalleryImages(this);
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdate = new Runnable() {
        @Override public void run() {
            refreshProgress();
            progressHandler.postDelayed(this, 500);
        }
    };
    private final List<CheckBox> pageChecks = new ArrayList<>();
    private LinearLayout root;
    private boolean fadeNextTab;
    private LinearLayout content;
    private LinearLayout bottom;
    private ScrollView scroll;
    private EditText link;
    private TextView status;
    private TextView sessionBadge;
    private TextView selectionCount;
    private TextView selectionDownloadButton;
    private TextView progressTitle;
    private TextView progressNumber;
    private ImageView progressClose;
    private TextView progressOpen;
    private ProgressBar progressBar;
    private LinearLayout progressPanel;
    private PixivClient.Work work;
    private String linkValue;
    private String message = "Pega un enlace para consultar una obra de Pixiv.";
    private int tab;
    private boolean loading;
    private boolean loggedIn;
    private boolean downloadAfterFolderPick;
    private boolean mp4;
    private boolean defaultMp4;
    private GifPreset gifPreset;
    private int sessionGeneration;
    private boolean[] selectedPages;
    private GalleryStore.Entry galleryEntry;
    private List<GalleryDocuments.Item> galleryItems;
    private boolean galleryLoading;
    private String galleryError;
    private int galleryIndex;
    private VideoView galleryVideo;
    private View fullscreenMedia;
    private boolean galleryFullscreen;
    private int galleryScrollY;
    private boolean galleryListRendered;
    private boolean galleryScrollPending;
    private float swipeStartX;
    private float swipeStartY;
    private boolean swipeEligible;
    private boolean swiping;
    private String lastGalleryStatusUri = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.INPUT);
        getWindow().setNavigationBarColor(Ui.BACK);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
        linkValue = getPreferences(MODE_PRIVATE).getString("last_link", "");
        importLastDownload();
        defaultMp4 = "mp4".equals(getPreferences(MODE_PRIVATE).getString("animation_format", "gif"));
        mp4 = defaultMp4;
        gifPreset = GifPreset.fromId(getPreferences(MODE_PRIVATE).getString("gif_preset", "balanced"));
        if (state != null) {
            tab = state.getInt("tab", 0);
            linkValue = state.getString("link", linkValue);
            mp4 = state.getBoolean("mp4", mp4);
            selectedPages = state.getBooleanArray("pages");
            downloadAfterFolderPick = state.getBoolean("downloadAfterFolderPick");
            galleryIndex = state.getInt("galleryIndex", 0);
            galleryScrollY = state.getInt("galleryScrollY", 0);
            galleryFullscreen = state.getBoolean("galleryFullscreen");
            try { galleryEntry = GalleryStore.find(this, state.getString("galleryEntry", "")); }
            catch (IllegalStateException ignored) { galleryEntry = null; }
            galleryLoading = galleryEntry != null;
            if (galleryEntry == null) galleryFullscreen = false;
            Object retained = getLastNonConfigurationInstance();
            if (retained instanceof PixivClient.Work) work = (PixivClient.Work) retained;
        }
        render();
        if (galleryEntry != null) loadGalleryEntry(galleryEntry, galleryIndex);
        consume(getIntent());
    }

    @Override public Object onRetainNonConfigurationInstance() { return work; }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (link != null) linkValue = link.getText().toString();
        syncSelection();
        state.putInt("tab", tab);
        state.putString("link", linkValue);
        state.putBoolean("mp4", mp4);
        state.putBooleanArray("pages", selectedPages);
        state.putBoolean("downloadAfterFolderPick", downloadAfterFolderPick);
        state.putString("galleryEntry", galleryEntry == null ? "" : galleryEntry.uri);
        state.putInt("galleryIndex", galleryIndex);
        if (galleryListRendered && !galleryScrollPending && scroll != null) galleryScrollY = scroll.getScrollY();
        state.putInt("galleryScrollY", galleryScrollY);
        state.putBoolean("galleryFullscreen", galleryFullscreen);
        super.onSaveInstanceState(state);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consume(intent);
    }

    private void consume(Intent intent) {
        if (intent == null) return;
        String input = Intent.ACTION_SEND.equals(intent.getAction()) ? intent.getStringExtra(Intent.EXTRA_TEXT)
                : Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null ? intent.getData().toString() : null;
        if (input != null) {
            String found = PixivLink.extract(input);
            linkValue = found == null ? input : found;
            if (link != null) link.setText(linkValue);
            intent.setAction(null);
            load();
        }
    }

    private void render() {
        if (galleryListRendered && !galleryScrollPending && scroll != null) galleryScrollY = scroll.getScrollY();
        galleryListRendered = false;
        galleryScrollPending = false;
        images.clear();
        galleryImages.clear();
        selectionDownloadButton = null;
        if (galleryVideo != null) { galleryVideo.stopPlayback(); galleryVideo = null; }
        progressTitle = null;
        progressNumber = null;
        progressClose = null;
        progressOpen = null;
        progressBar = null;
        progressPanel = null;
        boolean fade = fadeNextTab;
        fadeNextTab = false;
        root = Ui.column(this);
        root.setBackgroundColor(Ui.BACK);
        if (fade) root.setAlpha(.92f);
        setContentView(root);
        if (fade) root.animate().alpha(1f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).start();
        getWindow().getDecorView().setSystemUiVisibility(galleryFullscreen
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                : 0);
        if (galleryFullscreen && tab == 2 && galleryEntry != null) {
            scroll = null;
            fullscreenGallery();
            return;
        }
        header();
        scroll = new ScrollView(this);
        scroll.setOnScrollChangeListener((view, left, top, oldLeft, oldTop) -> {
            images.loadVisible();
            galleryImages.loadVisible();
        });
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        content = Ui.column(this);
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        bottom = Ui.column(this);
        bottom.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 12));
        root.addView(bottom);
        if (tab == 0) home();
        else if (tab == 1) preview();
        else if (tab == 2) gallery();
        else settings();
        navigation();
        refreshProgress();
        scroll.post(images::loadVisible);
        scroll.post(galleryImages::loadVisible);
        if (tab == 2 && galleryEntry == null) {
            galleryListRendered = true;
            galleryScrollPending = true;
            ScrollView target = scroll;
            target.post(() -> {
                if (scroll == target && tab == 2 && galleryEntry == null) {
                    target.scrollTo(0, galleryScrollY);
                    galleryScrollPending = false;
                    galleryImages.loadVisible();
                }
            });
        }
    }

    private void header() {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 12));
        bar.setBackgroundColor(Ui.INPUT);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setContentDescription("Pixload");
        bar.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        LinearLayout brand = Ui.row(this);
        brand.addView(Ui.text(this, "Pixload", 23, Ui.WHITE, true));
        View dot = new View(this);
        dot.setBackground(Ui.background(this, Ui.CYAN, 10));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(Ui.dp(this, 6), Ui.dp(this, 6));
        dotParams.leftMargin = Ui.dp(this, 3);
        brand.addView(dot, dotParams);
        LinearLayout.LayoutParams name = new LinearLayout.LayoutParams(0, -2, 1);
        name.leftMargin = Ui.dp(this, 10);
        bar.addView(brand, name);
        sessionBadge = Ui.text(this, loggedIn ? "Pixiv activo" : "Sin sesión", 12, loggedIn ? Ui.GREEN : Ui.MUTED, true);
        Ui.withIcon(sessionBadge, loggedIn ? R.drawable.tabler_circle_check : R.drawable.tabler_circle,
                loggedIn ? Ui.GREEN : Ui.MUTED);
        sessionBadge.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        sessionBadge.setBackground(Ui.background(this, Ui.RAISED, 40));
        sessionBadge.setOnClickListener(view -> switchTab(3));
        bar.addView(sessionBadge);
        root.addView(bar);
    }

    private void navigation() {
        LinearLayout nav = Ui.row(this);
        nav.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        nav.setBackground(Ui.outlined(this, Ui.RAISED, 48));
        String[] labels = {"Descargar", "Vista previa", "Galería", "Ajustes"};
        int[] icons = {R.drawable.tabler_download, R.drawable.tabler_photo_edit,
                R.drawable.tabler_layout_grid, R.drawable.tabler_settings_2};
        for (int i = 0; i < labels.length; i++) {
            final int target = i;
            int color = tab == i ? Ui.CYAN : Ui.MUTED;
            LinearLayout item = Ui.column(this);
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(Ui.dp(this, 56));
            item.setContentDescription(labels[i]);
            item.addView(Ui.icon(this, icons[i], color),
                    new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
            TextView label = Ui.text(this, labels[i], 12, color, tab == i);
            label.setGravity(Gravity.CENTER);
            item.addView(label);
            item.setOnClickListener(view -> switchTab(target));
            nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        bottom.addView(nav);
    }

    private void refreshProgress() {
        updateDownloadButton();
        DownloadStatus current = DownloadStatus.read(this);
        if (current != null && clearCompletedWork(current)) { render(); return; }
        if (current != null && "complete".equals(current.state) && !current.documentUri.isEmpty()
                && !current.documentUri.equals(lastGalleryStatusUri)) {
            lastGalleryStatusUri = current.documentUri;
            importLastDownload();
            if (tab == 2 && galleryEntry == null) { render(); return; }
        }
        if (current == null || bottom == null) {
            if (progressPanel != null && bottom != null) bottom.removeView(progressPanel);
            progressPanel = null;
            progressTitle = null;
            return;
        }
        if (progressTitle == null) {
            progressPanel = Ui.column(this);
            LinearLayout card = Ui.card(this, 12);
            LinearLayout heading = Ui.row(this);
            progressTitle = Ui.text(this, "", 13, Ui.WHITE, true);
            progressTitle.setMaxLines(2);
            heading.addView(progressTitle, new LinearLayout.LayoutParams(0, -2, 1));
            progressNumber = Ui.text(this, "", 12, Ui.CYAN, true);
            heading.addView(progressNumber);
            progressClose = Ui.icon(this, R.drawable.tabler_x, Ui.MUTED);
            progressClose.setContentDescription("Ocultar estado de descarga");
            progressClose.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            progressClose.setOnClickListener(view -> {
                DownloadStatus.dismiss(this);
                refreshProgress();
            });
            heading.addView(progressClose, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 40)));
            card.addView(heading);
            Ui.gap(card, this, 8);
            progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.CYAN));
            progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.CYAN));
            card.addView(progressBar, new LinearLayout.LayoutParams(-1, Ui.dp(this, 5)));
            progressOpen = Ui.button(this, "", false, this::openCompletedDownload);
            LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(-1, -2);
            openParams.topMargin = Ui.dp(this, 8);
            card.addView(progressOpen, openParams);
            progressPanel.addView(card);
            Ui.gap(progressPanel, this, 8);
            bottom.addView(progressPanel, bottom.getChildCount() - 1);
        }
        progressTitle.setText(current.title);
        boolean active = "active".equals(current.state);
        progressClose.setVisibility(active ? View.GONE : View.VISIBLE);
        progressNumber.setText(active ? current.max > 0 ? Math.min(100, current.progress * 100 / current.max) + "%" : "En curso"
                : "complete".equals(current.state) ? "Completada" : "cancelled".equals(current.state) ? "Cancelada" : "Error");
        progressNumber.setTextColor("error".equals(current.state) ? Ui.MUTED :
                "complete".equals(current.state) ? Ui.GREEN : Ui.CYAN);
        progressBar.setVisibility(active ? View.VISIBLE : View.GONE);
        boolean canOpen = "complete".equals(current.state) && !current.documentUri.isEmpty();
        progressOpen.setVisibility(canOpen ? View.VISIBLE : View.GONE);
        if (canOpen) {
            progressOpen.setText("Abrir en galería");
            Ui.withIcon(progressOpen, R.drawable.tabler_layout_grid, Ui.CYAN);
        }
        if (active) {
            progressBar.setIndeterminate(current.max <= 0);
            if (current.max > 0) progressBar.setProgress(Math.min(100, current.progress * 100 / current.max));
        }
    }

    private void openCompletedDownload() {
        DownloadStatus saved = DownloadStatus.read(this);
        if (saved == null || !"complete".equals(saved.state) || saved.documentUri.isEmpty()) return;
        clearCompletedWork(saved);
        importLastDownload();
        GalleryStore.Entry entry;
        try { entry = GalleryStore.find(this, saved.documentUri); }
        catch (IllegalStateException error) { Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); return; }
        if (entry == null) {
            Toast.makeText(this, "La descarga no está disponible en la galería.", Toast.LENGTH_LONG).show();
            return;
        }
        DownloadStatus.dismiss(this);
        tab = 2;
        loadGalleryEntry(entry, 0);
    }

    private boolean clearCompletedWork(DownloadStatus completed) {
        if (!"complete".equals(completed.state) || completed.documentUri.isEmpty()) return false;
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        if (completed.documentUri.equals(prefs.getString("last_cleared_download_uri", ""))) return false;
        String queuedId = prefs.getString("last_queued_work_id", "");
        String queuedLink = prefs.getString("last_queued_link", "");
        boolean matchingJob = !queuedId.isEmpty() && queuedId.equals(completed.workId);
        boolean clearedPreview = matchingJob && work != null && queuedId.equals(work.id);
        if (clearedPreview) {
            work = null;
            selectedPages = null;
            pageChecks.clear();
        }
        String currentLink = tab == 0 && link != null ? link.getText().toString() : linkValue;
        boolean clearedLink = matchingJob && !queuedLink.isEmpty() && queuedLink.equals(currentLink);
        SharedPreferences.Editor save = prefs.edit().putString("last_cleared_download_uri", completed.documentUri);
        if (clearedLink) {
            linkValue = "";
            if (link != null) link.setText("");
            save.remove("last_link");
        }
        save.apply();
        return clearedPreview && tab == 1;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            swipeStartX = event.getRawX();
            swipeStartY = event.getRawY();
            swiping = false;
            View area = galleryFullscreen ? fullscreenMedia : scroll;
            swipeEligible = area != null && (galleryFullscreen ? galleryItems != null && galleryItems.size() > 1
                    : galleryEntry == null) && containsTouch(area, event)
                    && !touchesGestureControl(area, event);
        } else if (action == MotionEvent.ACTION_MOVE && swipeEligible && !swiping) {
            float dx = event.getRawX() - swipeStartX;
            float dy = event.getRawY() - swipeStartY;
            int slop = ViewConfiguration.get(this).getScaledTouchSlop();
            if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) swipeEligible = false;
            else if (Math.abs(dx) > Math.max(Ui.dp(this, 56), slop * 3)
                    && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                int direction = dx < 0 ? 1 : -1;
                boolean canMove = galleryFullscreen ? galleryIndex + direction >= 0
                        && galleryIndex + direction < galleryItems.size()
                        : tab + direction >= 0 && tab + direction <= 3;
                if (canMove) {
                    swiping = true;
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                } else swipeEligible = false;
            }
        }
        if (swiping) {
            if (action == MotionEvent.ACTION_UP) {
                int direction = event.getRawX() < swipeStartX ? 1 : -1;
                if (galleryFullscreen) selectGalleryItem(galleryIndex + direction);
                else switchTab(tab + direction);
                swiping = false;
                swipeEligible = false;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                swiping = false;
                swipeEligible = false;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) swipeEligible = false;
        return super.dispatchTouchEvent(event);
    }

    private boolean containsTouch(View view, MotionEvent event) {
        Rect rect = new Rect();
        return view.isShown() && view.getGlobalVisibleRect(rect)
                && rect.contains((int) event.getRawX(), (int) event.getRawY());
    }

    private boolean touchesGestureControl(View view, MotionEvent event) {
        if (!containsTouch(view, event)) return false;
        if (view instanceof HorizontalScrollView || view instanceof EditText || view instanceof VideoView
                || !galleryFullscreen && view instanceof TextView && ((TextView) view).isTextSelectable()) return true;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--)
                if (touchesGestureControl(group.getChildAt(i), event)) return true;
        }
        return false;
    }

    private void switchTab(int target) {
        fadeNextTab = target != tab;
        if (link != null) linkValue = link.getText().toString();
        syncSelection();
        galleryEntry = null;
        galleryItems = null;
        galleryFullscreen = false;
        tab = target;
        render();
    }

    private void gallery() {
        if (galleryEntry != null) { galleryDetail(); return; }
        content.addView(Ui.text(this, "Tu galería", 26, Ui.WHITE, true));
        Ui.gap(content, this, 6);
        content.addView(Ui.text(this, "Tus obras descargadas de Pixiv, reunidas en un solo lugar.", 14, Ui.MUTED, false));
        Ui.gap(content, this, 20);
        List<GalleryStore.Entry> entries;
        try { entries = GalleryStore.list(this); }
        catch (IllegalStateException error) {
            content.addView(Ui.text(this, error.getMessage(), 14, Ui.MUTED, false));
            return;
        }
        if (entries.isEmpty()) {
            LinearLayout empty = Ui.card(this, 18);
            empty.addView(Ui.label(this, "Aún no hay descargas", R.drawable.tabler_layout_grid, 18, Ui.CYAN));
            Ui.gap(empty, this, 9);
            empty.addView(Ui.text(this, "Las próximas obras que descargues aparecerán aquí.", 14, Ui.MUTED, false));
            Ui.gap(empty, this, 12);
            empty.addView(Ui.button(this, "Buscar una obra", R.drawable.tabler_search, true, () -> switchTab(0)));
            content.addView(empty);
            return;
        }
        content.addView(Ui.text(this, entries.size() + (entries.size() == 1 ? " descarga" : " descargas"), 14, Ui.CYAN, true));
        Ui.gap(content, this, 12);
        for (int i = 0; i < entries.size(); i += 2) {
            LinearLayout row = Ui.row(this);
            row.setGravity(Gravity.TOP);
            for (int j = i; j < Math.min(i + 2, entries.size()); j++) {
                GalleryStore.Entry entry = entries.get(j);
                LinearLayout card = Ui.card(this, 8);
                View thumb = galleryImages.tile(entry.cover, entry.mime, 140);
                card.addView(thumb, new LinearLayout.LayoutParams(-1, Ui.dp(this, 140)));
                Ui.gap(card, this, 8);
                TextView name = Ui.text(this, entry.title, 14, Ui.WHITE, true);
                name.setMaxLines(2);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(name);
                Ui.gap(card, this, 4);
                card.addView(Ui.text(this, entry.date > 0 ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(entry.date)) : "Fecha no registrada", 11, Ui.MUTED, false));
                TextView count = Ui.text(this, entry.folder ? entry.count >= 0 ? entry.count + " elementos" : "Calculando elementos…"
                        : "1 archivo", 11, Ui.CYAN, true);
                card.addView(count);
                if (entry.folder && entry.count < 0) {
                    galleryIO.execute(() -> {
                        try {
                            List<GalleryDocuments.Item> files = GalleryDocuments.list(getContentResolver(), entry);
                            GalleryStore.updateMetadata(this, entry, files.size(), files.isEmpty() ? "" : files.get(0).uri.toString());
                            runOnUiThread(() -> {
                                if (tab == 2 && galleryEntry == null && count.isAttachedToWindow()) render();
                            });
                        } catch (Exception error) { runOnUiThread(() -> count.setText("Carpeta no disponible")); }
                    });
                }
                Ui.gap(card, this, 7);
                LinearLayout actions = Ui.row(this);
                actions.setGravity(Gravity.END);
                ImageView share = Ui.plainIconAction(this, R.drawable.tabler_share, () -> shareGalleryEntry(entry));
                share.setContentDescription("Compartir " + entry.title);
                actions.addView(share, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
                ImageView delete = Ui.plainIconAction(this, R.drawable.tabler_trash, Ui.DANGER,
                        () -> confirmDelete(entry));
                delete.setContentDescription("Eliminar " + entry.title);
                actions.addView(delete, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
                card.addView(actions);
                card.setOnClickListener(view -> loadGalleryEntry(entry, 0));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                if (j == i) params.rightMargin = Ui.dp(this, 8);
                row.addView(card, params);
            }
            content.addView(row);
            Ui.gap(content, this, 8);
        }
    }

    private void loadGalleryEntry(GalleryStore.Entry entry, int index) {
        if (galleryListRendered && !galleryScrollPending && scroll != null) galleryScrollY = scroll.getScrollY();
        galleryEntry = entry;
        galleryItems = null;
        galleryLoading = true;
        galleryError = null;
        galleryIndex = index;
        tab = 2;
        render();
        galleryIO.execute(() -> {
            try {
                List<GalleryDocuments.Item> files = GalleryDocuments.list(getContentResolver(), entry);
                GalleryStore.Entry updated = entry;
                if (entry.count != files.size() || (entry.folder && entry.cover.isEmpty() && !files.isEmpty())) {
                    updated = GalleryStore.updateMetadata(this, entry, files.size(), files.isEmpty() ? "" : files.get(0).uri.toString());
                }
                GalleryStore.Entry loaded = updated;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || galleryEntry == null || !galleryEntry.uri.equals(entry.uri)) return;
                    galleryEntry = loaded;
                    galleryItems = files;
                    galleryIndex = Math.min(galleryIndex, Math.max(0, files.size() - 1));
                    galleryLoading = false;
                    render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (galleryEntry == null || !galleryEntry.uri.equals(entry.uri)) return;
                    galleryLoading = false;
                    galleryError = "No se pudo acceder a la descarga: " + error.getMessage();
                    render();
                });
            }
        });
    }

    private void fullscreenGallery() {
        LinearLayout toolbar = Ui.row(this);
        toolbar.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        String title = galleryItems != null && !galleryItems.isEmpty()
                ? galleryItems.get(galleryIndex).name : galleryEntry.title;
        TextView heading = Ui.text(this, title, 16, Ui.WHITE, true);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1);
        headingParams.leftMargin = Ui.dp(this, 12);
        toolbar.addView(heading, headingParams);
        ImageView close = Ui.plainIconAction(this, R.drawable.tabler_x, () -> {
            galleryFullscreen = false;
            render();
        });
        close.setContentDescription("Salir de pantalla completa");
        toolbar.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        root.addView(toolbar);
        fullscreenMedia = null;
        if (galleryLoading || galleryItems == null || galleryItems.isEmpty() || galleryError != null) {
            TextView state = Ui.text(this, galleryError != null ? galleryError
                    : galleryLoading ? "Cargando archivos…" : "Esta carpeta está vacía.", 15, Ui.MUTED, false);
            state.setGravity(Gravity.CENTER);
            root.addView(state, new LinearLayout.LayoutParams(-1, 0, 1));
            return;
        }
        GalleryDocuments.Item item = galleryItems.get(galleryIndex);
        boolean text = (item.mime != null && item.mime.startsWith("text/"))
                || item.name.toLowerCase(Locale.ROOT).endsWith(".txt");
        LinearLayout viewer = Ui.column(this);
        if (text) {
            ScrollView reader = new ScrollView(this);
            reader.setFillViewport(true);
            reader.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
            reader.addView(viewer);
            root.addView(reader, new LinearLayout.LayoutParams(-1, 0, 1));
            fullscreenMedia = reader;
        } else {
            root.addView(viewer, new LinearLayout.LayoutParams(-1, 0, 1));
            fullscreenMedia = viewer;
        }
        galleryVideo = GalleryViewer.show(this, viewer, item, galleryIO, -1);
        if (galleryItems.size() > 1) {
            TextView counter = Ui.text(this, (galleryIndex + 1) + " / " + galleryItems.size(), 13, Ui.CYAN, true);
            counter.setGravity(Gravity.CENTER);
            root.addView(counter);
            HorizontalScrollView strip = new HorizontalScrollView(this);
            strip.setHorizontalScrollBarEnabled(false);
            strip.setFillViewport(false);
            LinearLayout thumbnails = Ui.row(this);
            thumbnails.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 12));
            strip.addView(thumbnails);
            View selectedTile = null;
            for (int i = 0; i < galleryItems.size(); i++) {
                int selected = i;
                GalleryDocuments.Item child = galleryItems.get(i);
                LinearLayout tile = Ui.column(this);
                tile.setPadding(Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3));
                tile.setBackground(Ui.outlined(this, i == galleryIndex ? Ui.BLUE : Ui.PANEL, 10));
                tile.addView(galleryImages.tile(child.uri.toString(), child.mime, 72),
                        new LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72)));
                tile.setContentDescription("Archivo " + (i + 1) + ": " + child.name);
                tile.setOnClickListener(v -> selectGalleryItem(selected));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
                params.rightMargin = Ui.dp(this, 8);
                thumbnails.addView(tile, params);
                if (i == galleryIndex) selectedTile = tile;
            }
            root.addView(strip);
            View centered = selectedTile;
            strip.post(() -> {
                if (galleryFullscreen && strip.isAttachedToWindow()) {
                    strip.scrollTo(Math.max(0, centered.getLeft() - (strip.getWidth() - centered.getWidth()) / 2), 0);
                    galleryImages.loadVisible();
                }
            });
            strip.setOnScrollChangeListener((view, x, y, oldX, oldY) -> galleryImages.loadVisible());
        }
    }

    private void galleryDetail() {
        GalleryStore.Entry entry = galleryEntry;
        TextView back = Ui.text(this, "Volver a la galería", 14, Ui.CYAN, true);
        Ui.withIcon(back, R.drawable.tabler_arrow_left, Ui.CYAN);
        back.setOnClickListener(v -> returnToGallery());
        content.addView(back);
        Ui.gap(content, this, 16);
        content.addView(Ui.text(this, entry.title, 23, Ui.WHITE, true));
        Ui.gap(content, this, 6);
        String detail = entry.date > 0 ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(entry.date)) : "Fecha no registrada";
        detail += "  ·  " + (entry.folder ? entry.count >= 0 ? entry.count + " elementos" : "Carpeta" : "1 archivo");
        content.addView(Ui.text(this, detail, 13, Ui.MUTED, false));
        Ui.gap(content, this, 16);
        if (galleryLoading) {
            content.addView(Ui.text(this, "Cargando archivos…", 14, Ui.CYAN, false));
        } else if (galleryError != null) {
            content.addView(Ui.text(this, galleryError, 14, Ui.MUTED, false));
        } else if (galleryItems == null || galleryItems.isEmpty()) {
            content.addView(Ui.text(this, "Esta carpeta está vacía.", 14, Ui.MUTED, false));
        } else {
            GalleryDocuments.Item item = galleryItems.get(galleryIndex);
            LinearLayout viewer = Ui.card(this, 12);
            LinearLayout viewerHeader = Ui.row(this);
            TextView filename = Ui.text(this, item.name, 14, Ui.WHITE, true);
            filename.setSingleLine(true);
            filename.setEllipsize(android.text.TextUtils.TruncateAt.END);
            viewerHeader.addView(filename, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView fullscreen = Ui.plainIconAction(this, R.drawable.tabler_maximize,
                    () -> { galleryFullscreen = true; render(); });
            fullscreen.setContentDescription("Ver en pantalla completa");
            LinearLayout.LayoutParams fullscreenParams = new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
            fullscreenParams.leftMargin = Ui.dp(this, 8);
            viewerHeader.addView(fullscreen, fullscreenParams);
            viewer.addView(viewerHeader);
            Ui.gap(viewer, this, 10);
            galleryVideo = GalleryViewer.show(this, viewer, item, galleryIO);
            content.addView(viewer);
            if (galleryItems.size() > 1) {
                Ui.gap(content, this, 12);
                LinearLayout controls = Ui.row(this);
                ImageView previous = Ui.iconAction(this, R.drawable.tabler_chevron_left, () -> selectGalleryItem(galleryIndex - 1));
                previous.setContentDescription("Archivo anterior");
                previous.setEnabled(galleryIndex > 0);
                previous.setAlpha(galleryIndex > 0 ? 1f : .35f);
                controls.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
                TextView position = Ui.text(this, (galleryIndex + 1) + " / " + galleryItems.size(), 14, Ui.CYAN, true);
                position.setGravity(Gravity.CENTER);
                controls.addView(position, new LinearLayout.LayoutParams(0, -2, 1));
                ImageView next = Ui.iconAction(this, R.drawable.tabler_chevron_right, () -> selectGalleryItem(galleryIndex + 1));
                next.setContentDescription("Archivo siguiente");
                next.setEnabled(galleryIndex + 1 < galleryItems.size());
                next.setAlpha(galleryIndex + 1 < galleryItems.size() ? 1f : .35f);
                controls.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
                content.addView(controls);
                Ui.gap(content, this, 16);
                content.addView(Ui.text(this, "Contenido de la carpeta", 17, Ui.WHITE, true));
                Ui.gap(content, this, 10);
                for (int i = 0; i < galleryItems.size(); i += 2) {
                    LinearLayout row = Ui.row(this);
                    row.setGravity(Gravity.TOP);
                    for (int j = i; j < Math.min(i + 2, galleryItems.size()); j++) {
                        int selected = j;
                        GalleryDocuments.Item child = galleryItems.get(j);
                        LinearLayout card = Ui.card(this, 7);
                        card.addView(galleryImages.tile(child.uri.toString(), child.mime, 110),
                                new LinearLayout.LayoutParams(-1, Ui.dp(this, 110)));
                        TextView name = Ui.text(this, child.name, 12, j == galleryIndex ? Ui.CYAN : Ui.MUTED, j == galleryIndex);
                        name.setSingleLine(true);
                        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        card.addView(name);
                        card.setOnClickListener(v -> selectGalleryItem(selected));
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                        if (j == i) params.rightMargin = Ui.dp(this, 8);
                        row.addView(card, params);
                    }
                    content.addView(row);
                    Ui.gap(content, this, 8);
                }
            }
        }
        Ui.gap(content, this, 18);
        content.addView(Ui.dangerButton(this, "Eliminar descarga", R.drawable.tabler_trash,
                () -> confirmDelete(entry)));
    }

    private void returnToGallery() {
        galleryFullscreen = false;
        galleryEntry = null;
        galleryItems = null;
        render();
    }

    private void selectGalleryItem(int index) {
        if (galleryItems == null || index < 0 || index >= galleryItems.size()) return;
        galleryIndex = index;
        render();
    }

    private void importLastDownload() {
        try { GalleryStore.importLast(this); }
        catch (IllegalStateException ignored) { /* The gallery displays the storage error instead of replacing history. */ }
    }

    private void shareGalleryEntry(GalleryStore.Entry entry) {
        galleryIO.execute(() -> {
            Intent send;
            try {
                List<GalleryDocuments.Item> items = GalleryDocuments.list(getContentResolver(), entry);
                send = GalleryShare.intent(items);
            } catch (Exception error) {
                String message = "No se pudo compartir la descarga: " + error.getMessage();
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                try {
                    Intent chooser = Intent.createChooser(send, "Compartir descarga");
                    chooser.setClipData(send.getClipData());
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(chooser);
                } catch (Exception error) {
                    Toast.makeText(this, "No hay ninguna aplicación disponible para compartir estos archivos.",
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void confirmDelete(GalleryStore.Entry entry) {
        AlertDialog dialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Eliminar descarga")
                .setMessage("Se eliminará «" + entry.title + "» y sus archivos del almacenamiento. Esta acción no se puede deshacer.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (confirmation, which) -> galleryIO.execute(() -> {
                    String failure = null;
                    try {
                        if (!DocumentsContract.deleteDocument(getContentResolver(), Uri.parse(entry.uri)))
                            throw new Exception("Android no permitió eliminar el archivo o la carpeta.");
                        DownloadStatus.forget(this, entry.uri);
                        GalleryStore.remove(this, entry.uri);
                    } catch (Exception error) { failure = "No se pudo eliminar: " + error.getMessage(); }
                    String result = failure;
                    runOnUiThread(() -> {
                        if (result == null) {
                            if (galleryEntry != null && entry.uri.equals(galleryEntry.uri)) {
                                galleryEntry = null;
                                galleryItems = null;
                                galleryFullscreen = false;
                            }
                            if (tab == 2) render();
                            refreshProgress();
                            Toast.makeText(this, "Descarga eliminada", Toast.LENGTH_SHORT).show();
                        } else Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                    });
                })).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Ui.DANGER));
        dialog.show();
    }

    private void home() {
        content.addView(Ui.text(this, "SOLO PIXIV", 12, Ui.CYAN, true));
        Ui.gap(content, this, 9);
        content.addView(Ui.text(this, "Tus obras favoritas,\na tu manera.", 29, Ui.WHITE, true));
        Ui.gap(content, this, 8);
        content.addView(Ui.text(this, "Guarda ilustraciones, manga, novelas y ugoira de Pixiv.", 15, Ui.MUTED, false));
        Ui.gap(content, this, 22);
        LinearLayout chips = Ui.row(this);
        for (String chip : new String[]{"Ilustraciones", "Manga", "Ugoira", "Novelas"}) {
            TextView badge = Ui.text(this, chip, 11, Ui.CYAN, true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(Ui.dp(this, 7), Ui.dp(this, 9), Ui.dp(this, 7), Ui.dp(this, 9));
            badge.setBackground(Ui.background(this, Ui.PANEL, 28));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.rightMargin = Ui.dp(this, 4);
            chips.addView(badge, params);
        }
        content.addView(chips);
        Ui.gap(content, this, 24);
        LinearLayout hub = Ui.card(this, 16);
        LinearLayout field = Ui.row(this);
        field.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 7), 0);
        field.setBackground(Ui.background(this, Ui.INPUT, 14));
        field.addView(Ui.icon(this, R.drawable.tabler_link, Ui.CYAN),
                new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        link = new EditText(this);
        link.setSingleLine(true);
        link.setTextColor(Ui.WHITE);
        link.setHintTextColor(Ui.MUTED);
        link.setTextSize(13);
        link.setHint("Pega la URL de pixiv aquí");
        link.setBackgroundColor(Color.TRANSPARENT);
        link.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        link.setTypeface(Ui.typeface(this, false));
        link.setText(linkValue);
        LinearLayout.LayoutParams edit = new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1);
        edit.leftMargin = Ui.dp(this, 8);
        field.addView(link, edit);
        ImageView clear = Ui.icon(this, R.drawable.tabler_x, Ui.MUTED);
        clear.setContentDescription("Limpiar URL");
        clear.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        clear.setOnClickListener(view -> {
            link.setText("");
            linkValue = "";
            getPreferences(MODE_PRIVATE).edit().remove("last_link").apply();
        });
        clear.setVisibility(linkValue.isEmpty() ? View.GONE : View.VISIBLE);
        link.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                clear.setVisibility(text.length() == 0 ? View.GONE : View.VISIBLE);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        field.addView(clear, new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 44)));
        ImageView paste = Ui.icon(this, R.drawable.tabler_clipboard, Ui.CYAN);
        paste.setContentDescription("Pegar URL del portapapeles");
        paste.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        paste.setPadding(Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9));
        paste.setBackground(Ui.background(this, Ui.RAISED, 30));
        paste.setOnClickListener(view -> paste());
        field.addView(paste, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
        hub.addView(field);
        Ui.gap(hub, this, 12);
        TextView open = Ui.button(this, loading ? "Consultando Pixiv…" : "Previsualizar obra",
                R.drawable.tabler_photo_edit, true, this::load);
        open.setEnabled(!loading);
        hub.addView(open);
        Ui.gap(hub, this, 12);
        status = Ui.text(this, message, 13, Ui.MUTED, false);
        hub.addView(status);
        content.addView(hub);
        Ui.gap(content, this, 22);
        content.addView(Ui.text(this, "Hecho para tus obras", 19, Ui.WHITE, true));
        Ui.gap(content, this, 12);
        feature(R.drawable.tabler_photo, "Imágenes originales", "Selecciona las páginas que quieras descargar.");
        Ui.gap(content, this, 10);
        feature(R.drawable.tabler_movie, "GIF o MP4", "Convierte las ugoira al formato que prefieras.");
        Ui.gap(content, this, 10);
        feature(R.drawable.tabler_file_text, "Novelas en TXT", "Guarda el texto proporcionado por Pixiv.");
        Ui.gap(content, this, 20);
        content.addView(Ui.text(this, "Las descargas continúan en segundo plano. Consulta la notificación para ver su progreso o cancelarlas.", 13, Ui.MUTED, false));
    }

    private void feature(int icon, String title, String detail) {
        LinearLayout card = Ui.card(this, 14);
        LinearLayout row = Ui.row(this);
        ImageView glyph = Ui.icon(this, icon, Ui.CYAN);
        row.addView(glyph, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
        LinearLayout description = Ui.column(this);
        description.addView(Ui.text(this, title, 15, Ui.WHITE, true));
        description.addView(Ui.text(this, detail, 12, Ui.MUTED, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.leftMargin = Ui.dp(this, 10);
        row.addView(description, params);
        card.addView(row);
        content.addView(card);
    }

    private void paste() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        ClipData data = clipboard == null ? null : clipboard.getPrimaryClip();
        if (data == null || data.getItemCount() == 0) {
            status.setText("El portapapeles está vacío.");
            return;
        }
        CharSequence value = data.getItemAt(0).coerceToText(this);
        String url = PixivLink.extract(value == null ? null : value.toString());
        if (url == null) {
            status.setText("El portapapeles no contiene un enlace de Pixiv.");
            return;
        }
        link.setText(url);
        link.setSelection(link.length());
    }

    private void load() {
        if (loading) return;
        if (link != null) linkValue = link.getText().toString().trim();
        String extracted = PixivLink.extract(linkValue);
        if (extracted != null && !extracted.equals(linkValue)) {
            linkValue = extracted;
            if (link != null) link.setText(extracted);
        }
        if (linkValue.isEmpty()) {
            message = "Introduce un enlace de obra de Pixiv.";
            if (status != null) status.setText(message);
            return;
        }
        loading = true;
        work = null;
        mp4 = defaultMp4;
        selectedPages = null;
        message = "Consultando Pixiv…";
        getPreferences(MODE_PRIVATE).edit().putString("last_link", linkValue).apply();
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (keyboard != null && link != null) keyboard.hideSoftInputFromWindow(link.getWindowToken(), 0);
        render();
        String input = linkValue;
        executor.execute(() -> {
            try {
                PixivClient.Work found = PixivClient.load(input);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    loading = false;
                    work = found;
                    message = "Obra lista para previsualizar.";
                    tab = 1;
                    render();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    loading = false;
                    message = "No se pudo abrir la obra: " + e.getMessage();
                    tab = 0;
                    render();
                });
            }
        });
    }

    private void preview() {
        pageChecks.clear();
        if (work == null) {
            content.addView(Ui.text(this, "Vista previa", 26, Ui.WHITE, true));
            Ui.gap(content, this, 18);
            LinearLayout empty = Ui.card(this, 20);
            empty.addView(Ui.label(this, "No hay una obra cargada", R.drawable.tabler_photo_off, 18, Ui.CYAN));
            Ui.gap(empty, this, 9);
            empty.addView(Ui.text(this, "Introduce un enlace de Pixiv para ver sus detalles y elegir qué descargar.", 14, Ui.MUTED, false));
            Ui.gap(empty, this, 16);
            empty.addView(Ui.button(this, "Ir a descargar", R.drawable.tabler_download,
                    true, () -> switchTab(0)));
            content.addView(empty);
            return;
        }
        content.addView(Ui.label(this, "OBRA ENCONTRADA", R.drawable.tabler_circle_check, 12, Ui.GREEN));
        Ui.gap(content, this, 12);
        LinearLayout summary = Ui.card(this, 16);
        summary.addView(Ui.text(this, kind() + "  ·  Pixiv #" + work.id, 12, Ui.CYAN, true));
        Ui.gap(summary, this, 10);
        summary.addView(Ui.text(this, work.title, 22, Ui.WHITE, true));
        Ui.gap(summary, this, 4);
        summary.addView(Ui.text(this, "Por " + work.author, 14, Ui.MUTED, false));
        if (!work.thumbnail.isEmpty()) {
            Ui.gap(summary, this, 13);
            View cover = images.view(work.thumbnail, 190);
            summary.addView(cover, new LinearLayout.LayoutParams(-1, Ui.dp(this, 190)));
        }
        content.addView(summary);
        Ui.gap(content, this, 20);
        if ("image".equals(work.type)) imagePages();
        else if ("ugoira".equals(work.type)) animation();
        else novel();
        Ui.gap(content, this, 20);
        content.addView(Ui.text(this, "La carpeta y el nombre final se determinarán al guardar. Algunas obras pueden requerir una sesión autorizada en Pixiv.", 12, Ui.MUTED, false));
        downloadBar();
    }

    private String kind() {
        if ("novel".equals(work.type)) return "Novela";
        if ("ugoira".equals(work.type)) return "Ugoira";
        return work.pages.size() > 1 ? "Ilustración / manga" : "Ilustración";
    }

    private void imagePages() {
        int count = work.pages.size();
        LinearLayout label = Ui.row(this);
        TextView heading = Ui.text(this, "Páginas (" + count + ")", 18, Ui.WHITE, true);
        label.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView all = Ui.text(this, "Deseleccionar todas", 12, Ui.CYAN, true);
        all.setOnClickListener(v -> {
            boolean everything = true;
            for (CheckBox check : pageChecks) everything &= check.isChecked();
            for (CheckBox check : pageChecks) check.setChecked(!everything);
            all.setText(everything ? "Seleccionar todas" : "Deseleccionar todas");
        });
        label.addView(all);
        content.addView(label);
        Ui.gap(content, this, 12);
        for (int index = 0; index < count; index += 2) {
            LinearLayout row = Ui.row(this);
            for (int j = index; j < Math.min(index + 2, count); j++) {
                LinearLayout card = Ui.card(this, 8);
                String url = j < work.pageThumbnails.size() ? work.pageThumbnails.get(j) : "";
                View thumb = images.view(url, 152);
                card.addView(thumb, new LinearLayout.LayoutParams(-1, Ui.dp(this, 152)));
                CheckBox check = new CheckBox(this);
                check.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.CYAN));
                check.setText("Página " + (j + 1));
                check.setTextSize(13);
                check.setTextColor(Ui.WHITE);
                check.setTypeface(Ui.typeface(this, false));
                check.setChecked(selectedPages == null || j >= selectedPages.length || selectedPages[j]);
                check.setOnCheckedChangeListener((v, checked) -> {
                    syncSelection();
                    updateSelection();
                });
                card.setOnClickListener(v -> check.setChecked(!check.isChecked()));
                pageChecks.add(check);
                card.addView(check);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                if (j == index) params.rightMargin = Ui.dp(this, 8);
                row.addView(card, params);
            }
            content.addView(row);
            Ui.gap(content, this, 8);
        }
    }

    private void animation() {
        content.addView(Ui.text(this, "Formato de animación", 18, Ui.WHITE, true));
        Ui.gap(content, this, 10);
        LinearLayout formats = Ui.row(this);
        for (int i = 0; i < 2; i++) {
            final boolean chooseMp4 = i == 1;
            TextView option = Ui.button(this, chooseMp4 ? "MP4" : "GIF", chooseMp4 == mp4, () -> {
                mp4 = chooseMp4;
                render();
            });
            if (chooseMp4 == mp4) Ui.withIcon(option, R.drawable.tabler_check, Ui.INPUT);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (i == 0) params.rightMargin = Ui.dp(this, 8);
            formats.addView(option, params);
        }
        content.addView(formats);
        Ui.gap(content, this, 10);
        content.addView(Ui.text(this, "MP4 no incluye sonido. La conversión puede reducir el tamaño o la fidelidad de la animación.", 13, Ui.MUTED, false));
        if (!mp4) {
            Ui.gap(content, this, 14);
            content.addView(Ui.text(this, "Calidad del GIF", 16, Ui.WHITE, true));
            Ui.gap(content, this, 8);
            LinearLayout presets = Ui.row(this);
            for (GifPreset choice : GifPreset.values()) {
                String title = choice == GifPreset.FAST ? "Rápido" : choice == GifPreset.BALANCED ? "Equilibrado" : "Calidad";
                TextView button = Ui.button(this, title, choice == gifPreset, () -> {
                    gifPreset = choice;
                    getPreferences(MODE_PRIVATE).edit().putString("gif_preset", choice.getId()).apply();
                    render();
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                params.rightMargin = Ui.dp(this, 6);
                presets.addView(button, params);
            }
            content.addView(presets);
            Ui.gap(content, this, 6);
            content.addView(Ui.text(this, "Hasta " + gifPreset.getMaxWidth() + " px · " + gifPreset.getFpsMax()
                    + " fps · " + (gifPreset.getDiffuse() ? "difusión de error" : "sin dithering"), 12, Ui.MUTED, false));
        }
    }

    private void novel() {
        content.addView(Ui.text(this, "Texto de la novela", 18, Ui.WHITE, true));
        Ui.gap(content, this, 10);
        LinearLayout card = Ui.card(this, 16);
        String excerpt = work.text.length() > 600 ? work.text.substring(0, 600) + "…" : work.text;
        card.addView(Ui.text(this, excerpt, 14, Ui.WHITE, false));
        content.addView(card);
        Ui.gap(content, this, 10);
        content.addView(Ui.text(this, "Formato: TXT · texto proporcionado por Pixiv", 12, Ui.CYAN, false));
    }

    private void downloadBar() {
        LinearLayout bar = Ui.card(this, 12);
        selectionCount = Ui.text(this, "", 14, Ui.WHITE, true);
        bar.addView(selectionCount);
        Ui.gap(bar, this, 9);
        selectionDownloadButton = Ui.button(this, "Descargar selección", R.drawable.tabler_download, true, this::download);
        bar.addView(selectionDownloadButton);
        updateDownloadButton();
        bottom.addView(bar);
        Ui.gap(bottom, this, 8);
        updateSelection();
    }

    private void updateDownloadButton() {
        if (selectionDownloadButton == null) return;
        boolean active = DownloadStatus.isActive(this);
        if (selectionDownloadButton.isEnabled() == !active) return;
        selectionDownloadButton.setEnabled(!active);
        selectionDownloadButton.setAlpha(active ? .55f : 1f);
        if (active) {
            selectionDownloadButton.setText("Descargando...");
            selectionDownloadButton.setContentDescription("Descargando...");
        } else {
            selectionDownloadButton.setText("Descargar selección");
            Ui.withIcon(selectionDownloadButton, R.drawable.tabler_download, Ui.INPUT);
        }
    }

    private void syncSelection() {
        if (pageChecks.isEmpty()) return;
        selectedPages = new boolean[pageChecks.size()];
        for (int i = 0; i < pageChecks.size(); i++) selectedPages[i] = pageChecks.get(i).isChecked();
    }

    private void updateSelection() {
        if (selectionCount == null || work == null) return;
        if (!"image".equals(work.type)) {
            selectionCount.setText("novel".equals(work.type) ? "1 novela · TXT" : "1 animación · " + (mp4 ? "MP4" : "GIF"));
            return;
        }
        int count = 0;
        for (CheckBox check : pageChecks) if (check.isChecked()) count++;
        selectionCount.setText(count + " de " + work.pages.size() + " páginas seleccionadas");
    }

    private void settings() {
        content.addView(Ui.text(this, "Ajustes y preferencias", 25, Ui.WHITE, true));
        Ui.gap(content, this, 6);
        content.addView(Ui.text(this, "Personaliza cómo se guardan tus obras de Pixiv.", 14, Ui.MUTED, false));
        Ui.gap(content, this, 22);
        content.addView(Ui.label(this, "Formato de animación", R.drawable.tabler_movie, 17, Ui.CYAN));
        Ui.gap(content, this, 10);
        LinearLayout formats = Ui.card(this, 16);
        formats.addView(Ui.text(this, "Formato predeterminado de ugoira", 15, Ui.WHITE, true));
        Ui.gap(formats, this, 12);
        LinearLayout choices = Ui.row(this);
        for (int i = 0; i < 2; i++) {
            boolean choice = i == 1;
            TextView option = Ui.button(this, choice ? "MP4" : "GIF", defaultMp4 == choice, () -> {
                defaultMp4 = choice;
                mp4 = choice;
                getPreferences(MODE_PRIVATE).edit().putString("animation_format", mp4 ? "mp4" : "gif").apply();
                render();
            });
            if (defaultMp4 == choice) Ui.withIcon(option, R.drawable.tabler_check, Ui.INPUT);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (i == 0) params.rightMargin = Ui.dp(this, 8);
            choices.addView(option, params);
        }
        formats.addView(choices);
        Ui.gap(formats, this, 9);
        formats.addView(Ui.text(this, "También podrás cambiarlo al previsualizar cada obra.", 12, Ui.MUTED, false));
        content.addView(formats);
        Ui.gap(content, this, 24);
        content.addView(Ui.label(this, "Ubicación de descargas", R.drawable.tabler_folder, 17, Ui.CYAN));
        Ui.gap(content, this, 10);
        LinearLayout storage = Ui.card(this, 16);
        storage.addView(Ui.text(this, "Carpeta principal", 15, Ui.WHITE, true));
        Ui.gap(storage, this, 7);
        String folder = getPreferences(MODE_PRIVATE).getString("folder", "");
        storage.addView(Ui.text(this, folder.isEmpty() ? "Aún no se ha elegido una carpeta" : "Carpeta seleccionada mediante Android", 13, Ui.MUTED, false));
        Ui.gap(storage, this, 12);
        storage.addView(Ui.button(this, "Cambiar carpeta", R.drawable.tabler_folder, false, () -> pickFolder(false)));
        Ui.gap(storage, this, 14);
        CheckBox ask = new CheckBox(this);
        ask.setText("Preguntar dónde guardar cada descarga");
        ask.setTextColor(Ui.WHITE);
        ask.setTextSize(14);
        ask.setTypeface(Ui.typeface(this, false));
        ask.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.CYAN));
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        ask.setChecked(prefs.getBoolean("ask", true));
        ask.setOnCheckedChangeListener((v, value) -> prefs.edit().putBoolean("ask", value).apply());
        storage.addView(ask);
        content.addView(storage);
        Ui.gap(content, this, 24);
        content.addView(Ui.label(this, "Cuenta de Pixiv", R.drawable.tabler_user_circle, 17, Ui.CYAN));
        Ui.gap(content, this, 10);
        LinearLayout account = Ui.card(this, 16);
        account.addView(Ui.text(this, loggedIn ? "Sesión de Pixiv activa" : "Sin sesión confirmada", 16, loggedIn ? Ui.GREEN : Ui.WHITE, true));
        Ui.gap(account, this, 7);
        account.addView(Ui.text(this, "Puedes consultar obras públicas sin iniciar sesión. El acceso a otras obras depende de los permisos de tu cuenta en Pixiv.", 13, Ui.MUTED, false));
        Ui.gap(account, this, 12);
        account.addView(Ui.button(this, loggedIn ? "Abrir mi sesión de Pixiv" : "Iniciar sesión en Pixiv",
                R.drawable.tabler_user_circle, false,
                () -> startActivityForResult(new Intent(this, LoginActivity.class), PIXIV_LOGIN)));
        if (loggedIn) {
            Ui.gap(account, this, 10);
            account.addView(Ui.button(this, "Cerrar sesión de Pixiv", R.drawable.tabler_logout,
                    false, this::confirmLogout));
        }
        content.addView(account);
        Ui.gap(content, this, 24);
        content.addView(Ui.label(this, "Acerca de Pixload", R.drawable.tabler_info_circle, 17, Ui.CYAN));
        Ui.gap(content, this, 10);
        LinearLayout about = Ui.card(this, 16);
        String version = "0.1.0";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException ignored) { }
        about.addView(Ui.text(this, "Versión " + version, 14, Ui.WHITE, true));
        Ui.gap(about, this, 7);
        about.addView(Ui.text(this, "Pixload es una aplicación de desarrollo independiente; no es un servicio oficial de Pixiv.", 12, Ui.MUTED, false));
        content.addView(about);
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Cerrar sesión de Pixiv")
                .setMessage("Se borrarán las cookies de Pixiv guardadas en Pixload. No se cerrará la sesión del navegador del teléfono ni se borrarán los archivos descargados.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar sesión", (dialog, which) -> {
                    sessionGeneration++;
                    CookieManager.getInstance().removeAllCookies(removed -> {
                        CookieManager.getInstance().flush();
                        loggedIn = false;
                        work = null;
                        selectedPages = null;
                        message = "Sesión cerrada. Puedes entrar con otra cuenta de Pixiv.";
                        render();
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    });
                }).show();
    }

    private void download() {
        if (work == null || DownloadStatus.isActive(this)) return;
        if ("image".equals(work.type)) {
            boolean chosen = false;
            for (CheckBox check : pageChecks) chosen |= check.isChecked();
            if (!chosen) { Toast.makeText(this, "Selecciona al menos una página.", Toast.LENGTH_SHORT).show(); return; }
        }
        if (getPreferences(MODE_PRIVATE).getBoolean("ask", true) || !getPreferences(MODE_PRIVATE).contains("folder")) pickFolder(true);
        else queue(Uri.parse(getPreferences(MODE_PRIVATE).getString("folder", "")));
    }

    private void pickFolder(boolean startDownload) {
        downloadAfterFolderPick = startDownload;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PIXIV_LOGIN) { refreshSession(true); return; }
        if (request != PICK_FOLDER) return;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            downloadAfterFolderPick = false;
            return;
        }
        Uri folder = data.getData();
        getContentResolver().takePersistableUriPermission(folder,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (downloadAfterFolderPick && work != null) queue(folder);
        else {
            getPreferences(MODE_PRIVATE).edit().putString("folder", folder.toString()).apply();
            Toast.makeText(this, "Carpeta principal seleccionada", Toast.LENGTH_SHORT).show(); render();
        }
        downloadAfterFolderPick = false;
    }

    private void queue(Uri folder) {
        if (work == null || DownloadStatus.isActive(this)) return;
        boolean duplicate;
        try { duplicate = GalleryStore.hasCompleted(this, work.id, folder.toString()); }
        catch (IllegalStateException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (duplicate) {
            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("Obra ya descargada")
                    .setMessage("Esta obra ya está descargada en la carpeta elegida. ¿Quieres descargarla otra vez?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Continuar", (dialog, which) -> queueConfirmed(folder, true))
                    .show();
            return;
        }
        queueConfirmed(folder, false);
    }

    private void queueConfirmed(Uri folder, boolean duplicate) {
        if (work == null || DownloadStatus.isActive(this)) return;
        boolean statusWritten = false;
        try {
            JSONObject job = new JSONObject();
            job.put("title", work.title);
            job.put("id", work.id);
            job.put("type", work.type);
            job.put("folder", folder.toString());
            job.put("grouped", "image".equals(work.type) && work.pages.size() >= 2);
            job.put("duplicate", duplicate);
            JSONArray files = new JSONArray();
            if ("image".equals(work.type)) {
                for (int i = 0; i < pageChecks.size(); i++) {
                    if (pageChecks.get(i).isChecked()) {
                        String url = work.pages.get(i);
                        files.put(new JSONObject().put("url", url).put("name", String.format(Locale.ROOT,
                                "%03d.%s", i + 1, PixivClient.extension(url))));
                    }
                }
            } else if ("ugoira".equals(work.type)) {
                files.put(new JSONObject().put("url", work.animationUrl)
                        .put("name", mp4 ? "animacion.mp4" : "animacion.gif")
                        .put("frames", work.frames).put("format", mp4 ? "mp4" : "gif")
                        .put("preset", gifPreset.getId()));
            } else {
                files.put(new JSONObject().put("name", "novela.txt")
                        .put("text", work.title + "\n" + work.author + "\n\n" + work.text));
            }
            job.put("files", files);
            DownloadStatus.write(this, "Preparando descarga…", "active", 0, 0);
            statusWritten = true;
            updateDownloadButton();
            getPreferences(MODE_PRIVATE).edit().putString("folder", folder.toString())
                    .putString("last_queued_work_id", work.id)
                    .putString("last_queued_link", linkValue).apply();
            Intent intent = new Intent(this, DownloadService.class);
            intent.putExtra("job", job.toString());
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            message = "Descarga iniciada. Consulta la notificación para ver el avance.";
            refreshProgress();
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            if (statusWritten) {
                DownloadStatus.write(this, "No se pudo iniciar la descarga", "error", 0, 0);
                refreshProgress();
            }
            Toast.makeText(this, "No se pudo iniciar la descarga: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        importLastDownload();
        progressHandler.removeCallbacks(progressUpdate);
        progressHandler.post(progressUpdate);
        if (sessionBadge != null) refreshSession(false);
    }

    @Override protected void onPause() {
        progressHandler.removeCallbacks(progressUpdate);
        if (galleryVideo != null) galleryVideo.pause();
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (galleryFullscreen) {
            galleryFullscreen = false;
            render();
        } else if (tab == 2 && galleryEntry != null) {
            returnToGallery();
        } else super.onBackPressed();
    }

    private void refreshSession(boolean reloadPreview) {
        int generation = sessionGeneration;
        executor.execute(() -> {
            boolean active = PixivClient.isLoggedIn();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != sessionGeneration) return;
                loggedIn = active;
                if (sessionBadge != null) {
                    sessionBadge.setText(active ? "Pixiv activo" : "Sin sesión");
                    sessionBadge.setTextColor(active ? Ui.GREEN : Ui.MUTED);
                    Ui.withIcon(sessionBadge, active ? R.drawable.tabler_circle_check : R.drawable.tabler_circle,
                            active ? Ui.GREEN : Ui.MUTED);
                }
                if (tab == 3) render();
                if (reloadPreview && active && !linkValue.trim().isEmpty()) load();
                else if (reloadPreview && !active) {
                    message = "No se ha confirmado la sesión. Puedes seguir con contenido público.";
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        galleryIO.shutdownNow();
        images.shutdown();
        galleryImages.shutdown();
        super.onDestroy();
    }
}
