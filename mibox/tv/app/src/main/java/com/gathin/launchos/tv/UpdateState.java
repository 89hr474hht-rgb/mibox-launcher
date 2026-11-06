package com.gathin.launchos.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** What the updater remembers between runs: last check, the offered release, choices and history. */
final class UpdateState {
    private static final String PREFS = "updates";
    private static final String LAST_CHECK = "last_check";
    private static final String LAST_ERROR = "last_error";
    private static final String AVAILABLE = "available";
    private static final String IGNORED = "ignored_version";
    private static final String SNOOZED = "snoozed_version";
    private static final String SNOOZED_UNTIL = "snoozed_until";
    private static final String PENDING = "pending_install";
    private static final String HISTORY = "history";
    private static final int HISTORY_MAX = 30;

    /** One line of the history: what happened to which version, and when. */
    static final class Entry {
        final long time;
        final String version;
        final String result;
        final String detail;

        Entry(long time, String version, String result, String detail) {
            this.time = time;
            this.version = version;
            this.result = result;
            this.detail = detail;
        }
    }

    static final String R_INSTALLED = "installed";
    static final String R_CANCELLED = "cancelled";
    static final String R_FAILED = "failed";
    static final String R_IGNORED = "ignored";

    private final SharedPreferences prefs;

    UpdateState(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The installed version, as the package manager reports it. */
    static SemVer installed(Context context) {
        try {
            String name = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            SemVer v = SemVer.parse(name);
            if (v != null) return v;
        } catch (PackageManager.NameNotFoundException ignored) {
            // Not possible for our own package; fall through to the build's value.
        }
        return SemVer.parse(BuildConfig.VERSION_NAME);
    }

    long lastCheck() {
        return prefs.getLong(LAST_CHECK, 0);
    }

    String lastError() {
        return prefs.getString(LAST_ERROR, null);
    }

    void checked(Release available, String error) {
        SharedPreferences.Editor e = prefs.edit().putLong(LAST_CHECK, System.currentTimeMillis());
        if (error != null) {
            e.putString(LAST_ERROR, error);
        } else {
            e.remove(LAST_ERROR);
            if (available == null) e.remove(AVAILABLE);
            else e.putString(AVAILABLE, available.toJson().toString());
        }
        e.apply();
    }

    Release available() {
        String json = prefs.getString(AVAILABLE, null);
        if (json == null) return null;
        try {
            return Release.fromJson(new JSONObject(json));
        } catch (JSONException e) {
            return null;
        }
    }

    String ignored() {
        return prefs.getString(IGNORED, null);
    }

    void ignore(SemVer version) {
        prefs.edit().putString(IGNORED, version.toString()).apply();
        add(version.toString(), R_IGNORED, null);
    }

    void unignore() {
        prefs.edit().remove(IGNORED).apply();
    }

    void snooze(SemVer version) {
        prefs.edit().putString(SNOOZED, version.toString())
                .putLong(SNOOZED_UNTIL, System.currentTimeMillis() + UpdatePolicy.SNOOZE_MS).apply();
    }

    boolean shouldPrompt(Context context) {
        Release r = available();
        return r != null && UpdatePolicy.shouldPrompt(r.version, installed(context), ignored(),
                prefs.getString(SNOOZED, null), prefs.getLong(SNOOZED_UNTIL, 0), System.currentTimeMillis());
    }

    void pending(SemVer version) {
        prefs.edit().putString(PENDING, version == null ? null : version.toString()).apply();
    }

    String pending() {
        return prefs.getString(PENDING, null);
    }

    /** After MY_PACKAGE_REPLACED: records the install and forgets the offer. True when it was ours. */
    boolean replaced(SemVer now) {
        String pending = pending();
        boolean ours = pending != null && pending.equals(String.valueOf(now));
        Release offered = available();
        SharedPreferences.Editor e = prefs.edit().remove(PENDING).remove(SNOOZED).remove(SNOOZED_UNTIL);
        if (offered != null && !offered.version.isNewerThan(now)) e.remove(AVAILABLE);
        e.apply();
        add(String.valueOf(now), R_INSTALLED, ours ? null : "adb");
        return ours;
    }

    void add(String version, String result, String detail) {
        JSONArray old = history();
        JSONArray out = new JSONArray();
        try {
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("v", version);
            o.put("r", result);
            if (detail != null) o.put("d", detail);
            out.put(o);
            for (int i = 0; i < old.length() && out.length() < HISTORY_MAX; i++) out.put(old.get(i));
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(HISTORY, out.toString()).apply();
    }

    List<Entry> entries() {
        JSONArray arr = history();
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            out.add(new Entry(o.optLong("t"), o.optString("v"), o.optString("r"), o.optString("d", null)));
        }
        return out;
    }

    private JSONArray history() {
        try {
            return new JSONArray(prefs.getString(HISTORY, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
}
