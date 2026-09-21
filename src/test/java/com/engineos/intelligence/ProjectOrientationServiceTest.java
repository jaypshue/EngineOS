package com.engineos.intelligence;

import com.engineos.agent.AiUsage;
import com.engineos.domain.CopilotResultDetails;
import com.engineos.history.EngineeringHistoryRecord;
import com.engineos.history.EngineeringHistoryRepository;
import com.engineos.infrastructure.RepositoryDiscovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectOrientationServiceTest {
    @TempDir
    Path project;

    @Test
    void derivesOrientationFromRepositoryEvidenceAndHistory() throws Exception {
        Files.createDirectories(project.resolve("src/main/java/example"));
        Files.createDirectories(project.resolve("scripts"));
        Files.writeString(project.resolve("pom.xml"), """
                <project>
                  <spring-boot.version>3.5.6</spring-boot.version>
                  <artifactId>fixture-app</artifactId>
                  <junit>junit-jupiter</junit>
                </project>
                """);
        Files.writeString(project.resolve("src/main/java/example/Application.java"), """
                package example;
                @SpringBootApplication
                public class Application {
                    public static void main(String[] args) {}
                }
                """);
        Files.writeString(project.resolve("src/main/java/example/OrderService.java"), """
                package example;
                public interface OrderService {}
                """);

        var history = new StubHistory(EngineeringHistoryRecord.from(
                "run-1", Instant.parse("2026-09-18T20:00:00Z"),
                Instant.parse("2026-09-18T20:01:00Z"),
                "ENGINEOS_REQUEST_V1\nGoal: Verify fixture",
                "Verify fixture", "GitHub Copilot", "github",
                "gpt-5.6-luna", project.toString(), "MAVEN", "GREEN",
                "Coder completed successfully", 0, "session-1",
                new AiUsage("gpt-5.6-luna", "1.0", "10", "2", null, null, null, null),
                new CopilotResultDetails(true, "gpt-5.6-luna", 100L, 200L,
                        2, 0, 1, List.of("src/main/java/example/Application.java")),
                "result", "SUCCEEDED", true, null, "MAVEN"));

        var orientation = new ProjectOrientationService().orient(project, history);

        assertEquals("fixture-app", orientation.wholeRepository().identity());
        assertEquals(List.of("Java"), orientation.wholeRepository().languages());
        assertTrue(orientation.wholeRepository().frameworks().contains("Spring Boot"));
        assertTrue(orientation.wholeRepository().majorDirectories().contains("src"));
        assertTrue(orientation.wholeRepository().buildAndConfigurationFiles().contains("pom.xml"));
        assertTrue(orientation.relevantTypes().stream()
                .anyMatch(type -> type.name().equals("Application") && type.kind().equals("class")));
        assertTrue(orientation.relevantTypes().stream()
                .anyMatch(type -> type.name().equals("OrderService") && type.kind().equals("interface")));
        assertTrue(orientation.executionPath().stream().anyMatch(value -> value.contains("Application.java")));
        assertEquals("VERIFIED", orientation.verification().status());
        assertEquals("run-1", orientation.currentChange().getFirst().runId());
        assertEquals(List.of("src/main/java/example/Application.java"),
                orientation.currentChange().getFirst().modifiedFiles());
        assertEquals(1, orientation.recentEngineeringHistory().size());
        assertTrue(orientation.applicationArchitecture().components().stream()
                .anyMatch(value -> value.startsWith("example:")));
    }

    @Test
    void derivesApplicationIdentityAndPackageSubsystemsWithoutUsingParentArtifact() throws Exception {
        Files.createDirectories(project.resolve("src/main/java/com/example/agent"));
        Files.createDirectories(project.resolve("src/main/java/com/example/ui"));
        Files.writeString(project.resolve("pom.xml"), """
                <project><parent><artifactId>spring-boot-starter-parent</artifactId></parent>
                <artifactId>actual-app</artifactId></project>
                """);
        Files.writeString(project.resolve("src/main/java/com/example/AppApplication.java"), """
                package com.example;
                import com.example.agent.Agent;
                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                @SpringBootApplication
                public class AppApplication {
                    public static void main(String[] args) { SpringApplication.run(AppApplication.class, args); }
                }
                """);
        Files.writeString(project.resolve("src/main/java/com/example/agent/Agent.java"), """
                package com.example.agent;
                public class Agent {}
                """);
        Files.writeString(project.resolve("src/main/java/com/example/ui/Ui.java"), """
                package com.example.ui;
                public class Ui {}
                """);
        Files.writeString(project.resolve("src/main/java/com/example/Inspector.java"), """
                package com.example;
                public class Inspector {
                    boolean checksText(String content) {
                        return content.contains("import org.springframework.boot.SpringApplication")
                                && content.contains("SpringApplication.run(");
                    }
                }
                """);

        var orientation = new ProjectOrientationService().orient(project, null);

        assertEquals("actual-app", orientation.wholeRepository().identity());
        assertTrue(orientation.relevantSubsystems().stream().anyMatch(value -> value.name().equals("agent")));
        assertTrue(orientation.relevantSubsystems().stream().anyMatch(value -> value.name().equals("ui")));
        assertTrue(orientation.applicationArchitecture().relationships().stream()
                .anyMatch(value -> value.contains("imports agent")));
        assertTrue(orientation.executionPath().stream().anyMatch(value ->
                value.contains("SpringApplication.run from")
                        && value.contains("AppApplication.java")));
        assertTrue(orientation.executionPath().stream().noneMatch(value ->
                value.contains("Inspector.java")));
    }

    @Test
    void separatesCurrentChangeFromRecentHistory() throws Exception {
        Files.createDirectories(project.resolve("src"));
        Files.writeString(project.resolve("pom.xml"), "<project><artifactId>fixture</artifactId></project>");
        var latest = EngineeringHistoryRecord.from(
                "latest", Instant.parse("2026-09-18T20:00:00Z"),
                Instant.parse("2026-09-18T20:01:00Z"), "request", "request", "agent", "provider",
                "model", project.toString(), "MAVEN", "GREEN", "done", 0, "session",
                AiUsage.unavailable(), CopilotResultDetails.unavailable(), "result", "SUCCEEDED",
                true, null, "MAVEN");
        var older = EngineeringHistoryRecord.from(
                "older", Instant.parse("2026-09-17T20:00:00Z"),
                Instant.parse("2026-09-17T20:01:00Z"), "request", "request", "agent", "provider",
                "model", project.toString(), "MAVEN", "GREEN", "done", 0, "session",
                AiUsage.unavailable(), new CopilotResultDetails(true, "model", 1L, null,
                1, 0, 1, List.of("src/missing.java")), "result", "SUCCEEDED", true, null, "MAVEN");

        var orientation = new ProjectOrientationService(new RepositoryDiscovery()).orient(
                project, new StubHistory(List.of(latest, older)));

        assertTrue(orientation.currentChange().isEmpty());
        assertEquals(2, orientation.recentEngineeringHistory().size());
    }

    @Test
    void representsUnavailableEvidenceWithoutInventingFrameworksOrVerification() throws Exception {
        Files.writeString(project.resolve("notes.txt"), "plain project notes");

        var orientation = new ProjectOrientationService().orient(project, null);

        assertTrue(orientation.wholeRepository().languages().isEmpty());
        assertTrue(orientation.wholeRepository().frameworks().isEmpty());
        assertEquals("UNAVAILABLE", orientation.verification().status());
        assertTrue(orientation.verification().uncertainties().stream()
                .anyMatch(value -> value.contains("History")));
        assertTrue(orientation.executionPath().getFirst().contains("unavailable"));
    }

    private static final class StubHistory implements EngineeringHistoryRepository {
        private final EngineeringHistoryRecord record;

        private final List<EngineeringHistoryRecord> records;

        private StubHistory(EngineeringHistoryRecord record) {
            this(List.of(record));
        }

        private StubHistory(List<EngineeringHistoryRecord> records) {
            this.records = records;
            this.record = records.getFirst();
        }

        @Override
        public void save(EngineeringHistoryRecord record) {
        }

        @Override
        public List<EngineeringHistoryRecord> list() {
            return records;
        }

        @Override
        public Optional<EngineeringHistoryRecord> get(String runId) {
            return Optional.of(record);
        }
    }
}
