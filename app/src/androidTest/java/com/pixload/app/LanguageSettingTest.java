package com.pixload.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(AndroidJUnit4.class)
public class LanguageSettingTest {
    private static Context isolatedContext() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String prefix = "language_test_" + System.nanoTime() + "_";
        return new ContextWrapper(base) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(prefix + name, mode);
            }
            @Override public boolean deleteSharedPreferences(String name) {
                return super.deleteSharedPreferences(prefix + name);
            }
        };
    }

    @Test public void selectionPersistsAndChangesResourcesInBothDirections() {
        Context context = isolatedContext();
        try {
            AppLanguage.select(context, "en");
            assertEquals("en", AppLanguage.current(context));
            assertEquals("Settings", AppLanguage.wrap(context).getString(R.string.nav_settings));
            assertEquals("Download", AppLanguage.wrap(context).getString(R.string.nav_download));
            assertEquals("Already downloaded", AppLanguage.wrap(context).getString(R.string.duplicate_title));
            assertEquals("Your gallery", Ui.text(context, "Tu galería", 16, Ui.WHITE, false).getText().toString());
            assertEquals("Novela", Ui.rawText(context, "Novela", 16, Ui.WHITE, false).getText().toString());
            assertEquals("Sign in to Pixiv. When you finish, you’ll return to Pixload.",
                    AppLanguage.wrap(context).getString(R.string.login_hint));
            AppLanguage.select(context, "es");
            assertEquals("es", AppLanguage.current(context));
            assertEquals("Ajustes", AppLanguage.wrap(context).getString(R.string.nav_settings));
            assertEquals("Obra ya descargada", AppLanguage.wrap(context).getString(R.string.duplicate_title));
            assertEquals("Tu galería", Ui.text(context, "Tu galería", 16, Ui.WHITE, false).getText().toString());
        } finally {
            context.deleteSharedPreferences("app_language");
        }
    }

    @Test public void thumbnailAccessibilityLabelsSwitchWithTheAppLanguage() {
        Context context = isolatedContext();
        try {
            AppLanguage.select(context, "en");
            assertEquals("Pixiv work thumbnail", AppLanguage.label(context, "Miniatura de la obra de Pixiv"));
            assertEquals("Saved file thumbnail", AppLanguage.label(context, "Miniatura de archivo guardado"));
            AppLanguage.select(context, "es");
            assertEquals("Miniatura de la obra de Pixiv", AppLanguage.label(context, "Miniatura de la obra de Pixiv"));
        } finally {
            context.deleteSharedPreferences("app_language");
        }
    }

    @Test public void invalidSelectionDoesNotChangePersistedLanguage() {
        Context context = isolatedContext();
        try {
            assertEquals("es", AppLanguage.current(context));
            AppLanguage.select(context, "en");
            assertEquals("en", context.getSharedPreferences("app_language", Context.MODE_PRIVATE).getString("language", null));
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> AppLanguage.select(context, "fr"));
            assertEquals("Unsupported language", failure.getMessage());
            assertEquals("en", AppLanguage.current(context));
            assertEquals("en", context.getSharedPreferences("app_language", Context.MODE_PRIVATE).getString("language", null));
            assertThrows(NullPointerException.class, () -> AppLanguage.select(context, null));
            assertEquals("en", context.getSharedPreferences("app_language", Context.MODE_PRIVATE).getString("language", null));
        } finally {
            context.deleteSharedPreferences("app_language");
        }
    }

    @Test public void stageAndErrorKeepPrefixesAndUnknownLabels() {
        Context context = isolatedContext();
        try {
            AppLanguage.select(context, "en");
            assertEquals("Error: Pixiv responded with HTTP 403", AppLanguage.stage(context, "Error: Pixiv respondió con HTTP 403"));
            assertEquals("Downloading animation · File 2", AppLanguage.stage(context, "Descargando animación · Archivo 2"));
            assertEquals("Could not create Mi obra", AppLanguage.error(context, "No se pudo crear Mi obra"));
            assertEquals("Unknown label", AppLanguage.label(context, "Unknown label"));
            assertEquals(null, AppLanguage.error(context, null));
            AppLanguage.select(context, "es");
            assertEquals("Pixiv respondió con HTTP 403", AppLanguage.error(context, "Pixiv respondió con HTTP 403"));
        } finally {
            context.deleteSharedPreferences("app_language");
        }
    }

    @Test public void completedStatusKeepsOriginalWorkTitleAndStorageContract() {
        Context context = isolatedContext();
        try {
            AppLanguage.select(context, "en");
            assertEquals("Download completed: Mi obra", AppLanguage.stage(context, "Descarga completada: Mi obra"));
            assertEquals("Mi obra", AppLanguage.stage(context, "Mi obra"));
            AppLanguage.select(context, "es");
            assertEquals("Descarga completada: Mi obra", AppLanguage.stage(context, "Descarga completada: Mi obra"));
        } finally {
            context.deleteSharedPreferences("app_language");
        }
    }
}
