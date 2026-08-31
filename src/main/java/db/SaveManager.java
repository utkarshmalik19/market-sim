package db;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Lists, resolves, and deletes save-game files. Each save is its own SQLite file
 *  living in a "saves" folder next to the running jar. */
public class SaveManager {

    public static File savesDir() {
        try {
            File src = new File(SaveManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File dir = src.isFile() ? src.getParentFile() : src;
            File saves = new File(dir, "saves");
            saves.mkdirs();
            return saves;
        } catch (URISyntaxException e) {
            File saves = new File("saves");
            saves.mkdirs();
            return saves;
        }
    }

    /** Save names (without the .db extension), most recently played first. */
    public static List<String> listSaves() {
        File[] files = savesDir().listFiles((d, name) -> name.toLowerCase().endsWith(".db"));
        List<File> fileList = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
        fileList.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        List<String> names = new ArrayList<>();
        for (File f : fileList) {
            String n = f.getName();
            names.add(n.substring(0, n.length() - 3)); // strip ".db"
        }
        return names;
    }

    public static boolean exists(String saveName) {
        return fileFor(saveName).exists();
    }

    public static File fileFor(String saveName) {
        return new File(savesDir(), saveName + ".db");
    }

    public static boolean delete(String saveName) {
        return fileFor(saveName).delete();
    }

    /** Turns a user-typed save name into a safe filename stem. */
    public static String sanitize(String rawName) {
        String cleaned = rawName == null ? "" : rawName.trim().replaceAll("[^a-zA-Z0-9 _-]", "");
        return cleaned.isEmpty() ? "Save" : cleaned;
    }
}