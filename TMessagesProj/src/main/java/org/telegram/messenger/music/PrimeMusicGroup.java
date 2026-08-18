package org.telegram.messenger.music;

/** PrimeGram Music: one row of a grouped library browse list (album/artist/genre/
 *  folder) - see {@code PrimeMusicLibrary#getAlbums/getArtists/getGenres/getFolders}. */
public final class PrimeMusicGroup {

    public static final int TYPE_ALBUM = 0;
    public static final int TYPE_ARTIST = 1;
    public static final int TYPE_GENRE = 2;
    public static final int TYPE_FOLDER = 3;

    public String key;
    public String title;
    public String subtitle;
    public int trackCount;
    public String coverPath;
}
