package com.example.smartsight;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

public final class LocaleManager {

    private LocaleManager() {}

    public static Context wrap(Context c) {
        String lang = SettingsPrefs.getLanguage(c);
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Resources res = c.getResources();
        Configuration cfg = new Configuration(res.getConfiguration());
        cfg.setLocale(locale);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            cfg.setLayoutDirection(locale);
        }

        return c.createConfigurationContext(cfg);
    }

    public static Locale getCurrentLocale(Context c) {
        return new Locale(SettingsPrefs.getLanguage(c));
    }

    public static String getSttLanguageTag(Context c) {
        String lang = SettingsPrefs.getLanguage(c);
        switch (lang) {
            case "fr": return "fr-FR";
            case "en":
            default:   return "en-US";
        }
    }
}