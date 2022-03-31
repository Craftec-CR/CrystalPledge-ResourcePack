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
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourcePackBuilder {
    private static final String WARNING = "[WARNING] ";
    private static final String CRYSTAL_PLEDGE_ZIP = "CrystalPledge.zip";
    private static final String VANILLA_TWEAKS = "VanillaTweaks.zip";
    private static final String NEGATIVE_SPACE = "NegativeSpaceFont.zip";
    private static final List<String> mainFiles = List.of("LICENSE.txt",
                                                          "pack.mcmeta",
                                                          "pack.png");

    public synchronized static void main(String[] args) throws IOException {
        if (System.console() == null) {
            // Start new instance from cmd
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "cmd", "/k", "java -jar \""
                    +URLDecoder.decode(ResourcePackBuilder.class.getProtectionDomain().getCodeSource().getLocation().toString().substring("file:/".length()), StandardCharsets.UTF_8)
                    +"\" && pause && exit"});
            return;
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
                System.out.println(WARNING+"Could not find "+VANILLA_TWEAKS);
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
                System.out.println(WARNING+"Could not find "+NEGATIVE_SPACE);
            }

            System.out.println("Copying assets...");
            for (Path path : Files.walk(Path.of("assets")).collect(Collectors.toList())) {
                File file = path.toFile();
                if (file.isDirectory()) { continue; }
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
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
                for (Path path : tempFiles) {
                    File file = path.toFile();
                    if (file.isDirectory()) { continue; }
                    try (InputStream in = new FileInputStream(file)) {
                        out.putNextEntry(new ZipEntry(temp.relativize(path).toString()));
                        out.write(in.readAllBytes());
                    } catch (ZipException e) {
                        System.out.println(WARNING+e.getMessage());
                    } finally { out.closeEntry(); }
                }
            }
        } finally {
            try {
                deleteFile(temp.toFile());
            } catch (IOException e) { System.out.println(WARNING+e.getMessage()); }
        }

        System.out.println("\nSuccessfully built "+CRYSTAL_PLEDGE_ZIP+'!');
    }

    private static void deleteFile(File file) throws IOException {
        if (!file.exists()) { return; }
        if (file.isDirectory()) { for (File subFile : file.listFiles()) { deleteFile(subFile); } }
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
        if (strings[0].equals("assets") && strings[2].equals("font") && file.exists()) {
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
                                System.out.println(WARNING+"Duplicate char "+character+" in font "+path);
                            }
                        }
                    }
                }
                for (JsonElement provider : JsonParser.parseString(new String(in.readAllBytes()).replace("\\", "\\\\")).getAsJsonObject().getAsJsonArray("providers")) {
                    for (JsonElement charLine : provider.getAsJsonObject().getAsJsonArray("chars")) {
                        for (String character : splitCharLine(charLine.getAsString())) {
                            if (!character.equals("\\u0000") && !existingChars.add(character)) {
                                System.out.println(WARNING+"Char "+character+" in font "+path+" already exists");
                            }
                        }
                    }
                    providers.add(provider);
                }
                file.delete();
                String json = new GsonBuilder().setPrettyPrinting().create().toJson(font).replace("\\\\", "\\");
                try (Writer writer = new FileWriter(file)) { writer.write(json); }
            } catch (IllegalStateException | MalformedJsonException e) {
                System.out.println(WARNING+"Invalid font: "+path);
            }
            return;
        }

        makeParent(file);
        if (file.exists()) {
            System.out.println(WARNING+"\""+path+"\" already exists. Overwriting...");
            file.delete();
        }
        try (OutputStream out = new FileOutputStream(file)) { out.write(in.readAllBytes()); }
    }
}
