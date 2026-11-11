package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The two columns of the Mi Box settings page (mibox/app-res/layout/activity_launcher_settings.xml): focusing a
 * category on the left shows its page on the right, with a short fade; Right goes into the page, Left from the
 * page's first column comes back to the category. Also runs the Veille page, which the upstream code knows nothing
 * of. The ids are looked up by name, since the app's R is not visible from this library; when the page is the
 * upstream one (no categories), nothing is done.
 */
final class SettingsPanes {
    private static final String[] CATEGORIES = {"miboxCatGeneral", "miboxCatHome", "miboxCatClock", "miboxCatSleep",
            "miboxCatLook", "miboxCatStart", "miboxCatMusic"};
    private static final String[] PANES = {"miboxPaneGeneral", "miboxPaneHome", "miboxPaneClock", "miboxPaneSleep",
            "miboxPaneLook", "miboxPaneStart", "miboxPaneMusic"};
    private static final int SLEEP = 3, MUSIC = 6;

    private final Activity activity;
    private final View[] categories = new View[CATEGORIES.length];
    private final ViewGroup[] panes = new ViewGroup[PANES.length];
    private int shown = -1;
    private MusicSettings music;

    private SettingsPanes(Activity activity) {
        this.activity = activity;
    }

    @SuppressWarnings("DiscouragedApi") // the app's R is not visible from this library
    private View find(String name) {
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }

    /** Once per page: sets up the columns and shows the first category. */
    static void attach(Activity activity) {
        SettingsPanes p = new SettingsPanes(activity);
        View list = p.find("miboxCategories");
        if (list == null) return;
        Object done = list.getTag();
        if (done instanceof SettingsPanes) {
            SettingsPanes again = (SettingsPanes) done;
            again.sleepStates();
            if (again.music != null && again.shown == MUSIC) again.music.shown();
            return;
        }
        for (int i = 0; i < CATEGORIES.length; i++) {
            p.categories[i] = p.find(CATEGORIES[i]);
            View pane = p.find(PANES[i]);
            if (p.categories[i] == null || !(pane instanceof ViewGroup)) return;
            p.panes[i] = (ViewGroup) pane;
        }
        list.setTag(p);
        p.setUp();
    }

    private void setUp() {
        for (int i = 0; i < categories.length; i++) {
            int index = i;
            View c = categories[i];
            c.setOnFocusChangeListener((v, focused) -> {
                if (focused) show(index, true);
            });
            c.setOnClickListener(v -> enterPane());
            c.setOnKeyListener((v, code, e) -> {
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT && e.getAction() == KeyEvent.ACTION_DOWN) return enterPane();
                return false;
            });
        }
        categories[0].setNextFocusUpId(categories[0].getId());
        categories[categories.length - 1].setNextFocusDownId(categories[categories.length - 1].getId());
        sleepPage();
        music = MusicSettings.attach(activity, () -> {
            if (shown == MUSIC) wireEdges(panes[MUSIC], categories[MUSIC]);
        });
        show(0, false);
        categories[0].requestFocus();
    }

    private void show(int index, boolean fade) {
        if (index == shown) return;
        shown = index;
        for (int i = 0; i < panes.length; i++) {
            panes[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            categories[i].setSelected(i == index);
        }
        View scroll = find("miboxPanes");
        if (scroll != null) scroll.scrollTo(0, 0);
        ViewGroup pane = panes[index];
        if (fade) {
            pane.setAlpha(0f);
            pane.setTranslationX(24 * activity.getResources().getDisplayMetrics().density);
            pane.animate().alpha(1f).translationX(0).setDuration(180).start();
        }
        wireEdges(pane, categories[index]);
        if (index == SLEEP) sleepStates();
        if (music != null) {
            if (index == MUSIC) music.shown();
            else music.hidden();
        }
    }

    /** Right from a category, or OK: the page's first row. */
    private boolean enterPane() {
        List<View> f = focusables(panes[shown]);
        if (f.isEmpty()) return false;
        f.get(0).requestFocus();
        return true;
    }

    /**
     * Left from a row, or from the first choice of a line of choices, goes back to the category; Up from the first
     * row and Down from the last stay in the page.
     */
    private static void wireEdges(ViewGroup pane, View category) {
        List<View> f = focusables(pane);
        for (View v : f) {
            boolean firstOfLine = !(v.getParent() instanceof LinearLayout)
                    || ((LinearLayout) v.getParent()).getOrientation() == LinearLayout.VERTICAL
                    || ((ViewGroup) v.getParent()).indexOfChild(v) == firstFocusableIndex((ViewGroup) v.getParent());
            if (firstOfLine) v.setNextFocusLeftId(category.getId());
        }
        if (!f.isEmpty()) {
            View first = f.get(0), last = f.get(f.size() - 1);
            if (first.getId() != View.NO_ID) first.setNextFocusUpId(first.getId());
            if (last.getId() != View.NO_ID) last.setNextFocusDownId(last.getId());
        }
    }

    private static int firstFocusableIndex(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getVisibility() == View.VISIBLE && c.isFocusable()) return i;
        }
        return -1;
    }

    private static List<View> focusables(ViewGroup g) {
        List<View> out = new ArrayList<>();
        collect(g, out);
        return out;
    }

    private static void collect(ViewGroup g, List<View> out) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE) continue;
            if (c.isFocusable()) {
                if (c.getId() == View.NO_ID) c.setId(View.generateViewId());
                out.add(c);
            }
            if (c instanceof ViewGroup) collect((ViewGroup) c, out);
        }
    }

    // ---- Veille ----

    private TextView dreamChoice, dreamState, night;
    private final View[] after = new View[DreamSettings.START_MIN.length];
    private final View[] off = new View[DreamSettings.OFF_HOURS.length];
    private final View[] styles = new View[DreamSettings.Style.values().length];

    private void sleepPage() {
        View on = find("miboxDreamChoice");
        if (!(on instanceof TextView)) return;
        dreamChoice = (TextView) on;
        dreamState = (TextView) find("miboxDreamState");
        night = (TextView) find("miboxDreamNight");
        String[] afterIds = {"miboxDreamAfter5", "miboxDreamAfter10", "miboxDreamAfter15", "miboxDreamAfter30"};
        String[] offIds = {"miboxDreamOff1", "miboxDreamOff2", "miboxDreamOff4", "miboxDreamOffNever"};
        String[] styleIds = {"miboxDreamStyleClock", "miboxDreamStyleDate", "miboxDreamStyleSmall", "miboxDreamStyleBlack"};
        for (int i = 0; i < after.length; i++) {
            int minutes = DreamSettings.START_MIN[i];
            after[i] = find(afterIds[i]);
            if (after[i] != null) after[i].setOnClickListener(v -> refused(DreamSettings.setStartMinutes(activity, minutes)));
        }
        for (int i = 0; i < off.length; i++) {
            int hours = DreamSettings.OFF_HOURS[i];
            off[i] = find(offIds[i]);
            if (off[i] != null) off[i].setOnClickListener(v -> refused(DreamSettings.setOffHours(activity, hours)));
        }
        for (int i = 0; i < styles.length; i++) {
            DreamSettings.Style s = DreamSettings.Style.values()[i];
            styles[i] = find(styleIds[i]);
            if (styles[i] != null) styles[i].setOnClickListener(v -> {
                DreamSettings.setStyle(activity, s);
                sleepStates();
            });
        }
        dreamChoice.setOnClickListener(v -> refused(DreamSettings.setActive(activity, !DreamSettings.active(activity))));
        if (night != null) night.setOnClickListener(v -> {
            DreamSettings.setNightDim(activity, !DreamSettings.nightDim(activity));
            sleepStates();
        });
        View preview = find("miboxDreamPreview");
        if (preview != null) {
            preview.setOnClickListener(v -> activity.startActivity(new Intent(activity, DreamPreviewActivity.class)));
        }
        View system = find("miboxDreamSystem");
        if (system != null) system.setOnClickListener(v -> {
            try {
                activity.startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(activity, R.string.mibox_screen_unavailable, Toast.LENGTH_LONG).show();
            }
        });
        sleepStates();
    }

    /** After a change Android may refuse (no permission): the page says why, and shows the real state. */
    private void refused(boolean ok) {
        if (!ok) Toast.makeText(activity, R.string.mibox_dream_no_permission, Toast.LENGTH_LONG).show();
        sleepStates();
    }

    /** Each choice shows what Android and the page have now. */
    private void sleepStates() {
        if (dreamChoice == null) return;
        boolean active = DreamSettings.active(activity);
        dreamChoice.setSelected(active);
        if (dreamState != null) {
            dreamState.setText(!DreamSettings.canWrite(activity) ? R.string.mibox_dream_no_permission
                    : active ? R.string.mibox_dream_active : R.string.mibox_dream_other);
        }
        int minutes = DreamSettings.startMinutes(activity);
        for (int i = 0; i < after.length; i++) if (after[i] != null) after[i].setSelected(DreamSettings.START_MIN[i] == minutes);
        int hours = DreamSettings.offHours(activity);
        for (int i = 0; i < off.length; i++) if (off[i] != null) off[i].setSelected(DreamSettings.OFF_HOURS[i] == hours);
        DreamSettings.Style s = DreamSettings.style(activity);
        for (int i = 0; i < styles.length; i++) if (styles[i] != null) styles[i].setSelected(i == s.ordinal());
        if (night != null) night.setSelected(DreamSettings.nightDim(activity));
    }
}
