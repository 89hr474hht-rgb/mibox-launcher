package com.gathin.launchos.tv;

/** One thing the lists show: a live channel, a film, a series or an episode. */
final class Item {
    enum Kind { LIVE, MOVIE, SERIES, EPISODE }

    final Kind kind;
    final String id;
    final String name;
    final String image;
    /** The file extension the panel gives for films and episodes (mp4, mkv…); empty for channels and series. */
    final String ext;
    final int num;
    final String plot;

    Item(Kind kind, String id, String name, String image, String ext, int num, String plot) {
        this.kind = kind;
        this.id = id;
        this.name = name;
        this.image = image;
        this.ext = ext;
        this.num = num;
        this.plot = plot;
    }
}
