package com.gathin.launchos.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;

import java.util.List;
import java.util.Locale;

/**
 * Actualiser: fetches a section's categories again (the lists inside them follow when they are shown), empties
 * the programme guide, or empties the logos and posters kept on the box. Each row says when it was last loaded.
 */
public final class RefreshActivity extends Activity {
    private static final Xtream.Section[] SECTIONS = Xtream.Section.values();

    private final Rows.Row[] sectionRows = new Rows.Row[SECTIONS.length];
    private Rows.Row allRow, guideRow, imagesRow;
    private Xtream api;
    private int running;
    private boolean anyFailed;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_refresh);
        Rows.header(this, R.drawable.ic_refresh, R.string.refresh_title, R.string.refresh_hint);
        Account a = AccountStore.load(this);
        Library.forAccount(AccountStore.version());
        Epg.forAccount(AccountStore.version());
        api = a == null ? null : new Xtream(a);
        Details.forAccount(AccountStore.version());
        ViewGroup column = findViewById(R.id.column);

        allRow = Rows.add(column, R.drawable.ic_refresh, R.string.refresh_all).sub(R.string.refresh_all_sub)
                .click(v -> refreshAll());
        Rows.section(column, R.string.refresh_section_lists);
        int[] names = {R.string.refresh_live, R.string.refresh_movies, R.string.refresh_series};
        int[] icons = {R.drawable.ic_live, R.drawable.ic_movie, R.drawable.ic_series};
        for (int i = 0; i < SECTIONS.length; i++) {
            Xtream.Section s = SECTIONS[i];
            sectionRows[i] = Rows.add(column, icons[i], names[i]).click(v -> refresh(s));
        }
        Rows.section(column, R.string.refresh_section_memory);
        guideRow = Rows.add(column, R.drawable.ic_guide, R.string.refresh_guide).click(v -> {
            Epg.clear();
            done(guideRow, getString(R.string.refresh_cleared), false);
            describe();
        });
        imagesRow = Rows.add(column, R.drawable.ic_image, R.string.refresh_images).click(v -> clearImages());
        describe();
        allRow.view.requestFocus();
    }

    /** What each row holds now. */
    private void describe() {
        for (int i = 0; i < SECTIONS.length; i++) {
            Library lib = Library.of(SECTIONS[i]);
            List<Xtream.Category> cats = lib.categories();
            if (cats == null) {
                sectionRows[i].sub(getString(R.string.refresh_not_loaded));
                continue;
            }
            int n = cats.size();
            String what = lib.all() == null ? "" : String.format(Locale.FRANCE, " · %d titre%s", lib.all().size(),
                    lib.all().size() > 1 ? "s" : "");
            if (SECTIONS[i] == Xtream.Section.LIVE && lib.all() != null) {
                what = String.format(Locale.FRANCE, " · %d chaîne%s", lib.all().size(), lib.all().size() > 1 ? "s" : "");
            }
            sectionRows[i].sub(String.format(Locale.FRANCE, "%d groupe%s%s · chargé %s", n, n > 1 ? "s" : "",
                    what, ago(lib.loadedAt())));
        }
        guideRow.sub(getResources().getQuantityString(R.plurals.refresh_guide_sub, Epg.size(), Epg.size()));
        imagesRow.sub(getString(R.string.refresh_images_sub, "…"));
        Bg.run(() -> Images.diskBytes(this), (bytes, error) -> {
            if (!isDestroyed() && bytes != null) imagesRow.sub(getString(R.string.refresh_images_sub, size(bytes)));
        });
    }

    private void refreshAll() {
        if (running > 0) return;
        Epg.clear();
        done(guideRow, getString(R.string.refresh_cleared), false);
        anyFailed = false;
        for (Xtream.Section s : SECTIONS) refresh(s);
        if (api != null) done(allRow, getString(R.string.refresh_running), false);
    }

    private void refresh(Xtream.Section s) {
        Rows.Row r = sectionRows[s.ordinal()];
        if (api == null) {
            done(r, getString(R.string.refresh_no_account), true);
            if (running == 0) done(allRow, getString(R.string.refresh_no_account), true);
            return;
        }
        if (r.stateText().equals(getString(R.string.refresh_running))) return;
        Library lib = Library.of(s);
        lib.forget();
        done(r, getString(R.string.refresh_running), false);
        running++;
        lib.load(api, () -> {
            running--;
            boolean failed = lib.error() != null && !lib.ready();
            if (failed) anyFailed = true;
            if (isDestroyed()) return;
            done(r, failed ? lib.error().french() : getString(R.string.refresh_done), failed);
            describe();
            if (running == 0 && allRow.stateText().equals(getString(R.string.refresh_running))) {
                done(allRow, getString(anyFailed ? R.string.refresh_some_failed : R.string.refresh_done), anyFailed);
            }
        });
    }

    private void clearImages() {
        done(imagesRow, getString(R.string.refresh_running), false);
        Images.clearMemory();
        Bg.run(() -> {
            Images.clearDisk(this);
            return Boolean.TRUE;
        }, (ok, error) -> {
            if (isDestroyed()) return;
            done(imagesRow, getString(R.string.refresh_cleared), false);
            describe();
        });
    }

    private void done(Rows.Row r, String text, boolean error) {
        boolean finished = text.equals(getString(R.string.refresh_done)) || text.equals(getString(R.string.refresh_cleared));
        r.state(text, error ? Rows.Tone.ERROR : finished ? Rows.Tone.OK : Rows.Tone.NORMAL);
    }

    static String ago(long at) {
        long min = (System.currentTimeMillis() - at) / 60_000;
        if (min < 1) return "à l'instant";
        if (min < 60) return "il y a " + min + " min";
        return "il y a " + (min / 60) + " h";
    }

    static String size(long bytes) {
        if (bytes < 1024 * 1024) return String.format(Locale.FRANCE, "%d Ko", bytes / 1024);
        return String.format(Locale.FRANCE, "%.1f Mo", bytes / (1024.0 * 1024));
    }
}
