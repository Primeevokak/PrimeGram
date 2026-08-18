package org.telegram.messenger.music;

/** PrimeGram Music: one row of {@code PrimeMusicDatabase}'s `tracks` table. */
public final class PrimeTrack {

    public static final int SOURCE_TELEGRAM = 0;
    public static final int SOURCE_LOCAL = 1;
    /** Indexed off the device's own {@code MediaStore.Audio.Media} - see
     *  {@code PrimeDeviceMusicScanner}. Distinct from {@code SOURCE_LOCAL} (tracks the
     *  user explicitly downloaded into the player's own library) even though both play
     *  back via a local {@code localPath} - kept separate so a future "remove from
     *  library" action can tell "delete the file" (LOCAL) apart from "just stop
     *  showing it, the file isn't ours" (DEVICE). */
    public static final int SOURCE_DEVICE = 2;

    public long id;
    public int source;
    public long dialogId;
    public int messageId;
    public String localPath;
    public String title;
    public String artist;
    public int duration;
    public String coverPath;
    public boolean liked;
    public String lyrics;
    public long addedAt;
    public String album;
    public String genre;
    public long dateAdded;
    /** "Не нравится" - a taste signal for the wave/recommendation scoring, distinct
     *  from {@link #liked} being simply false. See {@code PrimeMusicLibrary#setDisliked}. */
    public boolean disliked;
}
