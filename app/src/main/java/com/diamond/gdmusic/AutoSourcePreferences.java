package com.diamond.gdmusic;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/** Shared by phone and playback service. Cache cleanup deliberately leaves this preference intact. */
public final class AutoSourcePreferences {
    private static SharedPreferences preferences;
    private static final List<String> DEFAULTS = Arrays.asList("netease", "joox", "bilibili");
    private static final List<String> ALLOWED = Arrays.asList(
            "netease", "joox", "bilibili", "tencent", "kuwo", "tidal", "qobuz", "ytmusic", "spotify");
    private AutoSourcePreferences() { }
    public static synchronized void initialize(Context context) {
        if (preferences == null) preferences = context.getApplicationContext()
                .getSharedPreferences("auto_sources", Context.MODE_PRIVATE);
    }
    public static synchronized List<String> selected() {
        if (preferences == null || !preferences.contains("selected")) return new ArrayList<>(DEFAULTS);
        return sanitize(Arrays.asList(preferences.getString("selected", "").split(",")));
    }
    public static synchronized void save(List<String> sources) {
        if (preferences == null) throw new IllegalStateException("Auto sources not initialized");
        preferences.edit().putString("selected", android.text.TextUtils.join(",", sanitize(sources))).apply();
    }
    static List<String> sanitize(List<String> sources) {
        List<String> result = new ArrayList<>();
        for (String source : sources) if (ALLOWED.contains(source) && !result.contains(source)) result.add(source);
        return result;
    }
}
