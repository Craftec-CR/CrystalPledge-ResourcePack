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
    private static final String CONFIG = "config.json";
    private static final List<String> mainFiles = List.of("LICENSE.txt",
                                                          "pack.mcmeta",
                                                          "pack.png");
    private static JsonObject config;
    private static final Map<WarningType,List<String>> warningSuppressions = new HashMap<>();

    private static final Map<Path,JsonObject> langs = new HashMap<>();
    private static final Map<Path,JsonObject> fonts = new HashMap<>();
    private static final Map<Path,Set<String>> chars = new HashMap<>();
    private static final Map<Path,JsonObject> sounds = new HashMap<>();

    private static void warn(WarningType warningType, String id, String detail) {
        id = id.replace('\\', '/');
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

        File configFile = new File(CONFIG);
        if (!configFile.exists()) {
            // Save default config
            try (InputStream in = ResourcePackBuilder.class.getClassLoader().getResourceAsStream(CONFIG);
                 OutputStream out = new FileOutputStream(configFile)) {
                out.write(in.readAllBytes());
            }
        }
        // Load config
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configFile))) {
            config = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (MalformedJsonException | IllegalStateException ignored) {
            warn(WarningType.INVALID, CONFIG, null);
        }
        // Load warning suppressions
        for (Map.Entry<String,JsonElement> entry : config.getAsJsonObject("warning_suppressions").entrySet()) {
            String key = entry.getKey();
            List<String> suppressions = new LinkedList<>();
            try {
                WarningType warningType = WarningType.valueOf(key.toUpperCase());
                JsonArray array = entry.getValue().getAsJsonArray();
                for (JsonElement jsonElement : array) { suppressions.add(jsonElement.getAsString()); }
                warningSuppressions.put(warningType, suppressions);
            } catch (IllegalArgumentException | IllegalStateException e) {
                warn(WarningType.INVALID, CONFIG, "key \""+key+"\"");
            }
        }

        Path temp = Files.createTempDirectory("CrystalPledgeRP");
        deleteFile(temp.toFile());

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
            for (Map.Entry<Path,JsonObject> entry : langs.entrySet()) {
                Path path = temp.resolve(entry.getKey());
                File langFile = path.toFile();
                makeParent(langFile);
                try (Writer writer = new FileWriter(langFile)) {
                    writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(entry.getValue()));
                }
            }
            for (Map.Entry<Path,JsonObject> entry : fonts.entrySet()) {
                Path path = temp.resolve(entry.getKey());
                File fontFile = path.toFile();
                makeParent(fontFile);
                try (Writer writer = new FileWriter(fontFile)) {
                    writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(entry.getValue()).replace("\\\\", "\\"));
                }
            }
            for (Map.Entry<Path,JsonObject> entry : sounds.entrySet()) {
                Path path = temp.resolve(entry.getKey());
                File soundFile = path.toFile();
                makeParent(soundFile);
                try (Writer writer = new FileWriter(soundFile)) {
                    writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(entry.getValue()));
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
            try { deleteFile(temp.toFile()); } catch (IOException e) {
                warn(WarningType.DELETE, temp.toString(), e.getMessage());
            }
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
        String[] strings = path.toString().split("\\\\");
        File file = destination.resolve(path).toFile();

        // Lang files
        if (path.startsWith("assets\\minecraft\\lang")) {
            try {
                JsonObject sourceLang;
                try (Reader reader = new InputStreamReader(in)) {
                    sourceLang = JsonParser.parseReader(reader).getAsJsonObject();
                }
                JsonArray langs = sourceLang.getAsJsonArray("langs");
                if (langs == null) {
                    JsonObject masterLang = ResourcePackBuilder.langs.get(path);
                    if (masterLang == null) {
                        if (file.exists()) {
                            try (Reader existingReader = new InputStreamReader(new FileInputStream(file))) {
                                masterLang = JsonParser.parseReader(existingReader).getAsJsonObject();
                            } catch (IllegalStateException | MalformedJsonException e) {
                                warn(WarningType.INVALID, file.toString(), null);
                                return;
                            }
                            file.delete();
                        } else {
                            masterLang = new JsonObject();
                        }
                        ResourcePackBuilder.langs.put(path, masterLang);
                    }
                    for (Map.Entry<String,JsonElement> entry : sourceLang.entrySet()) {
                        String key = entry.getKey();
                        if (masterLang.has(key)) { warn(WarningType.LANG, path.toString()+':'+key, null); }
                        masterLang.add(key, entry.getValue());
                    }
                } else {
                    for (JsonElement childLangKey : langs) {
                        Path childPath = Path.of("assets/minecraft/lang/"+childLangKey.getAsString()+".json");
                        File childFile = destination.resolve(childPath).toFile();
                        JsonObject masterLang = ResourcePackBuilder.langs.get(childPath);
                        if (masterLang == null) {
                            if (childFile.exists()) {
                                try (Reader existingReader = new InputStreamReader(new FileInputStream(childFile))) {
                                    masterLang = JsonParser.parseReader(existingReader).getAsJsonObject();
                                } catch (IllegalStateException | MalformedJsonException e) {
                                    warn(WarningType.INVALID, childFile.toString(), null);
                                    return;
                                }
                                childFile.delete();
                            } else {
                                masterLang = new JsonObject();
                            }
                            ResourcePackBuilder.langs.put(childPath, masterLang);
                        }
                        for (Map.Entry<String,JsonElement> entry : sourceLang.entrySet()) {
                            String key = entry.getKey();
                            if (key.equals("langs")) { continue; }
                            if (masterLang.has(key)) { warn(WarningType.LANG, childPath.toString()+':'+key, null); }
                            masterLang.add(key, entry.getValue());
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
                    JsonArray masterProviders;
                    {
                        JsonObject masterFont = fonts.get(path);
                        if (masterFont == null) {
                            if (file.exists()) {
                                try (InputStream inputStream = new FileInputStream(file)) {
                                    masterFont = JsonParser.parseString(new String(inputStream.readAllBytes()).replace("\\", "\\\\")).getAsJsonObject();
                                } catch (IllegalStateException | MalformedJsonException e) {
                                    warn(WarningType.INVALID, file.toString(), null);
                                    return;
                                }
                                file.delete();
                            } else {
                                masterFont = new JsonObject();
                            }
                            fonts.put(path, masterFont);
                        }
                        masterProviders = masterFont.getAsJsonArray("providers");
                        if (masterProviders == null) { masterFont.add("providers", masterProviders = new JsonArray()); }
                    }
                    Set<String> existingChars = chars.computeIfAbsent(path, key -> new HashSet<>());
                    for (JsonElement provider : JsonParser.parseString(new String(in.readAllBytes()).replace("\\", "\\\\")).getAsJsonObject().getAsJsonArray("providers")) {
                        for (JsonElement charLine : provider.getAsJsonObject().getAsJsonArray("chars")) {
                            for (String character : splitCharLine(charLine.getAsString())) {
                                if (!character.equals("\\u0000") && !existingChars.add(character)) {
                                    warn(WarningType.CHAR, file.toString()+':'+character, null);
                                }
                            }
                        }
                        masterProviders.add(provider);
                    }
                } catch (IllegalStateException | MalformedJsonException e) {
                    warn(WarningType.INVALID, path.toString(), null);
                }
                return;
            } else if (strings[0].equals("assets") && strings[2].equals("sounds.json")) {
                // Merge sounds.json
                try {
                    JsonObject masterSounds = sounds.get(path);
                    if (masterSounds == null) {
                        if (file.exists()) {
                            try (Reader existingReader = new InputStreamReader(new FileInputStream(file))) {
                                masterSounds = JsonParser.parseReader(existingReader).getAsJsonObject();
                            } catch (IllegalStateException | MalformedJsonException e) {
                                warn(WarningType.INVALID, file.toString(), null);
                                return;
                            }
                            file.delete();
                        } else {
                            masterSounds = new JsonObject();
                        }
                        sounds.put(path, masterSounds);
                    }
                    JsonObject newSounds;
                    try (Reader reader = new InputStreamReader(in)) {
                        newSounds = JsonParser.parseReader(reader).getAsJsonObject();
                    }
                    for (Map.Entry<String,JsonElement> soundEntry : newSounds.entrySet()) {
                        String soundId = soundEntry.getKey();
                        if (masterSounds.has(soundId)) {
                            warn(WarningType.SOUND, path.toString(), null);
                        }
                        masterSounds.add(soundId, soundEntry.getValue());
                    }
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
