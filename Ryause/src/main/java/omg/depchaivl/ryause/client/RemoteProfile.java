package omg.depchaivl.ryause.client;

/**
 * One entry from the remote {@code index.json}. Purely a catalogue entry; the actual
 * profile file is only written to disk once it is installed.
 */
public class RemoteProfile {
    /** File name inside {@code profiles/} without the {@code .json} extension. */
    public final String file;
    public final String title;
    public final String author;
    public final String description;
    public final String mc;
    public String sha1;

    public ProfileRepo.Install install = ProfileRepo.Install.NOT_INSTALLED;
    public boolean downloading;

    public RemoteProfile(String file, String title, String author, String description, String mc, String sha1) {
        this.file = file;
        this.title = title == null ? "" : title;
        this.author = author == null ? "" : author;
        this.description = description == null ? "" : description;
        this.mc = mc == null ? "" : mc;
        this.sha1 = sha1 == null ? "" : sha1.trim().toLowerCase();
    }

    public String displayTitle() {
        return this.title.isEmpty() ? this.file : this.title;
    }

    /** Local file name used when this profile is installed. */
    public String fileName() {
        return this.file + Profiles.EXT;
    }
}
