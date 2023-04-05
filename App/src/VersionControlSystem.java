import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;

public class VersionControlSystem extends VCSUtils {
    private final Path currentDirectory;
    private final Path vcsDirectory;
    private final File head;
    private final File index;
    private Commit lastCommit;
    private final File AllCommits;
    private final Path objects;
    private final Path branches;
    private String branch;
    private Map<String, String> indexMap;
    private static final String[] SUBDIRECTORIES = {"Objects", "Branches"};
    private static final String[] FILES = {"HEAD", "Index", "AllCommits"};
    private static final int LENGTHOFHASHANDSTATUS = 43;
    private final Map<String, Commit> commitCache;
    public VersionControlSystem(String currentDirectory) {
        this.currentDirectory = Paths.get(currentDirectory);
        this.vcsDirectory = this.currentDirectory.resolve(".vcs");
        this.head = this.vcsDirectory.resolve("HEAD").toFile();
        this.index = this.vcsDirectory.resolve("Index").toFile();
        this.objects = this.vcsDirectory.resolve("Objects");
        this.branches = this.vcsDirectory.resolve("Branches");
        this.AllCommits = this.vcsDirectory.resolve("AllCommits").toFile();
        commitCache = new HashMap<>();
        this.lastCommit = Commit.getHeadCommit(this.vcsDirectory);
        if (lastCommit != null) {
            commitCache.put(lastCommit.hash, lastCommit);
            this.branch = lastCommit.branch;
        } else {
            this.branch = Objects.requireNonNull(getHeadPath()).getName();
        }
    }
    public VersionControlSystem(String currentDirectory, String vcsDirectory, String head, String index,
                                String objects, String AllCommits) {
        this.currentDirectory = Paths.get(currentDirectory);
        this.vcsDirectory = Paths.get(vcsDirectory);
        this.head = new File(head);
        this.index = new File(index);
        this.objects = Paths.get(objects);
        this.branches = this.vcsDirectory.resolve("Branches");
        this.AllCommits = new File(AllCommits);
        commitCache = new HashMap<>();
        this.lastCommit = Commit.getHeadCommit(this.vcsDirectory);
        if (lastCommit != null) {
            commitCache.put(lastCommit.hash, lastCommit);
            this.branch = lastCommit.branch;
        } else {
            this.branch = Objects.requireNonNull(getHeadPath()).getName();
        }
    }

    /**
     * The init command, creates a .vcs folder and subfolders to initialize version control system.
     * If a .vcs folder already exists, do nothing
     */
    public static VersionControlSystem init(String dir) {
        Path start = Paths.get(dir);
        File vcs = start.resolve(".vcs").toFile();
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
                for (String subDirectory : SUBDIRECTORIES) {
                    File subfolder = new File(path.toFile(), subDirectory);
                    if (!subfolder.mkdir()) {
                        vcs.delete();
                        System.out.println("Failed to create " + path.resolve(subDirectory));
                        return null;
                    } else {
                        sub.put(subDirectory, subfolder.toPath().toAbsolutePath().toString());
                    }
                }
                for (String f : FILES) {
                    File file = new File(path.toFile(), f);
                    try {
                        if (!file.createNewFile()) {
                            vcs.delete();
                            System.out.println("Failed to create " + path.resolve(f));
                            return null;
                        } else {
                            sub.put(f, file.toPath().toAbsolutePath().toString());
                        }
                    } catch (Exception e) {
                        vcs.delete();
                        System.out.println("Failed to create " + path.resolve(f));
                        System.out.println(e.getMessage());
                        return null;
                    }
                }
                File file = new File(path.resolve("Branches").toFile(), "master");
                try {
                    if (!file.createNewFile()) {
                        System.out.println("Failed to create " + file.getAbsolutePath());
                    } else {
                        FileWriter writer = new FileWriter(sub.get("HEAD"));
                        writer.write(path.resolve("Branches").resolve("master").toString());
                        writer.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return new VersionControlSystem(dir, path.toString(), sub.get("HEAD"),
                        sub.get("Index"), sub.get("Objects"), sub.get("AllCommits"));
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
            String hash = hash(file);
            String lastHash = null;
            if (lastCommit != null) {
                lastHash = lastCommit.getTree().map.getOrDefault(name, null);
            }
            if (!file.exists()) {
                if (lastHash != null) {
                    this.indexMap.put(name, "________________________________________ 2");
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
                createFile(file, hash, vcsDirectory);
            }
            StringBuilder sb = new StringBuilder();
            for (String key : this.indexMap.keySet()) {
                sb.append(String.format("%s %s\n", key, indexMap.get(key)));
            }
            FileWriter writer = new FileWriter(index);
            writer.write(sb.toString());
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a commit, which includes 5 lines:
     *  [tree]
     *  [previous commit]
     *  [time]
     *  [author]
     *  [message]
     * @param message: string, message for the commit
     * @param user: string, the author of the commit
     */
    public void commit(String message, String user) {
        if (this.indexMap == null || this.indexMap.keySet().size() == 0) {
            System.out.println("No changes added to the commit");
            return;
        } else if (message.length() == 0) {
            System.out.println("Please enter a commit message.");
            return;
        }
        try {
            readIndex();
            this.lastCommit = Commit.writeCommit(user, message, vcsDirectory, lastCommit, indexMap, this.branch);
            commitCache.put(lastCommit.hash, lastCommit);
            FileWriter fw = new FileWriter(Objects.requireNonNull(getHeadPath()), false);
            fw.write(lastCommit.hash);
            fw.close();
            this.indexMap = null;
            fw = new FileWriter(this.index, false);
            fw.write("");
            fw.close();
            fw = new FileWriter(this.AllCommits, true);
            fw.write(lastCommit.hash + "\n");
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Unstages the file if the file is untracked, otherwise mark the file to be removed,
     * and delete the file if it hasn't been deleted already.
     * @param path: path to the file
     */
    public void remove(String path) {
        try {
            readIndex();
            File file = new File(path);
            String name = this.currentDirectory.relativize(file.toPath()).toString();
            if (lastCommit == null || lastCommit.getTree().map.getOrDefault(name, null) == null) {
                if (this.indexMap.containsKey(name)) {
                    this.indexMap.remove(name);
                } else {
                    System.out.println("No reason to remove the file");
                }
            } else {
                this.indexMap.put(name, String.format("________________________________________ %d", 2));
                if (file.exists()) {
                    file.delete();
                }
            }
            StringBuilder sb = new StringBuilder();
            for (String key : this.indexMap.keySet()) {
                sb.append(String.format("%s %s\n", key, indexMap.get(key)));
            }
            FileWriter writer = new FileWriter(index);
            writer.write(sb.toString());
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * returns the current commit and all its ancestors
     * ===
     * commit [hash]
     * Date: [MM/DD/YYYY] [hh/dd/ss]
     * Author: [author]
     * [message]
     * @return a string of all the commits
     */
    public String log() {
        StringBuilder sb = new StringBuilder();
        Commit c = lastCommit;
        while (c != null) {
            if (!commitCache.containsKey(c.hash)) {
                commitCache.put(c.hash, c);
            }
            sb.append(c.toString(false));
            c = c.parentCommit(this.commitCache);
        }
        return sb.toString();
    }

    /**
     * returns the all commits
     * ===
     * commit [hash]
     * Date: [MM/DD/YYYY] [hh/dd/ss]
     * Author: [author]
     * Branch: [branch]
     * [message]
     * @return a string of all the commits
     */
    public String globalLog() {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(AllCommits));
            String line;
            while ((line = reader.readLine()) != null) {
                if (commitCache.containsKey(line)) {
                    sb.append(commitCache.get(line).toString(true));
                } else {
                    sb.append(Objects.requireNonNull(Commit.findCommit(line, vcsDirectory, commitCache)).
                            toString(true));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Checks all files in the directory, and returns it formatted by status of file
     * === Branches ===
     * [branches]
     * === Staged Files ===
     * [files that have been modified and staged]
     * === Removed Files ===
     * [files that have been removed and staged]
     * === Modified Files ===
     * [modified files that are unstaged, followed by (modified) or (deleted)]
     * === Untracked Files ===
     * [untracked files]
     * @return string representing the status
     */
    public String status() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("=== Branches ===\n");
            for (Path path : Objects.requireNonNull(getBranches())) {
                if (path.getFileName().toString().equals(branch)) {
                    sb.append("*").append(path.getFileName()).append("\n");
                } else {
                    sb.append(path.getFileName()).append("\n");
                }
            }
            readIndex();
            String p;
            Set<String> staged = new HashSet<>();
            Set<String> modified = new HashSet<>();
            Set<String> untracked = new HashSet<>();
            Set<String> removed = new HashSet<>();
            Set<String> indexFiles = new HashSet<>(indexMap.keySet());
            Set<String> commitFiles = new HashSet<>(lastCommit.getTree().map.keySet());
            String line;
            for (Path path : Objects.requireNonNull(getWorkingDir())) {
                p = this.currentDirectory.relativize(path).toString();
                if (indexFiles.contains(p)) {
                    line = indexMap.get(p);
                    if (line.endsWith("2")) {
                        untracked.add(p);
                    } else if (line.startsWith(Objects.requireNonNull(hash(path.toFile())))) {
                        staged.add(p);
                    } else {
                        modified.add(p + " (modified)");
                    }
                } else if (commitFiles.contains(p) && !lastCommit.getTree().map.get(p).equals(hash(path.toFile()))) {
                    modified.add(p + " (modified)");
                } else if (!commitFiles.contains(p)) {
                    untracked.add(p);
                }
                indexFiles.remove(p);
                commitFiles.remove(p);
            }
            for (String i : indexFiles) {
                if (indexMap.get(i).endsWith("2")) {
                    removed.add(i);
                } else {
                    modified.add(i + " (deleted)");
                }
                commitFiles.remove(i);
            }
            for (String i : commitFiles) {
                modified.add(i + " (deleted)");
            }
            if (!staged.isEmpty()) {
                sb.append("=== Staged Files ===\n");
                for (String f : staged) {
                    sb.append(f).append("\n");
                }
            }
            if (!removed.isEmpty()) {
                sb.append("=== Removed Files ===\n");
                for (String f : removed) {
                    sb.append(f).append("\n");
                }
            }
            if (!modified.isEmpty()) {
                sb.append("=== Modified Files ===\n");
                for (String f : modified) {
                    sb.append(f).append("\n");
                }
            }
            if (!untracked.isEmpty()) {
                sb.append("=== Untracked Files ===\n");
                for (String f : untracked) {
                    sb.append(f).append("\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    /**
     * If branch == false:
     *      Checks to make sure the last commit tracked the file path given by input,
     *      then overwrites the file's contents with the file contents in
     *      the last commit, creating the file if necessary
     * if branch == true:
     *      Deletes all the files tracked in current commit but not
     *      tracked in the head commit of the branch, given by input
     *      Overwrites each file tracked in the head commit of the branch, creating files if necessary
     *      Switches HEAD to point at branch
     * @param input: file path or branch name
     * @param branch: true if input is a branch name, false if input is file path
     */
    public void checkout(String input, boolean branch) {
        try {
            if (!branch) {
                Path p = Path.of(input);
                Path shortP = this.currentDirectory.relativize(p);
                String hash = this.lastCommit.getTree().map.getOrDefault(shortP.toString(), null);
                if (hash == null) {
                    System.out.println("File does not exist in that commit.");
                    return;
                }
                if (shortP.getParent() != null && shortP.getParent().toFile().exists()) {
                    shortP.getParent().toFile().mkdirs();
                }
                Files.copy(findHash(hash, vcsDirectory), p, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Map<String, String> headMap;
                Commit c = Commit.getHeadCommit(vcsDirectory, input);
                Map<String, String> m = this.lastCommit.getTree().map;
                if (input.equals(this.branch)) {
                    System.out.println("No need to checkout the current branch.");
                    return;
                } else if (c == null) {
                    System.out.println("No such branch exists.");
                    return;
                } else {
                    headMap = c.getTree().map;
                    String name;
                    Set<String> names = new HashSet<>();
                    for (Path path : Objects.requireNonNull(getWorkingDir())) {
                        name = this.currentDirectory.relativize(path).toString();
                        if (headMap.containsKey(name) && !m.containsKey(name)) {
                            names.add(name);
                        }
                    }
                    if (names.size() > 0) {
                        System.out.println("There are untracked files in the way; delete it or add it first.");
                        for (String n : names) {
                            System.out.println(n);
                        }
                        return;
                    }
                }
                for (String name : m.keySet()) {
                    if (!headMap.containsKey(name)) {
                        this.currentDirectory.resolve(name).toFile().delete();
                    }
                }
                Path shortP;
                for (String name : headMap.keySet()) {
                    shortP = Path.of(name);
                    if (shortP.getParent() != null && shortP.getParent().toFile().exists()) {
                        shortP.getParent().toFile().mkdirs();
                    }
                    Files.copy(findHash(headMap.get(name), vcsDirectory),
                            this.currentDirectory.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
                this.branch = input;
                FileWriter writer = new FileWriter(this.head);
                writer.write(this.branches.resolve(input).toString());
                writer.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Overwrites the contents of file at the end of path with its contents in commit with id == commitID
     * @param commitId: ID/Hash of a commit
     * @param path: the path to a file
     */
    public void checkout(String commitId, String path) {
        try {
            Path p = Path.of(path);
            Path shortP = this.currentDirectory.relativize(p);
            Commit c = Commit.findCommit(commitId, vcsDirectory, commitCache);
            if (c == null) {
                System.out.println("No commit with that id exists.");
                return;
            }
            String hash = c.getTree().map.getOrDefault(shortP.toString(), null);
            if (hash == null) {
                System.out.println("File does not exist in that commit.");
                return;
            }
            if (shortP.getParent() != null && shortP.getParent().toFile().exists()) {
                shortP.getParent().toFile().mkdirs();
            }
            Files.copy(findHash(hash, vcsDirectory), p, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the file of the head branch pointer
     * @return file object pointing to head commit
     */
    private File getHeadPath() {
        try {
            return new File(Files.readAllLines((this.head.toPath())).get(0));
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
                this.indexMap.put(line.substring(0, line.length() - LENGTHOFHASHANDSTATUS),
                        line.substring(line.length() - LENGTHOFHASHANDSTATUS));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns a Set of paths to each branch file
     * @return set of paths
     */
    private Set<Path> getBranches() {
        try (Stream<Path> walk = Files.walk(branches).filter(path -> !Files.isDirectory(path))) {
            return new HashSet<>(walk.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Returns a Set of the paths of all files in the working directory, excluding the .vcs subdirectory
     * @return set of paths
     */
    private Set<Path> getWorkingDir() {
        try (Stream<Path> walk = Files.walk(currentDirectory).filter(path -> !Files.isDirectory(path)).
                filter(path -> !path.startsWith(vcsDirectory))) {
            return new HashSet<>(walk.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Returns a commit object representing the last commit
     * @return commit object
     */
    public Commit getLastCommit() {
        return lastCommit;
    }
}
