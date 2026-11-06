package com.gathin.launchos.tv;

/** One thing the lists show: a live channel, a film, a series or an episode. */
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

    Item(Kind kind, String id, String name, String image, String ext, int num, String plot) {
        this(kind, id, name, image, ext, num, plot, "", 0, 0f, "", "", 0, 0);
    }

    Item(Kind kind, String id, String name, String image, String ext, int num, String plot, String categoryId,
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
