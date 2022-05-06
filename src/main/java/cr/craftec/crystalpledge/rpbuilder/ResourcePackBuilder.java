package cr.craftec.crystalpledge.rpbuilder;

import com.google.gson.*;
import com.google.gson.stream.MalformedJsonException;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourcePackBuilder {
    private static final String CRYSTAL_PLEDGE_ZIP = "CrystalPledge.zip";
    private static final String VANILLA_TWEAKS = "VanillaTweaks.zip";
    private static final String NEGATIVE_SPACE = "NegativeSpaceFont.zip";
    private static final String WARNING_SUPRESSIONS = "warning_suppressions.json";
    private static final List<String> mainFiles = List.of("LICENSE.txt",
                                                          "pack.mcmeta",
                                                          "pack.png");
    private static final Map<WarningType,List<String>> warningSuppressions = new HashMap<>();

    private static void warn(WarningType warningType, String id, String detail) {
        id = id.replace('\\','/');
        List<String> supressions = warningSuppressions.get(warningType);
        if (supressions != null) {
            for (String suppression : supressions) {
                if (suppression.equals(id)) { return; }
            }
        }
        System.out.println("[WARNING] "+warningType.getMessage()+id+((detail == null ? "" : ": "+detail)));
    }

    public synchronized static void main(String[] args) throws IOException {
        if (System.console() == null) {
            // Start new instance from cmd
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "cmd", "/k", "java -jar \""
                    +URLDecoder.decode(ResourcePackBuilder.class.getProtectionDomain().getCodeSource().getLocation().toString().substring("file:/".length()), StandardCharsets.UTF_8)
                    +"\" && pause && exit"});
            return;
        }

        // Parse warning supressions
        File supressionsFile = new File(WARNING_SUPRESSIONS);
        if (supressionsFile.exists()) {
            JsonObject supressionsObject = null;
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(supressionsFile))) {
                supressionsObject = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (MalformedJsonException | IllegalStateException ignored) {
                warn(WarningType.INVALID, WARNING_SUPRESSIONS, null);
            }
            if (supressionsObject != null) {
                for (Map.Entry<String,JsonElement> entry : supressionsObject.entrySet()) {
                    String key = entry.getKey();
                    List<String> suppressions = new LinkedList<>();
                    try {
                        WarningType warningType = WarningType.valueOf(key.toUpperCase());
                        JsonArray array = entry.getValue().getAsJsonArray();
                        for (JsonElement jsonElement : array) { suppressions.add(jsonElement.getAsString()); }
                        warningSuppressions.put(warningType, suppressions);
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        warn(WarningType.INVALID, WARNING_SUPRESSIONS, "key \""+key+"\"");
                    }
                }
            }

        }

        deleteFile(new File("temp"));
        Path temp = Path.of("temp");
        temp.toFile().mkdir();
        Runtime.getRuntime().exec("attrib +H temp");

        try {
            File zip = new File(CRYSTAL_PLEDGE_ZIP);
            if (zip.exists() && !zip.delete()) { throw new IOException("Failed to delete old "+CRYSTAL_PLEDGE_ZIP); }

            System.out.println("Copying main resource pack files...");
            for (String fileName : mainFiles) {
                try (InputStream in = new FileInputStream(fileName)) { copy(temp, Path.of(fileName), in); }
            }

            System.out.println("Copying Vanilla Tweaks...");
            try (ZipFile vanillaTweaks = new ZipFile(VANILLA_TWEAKS)) {
                for (Enumeration<? extends ZipEntry> enumeration = vanillaTweaks.entries(); enumeration.hasMoreElements(); ) {
                    ZipEntry entry = enumeration.nextElement();
                    String entryName = entry.getName();
                    if (entry.isDirectory() || mainFiles.contains(entryName)) { continue; }
                    try (InputStream in = vanillaTweaks.getInputStream(entry)) {
                        copy(temp, Path.of(entryName), in);
                    }
                }
            } catch (FileNotFoundException e) {
                warn(WarningType.MISSING, VANILLA_TWEAKS, null);
            }

            System.out.println("Copying Negative Space Font...");
            try (ZipFile negativeSpaceFont = new ZipFile(NEGATIVE_SPACE)) {
                for (Enumeration<? extends ZipEntry> enumeration = negativeSpaceFont.entries(); enumeration.hasMoreElements(); ) {
                    ZipEntry entry = enumeration.nextElement();
                    if (entry.isDirectory()) { continue; }
                    String entryName = entry.getName();
                    if (entryName.startsWith("assets/space/textures") || entryName.equals("assets/minecraft/font/default.json")) {
                        try (InputStream in = negativeSpaceFont.getInputStream(entry)) {
                            copy(temp, Path.of(entryName), in);
                        }
                    } else if (entryName.equals("LICENSE.txt")) {
                        try (InputStream in = negativeSpaceFont.getInputStream(entry)) {
                            copy(temp, Path.of("NegativeSpaceFont_LICENSE.txt"), in);
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                warn(WarningType.MISSING, NEGATIVE_SPACE, null);
            }

            System.out.println("Copying assets...");
            for (Path path : Files.walk(Path.of("assets")).collect(Collectors.toList())) {
                File file = path.toFile();
                if (file.isDirectory() || path.endsWith(".bbmodel")) { continue; }
                try (InputStream in = new FileInputStream(file)) {
                    copy(temp, path, in);
                }
            }

            List<Path> tempFiles = Files.walk(temp).collect(Collectors.toList());

            // Copy to .minecraft
            File minecraftFolder = new File(System.getenv("APPDATA")+"/.minecraft");
            if (minecraftFolder.exists()) {
                System.out.println("Found .minecraft folder. Copying files to resource pack folder...");
                Path rpPath = minecraftFolder.toPath().resolve("resourcepacks/CrystalPledge");
                deleteFile(rpPath.toFile());
                for (Path path : tempFiles) { Files.copy(path, rpPath.resolve(temp.relativize(path))); }
            }

            System.out.println("Zipping files...");
            try (OutputStream fileOut = new FileOutputStream(zip);
                 ZipOutputStream out = new ZipOutputStream(fileOut)) {
                for (Path path : tempFiles) {
                    File file = path.toFile();
                    if (file.isDirectory()) { continue; }
                    out.putNextEntry(new ZipEntry(temp.relativize(path).toString().replace('\\', '/')));
                    try (InputStream in = new FileInputStream(file)) { out.write(in.readAllBytes()); }
                    out.closeEntry();
                }
            }
        } finally {
            try { deleteFile(temp.toFile()); } catch (IOException e) { warn(WarningType.DELETE, temp.toString(), e.getMessage()); }
        }

        System.out.println("\nSuccessfully built "+CRYSTAL_PLEDGE_ZIP+'!');
    }

    private static void deleteFile(File file) throws IOException {
        if (!file.exists()) { return; }
        if (file.isDirectory()) {
            File[] contents = file.listFiles();
            if (contents != null) { for (File subFile : contents) { deleteFile(subFile); } }
        }
        if (!file.delete()) { throw new IOException("Failed to delete temp files"); }
    }

    private static void makeParent(File file) {
        File parentFile = file.getParentFile();
        if (!parentFile.exists()) { parentFile.mkdirs(); }
    }

    private static List<String> splitCharLine(String charLine) {
        List<String> result = new LinkedList<>();
        int i = 0;
        while (i < charLine.length()) {
            if (charLine.charAt(i) == '\\') {
                result.add(charLine.substring(i, i+6));
                i += 6;
            } else {
                result.add(charLine.substring(i, i+1));
                i += 1;
            }
        }
        return result;
    }

    private static void copy(Path destination, Path path, InputStream in) throws IOException {
        File file = destination.resolve(path).toFile();
        String[] strings = path.toString().split("\\\\");

        // Lang files
        if (path.startsWith("assets\\minecraft\\lang")) {
            try {
                JsonObject lang;
                try (Reader reader = new InputStreamReader(in)) {
                    lang = JsonParser.parseReader(reader).getAsJsonObject();
                }
                JsonArray langs = lang.getAsJsonArray("langs");
                if (langs == null) {
                    JsonObject existingLang;
                    if (file.exists()) {
                        try (Reader existingReader = new InputStreamReader(new FileInputStream(file))) {
                            existingLang = JsonParser.parseReader(existingReader).getAsJsonObject();
                        } catch (IllegalStateException | MalformedJsonException e) {
                            warn(WarningType.INVALID, file.toString(), null);
                            return;
                        }
                        file.delete();
                    } else {
                        existingLang = new JsonObject();
                    }
                    for (Map.Entry<String,JsonElement> entry : lang.entrySet()) {
                        String key = entry.getKey();
                        if (existingLang.has(key)) {
                            warn(WarningType.LANG, path.toString()+':'+key, null);
                        }
                        existingLang.add(key, entry.getValue());
                    }
                    makeParent(file);
                    try (Writer writer = new FileWriter(file)) {
                        writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(existingLang));
                    }
                } else {
                    for (JsonElement childLangKey : langs) {
                        File childLangFile = destination.resolve("assets/minecraft/lang/"+childLangKey.getAsString()+".json").toFile();
                        JsonObject childLang;
                        if (childLangFile.exists()) {
                            try (Reader childReader = new InputStreamReader(new FileInputStream(childLangFile))) {
                                childLang = JsonParser.parseReader(childReader).getAsJsonObject();
                            } catch (IllegalStateException | MalformedJsonException e) {
                                warn(WarningType.INVALID, file.toString(), null);
                                continue;
                            }
                        } else { childLang = new JsonObject(); }
                        for (Map.Entry<String,JsonElement> entry : lang.entrySet()) {
                            String key = entry.getKey();
                            if (key.equals("langs")) { continue; }
                            if (childLang.has(key)) {
                                warn(WarningType.LANG, childLangFile.toString()+':'+key, null);
                            }
                            childLang.add(key, entry.getValue());
                        }
                        makeParent(childLangFile);
                        try (Writer writer = new FileWriter(childLangFile)) {
                            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(childLang));
                        }
                    }
                }
            } catch (IllegalStateException | MalformedJsonException e) {
                warn(WarningType.INVALID, path.toString(), null);
            }
            return;
        }

        if (file.exists()) {
            // Attempt merging
            if (strings[0].equals("assets") && strings[2].equals("font")) {
                // Merge fonts
                try {
                    JsonObject font;
                    try (InputStream existingIn = new FileInputStream(file)) {
                        String json = new String(existingIn.readAllBytes()).replace("\\", "\\\\");
                        font = JsonParser.parseString(json).getAsJsonObject();
                    }
                    JsonArray providers = font.getAsJsonArray("providers");
                    Set<String> existingChars = new HashSet<>();
                    for (JsonElement provider : providers) {
                        for (JsonElement charLine : provider.getAsJsonObject().getAsJsonArray("chars")) {
                            for (String character : splitCharLine(charLine.getAsString())) {
                                if (!character.equals("\\u0000") && !existingChars.add(character)) {
                                    warn(WarningType.CHAR, file.toString()+':'+character, null);
                                }
                            }
                        }
                    }
                    for (JsonElement provider : JsonParser.parseString(new String(in.readAllBytes()).replace("\\", "\\\\")).getAsJsonObject().getAsJsonArray("providers")) {
                        for (JsonElement charLine : provider.getAsJsonObject().getAsJsonArray("chars")) {
                            for (String character : splitCharLine(charLine.getAsString())) {
                                if (!character.equals("\\u0000") && !existingChars.add(character)) {
                                    warn(WarningType.CHAR, file.toString()+':'+character, null);
                                }
                            }
                        }
                        providers.add(provider);
                    }
                    file.delete();
                    try (Writer writer = new FileWriter(file)) {
                        writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(font).replace("\\\\", "\\"));
                    }
                } catch (IllegalStateException | MalformedJsonException e) {
                    warn(WarningType.INVALID, path.toString(), null);
                }
                return;
            } else if (strings[0].equals("assets") && strings[2].equals("sounds.json")) {
                // Merge sounds.json
                try {
                    JsonObject sounds;
                    try (Reader reader = new InputStreamReader(new FileInputStream(file))) {
                        sounds = JsonParser.parseReader(reader).getAsJsonObject();
                    }
                    JsonObject newSounds;
                    try (Reader reader = new InputStreamReader(in)) {
                        newSounds = JsonParser.parseReader(reader).getAsJsonObject();
                    }
                    for (Map.Entry<String,JsonElement> soundEntry : newSounds.entrySet()) {
                        String soundId = soundEntry.getKey();
                        if (sounds.has(soundId)) {
                            warn(WarningType.SOUND, path.toString(), null);
                        }
                        sounds.add(soundId, soundEntry.getValue());
                    }
                    file.delete();
                    String json = new GsonBuilder().setPrettyPrinting().create().toJson(sounds);
                    try (Writer writer = new FileWriter(file)) { writer.write(json); }
                } catch (IllegalStateException | MalformedJsonException e) {
                    warn(WarningType.INVALID, path.toString(), null);
                }
                return;
            } else {
                warn(WarningType.FILE, path.toString(), null);
                file.delete();
            }
        }
        makeParent(file);
        try (OutputStream out = new FileOutputStream(file)) { out.write(in.readAllBytes()); }
    }
}
