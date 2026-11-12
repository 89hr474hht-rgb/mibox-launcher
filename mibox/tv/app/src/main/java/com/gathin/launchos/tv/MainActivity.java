package com.gathin.launchos.tv;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * The main screen. On the left, the rail: a narrow dark strip of icons (Rechercher, Accueil, Chaînes, Guide TV,
 * Films, Séries, then Réglages) that opens over the page only while it has the focus. The page fills the rest:
 * Accueil (the dashboard, shown first), Chaînes with its three panels and the live player, or Films and Séries.
 *
 * The remote: the arrows move inside a page; Left at a page's left edge opens the rail on the section shown, Right
 * leaves it back where the page was. Back steps out one level at a time (full screen, small window, panel, rail),
 * then leaves the app.
 */
public final class MainActivity extends Activity {
    static final String EXTRA_PLAY_CHANNEL = "play_channel";
    private static final long SECTION_DELAY_MS = 320;
    private static final int NAV_SEARCH = 0, NAV_HOME = 1, NAV_LIVE = 2, NAV_GUIDE = 3, NAV_MOVIES = 4, NAV_SERIES = 5,
            NAV_REFRESH = 6, NAV_SETTINGS = 7, NAV_UPDATES = 8, NAV_ACCOUNT = 9;
    /** The login screen opens by itself once, when there is no account yet. */
    private static boolean loginOffered;

    private Root root;
    private ViewGroup rail;
    private View railShade, railTitle;
    private View[] railItems;
    private View[] sectionItems;
    private View navUpdates;
    private TextView banner, status;
    private LivePage live;
    private BrowsePage moviesPage, seriesPage;
    private HomePage homePage;
    /** Accueil is shown (section then only says which section the rail last had). */
    private boolean home = true;
    private boolean pendingHome;
    private LivePlayer player;
    private FrameLayout liveLayer;

    private Xtream api;
    private int accountVersion = -1;
    private Xtream.Section section = Xtream.Section.LIVE;
    private boolean railOpen;
    private ValueAnimator railAnim;
    /** The last view focused in the page, to go back to when the rail closes. */
    private View pageFocus;
    private boolean resumed;
    /** The start-up screen, while it shows (opened from the launcher, not when coming back). */
    private StartScreen start;
    private static String bannerShownFor;
    private final Updater.Listener updates = this::showUpdate;
    private Xtream.Section pendingSection;
    private final Runnable switchSection = () -> {
        if (pendingHome && !home) showHome(false);
        else if (pendingSection != null && (home || pendingSection != section)) showSection(pendingSection, false);
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        if (state == null && android.content.Intent.ACTION_MAIN.equals(getIntent().getAction())) {
            start = StartScreen.show(this);
        }
        root = findViewById(R.id.root);
        rail = findViewById(R.id.rail);
        railShade = findViewById(R.id.rail_shade);
        railTitle = findViewById(R.id.rail_title);
        status = findViewById(R.id.status);
        banner = findViewById(R.id.update_banner);
        liveLayer = findViewById(R.id.live_layer);

        int[] ids = {R.id.nav_search, R.id.nav_home, R.id.nav_live, R.id.nav_guide, R.id.nav_movies, R.id.nav_series,
                R.id.nav_refresh, R.id.nav_settings, R.id.nav_updates, R.id.nav_account};
        int[] icons = {R.drawable.ic_search, R.drawable.ic_home, R.drawable.ic_live, R.drawable.ic_guide,
                R.drawable.ic_movie, R.drawable.ic_series, R.drawable.ic_refresh, R.drawable.ic_settings,
                R.drawable.ic_update, R.drawable.ic_account};
        int[] labels = {R.string.nav_search, R.string.nav_home, R.string.nav_live, R.string.nav_guide,
                R.string.nav_movies, R.string.nav_series, R.string.nav_refresh, R.string.nav_settings,
                R.string.nav_updates, R.string.nav_account};
        railItems = new View[ids.length];
        for (int i = 0; i < ids.length; i++) {
            View v = findViewById(ids[i]);
            ((ImageView) v.findViewById(R.id.rail_icon)).setImageResource(icons[i]);
            ((TextView) v.findViewById(R.id.rail_label)).setText(labels[i]);
            railItems[i] = v;
        }
        sectionItems = new View[]{railItems[NAV_LIVE], railItems[NAV_MOVIES], railItems[NAV_SERIES]};
        // A short menu: search, Accueil, the sections and the guide, Réglages. Actualiser, Mises à jour and Compte are
        // in Réglages; their rail items stay in the layout, out of sight, so Up and Down skip them.
        railItems[NAV_REFRESH].setVisibility(View.GONE);
        railItems[NAV_UPDATES].setVisibility(View.GONE);
        railItems[NAV_ACCOUNT].setVisibility(View.GONE);
        navUpdates = railItems[NAV_SETTINGS];
        Xtream.Section[] sections = Xtream.Section.values();
        for (int i = 0; i < sectionItems.length; i++) {
            Xtream.Section s = sections[i];
            sectionItems[i].setOnFocusChangeListener((v, focused) -> {
                if (!focused || (s == section && !home)) return;
                // Moving along the rail previews the section after a short pause, not at every step.
                pendingSection = s;
                pendingHome = false;
                rail.removeCallbacks(switchSection);
                rail.postDelayed(switchSection, SECTION_DELAY_MS);
            });
            sectionItems[i].setOnClickListener(v -> {
                rail.removeCallbacks(switchSection);
                if (s != section || home) showSection(s, false);
                enterPage();
            });
        }
        railItems[NAV_HOME].setOnFocusChangeListener((v, focused) -> {
            if (!focused || home) return;
            pendingHome = true;
            pendingSection = null;
            rail.removeCallbacks(switchSection);
            rail.postDelayed(switchSection, SECTION_DELAY_MS);
        });
        railItems[NAV_HOME].setOnClickListener(v -> {
            rail.removeCallbacks(switchSection);
            if (!home) showHome(false);
            enterPage();
        });
        railItems[NAV_SEARCH].setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        railItems[NAV_GUIDE].setOnClickListener(v -> startActivity(new Intent(this, GuideActivity.class)));
        railItems[NAV_REFRESH].setOnClickListener(v -> startActivity(new Intent(this, RefreshActivity.class)));
        railItems[NAV_SETTINGS].setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        railItems[NAV_UPDATES].setOnClickListener(v -> startActivity(new Intent(this, UpdatesActivity.class)));
        railItems[NAV_ACCOUNT].setOnClickListener(v -> startActivity(new Intent(this, AccountActivity.class)));

        player = new LivePlayer(this, liveLayer, new LivePlayer.Host() {
            @Override
            public Rect miniBounds() {
                return section == Xtream.Section.LIVE && !home ? live.miniBounds() : null;
            }

            @Override
            public void modeChanged(LivePlayer.Mode mode) {
                rail.setVisibility(mode == LivePlayer.Mode.FULL ? View.INVISIBLE : View.VISIBLE);
                live.modeChanged(mode);
            }

            @Override
            public void channelChanged(Item channel) {
                live.channelChanged(channel);
            }
        });
        live = new LivePage(this, findViewById(R.id.page_live), player);
        moviesPage = new BrowsePage(this, findViewById(R.id.page_movies), Xtream.Section.MOVIES);
        seriesPage = new BrowsePage(this, findViewById(R.id.page_series), Xtream.Section.SERIES);
        homePage = new HomePage(this, findViewById(R.id.page_home));

        root.setRouter(this::route);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> focusMoved(newFocus));

        // Same logic as LaunchOS: a daily check in the background, and one when the app opens (at most hourly).
        UpdateScheduler.schedule(this);
        UpdateScheduler.checkSoon(this);
        handle(getIntent());
    }

    Root root() {
        return root;
    }

    View liveLayer() {
        return liveLayer;
    }

    /** True while the activity is in front and page is the one shown. */
    boolean showing(Object page) {
        return resumed && page == current();
    }

    private Object current() {
        if (home) return homePage;
        return section == Xtream.Section.LIVE ? live : section == Xtream.Section.MOVIES ? moviesPage : seriesPage;
    }

    private View pageRoot(Xtream.Section s) {
        return s == Xtream.Section.LIVE ? live.root : s == Xtream.Section.MOVIES ? moviesPage.root : seriesPage.root;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    /** True while the focus is in the rail (a page loading then leaves the focus where it is). */
    boolean inRail() {
        View f = getCurrentFocus();
        return f != null && Root.inside(f, rail);
    }

    /** A channel chosen on Accueil plays in Chaînes. */
    void playChannel(Item c) {
        if (home || section != Xtream.Section.LIVE) showSection(Xtream.Section.LIVE, false);
        root.post(() -> live.play(c));
    }

    /** A channel chosen in search plays here, in Chaînes. */
    private void handle(Intent intent) {
        String id = intent == null ? null : intent.getStringExtra(EXTRA_PLAY_CHANNEL);
        if (id == null) return;
        intent.removeExtra(EXTRA_PLAY_CHANNEL);
        Item c = Library.of(Xtream.Section.LIVE).find(id);
        if (c == null) return;
        playChannel(c);
    }

    @Override
    protected void onStart() {
        super.onStart();
        player.resume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        player.pause();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        Updater.get().removeListener(updates);
        moviesPage.hidden();
        seriesPage.hidden();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        Updater.get().addListener(updates);
        showUpdate();
        int version = AccountStore.version();
        Library.forAccount(version);
        Epg.forAccount(version);
        Details.forAccount(version);
        boolean first = accountVersion == -1;
        if (version != accountVersion) {
            accountVersion = version;
            Account a = AccountStore.load(this);
            api = a == null ? null : new Xtream(a);
            player.setApi(api);
        }
        if (api == null) {
            for (Xtream.Section s : Xtream.Section.values()) pageRoot(s).setVisibility(View.GONE);
            homePage.root.setVisibility(View.GONE);
            status.setText(R.string.no_account);
            status.setVisibility(View.VISIBLE);
            markSection();
            railItems[NAV_SETTINGS].requestFocus();
            // No account yet: the login screen opens by itself, once.
            if (!loginOffered) {
                loginOffered = true;
                startActivity(new Intent(this, AccountActivity.class));
            }
            return;
        }
        status.setVisibility(View.GONE);
        if (home) showHome(first);
        else showSection(section, first);
    }

    // ---- sections ----

    /** Accueil: the dashboard. The live player stops, as for Films and Séries. */
    private void showHome(boolean focusPage) {
        if (player.mode() != LivePlayer.Mode.HIDDEN) player.stop();
        home = true;
        pendingHome = false;
        pendingSection = null;
        markSection();
        for (Xtream.Section x : Xtream.Section.values()) pageRoot(x).setVisibility(View.GONE);
        moviesPage.hidden();
        seriesPage.hidden();
        View page = homePage.root;
        if (page.getVisibility() != View.VISIBLE) {
            page.setAlpha(0f);
            page.setVisibility(View.VISIBLE);
            page.animate().alpha(1f).setDuration(220).start();
        }
        homePage.show(api);
        pageFocus = null;
        if (focusPage) root.post(this::enterPage);
    }

    private void showSection(Xtream.Section s, boolean focusPage) {
        if (s != Xtream.Section.LIVE && player.mode() != LivePlayer.Mode.HIDDEN) player.stop();
        section = s;
        home = false;
        homePage.root.setVisibility(View.GONE);
        pendingSection = null;
        pendingHome = false;
        markSection();
        for (Xtream.Section x : Xtream.Section.values()) {
            View page = pageRoot(x);
            boolean on = x == s;
            if (on && page.getVisibility() != View.VISIBLE) {
                page.setAlpha(0f);
                page.setVisibility(View.VISIBLE);
                page.animate().alpha(1f).setDuration(220).start();
            } else if (!on) {
                page.setVisibility(View.GONE);
            }
        }
        if (s == Xtream.Section.LIVE) {
            moviesPage.hidden();
            seriesPage.hidden();
            live.show(api);
        } else {
            (s == Xtream.Section.MOVIES ? seriesPage : moviesPage).hidden();
            (s == Xtream.Section.MOVIES ? moviesPage : seriesPage).show(api);
        }
        pageFocus = null;
        if (focusPage) root.post(this::enterPage);
    }

    private void markSection() {
        for (int i = 0; i < sectionItems.length; i++) sectionItems[i].setSelected(!home && i == section.ordinal());
        railItems[NAV_HOME].setSelected(home);
    }

    /** The rail item of the page shown. */
    private View shownItem() {
        return home ? railItems[NAV_HOME] : sectionItems[section.ordinal()];
    }

    private View shownRoot() {
        return home ? homePage.root : pageRoot(section);
    }

    /** Puts the focus in the page: where it was, else the page's own start. */
    private void enterPage() {
        View v = pageTarget();
        if (v != null) v.requestFocus();
    }

    private View pageTarget() {
        if (pageFocus != null && pageFocus.isAttachedToWindow() && pageFocus.isShown()
                && (Root.inside(pageFocus, shownRoot()) || (!home && pageFocus == liveLayer))) {
            return pageFocus;
        }
        if (home) return homePage.defaultFocus();
        View v = section == Xtream.Section.LIVE ? live.defaultFocus()
                : (section == Xtream.Section.MOVIES ? moviesPage : seriesPage).defaultFocus();
        if (v == null && section == Xtream.Section.LIVE) live.focusGroups();
        return v;
    }

    // ---- the remote ----

    private View route(View focused, int direction, View proposed) {
        if (player.isFull()) return focused;
        if (focused == null) return proposed;
        if (Root.inside(focused, rail)) {
            if (direction == View.FOCUS_RIGHT) {
                if (api == null) return focused;
                rail.removeCallbacks(switchSection);
                if (pendingHome && !home) showHome(false);
                else if (pendingSection != null && (home || pendingSection != section)) showSection(pendingSection, false);
                View v = pageTarget();
                return v != null ? v : focused;
            }
            if (direction == View.FOCUS_LEFT) return focused;
            if (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
                return railStep(focused, direction == View.FOCUS_DOWN ? 1 : -1);
            }
            return focused;
        }
        View r;
        if (home) r = homePage.route(focused, direction, proposed);
        else if (section == Xtream.Section.LIVE) r = live.route(focused, direction, proposed);
        else r = (section == Xtream.Section.MOVIES ? moviesPage : seriesPage).route(focused, direction, proposed);
        // Into the rail only on purpose, and then on the section shown.
        if (r == null || Root.inside(r, rail)) {
            return direction == View.FOCUS_LEFT ? shownItem() : focused;
        }
        return r;
    }

    /**
     * Up and Down in the rail go to the next item shown, in order. Android's own search looks for the nearest view
     * and, across the gap under Séries, finds the page instead: Actualiser, Réglages and Compte were out of reach.
     */
    private View railStep(View focused, int step) {
        int at = -1;
        for (int i = 0; i < railItems.length; i++) if (Root.inside(focused, railItems[i])) at = i;
        if (at < 0) return focused;
        for (int i = at + step; i >= 0 && i < railItems.length; i += step) {
            if (railItems[i].getVisibility() == View.VISIBLE && railItems[i].isFocusable()) return railItems[i];
        }
        return focused;
    }

    private void focusMoved(View now) {
        if (now == null) return;
        boolean inRail = Root.inside(now, rail);
        if (inRail != railOpen) openRail(inRail);
        if (!inRail) {
            pageFocus = now;
            if (home) homePage.focusChanged(now);
            else if (section == Xtream.Section.MOVIES) moviesPage.focusChanged(now);
            else if (section == Xtream.Section.SERIES) seriesPage.focusChanged(now);
        }
    }

    /** The rail opens over the page (the page is dimmed behind it) and closes back to its icons. */
    private void openRail(boolean open) {
        railOpen = open;
        if (railAnim != null) railAnim.cancel();
        int from = rail.getWidth();
        int to = getResources().getDimensionPixelSize(open ? R.dimen.rail_open : R.dimen.rail_closed);
        railAnim = ValueAnimator.ofInt(from, to);
        railAnim.setDuration(open ? 240 : 200);
        railAnim.setInterpolator(new DecelerateInterpolator(1.5f));
        railAnim.addUpdateListener(a -> {
            ViewGroup.LayoutParams lp = rail.getLayoutParams();
            lp.width = (int) a.getAnimatedValue();
            rail.setLayoutParams(lp);
        });
        railAnim.start();
        for (View v : railItems) v.findViewById(R.id.rail_label).animate().alpha(open ? 1f : 0f).setDuration(open ? 260 : 120).start();
        railTitle.animate().alpha(open ? 1f : 0f).setDuration(open ? 260 : 120).start();
        if (open) railShade.setVisibility(View.VISIBLE);
        railShade.animate().alpha(open ? 1f : 0f).setDuration(240)
                .withEndAction(() -> {
                    if (!railOpen) railShade.setVisibility(View.INVISIBLE);
                }).start();
    }

    /** The first focus is given before the window is shown, when no focus change is reported: catch up here. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) focusMoved(getCurrentFocus());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (start != null && start.showing()) {
            // Any key ends the start-up screen; it does nothing else.
            if (event.getAction() == KeyEvent.ACTION_UP) start.leave();
            return true;
        }
        if (player.key(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event);
        View f = getCurrentFocus();
        if (f != null && !Root.inside(f, rail)) {
            if (home) {
                View start = homePage.defaultFocus();
                if (start != null && f != start) {
                    start.requestFocus();
                    return true;
                }
                railItems[NAV_HOME].requestFocus();
                return true;
            }
            if (section == Xtream.Section.LIVE && live.back(f)) return true;
            if (section != Xtream.Section.LIVE) {
                BrowsePage p = section == Xtream.Section.MOVIES ? moviesPage : seriesPage;
                View start = p.defaultFocus();
                if (start != null && f != start) {
                    // Back in the rows goes to the top of the page first.
                    start.requestFocus();
                    return true;
                }
            }
            sectionItems[section.ordinal()].requestFocus();
            return true;
        }
        if (player.mode() == LivePlayer.Mode.MINI) {
            player.stop();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---- updates ----

    /** Réglages in the rail says when a newer version is waiting; the banner says it once per version and session. */
    private void showUpdate() {
        UpdateState state = new UpdateState(this);
        Release r = state.available();
        boolean newer = r != null && r.version.isNewerThan(UpdateState.installed(this));
        ((TextView) navUpdates.findViewById(R.id.rail_label)).setText(newer ? R.string.nav_settings_update : R.string.nav_settings);
        navUpdates.findViewById(R.id.rail_dot).setVisibility(newer ? View.VISIBLE : View.GONE);
        if (newer && state.shouldPrompt(this) && !String.valueOf(r.version).equals(bannerShownFor)) {
            bannerShownFor = String.valueOf(r.version);
            banner.setText(getString(R.string.upd_banner, bannerShownFor));
            banner.setAlpha(0f);
            banner.setTranslationY(Ui.dp(this, 30));
            banner.setVisibility(View.VISIBLE);
            banner.animate().alpha(1f).translationY(0).setDuration(320).start();
            banner.postDelayed(() -> banner.animate().alpha(0f).setDuration(400)
                    .withEndAction(() -> banner.setVisibility(View.GONE)).start(), 9000);
        }
    }
}
