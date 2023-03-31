import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class VersionControlSystem {
    private final Path currentDirectory;
    private final Path vcsDirectory;
    private final File head;
    private final File index;
    private final Path objects;
    private final File temp;
    private final String separator;
    private Map<String, String> indexMap;
    private static final String[] subDirectories = {"Objects"};
    private static final String[] files = {"HEAD", "Index", "tempFile"};
    public VersionControlSystem(String currentDirectory) {
        this.currentDirectory = Paths.get(currentDirectory);
        this.separator = System.getProperty("file.separator");
        this.vcsDirectory = pathBuilder(new String[]{".vcs"}, this.currentDirectory);
        this.head = pathBuilder(new String[]{"HEAD"}, vcsDirectory).toFile();
        this.index = pathBuilder(new String[]{"Index"}, vcsDirectory).toFile();
        this.objects = pathBuilder(new String[]{"Objects"}, vcsDirectory);
        this.temp = pathBuilder(new String[]{"tempFile"}, vcsDirectory).toFile();
    }
    public VersionControlSystem(String currentDirectory, String vcsDirectory, String head, String index, String objects, String tempFile) {
        this.currentDirectory = Paths.get(currentDirectory);
        this.vcsDirectory = Paths.get(vcsDirectory);
        this.head = new File(head);
        this.index = new File(index);
        this.objects = Paths.get(objects);
        this.separator = System.getProperty("file.separator");
        this.temp = new File(tempFile);
    }
    /**
     * The init command, creates a .vcs folder and subfolders to initialize version control system.
     * If a .vcs folder already exists, do nothing
     */
    public static VersionControlSystem init(String dir) {
        Path start = Paths.get(dir);
        File vcs = pathBuilder(new String[] {".vcs"}, start).toFile();
        if (!Files.exists(start)) {
            System.out.println("Directory doesn't exist");
        } else if (vcs.exists()) {
            System.out.println("Version Control System already exists");
        } else {
            if (vcs.mkdir()) {
                Map<String, String> sub = new HashMap<>();
                Path path = vcs.toPath();
                try {
                    Files.setAttribute(path, "dos:hidden", true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                for (String subDirectory : subDirectories) {
                    File subfolder = new File(path.toFile(), subDirectory);
                    if (!subfolder.mkdir()) {
                        vcs.delete();
                        System.out.println("Failed to create " + pathBuilder(new String[] {subDirectory}, path));
                        return null;
                    } else {
                        sub.put(subDirectory, subfolder.toPath().toAbsolutePath().toString());
                    }
                }
                for (String f : files) {
                    File file = new File(path.toFile(), f);
                    try {
                        if (!file.createNewFile()) {
                            vcs.delete();
                            System.out.println("Failed to create " + pathBuilder(new String[] {f}, path));
                            return null;
                        } else {
                            sub.put(f, file.toPath().toAbsolutePath().toString());
                        }
                    } catch (Exception e) {
                        vcs.delete();
                        System.out.println("Failed to create " + pathBuilder(new String[] {f}, path));
                        System.out.println(e.getMessage());
                        return null;
                    }
                }
                return new VersionControlSystem(dir, path.toString(), sub.get("HEAD"), sub.get("Index"), sub.get("Objects"), sub.get("tempFile"));
            } else {
                System.out.println("Unable to create " + vcs.toPath());
            }
        }
        return null;
    }

    /**
     * Stages the changes made to a file and updates the index file accordingly
     * @param path: the path to the file to be added
     */
    public void add(String path) {
        try {
            readIndex();
            File file = new File(path);
            String name = this.currentDirectory.relativize(file.toPath()).toString();
            String hash = hash(path);
            String lastHash = lastCommitHash(path);
            if (!file.exists()) {
                if (lastHash != null) {
                    this.indexMap.put(name, String.format("%s %d", hash, 2));
                } else {
                    System.out.println(name + " does not exist");
                    return;
                }
            } else {
                if (lastHash == null) {
                    this.indexMap.put(name, String.format("%s %d", hash, 1));
                } else if (lastHash.equals(hash)) {
                    this.indexMap.remove(name);
                } else {
                    this.indexMap.put(name, String.format("%s %d", hash, 0));
                }
                createFile(path, hash);
            }
            StringBuilder sb = new StringBuilder();
            for (String key : this.indexMap.keySet()) {
                sb.append(String.format("%s %s\n", key, indexMap.get(key)));
            }
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(index))) {
                bw.write(sb.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private File lastCommit() {
        try {
            List<String> path = Files.readAllLines(Paths.get(head.getAbsolutePath()));
            if (path.size() == 0) {
                return null;
            }
            return objects.resolve(path.get(0).substring(0, 2)).resolve(path.get(0).substring(2)).toFile();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    private File lastTree() {
        try {
            File commit = lastCommit();
            if (commit == null) {
                return null;
            }
            String path = Files.readAllLines(Paths.get(commit.getAbsolutePath())).get(0).substring(5);
            return objects.resolve(path.substring(0, 2)).resolve(path.substring(2)).toFile();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * Reads the contents of Index file and converts it into a hashmap
     * key: the relative path to the file
     * value: [hash] [state]
     */
    private void readIndex() {
        if (this.indexMap != null) {
            return;
        }
        try {
            this.indexMap = new HashMap<>();
            BufferedReader br = new BufferedReader(new FileReader(index));
            String line;
            while ((line = br.readLine()) != null) {
                this.indexMap.put(line.substring(0, line.length()-43), line.substring(line.length()-43));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * Returns the hash of the file at the last commit
     * @param path: path to the file
     * @return string of the hash of the file in the last commit, or null if the file isn't in the last commit
     */
    private String lastCommitHash(String path) {
        return null;
    }
    public String makeTree() {
        try {
            readIndex();
            StringBuilder sb = new StringBuilder();
            File tree = lastTree();
            if (tree == null) {
                for (String path : this.indexMap.keySet()) {
                    sb.append(String.format("%s %s\n", path, this.indexMap.get(path).substring(0, this.indexMap.get(path).length()-2)));
                }
            } else {
                BufferedReader br = new BufferedReader(new FileReader(tree));
                String line;
                String path;
                while ((line = br.readLine()) != null) {
                    path = line.split(" ")[0];
                    if (this.indexMap.containsKey(path)) {
                        if (!this.indexMap.get(path).substring(this.indexMap.get(path).length() - 1).equals("2")) {
                            sb.append(line).append("\n");
                        }
                    } else {
                        sb.append(line).append("\n");
                    }
                }
            }
            FileWriter fw = new FileWriter(temp, false);
            fw.write(sb.toString());
            fw.close();
            String hash = hash(temp.getAbsolutePath());
            createFile(temp.getAbsolutePath(), hash);
            return hash;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * Returns a path to a file or directory given how to get there from currentDirectory
     * @param paths: how to get to the desired destination, in the form of an array of strings
     * @return the desired path
     */
    private static Path pathBuilder(String[] paths, Path start) {
        Path output = start;
        for (String path : paths) {
            output = output.resolve(path);
        }
        return output;
    }
    /**
     * Returns the SHA-1 hash for a file
     * @param path: path to the file
     * @return returns the string hash for the file
     */
    public String hash(String path) {
        try (FileInputStream fis = new FileInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dataBytes = new byte[1024];
            int bytesRead;

            while ((bytesRead = fis.read(dataBytes)) != -1) {
                md.update(dataBytes, 0, bytesRead);
            }
            byte[] hashBytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte hashByte : hashBytes) {
                sb.append(Integer.toString((hashByte & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch (Exception e) {
            System.out.println("Hash failed for " + path + "due to:\n" + e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a file with the hash as the name is already saved
     * @param hash: the hash
     * @return boolean if the file is already saved.
     */
    public boolean hashExists(String hash) {
        return Files.exists(pathBuilder(new String[] {subDirectories[0], hash.substring(0, 2), hash.substring(2)}, vcsDirectory));
    }

    /**
     * Creates the bin of the first two characters of the hash if that doesn't exist, and copies the file to the bin
     * @param path: path to the file to be copied
     * @param hash: the hashcode of the file, for naming and bin assignment purposes
     * @return boolean whether the creation was successful or not
     */
    public boolean createFile(String path, String hash) {
        if (hashExists(hash)) {
            return true;
        } else {
            Path source = Paths.get(path);
            Path target = pathBuilder(new String[] {subDirectories[0], hash.substring(0, 2), hash.substring(2)}, vcsDirectory);
            File bin = pathBuilder(new String[] {subDirectories[0], hash.substring(0, 2)}, vcsDirectory).toFile();
            if (!bin.exists() && !bin.mkdir()) {
                return false;
            }
            try {
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}

