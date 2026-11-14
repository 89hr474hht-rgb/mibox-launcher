package com.gathin.launchos.tv;

/** One thing the lists show: a live channel, a film, a series or an episode. Immutable; built by the factories below. */
final class Item {
    enum Kind { LIVE, MOVIE, SERIES, EPISODE }

    final Kind kind;
    final String id;
    /** The name as the panel sent it. */
    final String name;
    final String image;
    /** The file extension the panel gives for films and episodes (mp4, mkv…); empty for channels and series. */
    final String ext;
    final int num;
    final String plot;
    final String categoryId;
    /** When the panel added it (seconds since 1970), 0 when unknown. Series: when it last changed. */
    final long added;
    /** Out of 10, 0 when unknown. */
    final float rating;
    final String genre;
    /** A wide picture (series only, from the list), "" when none. */
    final String backdrop;
    /** Length of a film or an episode, 0 when unknown. */
    final int seconds;

    /** The name to show, its quality tag and year (taken from the name, or from the panel's date). */
    final String title;
    final String tag;
    final int year;
    /** For sorting and search: folded title. */
    final String key;

    /** A channel, from what is remembered about it (favourites, hidden channels, history). */
    static Item live(String id, String name, String image, int num, String categoryId) {
        return new Item(Kind.LIVE, id, name, image, "", num, "", categoryId, 0, 0f, "", "", 0, 0);
    }

    /** A film known only by what was passed along (an Intent, Reprendre). */
    static Item movie(String id, String name, String image, String ext, String categoryId) {
        return new Item(Kind.MOVIE, id, name, image, ext, 0, "", categoryId, 0, 0f, "", "", 0, 0);
    }

    /** A series known only by its name and cover; added is when it last changed (or was watched), in seconds. */
    static Item series(String id, String name, String image, long added) {
        return new Item(Kind.SERIES, id, name, image, "", 0, "", "", added, 0f, "", "", 0, 0);
    }

    /** An episode of a season (get_series_info, or passed to the player). */
    static Item episode(String id, String name, String image, String ext, int num, String plot, int seconds) {
        return new Item(Kind.EPISODE, id, name, image, ext, num, plot, "", 0, 0f, "", "", 0, seconds);
    }

    /** An item of a section's list as the panel sends it (Xtream.readItems). */
    static Item listed(Kind kind, String id, String name, String image, String ext, int num, String plot,
                       String categoryId, long added, float rating, String genre, String backdrop, int releaseYear) {
        return new Item(kind, id, name, image, ext, num, plot, categoryId, added, rating, genre, backdrop, releaseYear, 0);
    }

    private Item(Kind kind, String id, String name, String image, String ext, int num, String plot, String categoryId,
                 long added, float rating, String genre, String backdrop, int releaseYear, int seconds) {
        this.kind = kind;
        this.id = id;
        this.name = name;
        this.image = image;
        this.ext = ext;
        this.num = num;
        this.plot = plot;
        this.categoryId = categoryId;
        this.added = added;
        this.rating = rating;
        this.genre = genre;
        this.backdrop = backdrop;
        this.seconds = seconds;
        Names.Clean c = Names.clean(name);
        this.title = c.title;
        this.tag = c.tag;
        this.year = c.year != 0 ? c.year : releaseYear;
        this.key = Names.sortKey(c.title);
    }
}
