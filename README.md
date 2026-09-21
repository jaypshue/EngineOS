# EngineOS (Java)

This repository is the Java port of the existing EngineOS .NET prototype. It
preserves the prototype's domain vocabulary and conversation-first workflow
without translating its UI or provider implementations line by line.

The first vertical slice is intentionally headless:

1. Load a workspace and discover repository state.
2. Record human engineering intent.
3. Let the Engineer Agent propose one bounded Engineering Task.
4. Require explicit human approval.
5. Let the Coder Agent execute the approved Coding Task.
6. Record the execution result as Evidence.
7. Let the Engineer Agent review the evidence for presentation to the human.

Engineer and Coder use separate interfaces and provider records, so they can
later use different models or vendors.

## Local two-process architecture

EngineOS Desktop is the client process, and EngineOS Service runs as a separate
process. The Desktop communicates with the Service over HTTP. The Service owns
local Copilot execution, verification, observation, and Engineering History.
Check Service readiness with `GET /api/engineos/health`.

## Engineering History

Completed Engineering Session runs are stored as one JSON file per run under
the user's application data directory (`%APPDATA%\EngineOS\history` on
Windows). History contains structured request, coding, verification, usage,
change, and observation metadata and is shown from the **HISTORY** button in
the desktop UI. Observation recordings and ZIP packages are not copied into
history. Individual unreadable history files are skipped so they do not prevent
the application from starting.

## Git integration

EngineOS uses the system Git CLI through a testable repository abstraction for
read-first workspace status, recent commits, working-tree/staged/commit diffs,
local branches, and explicitly requested commits. The desktop exposes the
read-only status, branch, change, history, and local-branch view through the
**GIT** button. Commit operations never stage files automatically; callers must
stage the intended changes before requesting a commit.

Run the automated checks with:

```text
mvn test
mvn package
```

## Separate local service host

The desktop is an HTTP client and does not host the EngineOS service. Start the
service host first, from the developer machine and with the local workspace
available:

```powershell
mvn -q package -Pservice-host -DskipTests
java -jar target\engineos-0.1.0-SNAPSHOT-service-host.jar
```

The `-service-host.jar` is a separately executable artifact whose
`Start-Class` is `com.engineos.ServiceHostApplication`. The host binds to
`127.0.0.1:8765` by default and owns the local workspace, Copilot CLI,
build/test execution, verification, observation, and Engineering History
persistence. Its readiness endpoint is
`http://127.0.0.1:8765/api/engineos/health`. Configure the bind address and
port with `-Dengineos.service.bind-address` /
`ENGINEOS_SERVICE_BIND_ADDRESS` and `-Dengineos.service.port` /
`ENGINEOS_SERVICE_PORT`.

Start the desktop separately:

```powershell
java -jar target\engineos-0.1.0-SNAPSHOT.jar
```

The desktop connects to `http://127.0.0.1:8765` and checks the dedicated
readiness endpoint before using business endpoints. Use
`-Dengineos.service.url=http://127.0.0.1:8766` or `ENGINEOS_SERVICE_URL` when
the service uses another port. Keep the service process running in its own
terminal; the desktop can start first, remains usable while disconnected, and
reconnects automatically when the readiness endpoint becomes available. `POST
/api/engineos/requests` returns a `requestId`, and the Desktop polls status,
activity, and result. The existing ENGINEOS_REQUEST_V1 request flow and result
semantics are unchanged.

## Evidence-driven coding workflow

Coding tasks receive adaptive workflow guidance rather than a fixed checklist.
The coding agent is asked to inspect the workspace and existing implementation,
make the smallest applicable change, run targeted verification, correct and
re-verify defects, broaden verification when relevant, and report evidence
gaps. The request remains unchanged; the guidance is attached only to the
internal coding-task prompt.

Verification requests use a bounded duration and prohibit workspace
modification.

The Engineering Result records lifecycle activity and classifies supervision
as `VERIFIED`, `FAILED`, or `INSUFFICIENT_EVIDENCE`, alongside an
`IMPLEMENTED`/`NOT_IMPLEMENTED` implementation status. A failed external
observation is reported as an evidence gap rather than automatically starting
a second coding request, because automatic correction could duplicate or
compound workspace changes.
Timeout and boundary-violation runs are not reported as successful.

## IntAssist observation proof of concept

From the EngineOS root, run:

```powershell
.\scripts\capture-intassist-observation.ps1
```

The runner launches IntAssist's existing visible JavaFX Desktop UI with
`mvn -q javafx:run`, invokes the existing `Start Demo` control through Windows
UI Automation, waits for the deterministic built-in demo to report
`Demo complete`, and closes the window. It writes a timestamped Observation
Package below `observations\` containing `manifest.json`, `timeline.json`,
Maven/application output, UI scenario output, git/workspace metadata, and an
automatic FFmpeg desktop recording.

The IntAssist scenario is intentionally unchanged. The runner requires an
interactive Windows desktop session with FFmpeg available on `PATH`.
