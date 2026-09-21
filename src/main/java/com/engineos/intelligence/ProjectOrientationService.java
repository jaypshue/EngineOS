package com.engineos.intelligence;

import com.engineos.history.EngineeringHistoryRecord;
import com.engineos.history.EngineeringHistoryRepository;
import com.engineos.infrastructure.RepositoryDiscovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProjectOrientationService {
    private static final Pattern TYPE = Pattern.compile(
            "\\b(class|interface|record|enum)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT = Pattern.compile("\\bimport\\s+([\\w.]+)\\s*;");
    private static final Pattern ARTIFACT = Pattern.compile(
            "<artifactId>\\s*([^<\\s]+)\\s*</artifactId>");
    private static final Pattern NAME = Pattern.compile("<name>\\s*([^<]+?)\\s*</name>");
    private static final Pattern SPRING_APPLICATION = Pattern.compile(
            "@SpringBootApplication[\\s\\S]*?\\bclass\\s+([A-Za-z_$][\\w$]*)");

    private final RepositoryDiscovery discovery;

    public ProjectOrientationService() {
        this(new RepositoryDiscovery());
    }

    public ProjectOrientationService(RepositoryDiscovery discovery) {
        this.discovery = discovery;
    }

    public ProjectOrientation orient(Path root, EngineeringHistoryRepository history) {
        var workspace = discovery.load(root);
        var files = workspace.discoveredFiles();
        var applicationFiles = applicationSourceFiles(root, files);
        var directories = majorDirectories(root, files);
        var buildFiles = files.stream()
                .filter(path -> isBuildOrConfiguration(path.getFileName().toString()))
                .map(path -> relative(root, path))
                .toList();
        var languages = languages(files);
        var frameworks = frameworks(root, files);
        var uncertainties = new ArrayList<String>();
        if (languages.isEmpty()) {
            uncertainties.add("Primary language could not be determined from file extensions.");
        }
        if (frameworks.isEmpty()) {
            uncertainties.add("Framework could not be determined from inspected build/configuration files.");
        }

        var subsystems = sourceSubsystems(root, applicationFiles);
        var types = relevantTypes(root, applicationFiles);
        var components = architectureComponents(directories, applicationFiles, subsystems);
        var relationships = architectureRelationships(root, applicationFiles, languages, frameworks, subsystems);
        var executionPath = executionPath(root, applicationFiles, languages, frameworks);
        var historyChanges = historyChanges(root, history);
        var changes = currentChanges(root, historyChanges);
        var verification = verification(root, history);

        return new ProjectOrientation(
                new ProjectOrientation.WholeRepository(
                        root.toAbsolutePath().normalize(),
                        identity(root, files, workspace.repositoryName()),
                        languages,
                        frameworks,
                        directories,
                        buildFiles,
                        uncertainties),
                new ProjectOrientation.ApplicationArchitecture(
                        components,
                        relationships,
                        architectureUncertainties(files, types)),
                subsystems,
                types,
                executionPath,
                changes,
                verification,
                historyChanges);
    }

    private static List<String> majorDirectories(Path root, List<Path> files) {
        Set<String> result = new HashSet<>();
        for (Path file : files) {
            Path relative = root.relativize(file);
            if (relative.getNameCount() > 1 && !relative.getName(0).toString().startsWith(".")) {
                result.add(relative.getName(0).toString());
            }
        }
        return result.stream().sorted().toList();
    }

    private static List<Path> applicationSourceFiles(Path root, List<Path> files) {
        Path primarySource = root.resolve("src").resolve("main").resolve("java");
        if (Files.isDirectory(primarySource)) {
            return files.stream().filter(path -> path.startsWith(primarySource)).toList();
        }
        return files.stream()
                .filter(path -> path.toString().contains(java.io.File.separator + "src"
                        + java.io.File.separator + "main" + java.io.File.separator + "java"
                        + java.io.File.separator))
                .toList();
    }

    private static String identity(Path root, List<Path> files, String fallback) {
        var pomIdentity = files.stream()
                .filter(path -> path.getFileName().toString().equalsIgnoreCase("pom.xml"))
                .map(ProjectOrientationService::read)
                .map(ProjectOrientationService::projectIdentity)
                .filter(value -> !value.isBlank())
                .findFirst().orElse("");
        if (!pomIdentity.isBlank()) return pomIdentity;
        return files.stream()
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .map(ProjectOrientationService::read)
                .map(ProjectOrientationService::springApplicationName)
                .filter(value -> !value.isBlank())
                .findFirst()
                .map(ProjectOrientationService::applicationIdentity)
                .orElse(fallback);
    }

    private static String projectIdentity(String content) {
        Matcher name = NAME.matcher(content);
        if (name.find() && !name.group(1).isBlank()) return name.group(1).trim();
        Matcher matcher = ARTIFACT.matcher(content);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!"spring-boot-starter-parent".equals(candidate)) return candidate;
        }
        return "";
    }

    private static String springApplicationName(String content) {
        Matcher matcher = SPRING_APPLICATION.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String applicationIdentity(String className) {
        String identity = className.endsWith("Application")
                ? className.substring(0, className.length() - "Application".length())
                : className;
        return identity.isBlank() ? "" : identity;
    }

    private static List<String> languages(List<Path> files) {
        var found = new HashSet<String>();
        for (Path file : files) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".java")) found.add("Java");
            if (name.endsWith(".kt")) found.add("Kotlin");
            if (name.endsWith(".ts") || name.endsWith(".tsx")) found.add("TypeScript");
            if (name.endsWith(".js") || name.endsWith(".jsx")) found.add("JavaScript");
            if (name.endsWith(".py")) found.add("Python");
            if (name.endsWith(".cs")) found.add("C#");
            if (name.endsWith(".go")) found.add("Go");
            if (name.endsWith(".rs")) found.add("Rust");
        }
        return found.stream().sorted().toList();
    }

    private static List<String> frameworks(Path root, List<Path> files) {
        var found = new HashSet<String>();
        for (Path file : files) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            String content = read(file);
            if (name.equals("pom.xml") && content.contains("spring-boot")) found.add("Spring Boot");
            if (name.equals("pom.xml") && content.contains("junit")) found.add("JUnit");
            if (name.equals("package.json") && content.contains("\"react\"")) found.add("React");
            if (name.equals("package.json") && content.contains("\"@angular/")) found.add("Angular");
            if (name.equals("pyproject.toml") && content.contains("django")) found.add("Django");
            if (name.equals("requirements.txt") && content.toLowerCase(Locale.ROOT).contains("flask")) {
                found.add("Flask");
            }
        }
        return found.stream().sorted().toList();
    }

    private static List<ProjectOrientation.RelevantType> relevantTypes(Path root, List<Path> files) {
        var result = new ArrayList<ProjectOrientation.RelevantType>();
        for (Path file : files) {
            if (!file.getFileName().toString().endsWith(".java")) continue;
            String content = read(file);
            String packageName = firstGroup(PACKAGE, content);
            Matcher matcher = TYPE.matcher(content);
            while (matcher.find()) {
                result.add(new ProjectOrientation.RelevantType(
                        matcher.group(2), matcher.group(1), relative(root, file), packageName));
            }
        }
        return result.stream()
                .sorted(Comparator.comparing(ProjectOrientation.RelevantType::path)
                        .thenComparing(ProjectOrientation.RelevantType::name))
                .toList();
    }

    private static List<ProjectOrientation.Subsystem> sourceSubsystems(Path root, List<Path> files) {
        var packages = files.stream()
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .map(ProjectOrientationService::read)
                .map(content -> firstGroup(PACKAGE, content))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (packages.isEmpty()) return List.of();
        String common = commonPackage(packages);
        return packages.stream().map(packageName -> {
            String name = packageName.startsWith(common + ".")
                    ? packageName.substring(common.length() + 1)
                    : packageName;
            if (name.isBlank()) name = packageName;
            Path source = files.stream()
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> packageName.equals(firstGroup(PACKAGE, read(path))))
                    .findFirst().orElse(root);
            long count = files.stream()
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> packageName.equals(firstGroup(PACKAGE, read(path))))
                    .count();
            return new ProjectOrientation.Subsystem(
                    name, relative(root, source.getParent() == null ? source : source.getParent()),
                    "Source package " + packageName + " (" + count + " Java file"
                            + (count == 1 ? "" : "s") + ")");
        }).sorted(Comparator.comparing(ProjectOrientation.Subsystem::name)).toList();
    }

    private static String commonPackage(List<String> packages) {
        if (packages.isEmpty()) return "";
        String[] prefix = packages.getFirst().split("\\.");
        int length = prefix.length;
        for (String packageName : packages) {
            String[] parts = packageName.split("\\.");
            length = Math.min(length, parts.length);
            for (int index = 0; index < length; index++) {
                if (!prefix[index].equals(parts[index])) {
                    length = index;
                    break;
                }
            }
        }
        return length == 0 ? "" : String.join(".", java.util.Arrays.copyOf(prefix, length));
    }

    private static List<String> architectureComponents(
            List<String> directories, List<Path> files, List<ProjectOrientation.Subsystem> subsystems) {
        var components = new ArrayList<String>();
        subsystems.forEach(subsystem ->
                components.add(subsystem.name() + ": " + subsystem.evidence()));
        if (directories.contains("src") && subsystems.isEmpty()) components.add("src: application source");
        if (directories.contains("scripts")) components.add("scripts: operational tooling");
        if (directories.contains("reference")) components.add("reference: supporting material");
        if (files.stream().anyMatch(path -> path.getFileName().toString().equals("pom.xml"))) {
            components.add("Maven build and dependency boundary");
        }
        return components;
    }

    private static List<String> architectureRelationships(
            Path root, List<Path> files, List<String> languages, List<String> frameworks,
            List<ProjectOrientation.Subsystem> subsystems) {
        var relationships = new ArrayList<String>();
        if (languages.contains("Java") && frameworks.contains("Spring Boot")) {
            relationships.add("Spring Boot application classes are built and packaged through Maven.");
        }
        var packageNames = files.stream()
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .collect(java.util.stream.Collectors.toMap(
                        path -> relative(root, path),
                        path -> firstGroup(PACKAGE, read(path)),
                        (first, ignored) -> first));
        var common = commonPackage(packageNames.values().stream().filter(value -> !value.isBlank()).distinct().toList());
        for (var entry : packageNames.entrySet()) {
            String sourcePackage = entry.getValue();
            if (sourcePackage.isBlank()) continue;
            for (String imported : imports(read(root.resolve(entry.getKey())))) {
                if (imported.startsWith(common + ".") && !imported.equals(sourcePackage)
                        && subsystems.stream().anyMatch(s -> imported.startsWith(common + "." + s.name()))) {
                    String from = subsystemName(sourcePackage, common);
                    String to = subsystemName(imported, common);
                    if (!from.equals(to)) relationships.add(from + " imports " + to + ".");
                }
            }
        }
        if (directoriesContainScripts(files)) {
            relationships.add("Operational scripts support the application workspace but are not application components.");
        }
        return relationships.stream().distinct().sorted().toList();
    }

    private static boolean directoriesContainScripts(List<Path> files) {
        return files.stream().anyMatch(path -> path.toString().contains(java.io.File.separator + "scripts" + java.io.File.separator));
    }

    private static List<String> imports(String content) {
        var result = new ArrayList<String>();
        Matcher matcher = IMPORT.matcher(content);
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private static String subsystemName(String packageName, String common) {
        return packageName.startsWith(common + ".")
                ? packageName.substring(common.length() + 1).split("\\.")[0]
                : packageName;
    }

    private static List<String> executionPath(
            Path root, List<Path> files, List<String> languages, List<String> frameworks) {
        var path = new ArrayList<String>();
        if (languages.contains("Java") && frameworks.contains("Spring Boot")) {
            files.stream()
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .filter(file -> read(file).contains("static void main(String[]"))
                    .map(file -> relative(root, file))
                    .findFirst()
                    .ifPresent(file -> path.add("Spring Boot starts from main method in " + file + "."));
            files.stream()
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .filter(file -> containsSpringApplicationRunCall(read(file)))
                    .map(file -> relative(root, file))
                    .findFirst()
                    .ifPresent(file -> path.add("EngineOS invokes SpringApplication.run from " + file
                            + " and then initializes the desktop workflow."));
            files.stream()
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .filter(file -> read(file).contains("new ObservationWindow"))
                    .map(file -> relative(root, file))
                    .findFirst()
                    .ifPresent(file -> path.add("The desktop ObservationWindow is created from " + file + "."));
            path.add("Maven supplies the build and test lifecycle through pom.xml.");
        } else if (files.stream().anyMatch(file -> file.getFileName().toString().equals("pom.xml"))) {
            path.add("Maven pom.xml is the available execution/build entry evidence.");
        } else {
            path.add("Application runtime entry point is unavailable from the inspected repository evidence.");
        }
        return path;
    }

    private static boolean containsSpringApplicationRunCall(String content) {
        boolean importsSpringApplication = false;
        boolean constructsSpringApplication = false;
        boolean invokesSpringApplication = false;
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*")
                    || trimmed.contains("contains(\"")) {
                continue;
            }
            importsSpringApplication |= trimmed.equals("import org.springframework.boot.SpringApplication;");
            constructsSpringApplication |= trimmed.matches(".*\\bnew\\s+SpringApplication\\s*\\(.*");
            invokesSpringApplication |= trimmed.matches(".*\\b(?:SpringApplication|application)\\.run\\s*\\(.*");
        }
        return importsSpringApplication
                && (invokesSpringApplication
                || (constructsSpringApplication && content.matches("(?s).*\\b\\w+\\.run\\s*\\(.*")));
    }

    private static List<ProjectOrientation.Change> historyChanges(
            Path root, EngineeringHistoryRepository history) {
        if (history == null) return List.of();
        return history.list().stream()
                .filter(record -> root.toAbsolutePath().normalize().toString().equals(record.workspace()))
                .map(record -> new ProjectOrientation.Change(
                        record.runId(), record.completedAt(), record.finalStatus(),
                        record.codingResultStatus(), record.observationAvailable(),
                        record.linesAdded(), record.linesRemoved(), record.modifiedFiles()))
                .toList();
    }

    private static List<ProjectOrientation.Change> currentChanges(
            Path root, List<ProjectOrientation.Change> historyChanges) {
        return historyChanges.stream()
                .filter(change -> !change.modifiedFiles().isEmpty())
                .filter(change -> change.modifiedFiles().stream()
                        .map(root::resolve)
                        .anyMatch(Files::isRegularFile))
                .limit(1)
                .toList();
    }

    private static ProjectOrientation.Verification verification(
            Path root, EngineeringHistoryRepository history) {
        if (history == null) {
            return new ProjectOrientation.Verification(
                    "UNAVAILABLE", List.of(), List.of("Engineering History was not supplied."));
        }
        var records = historyChanges(root, history);
        if (records.isEmpty()) {
            return new ProjectOrientation.Verification(
                    "UNAVAILABLE", List.of(), List.of("No durable verification record was found for this workspace."));
        }
        var evidence = records.stream()
                .filter(ProjectOrientation.Change::verified)
                .map(record -> "Run " + record.runId() + " reported observation success.")
                .toList();
        var uncertainties = records.stream()
                .filter(record -> !record.verified())
                .map(record -> "Run " + record.runId() + " was not verified as successful.")
                .toList();
        return new ProjectOrientation.Verification(
                evidence.isEmpty() ? "UNVERIFIED" : "VERIFIED",
                evidence,
                uncertainties);
    }

    private static List<String> architectureUncertainties(
            List<Path> files, List<ProjectOrientation.RelevantType> types) {
        if (!types.isEmpty()) return List.of();
        return List.of("No Java type declarations were found; subsystem relationships remain unavailable.");
    }

    private static boolean isBuildOrConfiguration(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("pom.xml") || lower.equals("build.gradle") || lower.equals("build.gradle.kts")
                || lower.equals("package.json") || lower.equals("pyproject.toml")
                || lower.equals("requirements.txt") || lower.equals("dockerfile")
                || lower.equals("application.yml") || lower.equals("application.yaml")
                || lower.equals("application.properties");
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            return "";
        }
    }

    private static String firstGroup(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }
}
