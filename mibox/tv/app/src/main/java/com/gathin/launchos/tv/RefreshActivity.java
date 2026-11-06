package com.gathin.launchos.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Locale;

/**
 * Actualiser: fetches a section's categories again (the lists inside them follow when they are shown), empties
 * the programme guide, or empties the logos and posters kept on the box. Each row says when it was last loaded.
 */
public final class RefreshActivity extends Activity {
    private static final Xtream.Section[] SECTIONS = Xtream.Section.values();

    private final Row[] sectionRows = new Row[SECTIONS.length];
    private Row allRow, guideRow, imagesRow;
    private Xtream api;
    private int running;
    private boolean anyFailed;

    private static final class Row {
        final View view;
        final TextView sub, state;

        Row(View view) {
            this.view = view;
            sub = view.findViewById(R.id.sub);
            state = view.findViewById(R.id.state);
        }
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_refresh);
        Account a = AccountStore.load(this);
        Catalog.forAccount(AccountStore.version());
        Epg.forAccount(AccountStore.version());
        api = a == null ? null : new Xtream(a);
        ViewGroup column = findViewById(R.id.column);

        allRow = row(column, R.string.refresh_all);
        allRow.sub.setText(R.string.refresh_all_sub);
        allRow.view.setOnClickListener(v -> refreshAll());
        int[] names = {R.string.refresh_live, R.string.refresh_movies, R.string.refresh_series};
        for (int i = 0; i < SECTIONS.length; i++) {
            Xtream.Section s = SECTIONS[i];
            sectionRows[i] = row(column, names[i]);
            sectionRows[i].view.setOnClickListener(v -> refresh(s));
        }
        guideRow = row(column, R.string.refresh_guide);
        guideRow.view.setOnClickListener(v -> {
            Epg.clear();
            done(guideRow, getString(R.string.refresh_cleared), false);
            describe();
        });
        imagesRow = row(column, R.string.refresh_images);
        imagesRow.view.setOnClickListener(v -> clearImages());
        describe();
        allRow.view.requestFocus();
    }

    private Row row(ViewGroup column, int title) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_refresh, column, false);
        ((TextView) v.findViewById(R.id.title)).setText(title);
        Ui.lift(v, 1.02f);
        column.addView(v);
        return new Row(v);
    }

    /** What each row holds now. */
    private void describe() {
        for (int i = 0; i < SECTIONS.length; i++) {
            int n = Catalog.categoryCount(SECTIONS[i]);
            sectionRows[i].sub.setText(n < 0 ? getString(R.string.refresh_not_loaded)
                    : String.format(Locale.FRANCE, "%d catégorie%s · chargé %s", n, n > 1 ? "s" : "",
                    ago(Catalog.loadedAt(SECTIONS[i]))));
        }
        guideRow.sub.setText(getResources().getQuantityString(R.plurals.refresh_guide_sub, Epg.size(), Epg.size()));
        imagesRow.sub.setText(getString(R.string.refresh_images_sub, "…"));
        Bg.run(() -> Images.diskBytes(this), (bytes, error) -> {
            if (!isDestroyed() && bytes != null) imagesRow.sub.setText(getString(R.string.refresh_images_sub, size(bytes)));
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
        Row r = sectionRows[s.ordinal()];
        if (api == null) {
            done(r, getString(R.string.refresh_no_account), true);
            if (running == 0) done(allRow, getString(R.string.refresh_no_account), true);
            return;
        }
        if (r.state.getText().toString().equals(getString(R.string.refresh_running))) return;
        Catalog.forget(s);
        done(r, getString(R.string.refresh_running), false);
        running++;
        Xtream x = api;
        Bg.run(() -> x.categories(s), (list, error) -> {
            running--;
            if (error == null) Catalog.putCategories(s, list);
            else anyFailed = true;
            if (isDestroyed()) return;
            done(r, error == null ? getString(R.string.refresh_done) : error.french(), error != null);
            describe();
            if (running == 0 && allRow.state.getText().toString().equals(getString(R.string.refresh_running))) {
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

    private void done(Row r, String text, boolean error) {
        r.state.setText(text);
        r.state.setTextColor(getColor(error ? R.color.danger : R.color.text));
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
