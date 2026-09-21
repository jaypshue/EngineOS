package com.engineos.agent;

import com.engineos.domain.WorkspaceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSessionStoreTest {
	@TempDir
	Path temp;

	@Test
	void savesAndLoadsSessionForTheSameWorkspace() {
		var store = new CopilotSessionStore(temp.resolve("session.json"));
		var workspace = workspace(temp.resolve("repo"));

		store.save(workspace, "session-a");

		assertEquals("session-a", store.sessionFor(workspace).orElseThrow());
		assertTrue(Files.exists(store.file()));
	}

	@Test
	void refusesToResumeSessionFromAnotherWorkspace() {
		var store = new CopilotSessionStore(temp.resolve("session.json"));
		store.save(workspace(temp.resolve("repo-a")), "session-a");

		assertTrue(store.sessionFor(workspace(temp.resolve("repo-b"))).isEmpty());
	}

	@Test
	void normalizesWorkspacePathsBeforeMatching() {
		var store = new CopilotSessionStore(temp.resolve("session.json"));
		var workspace = workspace(temp.resolve("repo-a"));
		store.save(workspace, "session-a");

		assertEquals("session-a", store.sessionFor(workspace(temp.resolve("repo-a").resolve("."))).orElseThrow());
	}

	private static WorkspaceContext workspace(Path root) {
		return new WorkspaceContext(root, "repo", "main", true, List.of());
	}
}
