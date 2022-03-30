package cr.craftec.crystalpledge.rpbuilder;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

;

public class ResourcePackBuilder {
    private static final String WARNING = "[WARNING] ";
    private static final String CRYSTAL_PLEDGE_ZIP = "CrystalPledge.zip";
    private static final String VANILLA_TWEAKS = "VanillaTweaks.zip";

    private static final List<String> mainFiles = List.of("LICENSE",
                                                          "Negative Spaces license.txt",
                                                          "pack.mcmeta",
                                                          "pack.png");

    public synchronized static void main(String[] args) throws IOException {
        if (System.console() == null) {
            // Start new instance from cmd
            String jarPath = args.length > 0 ? args[0] : URLDecoder.decode(
                    ResourcePackBuilder.class.getProtectionDomain().getCodeSource().getLocation().toString().substring("file:/".length()), StandardCharsets.UTF_8);
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "cmd", "/k", "java -jar \""+jarPath+"\" && pause && exit"});
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
                try (InputStream in = new FileInputStream(fileName);
                     FileOutputStream out = new FileOutputStream(temp.resolve(fileName).toFile())) {
                    out.write(in.readAllBytes());
                }
            }

            System.out.println("Copying Vanilla Tweaks...");
            try (ZipFile vanillaTweaks = new ZipFile(VANILLA_TWEAKS)) {
                for (Enumeration<? extends ZipEntry> enumeration = vanillaTweaks.entries(); enumeration.hasMoreElements(); ) {
                    ZipEntry entry = enumeration.nextElement();
                    File tempFile = temp.resolve(entry.getName()).toFile();
                    File parentFile = tempFile.getParentFile();
                    if (!parentFile.exists()) { parentFile.mkdirs(); }
                    try (InputStream in = vanillaTweaks.getInputStream(entry);
                         FileOutputStream out = new FileOutputStream(tempFile)) {
                        out.write(in.readAllBytes());
                    }
                }
            } catch (FileNotFoundException e) {
                System.out.println(WARNING+"Could not find "+VANILLA_TWEAKS);
                e.printStackTrace();
            }

            System.out.println("Copying assets...");
            for (Path path : Files.walk(Path.of("assets")).collect(Collectors.toList())) {
                File file = path.toFile();
                if (file.isDirectory()) { continue; }
                File tempFile = temp.resolve(path).toFile();
                if (tempFile.exists()) {
                    System.out.println(WARNING+"\""+tempFile.getPath()+"\" already exists. Overwriting...");
                    tempFile.delete();
                }
                File parentFile = tempFile.getParentFile();
                if (!parentFile.exists()) { parentFile.mkdirs(); }
                try (InputStream in = new FileInputStream(file);
                     FileOutputStream out = new FileOutputStream(tempFile)) {
                    out.write(in.readAllBytes());
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
}
