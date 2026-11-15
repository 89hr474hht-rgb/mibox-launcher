package com.gathin.launchos.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.gathin.launchos.design.Focus;
import com.gathin.launchos.design.Motion;

import java.util.List;

/**
 * The main screen: the navigation bar along the top (Réglages, Rechercher, Accueil, TV, Films, Séries, and
 * the app's logo) and one page at a time (HomePage, LivePage with the {@link LivePlayer}, or a BrowsePage for Films
 * and Séries). Main thread only.
 *
 * <p>Focus: every arrow goes through {@link Root}'s router ({@link #route}). Up at the top of a page enters the bar on
 * the section shown; Left and Right move along the bar, showing a section after a short pause; Down goes back into
 * the page where it was. The bar fades out while the remote is down in a page's rows. Back steps out one level at a
 * time (full screen, small window, panel, the bar), then leaves the app.
 *
 * <p>Lifecycle: the account is read again in onResume (a changed account reloads every page); the live stream stops
 * in onStop and starts again in onStart. The start-up screen ({@link StartScreen}) shows only when the app is opened
 * from the launcher; a key ends it and still reaches the app.
 */
public final class MainActivity extends Activity {
    static final String EXTRA_PLAY_CHANNEL = "play_channel";
    private static final long SECTION_DELAY_MS = 320;
    private static final int NAV_SEARCH = 0, NAV_HOME = 1, NAV_LIVE = 2, NAV_MOVIES = 3, NAV_SERIES = 4, NAV_SETTINGS = 5;
    /** The login screen opens by itself once, when there is no account yet. */
    private static boolean loginOffered;

    private Root root;
    private ViewGroup bar;
    /** The bar's items by NAV_*, and in the order they sit on screen (for Left and Right). */
    private View[] navItems, barOrder;
    private boolean barShown = true;
    private View[] sectionItems;
    private View navUpdates;
    private TextView banner, status;
    private LivePage live;
    private BrowsePage moviesPage, seriesPage;
    private HomePage homePage;
    /** Accueil is shown (section then only says which section the bar last had). */
    private boolean home = true;
    private boolean pendingHome;
    private LivePlayer player;
    private FrameLayout liveLayer;

    private Xtream api;
    private int accountVersion = -1;
    private Xtream.Section section = Xtream.Section.LIVE;
    /** The last view focused in the page, to go back to from the bar. */
    private View pageFocus;
    private boolean resumed;
    /** The start-up screen, while it shows (opened from the launcher, not when coming back). */
    private StartScreen start;
    /** Where the app opens (Réglages › Général), used once, when it was started from the home screen. */
    private Options.StartPage opening;
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
            opening = Options.startPage(this);
        }
        Options.applyGlobals(this);
        root = findViewById(R.id.root);
        bar = findViewById(R.id.topbar);
        status = findViewById(R.id.status);
        banner = findViewById(R.id.update_banner);
        liveLayer = findViewById(R.id.live_layer);

        int[] ids = {R.id.nav_search, R.id.nav_home, R.id.nav_live, R.id.nav_movies, R.id.nav_series,
                R.id.nav_settings};
        navItems = new View[ids.length];
        for (int i = 0; i < ids.length; i++) {
            navItems[i] = findViewById(ids[i]);
            Focus.lift(navItems[i]);
        }
        barOrder = new View[]{navItems[NAV_SETTINGS], navItems[NAV_SEARCH], navItems[NAV_HOME], navItems[NAV_LIVE],
                navItems[NAV_MOVIES], navItems[NAV_SERIES]};
        sectionItems = new View[]{navItems[NAV_LIVE], navItems[NAV_MOVIES], navItems[NAV_SERIES]};
        // Réglages holds the account, the lists' refresh and the updates.
        navUpdates = navItems[NAV_SETTINGS];
        Xtream.Section[] sections = Xtream.Section.values();
        for (int i = 0; i < sectionItems.length; i++) {
            Xtream.Section s = sections[i];
            sectionItems[i].setOnFocusChangeListener((v, focused) -> {
                if (!focused || (s == section && !home)) return;
                // Moving along the bar shows the section after a short pause, not at every step.
                pendingSection = s;
                pendingHome = false;
                bar.removeCallbacks(switchSection);
                bar.postDelayed(switchSection, SECTION_DELAY_MS);
            });
            sectionItems[i].setOnClickListener(v -> {
                bar.removeCallbacks(switchSection);
                if (s != section || home) showSection(s, false);
                enterPage();
            });
        }
        navItems[NAV_HOME].setOnFocusChangeListener((v, focused) -> {
            if (!focused || home) return;
            pendingHome = true;
            pendingSection = null;
            bar.removeCallbacks(switchSection);
            bar.postDelayed(switchSection, SECTION_DELAY_MS);
        });
        navItems[NAV_HOME].setOnClickListener(v -> {
            bar.removeCallbacks(switchSection);
            if (!home) showHome(false);
            enterPage();
        });
        navItems[NAV_SEARCH].setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        navItems[NAV_SETTINGS].setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        player = new LivePlayer(this, liveLayer, new LivePlayer.Host() {
            @Override
            public Rect miniBounds() {
                return section == Xtream.Section.LIVE && !home ? live.miniBounds() : null;
            }

            @Override
            public void modeChanged(LivePlayer.Mode mode) {
                bar.setVisibility(mode == LivePlayer.Mode.FULL ? View.INVISIBLE : View.VISIBLE);
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

    /** True while the focus is in the bar (a page loading then leaves the focus where it is). */
    boolean inBar() {
        View f = getCurrentFocus();
        return f != null && Root.inside(f, bar);
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
            navItems[NAV_SETTINGS].requestFocus();
            // No account yet: the login screen opens by itself, once.
            if (!loginOffered) {
                loginOffered = true;
                startActivity(new Intent(this, AccountActivity.class));
            }
            return;
        }
        status.setVisibility(View.GONE);
        Options.StartPage open = opening;
        opening = null;
        if (open == Options.StartPage.LIVE || open == Options.StartPage.LAST_CHANNEL) {
            home = false;
            section = Xtream.Section.LIVE;
        }
        if (home) showHome(first);
        else showSection(section, first);
        if (open == Options.StartPage.LAST_CHANNEL) {
            // The channel watched last plays at once, from History, even before the list is read again.
            History h = History.get(this);
            List<Item> last = h == null ? null : h.recentChannels(1);
            if (last != null && !last.isEmpty()) {
                playChannel(last.get(0));
                // Once the list is read, Up and Down zap through it from that channel.
                Library lib = Library.of(Xtream.Section.LIVE);
                lib.load(api, () -> {
                    if (lib.all() != null) player.widen(lib.all(), getString(R.string.group_all_channels));
                });
            }
        }
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
        for (int i = 0; i < sectionItems.length; i++) sectionItems[i].setActivated(!home && i == section.ordinal());
        navItems[NAV_HOME].setActivated(home);
    }

    /** The bar item of the page shown. */
    private View shownItem() {
        return home ? navItems[NAV_HOME] : sectionItems[section.ordinal()];
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
        if (Root.inside(focused, bar)) {
            if (direction == View.FOCUS_DOWN) {
                if (api == null) return focused;
                bar.removeCallbacks(switchSection);
                if (pendingHome && !home) showHome(false);
                else if (pendingSection != null && (home || pendingSection != section)) showSection(pendingSection, false);
                View v = pageTarget();
                return v != null ? v : focused;
            }
            if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                return barStep(focused, direction == View.FOCUS_RIGHT ? 1 : -1);
            }
            return focused;
        }
        View r;
        if (home) r = homePage.route(focused, direction, proposed);
        else if (section == Xtream.Section.LIVE) r = live.route(focused, direction, proposed);
        else r = (section == Xtream.Section.MOVIES ? moviesPage : seriesPage).route(focused, direction, proposed);
        // A page hands Up at its top (null) to the bar, on the section shown; other edges stay where they are.
        if (r == null || Root.inside(r, bar)) {
            return direction == View.FOCUS_UP ? shownItem() : focused;
        }
        return r;
    }

    /**
     * Left and Right in the bar go to the next item, in screen order. Android's own search could jump down into the
     * page from an item far from its neighbour; this keeps the remote on the bar.
     */
    private View barStep(View focused, int step) {
        int at = -1;
        for (int i = 0; i < barOrder.length; i++) if (Root.inside(focused, barOrder[i])) at = i;
        if (at < 0) return focused;
        for (int i = at + step; i >= 0 && i < barOrder.length; i += step) {
            if (barOrder[i].getVisibility() == View.VISIBLE && barOrder[i].isFocusable()) return barOrder[i];
        }
        return focused;
    }

    private void focusMoved(View now) {
        if (now == null) return;
        boolean inBar = Root.inside(now, bar);
        if (!inBar) {
            pageFocus = now;
            if (home) homePage.focusChanged(now);
            else if (section == Xtream.Section.MOVIES) moviesPage.focusChanged(now);
            else if (section == Xtream.Section.SERIES) seriesPage.focusChanged(now);
        }
        showBar(inBar || pageAtTop(now));
    }

    /** True while the focus is at the top of the page shown (not down in the rows of Accueil, Films or Séries). */
    private boolean pageAtTop(View focused) {
        if (home) return homePage.atTop(focused);
        if (section == Xtream.Section.MOVIES) return moviesPage.atTop(focused);
        if (section == Xtream.Section.SERIES) return seriesPage.atTop(focused);
        return true;
    }

    /** The bar fades out while the remote is down in a page's rows, and back in at the top. */
    private void showBar(boolean show) {
        if (show == barShown) return;
        barShown = show;
        bar.animate().cancel();
        float up = -Ui.dp(this, 12);
        if (!Motion.on()) {
            bar.setAlpha(show ? 1f : 0f);
            bar.setTranslationY(show ? 0 : up);
            return;
        }
        bar.animate().alpha(show ? 1f : 0f).translationY(show ? 0 : up).setDuration(show ? Motion.MEDIUM : Motion.SHORT)
                .setInterpolator(show ? Motion.ENTER : Motion.MOVE).start();
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
            // Any key ends the start-up screen, and still does what it does (OK, Back, an arrow are not lost).
            start.leave();
        }
        if (player.key(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event);
        View f = getCurrentFocus();
        if (f != null && !Root.inside(f, bar)) {
            if (home) {
                View start = homePage.defaultFocus();
                if (start != null && f != start) {
                    start.requestFocus();
                    return true;
                }
                navItems[NAV_HOME].requestFocus();
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

    /** Réglages in the bar says when a newer version is waiting; the banner says it once per version and session. */
    private void showUpdate() {
        UpdateState state = new UpdateState(this);
        Release r = state.available();
        boolean newer = r != null && r.version.isNewerThan(UpdateState.installed(this));
        navUpdates.setContentDescription(getString(newer ? R.string.nav_settings_update : R.string.nav_settings));
        navUpdates.findViewById(R.id.nav_dot).setVisibility(newer ? View.VISIBLE : View.GONE);
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
