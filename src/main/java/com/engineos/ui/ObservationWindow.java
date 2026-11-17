package com.engineos.ui;

import com.engineos.application.EngineOsService;
import com.engineos.agent.CopilotModelCatalog;
import com.engineos.history.EngineeringHistoryRecord;
import com.engineos.history.HistoryPresentation;
import com.engineos.infrastructure.git.GitBranch;
import com.engineos.infrastructure.git.GitFileChange;
import com.engineos.infrastructure.git.GitRepository;
import com.engineos.infrastructure.git.GitStatus;
import com.engineos.infrastructure.git.GitSnapshot;
import com.engineos.infrastructure.git.ProcessGitRepository;
import com.engineos.observation.CompletedObservation;
import com.engineos.observation.ObservationAttachmentPreparer;
import com.engineos.observation.ObservationPackageService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.SwingConstants;
import javax.swing.JSplitPane;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;

public final class ObservationWindow {
    private static final String DEFAULT_PROJECT = "C:\\projects\\intassist";
    static final String HEADER_SUBTITLE = "ENGINEERING CONTROL ENVIRONMENT";
    static final String BOUNDARY_PROMPT = "Given this engineering request, suggest a tight path, package, module, or area boundary. Prefer a concise path glob or scope, and identify anything to exclude.";

    private static final Color BACKGROUND = new Color(11, 17, 32);
    private static final Color SURFACE = new Color(15, 22, 36);
    private static final Color SURFACE_RAISED = new Color(23, 32, 51);
    private static final Color INPUT = new Color(13, 21, 38);
    private static final Color BORDER = new Color(42, 56, 80);
    private static final Color TEXT = new Color(229, 231, 235);
    private static final Color MUTED = new Color(148, 163, 184);
    private static final Color ACCENT = new Color(66, 190, 126);
    private static final Color ACCENT_HOVER = new Color(86, 211, 146);
    private static final Color SUCCESS = new Color(69, 212, 131);
    private static final Color ERROR = new Color(248, 113, 113);
    private static final Color ACTIVE_AMBER = new Color(245, 158, 11);
    private static final Color ACTIVE_AMBER_BRIGHT = new Color(251, 191, 36);
    private static final Color HISTORY_BORDER = new Color(52, 70, 95);
    private static final int HISTORY_TRANSITION_DURATION_MS = 840;
    private static final int HISTORY_TRANSITION_TICK_MS = 40;
    private static final String ENGINEOS_STARTUP_ACTIVITY = "EngineOS: Preparing Engineering Task Proposal...";
    private static final String DEFAULT_INVESTIGATION_REQUEST = """
            Investigate this existing system. Identify its architecture, major
            components, dependencies, entry points, APIs, data flow,
            configuration, and important implementation relationships.
            Do not modify the repository. Report what you can establish from
            the existing code and configuration.""";

    private static final Font DISPLAY_FONT = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font WORDMARK_FONT = new Font("Segoe UI", Font.BOLD | Font.ITALIC, 36);
    private static final Font MAJOR_SECTION_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font SECTION_FONT = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font SMALL_FONT = new Font("Segoe UI", Font.PLAIN, 12);

    private final ObservationPackageService packages;
    private final ObservationAttachmentPreparer attachments;
    private final EngineOsService sessions;
    private final GitRepository git;
    private final JLabel status = new JLabel();
    private final JLabel requestStatus = new JLabel();
    private final JLabel resultStatus = new JLabel();
    private final JLabel observationStatus = new JLabel();
    private final JButton sendButton = new JButton("COPY OBSERVATION");
    private final JButton copyResultButton = new JButton("COPY ENGINEERING RESULT");
    private final JButton viewHistoryResultButton = new JButton("VIEW IN HISTORY");
    private final JButton startButton = new JButton("SUBMIT ENGINEERING REQUEST");
    private final JButton cancelButton = new JButton("STOP CODING AGENT");
    private final JButton viewTargetButton = new JButton("VIEW ACTIVE TARGET");
    private final JButton recordLiveTestButton = new JButton("Record live test (Ctrl+Shift+R)");
    private final JButton historyButton = new JButton("HISTORY");
    private final JButton gitButton = new JButton("GIT");
    private final JButton refreshSourceControlButton = new JButton("REFRESH");
    private final JButton browseButton = new JButton("BROWSE...");
    private final JLabel requestValidation = new JLabel();
    private final JLabel workspaceTargetLabel = new JLabel();
    private final JLabel verificationTargetLabel = new JLabel();
    private final JLabel sourceControlLabel = new JLabel();
    private final JLabel preflightLabel = new JLabel();
    private final JLabel preflightSummaryLabel = new JLabel();
    private final JButton preflightToggle = new JButton("EXPAND \u25BE");
    private final JPanel preflightPanel = new JPanel(new BorderLayout(0, 6));
    private final JTextField projectField = new JTextField(DEFAULT_PROJECT);
    private final JTextArea promptArea = new JTextArea(8, 60);
    private final JTextArea resultArea = new JTextArea(8, 60);
    private final JTextArea activityArea = new JTextArea(5, 60);
    private final JTextArea usageArea = new JTextArea(8, 30);
    private final JScrollPane activityScroll = new JScrollPane(activityArea);
    private final JPanel activityPanel = new JPanel(new BorderLayout(0, 6));
    private final JPanel resultPanel = new JPanel(new BorderLayout(0, 6));
    private JPanel outputActions;
    private final JPanel activityHeading = new JPanel(new BorderLayout(8, 0));
    private final JLabel activityIndicator = new JLabel("●");
    private final JLabel activityHeadingLabel = new JLabel("ENGINEOS ACTIVITY");
    private final JLabel activityRetention = new JLabel();
    private final JComboBox<String> codingAgent = new JComboBox<>(new String[]{"GitHub Copilot"});
    private final JComboBox<String> model = new JComboBox<>();
    private final JComboBox<String> targetSelector = new JComboBox<>();
    private final JPanel onboardingPanel = new JPanel(new BorderLayout(16, 12));
    private final JPanel workspaceEditorPanel = new JPanel();
    private final JPanel workspaceSummaryPanel = new JPanel(new BorderLayout(12, 0));
    private final JLabel workspaceSummaryLabel = new JLabel();
    private final JButton workspaceChangeButton = new JButton("SHOW WORKSPACE");
    private final JButton workspaceDoneButton = new JButton("HIDE WORKSPACE");
    private final JTextField boundaryField = new JTextField();
    private final JPanel proposalProgressBanner = createProposalProgressBanner();
    private final JPanel submissionConfigPanel = new JPanel(new BorderLayout(0, 2));
    private final OnboardingPreferences onboarding = new OnboardingPreferences();
    private final JFrame frame = new JFrame("EngineOS");
    private final DesktopRequestLifecycle requestLifecycle = new DesktopRequestLifecycle();
    private CompletedObservation currentObservation;
    private String currentRunId;
    private EngineeringHistoryRecord currentHistoryRecord;
    private GitSnapshot currentGitSnapshot;
    private Path currentGitWorkspace;
    private boolean preflightExpanded;
    private boolean workspacePickerExpanded;
    private String serviceError;
    private int liveActivityCount;
    private Timer serviceRetryTimer;
    private final Timer activityPulseTimer = new Timer(900, event -> pulseActivityIndicator());
    private final LiveTestRecorder liveTestRecorder = new LiveTestRecorder();
    private final Timer liveTestRemainingTimer = new Timer(1000, event -> updateLiveTestRemaining());
    private long liveTestStartedAt;
    private String liveTestTargetName;
    private long liveTestGeneration;
    private RecordingOverlay liveTestOverlay;
    private final Timer historyTransitionTimer = new Timer(
            HISTORY_TRANSITION_TICK_MS, event -> animateHistoryTransition());
    private boolean activityPulseBright;
    private long historyTransitionStartedAt;
    private long requestGeneration;
    private long sourceControlRefreshGeneration;
    private boolean updatingTargetSelector;

    public ObservationWindow(Path observationsRoot) {
        this(observationsRoot, null, new ProcessGitRepository());
    }

    private static String proposalClipboardText(EngineOsService.TaskProposal proposal) {
        StringBuilder text = new StringBuilder()
                .append("Review Proposed Task\n\n")
                .append("Title:\n").append(proposal.title())
                .append("\n\nStatus:\n").append(proposal.status())
                .append("\n\nGoal:\n").append(proposal.goal())
                .append("\n\nIn scope:\n").append(proposal.scope())
                .append("\n\nOut of scope:\n").append(proposal.outOfScope());
        if (proposal.rulesPath() != null && !proposal.rulesPath().isBlank()) {
            text.append("\n\nProject rules (lower priority than request and safety boundaries):\n")
                    .append("Path: ").append(proposal.rulesPath())
                    .append("\nSHA-256: ").append(proposal.rulesSha256())
                    .append("\n\n").append(proposal.rulesContent());
        }
        return text.append("\n\n")
                .append(proposalDetailsWithoutOverview(proposal))
                .toString();
    }

    public ObservationWindow(Path observationsRoot, EngineOsService sessions) {
        this(observationsRoot, sessions, new ProcessGitRepository());
    }

    ObservationWindow(Path observationsRoot, EngineOsService sessions, GitRepository git) {
        packages = new ObservationPackageService(observationsRoot);
        attachments = new ObservationAttachmentPreparer(packages);
        this.sessions = sessions;
        this.git = git;
        configure();
    }

    public void show() {
        frame.setVisible(true);
    }

    private void configure() {
        currentObservation = null;
        currentRunId = null;
        if (sessions != null) {
            codingAgent.removeAllItems();
            try {
                if (!sessions.health().ready()) {
                    throw new IllegalStateException("EngineOS Service is not ready.");
                }
                codingAgent.addItem(sessions.codingAgentProvider().displayName());
            } catch (RuntimeException exception) {
                serviceError = "EngineOS Service is unavailable. Start the Service Host to continue.";
                codingAgent.addItem("Agent unavailable");
            }
            var availableModels = CopilotModelCatalog.discover();
            availableModels.forEach(model::addItem);
            model.setSelectedItem(onboarding.restoreModel(availableModels, "auto"));
        }
        projectField.setText(onboarding.restoreWorkspace(DEFAULT_PROJECT));
        promptArea.setText(DEFAULT_INVESTIGATION_REQUEST);

        configureControls();
        recordLiveTestButton.setToolTipText("Ctrl+Shift+R");
        recordLiveTestButton.setToolTipText("Record live test (Ctrl+Shift+R)");
        configureRequestValidation();
        refreshSourceControl();
        if (serviceError != null) {
            status.setText(serviceError);
            status.setForeground(ERROR);
            status.setIcon(new StatusDotIcon(ERROR));
        }

        var root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(20, 24, 18, 24));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildMainContent(), BorderLayout.CENTER);
        root.add(buildWorkflowFooter(), BorderLayout.SOUTH);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                DesktopDiagnostics.stage("windowClosing");
                onboarding.saveWindowBounds(frame.getBounds());
                liveTestRecorder.stopHotkeyMonitor();
                if (liveTestOverlay != null) {
                    liveTestOverlay.close();
                }
            }

            @Override
            public void windowClosed(WindowEvent event) {
                DesktopDiagnostics.stage("windowClosed");
            }
        });
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent event) {
                refreshSourceControl();
            }
        });
        frame.setContentPane(root);
        frame.setBackground(BACKGROUND);
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setSize(1040, onboardingPanel.isVisible() ? 790 : 680);
        frame.setLocation(40, 0);
        var fallbackBounds = new Dimension(frame.getWidth(), frame.getHeight());
        var restoredBounds = onboarding.restoreWindowBounds(
                new java.awt.Rectangle(40, 0, fallbackBounds.width, fallbackBounds.height),
                frame.getMinimumSize());
        frame.setBounds(restoredBounds);
        startServiceReconnectTimer();
        liveTestRecorder.startHotkeyMonitor(() -> SwingUtilities.invokeLater(this::toggleLiveTestRecording));
    }

    private void startServiceReconnectTimer() {
        if (serviceError == null || sessions == null) {
            return;
        }
        serviceRetryTimer = new Timer(2000, event -> {
            try {
                if (!sessions.health().ready()) {
                    throw new IllegalStateException("EngineOS Service is not ready.");
                }
                var provider = sessions.codingAgentProvider();
                codingAgent.removeAllItems();
                codingAgent.addItem(provider.displayName());
                serviceError = null;
                status.setText("Ready to begin");
                status.setForeground(SUCCESS);
                status.setIcon(new StatusDotIcon(SUCCESS));
                status.setToolTipText(null);
                updateRequestValidation();
                updateWorkspaceState();
                updateVerificationPlan();
                serviceRetryTimer.stop();
            } catch (IllegalStateException exception) {
                status.setText("Reconnecting to EngineOS...");
                status.setToolTipText(exception.getMessage());
            }
        });
        serviceRetryTimer.setRepeats(true);
        serviceRetryTimer.start();
    }

    private void configureControls() {
        refreshObservationClipboardState();
        status.setText(serviceError == null ? "Ready to begin" : serviceError);
        status.setForeground(MUTED);
        status.setIcon(new StatusDotIcon(SUCCESS));
        requestStatus.setText(requestLifecycle.displayText());
        requestStatus.setForeground(MUTED);
        resultStatus.setText("No result yet — submit a request to begin.");
        resultStatus.setForeground(MUTED);
        observationStatus.setText("No observation package available for the current request.");
        observationStatus.setForeground(MUTED);
        updateActionControls(false);

        stylePrimaryButton(startButton);
        styleSecondaryButton(cancelButton);
        styleSecondaryButton(sendButton);
        stylePrimaryButton(copyResultButton);
        styleSecondaryButton(viewHistoryResultButton);
        styleSecondaryButton(viewTargetButton);
        styleSecondaryButton(recordLiveTestButton);
        recordLiveTestButton.setForeground(ACTIVE_AMBER_BRIGHT);
        recordLiveTestButton.setBorder(new CompoundBorder(
                new LineBorder(new Color(146, 104, 35), 1, true),
                new EmptyBorder(8, 12, 8, 12)));
        styleSecondaryButton(gitButton);
        styleSecondaryButton(refreshSourceControlButton);
        styleSecondaryButton(historyButton);
        styleSecondaryButton(browseButton);
        styleSecondaryButton(workspaceChangeButton);
        styleSecondaryButton(workspaceDoneButton);
        styleSecondaryButton(codingAgent);
        styleTextField(projectField);
        styleTextField(boundaryField);
        styleTextArea(promptArea);
        styleTextArea(resultArea);
        styleTextArea(activityArea);
        styleTextArea(usageArea);
        activityRetention.setFont(SMALL_FONT);
        activityRetention.setForeground(MUTED);
        activityHeadingLabel.setFont(SMALL_FONT);
        activityIndicator.setFont(new Font("Segoe UI", Font.BOLD, 14));
        activityHeading.setOpaque(true);
        activityHeading.setBackground(SURFACE_RAISED);
        activityHeading.setBorder(new CompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)));
        setActivityActive(false);
        usageArea.setEditable(false);
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setText("No output yet.\nSubmit a request to begin; the Coding Agent result will appear here "
                + "after approval and verification.");
        resultPanel.setOpaque(false);
        resultPanel.setVisible(true);
        usageArea.setText("AI usage will appear here after a completed run.");
        styleScrollPane(activityScroll);
        activityScroll.setVisible(false);
        viewTargetButton.setEnabled(false);
        historyButton.setEnabled(serviceError == null && isValidWorkspace());
        viewHistoryResultButton.setEnabled(serviceError == null && isValidWorkspace());
        gitButton.setEnabled(isValidWorkspace());
        historyButton.addActionListener(event -> showHistory());
        gitButton.addActionListener(event -> showGitStatus());
        refreshSourceControlButton.addActionListener(event -> refreshSourceControl());

        model.addActionListener(event -> onboarding.saveModel((String) model.getSelectedItem()));
        model.addActionListener(event -> renderPreflight());
        targetSelector.addItem("WORKSPACE DEFAULT");
        for (var kind : com.engineos.observation.VerificationTarget.Kind.values()) {
            targetSelector.addItem(kind.name());
        }
        targetSelector.addActionListener(event -> {
            if (!updatingTargetSelector) {
                applyTargetSelection();
            }
        });
        projectField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                onboarding.saveWorkspace(projectField.getText());
                updateWorkspaceState();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                onboarding.saveWorkspace(projectField.getText());
                updateWorkspaceState();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                onboarding.saveWorkspace(projectField.getText());
                updateWorkspaceState();
            }
        });
        startButton.addActionListener(event -> startSession());
        cancelButton.addActionListener(event -> {
            if (sessions != null && sessions.cancelActive()) {
                cancelButton.setEnabled(false);
                requestStatus.setText(requestLifecycle.codingAgentActive()
                        ? "Stopping Coding Agent..." : "Cancelling request...");
                requestStatus.setForeground(ERROR);
            }
        });
        browseButton.addActionListener(event -> browseForWorkspace());
        viewTargetButton.addActionListener(event -> {
            if (sessions != null && sessions.showActiveVerificationTarget()) {
                observationStatus.setText("Active verification target shown.");
                observationStatus.setForeground(SUCCESS);
            } else {
                observationStatus.setText("No active verification target is available.");
                observationStatus.setForeground(MUTED);
            }
        });
        recordLiveTestButton.addActionListener(event -> toggleLiveTestRecording());
        copyResultButton.addActionListener(event -> copyCurrentEngineeringResult());
        viewHistoryResultButton.addActionListener(event -> showHistory());
        sendButton.addActionListener(event -> {
            if (currentObservation != null) {
                send(currentObservation);
            } else {
                observationStatus.setText("No observation package available for the current request.");
                observationStatus.setForeground(MUTED);
            }
        });
    }

    private void configureRequestValidation() {
        promptArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                requestLifecycle.replaceWithRecognizedRequest();
                updateRequestValidation();
                updateVerificationPlan();
                refreshSourceControl();
                invalidateCurrentRequestPresentation();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                requestLifecycle.replaceWithRecognizedRequest();
                updateRequestValidation();
                updateVerificationPlan();
                invalidateCurrentRequestPresentation();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                requestLifecycle.replaceWithRecognizedRequest();
                updateRequestValidation();
                updateVerificationPlan();
                invalidateCurrentRequestPresentation();
            }
        });
        promptArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent event) {
                if (DEFAULT_INVESTIGATION_REQUEST.equals(promptArea.getText())) {
                    SwingUtilities.invokeLater(promptArea::selectAll);
                }
            }
        });
        updateRequestValidation();
        updateVerificationPlan();
    }

    private JPanel buildHeader() {
        var header = new HeaderAtmosphere();
        header.setLayout(new BorderLayout(0, 8));
        header.setBorder(new EmptyBorder(4, 8, 12, 8));

        var brand = new JPanel(new BorderLayout(12, 0));
        brand.setOpaque(false);
        brand.add(new EngineLogo(), BorderLayout.WEST);
        var wordmark = new JPanel();
        wordmark.setOpaque(false);
        wordmark.setLayout(new javax.swing.BoxLayout(wordmark, javax.swing.BoxLayout.Y_AXIS));
        var title = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        title.setOpaque(false);
        title.add(label("ENGINE", WORDMARK_FONT, TEXT));
        title.add(label("OS", WORDMARK_FONT, ACCENT));
        wordmark.add(title);
        wordmark.add(label(HEADER_SUBTITLE, SMALL_FONT, TEXT));
        brand.add(wordmark, BorderLayout.CENTER);

        var identity = new JPanel(new BorderLayout(18, 0));
        identity.setOpaque(false);
        var execution = new JPanel();
        execution.setOpaque(false);
        execution.setLayout(new javax.swing.BoxLayout(execution, javax.swing.BoxLayout.Y_AXIS));
        execution.setBorder(new CompoundBorder(
                new MatteBorder(0, 3, 0, 0, ACCENT),
                new EmptyBorder(0, 14, 0, 0)));
        execution.add(label("EXECUTION /", SMALL_FONT, TEXT));
        execution.add(label("OBSERVATION", SMALL_FONT, TEXT));
        execution.add(label("INSIGHT", SMALL_FONT, MUTED));
        identity.add(execution, BorderLayout.WEST);
        identity.add(label("<html>PLAN<br>BUILD<br>VERIFY<br>IMPROVE</html>", SMALL_FONT, MUTED),
                BorderLayout.EAST);

        var mainHeaderRow = new JPanel(new BorderLayout(18, 0));
        mainHeaderRow.setOpaque(false);
        mainHeaderRow.add(brand, BorderLayout.WEST);
        mainHeaderRow.add(identity, BorderLayout.EAST);

        var runtimeStatus = new JPanel(new java.awt.GridLayout(1, 2, 18, 0));
        runtimeStatus.setOpaque(false);
        runtimeStatus.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, new Color(33, 47, 68)),
                new EmptyBorder(6, 0, 0, 0)));
        status.setFont(SMALL_FONT);
        requestStatus.setFont(SMALL_FONT);
        resultStatus.setFont(SMALL_FONT);
        observationStatus.setFont(SMALL_FONT);
        int statusIconGutter = status.getIcon() == null
                ? 0 : status.getIcon().getIconWidth() + status.getIconTextGap();
        requestStatus.setBorder(new EmptyBorder(0, statusIconGutter, 0, 0));
        var leftStatusStack = new JPanel(new java.awt.GridLayout(2, 1, 0, 2));
        leftStatusStack.setOpaque(false);
        leftStatusStack.add(statusRow("SERVICE", status, 64));
        leftStatusStack.add(statusRow("REQUEST", requestStatus, 64));
        var rightStatusStack = new JPanel(new java.awt.GridLayout(2, 1, 0, 2));
        rightStatusStack.setOpaque(false);
        rightStatusStack.add(statusRow("OBSERVATION", observationStatus, 92));
        rightStatusStack.add(statusRow("RESULT", resultStatus, 92));
        runtimeStatus.add(leftStatusStack);
        runtimeStatus.add(rightStatusStack);

        header.add(mainHeaderRow, BorderLayout.NORTH);
        header.add(runtimeStatus, BorderLayout.SOUTH);
        return header;
    }

    private JPanel buildMainContent() {
        var content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.setPreferredSize(new Dimension(0, 0));
        content.setMinimumSize(new Dimension(0, 0));
        var workflow = new ScrollablePanel(new BorderLayout(0, 10));
        workflow.setOpaque(false);
        workflow.add(buildAgentCard(), BorderLayout.NORTH);
        workflow.add(buildRequestCard(), BorderLayout.CENTER);
        var workflowScroll = new JScrollPane(workflow);
        workflowScroll.setBorder(null);
        workflowScroll.setOpaque(false);
        workflowScroll.setPreferredSize(new Dimension(0, 0));
        workflowScroll.setMinimumSize(new Dimension(0, 0));
        workflowScroll.getViewport().setOpaque(false);
        workflowScroll.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        workflowScroll.setVerticalScrollBarPolicy(
                JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        content.add(workflowScroll, BorderLayout.CENTER);

        return content;
    }

    private JPanel buildWorkflowFooter() {
        var footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new javax.swing.BoxLayout(
                footer, javax.swing.BoxLayout.Y_AXIS));
        footer.add(buildPreflightPanel());
        return footer;
    }

    private JPanel buildRequestCard() {
        var card = cardPanel();
        card.setLayout(new BorderLayout(0, 10));
        card.setBackground(new Color(17, 29, 44));
        card.setBorder(new CompoundBorder(
                new MatteBorder(0, 3, 0, 0, ACCENT),
                new EmptyBorder(18, 18, 18, 18)));

        var heading = buildSectionHeading(
                SectionGlyph.Kind.REQUEST,
                "ENGINEERING REQUEST",
                "Describe the engineering request. This becomes the starting point for the proposal and approved task.");

        var editorScroll = new JScrollPane(promptArea);
        styleScrollPane(editorScroll);
        editorScroll.setPreferredSize(new Dimension(0, 150));
        editorScroll.setMinimumSize(new Dimension(0, 120));
        card.setMinimumSize(new Dimension(0, 0));
        configureOnboarding();
        var requestEditor = new JPanel(new BorderLayout(0, 10));
        requestEditor.setOpaque(false);
        requestEditor.add(editorScroll, BorderLayout.CENTER);
        var resultScroll = new JScrollPane(resultArea);
        styleScrollPane(resultScroll);
        resultScroll.setPreferredSize(new Dimension(0, 180));
        resultPanel.add(resultScroll, BorderLayout.CENTER);
        outputActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        outputActions.setOpaque(false);
        outputActions.add(copyResultButton);
        outputActions.add(viewHistoryResultButton);
        outputActions.setVisible(false);
        resultPanel.add(outputActions, BorderLayout.SOUTH);
        var requestBelowPrompt = new JPanel(new BorderLayout(0, 10));
        requestBelowPrompt.setOpaque(false);
        var requestControls = new JPanel();
        requestControls.setOpaque(false);
        requestControls.setLayout(new javax.swing.BoxLayout(
                requestControls, javax.swing.BoxLayout.Y_AXIS));
        var submissionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        submissionRow.setOpaque(false);
        submissionRow.add(submissionConfigPanel);
        submissionRow.add(buildPrimaryActionRow());
        requestControls.add(submissionRow);
        requestControls.add(javax.swing.Box.createVerticalStrut(6));
        var boundaryRow = new JPanel(new BorderLayout(10, 0));
        boundaryRow.setOpaque(false);
        var boundaryLabel = label("BOUNDARY (OPTIONAL)", SMALL_FONT, MUTED);
        boundaryLabel.setPreferredSize(new Dimension(142, boundaryLabel.getPreferredSize().height));
        boundaryRow.add(boundaryLabel, BorderLayout.WEST);
        boundaryField.setToolTipText("Examples: src/ui/**, frontend only, or do not touch infrastructure");
        boundaryField.putClientProperty("JTextField.placeholderText",
                "e.g. src/main/java/com/engineos/ui/**  or  frontend only, exclude infrastructure");
        var boundaryInput = new JPanel(new BorderLayout(8, 0));
        boundaryInput.setOpaque(false);
        boundaryInput.add(boundaryField, BorderLayout.CENTER);
        var copyBoundaryPrompt = new JButton("COPY PROMPT");
        styleSecondaryButton(copyBoundaryPrompt);
        copyBoundaryPrompt.setToolTipText("Copy a question to ask your LLM for a boundary suggestion");
        copyBoundaryPrompt.addActionListener(event -> copyBoundaryPrompt());
        boundaryInput.add(copyBoundaryPrompt, BorderLayout.EAST);
        boundaryRow.add(boundaryInput, BorderLayout.CENTER);
        requestControls.add(boundaryRow);
        var boundaryHelper = label(
                "Ask your LLM: Given this engineering request, suggest a tight path, package, or area boundary.",
                SMALL_FONT, MUTED);
        boundaryHelper.setBorder(new EmptyBorder(3, 152, 0, 0));
        requestControls.add(boundaryHelper);
        requestBelowPrompt.add(requestControls, BorderLayout.NORTH);
        requestBelowPrompt.add(resultPanel, BorderLayout.CENTER);
        requestEditor.add(requestBelowPrompt, BorderLayout.SOUTH);

        activityPanel.setOpaque(false);
        activityHeading.add(activityIndicator, BorderLayout.WEST);
        activityHeading.add(activityHeadingLabel, BorderLayout.CENTER);
        activityPanel.add(activityHeading, BorderLayout.NORTH);
        activityPanel.add(activityScroll, BorderLayout.CENTER);
        activityPanel.add(activityRetention, BorderLayout.SOUTH);
        activityPanel.setVisible(false);

        var lower = new JPanel(new BorderLayout(0, 8));
        lower.setOpaque(false);
        lower.add(activityPanel, BorderLayout.CENTER);

        var requestFooter = new JPanel(new BorderLayout());
        requestFooter.setOpaque(false);
        var footerStack = new JPanel();
        footerStack.setOpaque(false);
        footerStack.setLayout(new javax.swing.BoxLayout(
                footerStack, javax.swing.BoxLayout.Y_AXIS));
        footerStack.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        footerStack.add(javax.swing.Box.createVerticalStrut(8));
        footerStack.add(onboardingPanel);
        footerStack.add(lower);
        requestFooter.add(proposalProgressBanner, BorderLayout.NORTH);
        requestFooter.add(footerStack, BorderLayout.CENTER);

        card.add(heading, BorderLayout.NORTH);
        card.add(requestEditor, BorderLayout.CENTER);
        card.add(requestFooter, BorderLayout.SOUTH);
        return card;
    }

    static JPanel createProposalProgressBanner() {
        var banner = new JPanel(new BorderLayout(8, 0));
        banner.setBackground(new Color(39, 31, 18));
        var accent = new JPanel();
        accent.setBackground(ACTIVE_AMBER_BRIGHT);
        accent.setPreferredSize(new Dimension(4, 1));
        var message = label("Preparing proposal\u2026 EngineOS is analyzing the request.",
                new Font("Segoe UI", Font.BOLD, 13), new Color(254, 243, 199));
        message.setHorizontalAlignment(JLabel.LEFT);
        message.setVerticalAlignment(JLabel.CENTER);
        message.setBorder(new EmptyBorder(10, 0, 10, 8));
        banner.add(accent, BorderLayout.WEST);
        banner.add(message, BorderLayout.CENTER);
        banner.setVisible(false);
        return banner;
    }

    private JPanel buildAgentCard() {
        var card = cardPanel();
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(10, 16, 10, 16));
        card.setMinimumSize(new Dimension(0, 0));

        var heading = buildSectionHeading(
                SectionGlyph.Kind.CONFIGURATION,
                "EXECUTION CONFIGURATION",
                "Workspace, source control, and investigation target.");

        var controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new javax.swing.BoxLayout(controls, javax.swing.BoxLayout.Y_AXIS));

        var projectRow = new JPanel(new BorderLayout(12, 0));
        projectRow.setOpaque(false);
        var projectLabel = label("PROJECT / WORKSPACE", SMALL_FONT, MUTED);
        projectLabel.setPreferredSize(new Dimension(142, projectLabel.getPreferredSize().height));
        projectRow.add(projectLabel, BorderLayout.WEST);

        var workspaceInput = new JPanel(new BorderLayout(10, 0));
        workspaceInput.setOpaque(false);
        workspaceInput.add(browseButton, BorderLayout.WEST);
        workspaceInput.add(projectField, BorderLayout.CENTER);
        projectField.setColumns(28);
        projectRow.add(workspaceInput, BorderLayout.CENTER);
        var projectActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        projectActions.setOpaque(false);
        projectActions.add(recordLiveTestButton);
        projectActions.add(gitButton);
        projectActions.add(historyButton);
        projectRow.add(projectActions, BorderLayout.EAST);
        workspaceEditorPanel.setOpaque(false);
        workspaceEditorPanel.setLayout(new javax.swing.BoxLayout(
                workspaceEditorPanel, javax.swing.BoxLayout.Y_AXIS));
        var workspaceEditorHeading = new JPanel(new BorderLayout(8, 0));
        workspaceEditorHeading.setOpaque(false);
        workspaceEditorHeading.add(label("WORKSPACE SETTINGS", SECTION_FONT, ACCENT), BorderLayout.WEST);
        workspaceDoneButton.addActionListener(event -> {
            workspacePickerExpanded = false;
            updateWorkspaceState();
        });
        workspaceEditorHeading.add(workspaceDoneButton, BorderLayout.EAST);
        workspaceEditorPanel.add(workspaceEditorHeading);
        workspaceEditorPanel.add(javax.swing.Box.createVerticalStrut(8));
        workspaceEditorPanel.add(projectRow);
        workspaceEditorPanel.add(javax.swing.Box.createVerticalStrut(8));

        var targetDetails = new JPanel(new GridLayout(1, 2, 16, 0));
        targetDetails.setOpaque(true);
        targetDetails.setBackground(SURFACE_RAISED);
        targetDetails.setBorder(new CompoundBorder(
                new LineBorder(new Color(38, 68, 78), 1, true),
                new EmptyBorder(8, 12, 8, 12)));
        workspaceTargetLabel.setBorder(new EmptyBorder(1, 0, 1, 0));
        targetDetails.add(targetCard("WORKSPACE IDENTITY", workspaceTargetLabel,
                SectionGlyph.Kind.WORKSPACE));

        var targetPanel = new JPanel(new BorderLayout(0, 6));
        targetPanel.setOpaque(false);
        targetPanel.add(targetCard("EXISTING SYSTEM / INVESTIGATION TARGET",
                verificationTargetLabel, SectionGlyph.Kind.TARGET), BorderLayout.CENTER);
        var targetSelection = new JPanel(new BorderLayout());
        targetSelection.setOpaque(false);
        styleSecondaryButton(targetSelector);
        targetSelector.setToolTipText("Select the investigation target");
        targetSelection.add(targetSelector, BorderLayout.CENTER);
        targetSelection.setPreferredSize(new Dimension(190, 0));
        targetPanel.add(targetSelection, BorderLayout.EAST);
        targetDetails.add(targetPanel);
        workspaceEditorPanel.add(targetDetails);
        workspaceEditorPanel.add(javax.swing.Box.createVerticalStrut(8));

        var sourceRow = new JPanel(new BorderLayout(8, 0));
        sourceRow.setOpaque(true);
        makeSourceControlIndicatorClickable(sourceControlLabel);
        sourceRow.add(targetCard("SOURCE CONTROL", sourceControlLabel, SectionGlyph.Kind.TARGET),
                BorderLayout.CENTER);
        sourceRow.add(refreshSourceControlButton, BorderLayout.EAST);
        sourceRow.setBackground(SURFACE_RAISED);
        sourceRow.setBorder(new CompoundBorder(
                new LineBorder(new Color(38, 68, 78), 1, true),
                new EmptyBorder(8, 12, 8, 12)));
        workspaceEditorPanel.add(sourceRow);

        workspaceSummaryPanel.setOpaque(true);
        workspaceSummaryPanel.setBackground(SURFACE_RAISED);
        workspaceSummaryPanel.setBorder(new CompoundBorder(
                new LineBorder(new Color(38, 68, 78), 1, true),
                new EmptyBorder(9, 12, 9, 12)));
        workspaceSummaryLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        workspaceSummaryLabel.setForeground(TEXT);
        makeSourceControlIndicatorClickable(workspaceSummaryLabel);
        workspaceSummaryPanel.add(workspaceSummaryLabel, BorderLayout.CENTER);
        workspaceChangeButton.addActionListener(event -> {
            workspacePickerExpanded = true;
            updateWorkspaceState();
        });
        workspaceSummaryPanel.add(workspaceChangeButton, BorderLayout.EAST);
        controls.add(workspaceSummaryPanel);
        controls.add(workspaceEditorPanel);
        boolean validWorkspace = isValidWorkspace();
        workspaceSummaryPanel.setVisible(validWorkspace && !workspacePickerExpanded);
        workspaceEditorPanel.setVisible(!validWorkspace || workspacePickerExpanded);
        controls.add(javax.swing.Box.createVerticalStrut(10));

        var agentModelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        agentModelRow.setOpaque(false);
        var agentTitle = label("Coding Agent", SECTION_FONT, ACCENT);
        Dimension agentTitleSize = agentTitle.getPreferredSize();
        agentTitle.setPreferredSize(new Dimension(
                Math.max(agentTitleSize.width, 92), agentTitleSize.height));
        agentTitle.setMinimumSize(agentTitle.getPreferredSize());
        agentTitle.setHorizontalAlignment(JLabel.LEFT);
        var agentField = new JPanel(new BorderLayout(8, 0));
        agentField.setOpaque(false);
        agentField.add(agentTitle, BorderLayout.WEST);
        agentField.add(codingAgent, BorderLayout.CENTER);
        agentModelRow.add(agentField);
        var modelTitle = label("Model", SECTION_FONT, ACCENT);
        var modelField = new JPanel(new BorderLayout(8, 0));
        modelField.setOpaque(false);
        modelField.add(modelTitle, BorderLayout.WEST);
        modelField.add(model, BorderLayout.CENTER);
        agentModelRow.add(modelField);

        submissionConfigPanel.setOpaque(false);
        submissionConfigPanel.add(agentModelRow, BorderLayout.CENTER);

        card.add(heading, BorderLayout.NORTH);
        card.add(controls, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildPrimaryActionRow() {
        var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        startButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        startButton.setBorder(new CompoundBorder(
                new LineBorder(ACCENT, 1, true),
                new EmptyBorder(10, 16, 10, 16)));
        actions.add(startButton);
        actions.add(cancelButton);
        return actions;
    }

    private void copyBoundaryPrompt() {
        String prompt = BOUNDARY_PROMPT + "\n\nEngineering request:\n" + promptArea.getText().trim();
        if (EngineeringPartnerClipboard.copyToClipboard(prompt)) {
            observationStatus.setText("\u2713 Boundary prompt copied for your LLM.");
            observationStatus.setForeground(SUCCESS);
        } else {
            observationStatus.setText("Unable to copy the boundary prompt.");
            observationStatus.setForeground(ERROR);
        }
    }

    private void toggleLiveTestRecording() {
        openHotkeyRecorder();
    }

    private void openHotkeyRecorder() {
        if (liveTestRecorder.isRecording()) {
            liveTestRecorder.stop();
            return;
        }
        try {
            Path workspace = Path.of(projectField.getText().trim()).toAbsolutePath().normalize();
            WindowSelectionDialog dialog = new WindowSelectionDialog(frame, workspace);
            LiveTestRecorder.WindowInfo selected = dialog.showAndSelect();
            if (selected != null) {
                startLiveTest(workspace, new LiveTestRecorder.CaptureTarget(
                        LiveTestRecorder.targetName(workspace),
                        java.util.Optional.of(selected.title())), selected);
            }
        } catch (RuntimeException exception) {
            status.setText("Unable to open live test recorder: " + exception.getMessage());
            status.setForeground(ERROR);
        }
    }

    private void startLiveTest(
            Path workspace, LiveTestRecorder.CaptureTarget target, LiveTestRecorder.WindowInfo selectedWindow) {
        liveTestGeneration++;
        status.setText("Starting live test recording...");
        status.setToolTipText(null);
        status.setForeground(ACTIVE_AMBER);
        status.setIcon(new StatusDotIcon(ACTIVE_AMBER));
        liveTestRecorder.start(workspace, promptArea.getText(), currentRunId, target, selectedWindow,
                result -> SwingUtilities.invokeLater(() -> finishLiveTestRecording(result)));
        recordLiveTestButton.setText("Stop live test");
        liveTestStartedAt = System.currentTimeMillis();
        liveTestTargetName = target.windowTitle().orElse(target.name());
        liveTestRemainingTimer.start();
        updateLiveTestRemaining();
        status.setText("Recording\u2026 " + liveTestTargetName);
        status.setForeground(ACTIVE_AMBER);
        liveTestOverlay = new RecordingOverlay(selectedWindow, liveTestRecorder::stop, liveTestRecorder);
    }

    private void finishLiveTestRecording(LiveTestRecorder.RecordingResult result) {
        liveTestRemainingTimer.stop();
        liveTestTargetName = null;
        recordLiveTestButton.setText("Record live test");
        if (liveTestOverlay != null) {
            liveTestOverlay.close();
            liveTestOverlay = null;
        }
        if (!result.succeeded()) {
            status.setText("Live test recording failed: " + result.error());
            status.setForeground(ERROR);
            status.setIcon(new StatusDotIcon(ERROR));
            return;
        }
        status.setToolTipText(null);
        status.setIcon(new StatusDotIcon(SUCCESS));
        status.setForeground(SUCCESS);
        status.setText("Recording saved. Copying paths and opening folder...");
        String fallbackNote = result.target().fallbackReason().isBlank()
                ? "" : " " + result.target().fallbackReason();
        long generation = liveTestGeneration;
        new SwingWorker<LiveTestRecorder.DeliveryResult, Void>() {
            @Override
            protected LiveTestRecorder.DeliveryResult doInBackground() {
                return LiveTestRecorder.deliverRecording(result);
            }

            @Override
            protected void done() {
                try {
                    var delivery = get();
                    if (generation != liveTestGeneration) {
                        return;
                    }
                    boolean delivered = delivery.copied() && delivery.opened();
                    status.setText(delivered
                            ? LiveTestRecorder.deliverySummary(result, delivery) + fallbackNote
                            : "Recording saved. " + delivery.error() + fallbackNote);
                    status.setToolTipText(delivered ? null
                            : result.video() + " | " + result.sidecar() + " | " + delivery.error());
                    status.setForeground(delivered ? SUCCESS : ACTIVE_AMBER);
                    status.setIcon(new StatusDotIcon(delivered ? SUCCESS : ACTIVE_AMBER));
                    String message = LiveTestRecorder.deliverySummary(result, delivery)
                            + "\n\nVideo:\n" + result.video().toAbsolutePath().normalize()
                            + "\n\nMarkdown:\n" + result.sidecar().toAbsolutePath().normalize();
                    JOptionPane.showMessageDialog(frame, message, "Live test recording complete",
                            delivered ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                } catch (Exception exception) {
                    DesktopDiagnostics.exception("live-test-delivery", exception);
                    if (generation == liveTestGeneration) {
                        status.setText("Recording saved. Delivery failed: " + exception.getMessage());
                        status.setForeground(ACTIVE_AMBER);
                        status.setIcon(new StatusDotIcon(ACTIVE_AMBER));
                    }
                }
            }
        }.execute();
    }

    private void updateLiveTestRemaining() {
        long elapsed = Math.max(0, (System.currentTimeMillis() - liveTestStartedAt) / 1000);
        long remaining = Math.max(0, LiveTestRecorder.DEFAULT_DURATION.toSeconds() - elapsed);
        status.setText("Recording " + (liveTestTargetName == null ? "live test" : liveTestTargetName)
                + " — " + remaining + " seconds remaining.");
    }

    private JPanel buildSectionHeading(
            SectionGlyph.Kind kind,
            String title,
            String subtitle) {
        var heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        heading.setBorder(new EmptyBorder(0, 0, 8, 0));
        heading.add(new SectionGlyph(kind), BorderLayout.WEST);

        var text = new JPanel(new BorderLayout(0, 4));
        text.setOpaque(false);
        text.add(label(title, MAJOR_SECTION_FONT, TEXT), BorderLayout.NORTH);
        text.add(label(subtitle, BODY_FONT, MUTED), BorderLayout.CENTER);

        var rule = new JPanel();
        rule.setBackground(ACCENT);
        rule.setPreferredSize(new Dimension(0, 2));
        rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));

        heading.add(text, BorderLayout.CENTER);
        heading.add(rule, BorderLayout.SOUTH);
        return heading;
    }

    private JPanel buildPreflightPanel() {
        preflightExpanded = false;
        preflightPanel.setOpaque(true);
        preflightPanel.setBackground(SURFACE_RAISED);
        preflightPanel.setBorder(new CompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)));
        var header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.add(label("PRE-FLIGHT", SECTION_FONT, TEXT), BorderLayout.WEST);
        preflightSummaryLabel.setFont(SMALL_FONT);
        preflightSummaryLabel.setForeground(MUTED);
        preflightSummaryLabel.setHorizontalAlignment(JLabel.LEFT);
        makeSourceControlIndicatorClickable(preflightSummaryLabel);
        header.add(preflightSummaryLabel, BorderLayout.CENTER);
        styleSecondaryButton(preflightToggle);
        preflightToggle.addActionListener(event -> {
            preflightExpanded = !preflightExpanded;
            preflightLabel.setVisible(preflightExpanded);
            preflightToggle.setText(preflightExpanded ? "COLLAPSE \u25B4" : "EXPAND \u25BE");
            preflightPanel.revalidate();
            preflightPanel.repaint();
        });
        header.add(preflightToggle, BorderLayout.EAST);
        preflightPanel.add(header, BorderLayout.NORTH);
        preflightLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        preflightLabel.setForeground(TEXT);
        preflightLabel.setVerticalAlignment(JLabel.TOP);
        preflightLabel.setText("<html><font color='#94A3B8'>Preparing pre-flight state...</font></html>");
        preflightLabel.setVisible(false);
        preflightPanel.add(preflightLabel, BorderLayout.CENTER);
        return preflightPanel;
    }

    private void makeSourceControlIndicatorClickable(JLabel indicator) {
        indicator.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        indicator.setToolTipText("Review Git changes, commit, or push");
        indicator.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    showGitStatus();
                }
            }
        });
    }

    private static JPanel statusRow(String label, JLabel value) {
        return statusRow(label, value, 0);
    }

    private static JPanel statusRow(String label, JLabel value, int labelWidth) {
        var row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(2, 0, 2, 0));
        var labelComponent = ObservationWindow.label(label, SMALL_FONT, MUTED);
        if (labelWidth > 0) {
            var fixedSize = new Dimension(labelWidth, labelComponent.getPreferredSize().height);
            labelComponent.setMinimumSize(fixedSize);
            labelComponent.setPreferredSize(fixedSize);
            labelComponent.setMaximumSize(fixedSize);
        }
        var labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = 0;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.fill = GridBagConstraints.HORIZONTAL;
        labelConstraints.weightx = 0;
        labelConstraints.insets = new Insets(0, 0, 0, 12);
        row.add(labelComponent, labelConstraints);
        var valueConstraints = new GridBagConstraints();
        valueConstraints.gridx = 1;
        valueConstraints.gridy = 0;
        valueConstraints.anchor = GridBagConstraints.WEST;
        valueConstraints.fill = GridBagConstraints.HORIZONTAL;
        valueConstraints.weightx = 1;
        row.add(value, valueConstraints);
        return row;
    }

    private void configureOnboarding() {
        onboardingPanel.setBackground(SURFACE_RAISED);
        onboardingPanel.setBorder(new CompoundBorder(
                new LineBorder(new Color(61, 82, 116), 1, true),
                new EmptyBorder(8, 12, 8, 12)));

        var explanation = new JTextArea("""
                EngineOS works with an AI Engineering Partner such as ChatGPT,
                Claude, or Gemini.

                Copy the instructions to your clipboard, then paste them into
                your Engineering Partner.

                Give your Engineering Partner the EngineOS instructions below.
                It will learn how to work with EngineOS and prepare Engineering
                Requests for the Coding Agent.

                After your Engineering Partner is prepared, tell it what you
                want to accomplish and paste its Engineering Request here.
                """);
        explanation.setEditable(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setOpaque(false);
        explanation.setBorder(null);
        explanation.setFont(BODY_FONT);
        explanation.setForeground(MUTED);

        var onboardingHeading = new JPanel(new BorderLayout(8, 0));
        onboardingHeading.setOpaque(false);
        var setupButton = new JButton("ENGINEERING PARTNER SETUP");
        styleSecondaryButton(setupButton);
        onboardingHeading.add(label("ENGINEERING PARTNER SETUP", SECTION_FONT, TEXT),
                BorderLayout.WEST);
        onboardingHeading.add(setupButton, BorderLayout.EAST);

        var instructions = new JTextArea(8, 60);
        instructions.setText(EngineeringPartnerInstructions.TEXT);
        instructions.setEditable(false);
        instructions.setLineWrap(true);
        instructions.setWrapStyleWord(true);
        styleTextArea(instructions);
        var instructionsScroll = new JScrollPane(instructions);
        styleScrollPane(instructionsScroll);

        var copyButton = new JButton("COPY TO CLIPBOARD");
        stylePrimaryButton(copyButton);
        copyButton.addActionListener(event -> copyInstructions(false));

        var instructionsPanel = new JPanel(new BorderLayout(0, 8));
        instructionsPanel.setOpaque(false);
        instructionsPanel.add(label("ENGINEERING PARTNER INSTRUCTIONS", SMALL_FONT, MUTED),
                BorderLayout.NORTH);
        instructionsPanel.add(instructionsScroll, BorderLayout.CENTER);
        instructionsPanel.add(copyButton, BorderLayout.SOUTH);

        onboardingPanel.add(onboardingHeading, BorderLayout.NORTH);
        var details = new JPanel(new BorderLayout(0, 8));
        details.setOpaque(false);
        details.add(explanation, BorderLayout.NORTH);
        details.add(instructionsPanel, BorderLayout.CENTER);
        details.setVisible(false);
        setupButton.addActionListener(event -> {
            details.setVisible(!details.isVisible());
            onboardingPanel.revalidate();
            onboardingPanel.repaint();
        });
        onboardingPanel.add(details, BorderLayout.CENTER);
        onboardingPanel.setVisible(!isValidWorkspace());
    }

    private void copyInstructions(boolean dismissOnboarding) {
        if (!EngineeringPartnerClipboard.copyToClipboard()) {
            observationStatus.setText("Unable to copy Engineering Partner instructions to clipboard.");
            observationStatus.setForeground(ERROR);
            return;
        }
        if (dismissOnboarding) {
            onboarding.markPrepared();
            onboardingPanel.setVisible(false);
            frame.setSize(1040, 680);
            frame.revalidate();
        }
        observationStatus.setText("\u2713 Engineering Partner instructions copied to clipboard.");
        observationStatus.setForeground(SUCCESS);
    }

    private void updateRequestValidation() {
        boolean valid = EngineeringRequestValidator.isValid(promptArea.getText());
        if (valid && !requestLifecycle.active()) {
            requestStatus.setText("Request ready.");
        }
        requestValidation.setText(valid
                ? ""
                : "Enter a request to continue.");
        requestValidation.setForeground(valid ? SUCCESS : MUTED);
        requestValidation.setFont(SMALL_FONT);
        updateActionControls(valid);
        renderSourceControl();
        renderPreflight();
        startButton.setBackground(valid ? ACCENT : SURFACE_RAISED);
        startButton.setForeground(valid ? Color.WHITE : MUTED);
        startButton.setBorder(new CompoundBorder(
                new LineBorder(valid ? ACCENT : BORDER, 1, true),
                new EmptyBorder(10, 16, 10, 16)));
    }

    private void updateActionControls(boolean validRequest) {
        boolean serviceReady = sessions != null && serviceError == null;
        boolean active = requestLifecycle.active();
        startButton.setEnabled(serviceReady && validRequest && !active);
        cancelButton.setEnabled(serviceReady && requestLifecycle.stopAvailable());
        cancelButton.setText(requestLifecycle.codingAgentActive()
                ? "STOP CODING AGENT" : "CANCEL REQUEST");
    }

    private boolean isValidWorkspace() {
        if (sessions == null || serviceError != null) {
            return false;
        }
        String project = projectField.getText().trim();
        if (project.isBlank()) {
            return false;
        }
        try {
            return sessions.verificationPlan(Path.of(project), null).workspaceRoot() != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void updateWorkspaceState() {
        boolean valid = isValidWorkspace();
        historyButton.setEnabled(valid);
        viewHistoryResultButton.setEnabled(valid);
        gitButton.setEnabled(valid);
        onboardingPanel.setVisible(!valid);
        workspaceSummaryLabel.setText(workspaceSummaryText(valid));
        workspaceSummaryPanel.setVisible(valid && !workspacePickerExpanded);
        workspaceEditorPanel.setVisible(!valid || workspacePickerExpanded);
        if (onboardingPanel.getParent() != null) {
            onboardingPanel.getParent().revalidate();
            onboardingPanel.getParent().repaint();
        }
        if (workspaceSummaryPanel.getParent() != null) {
            workspaceSummaryPanel.getParent().revalidate();
            workspaceSummaryPanel.getParent().repaint();
        }
        refreshSourceControl();
        refreshSourceControlButton.setEnabled(isValidWorkspace());
        updateVerificationPlan();
    }

    private String workspaceSummaryText(boolean valid) {
        if (!valid) {
            return "<html><b>WORKSPACE</b><br><font color='#94A3B8'>Select a workspace to begin.</font></html>";
        }
        String path = escape(projectField.getText().trim());
        if (currentGitSnapshot == null) {
            return "<html><font color='#E5E7EB'>" + path
                    + "</font> <font color='#CBD5E1'>&middot; Reading Git status...</font></html>";
        }
        if (!currentGitSnapshot.repository()) {
            return "<html><font color='#E5E7EB'>" + path
                    + "</font> <font color='#CBD5E1'>&middot; No Git repository detected</font></html>";
        }
        String branch = currentGitSnapshot.branch().isBlank()
                ? "branch unavailable" : escape(currentGitSnapshot.branch());
        String shortSha = currentGitSnapshot.headSha().isBlank()
                ? "" : " · " + escape(currentGitSnapshot.headSha().substring(
                        0, Math.min(7, currentGitSnapshot.headSha().length())));
        boolean clean = currentGitSnapshot.clean();
        String state = clean ? "CLEAN" : "CHANGED";
        String stateColor = clean ? "#45D483" : "#FBBF24";
        String stats = clean ? "" : " · " + currentGitSnapshot.changes().size() + " files · +"
                + currentGitSnapshot.linesAdded() + "/-" + currentGitSnapshot.linesRemoved();
        return "<html><font color='#E5E7EB'><b>" + path + "</b></font>"
                + " <font color='#CBD5E1'>&middot; <b>" + branch + shortSha
                + " &middot; </font><font color='" + stateColor + "'><b>" + state + "</b></font>"
                + "<font color='#CBD5E1'>" + stats + "</font></html>";
    }

    private void refreshSourceControl() {
        long refreshGeneration = ++sourceControlRefreshGeneration;
        if (sessions == null || serviceError != null) {
            currentGitSnapshot = null;
            currentGitWorkspace = null;
            sourceControlLabel.setText("<html><font color='#94A3B8'>Unavailable until the service is connected.</font></html>");
            refreshSourceControlButton.setEnabled(false);
            return;
        }
        String value = projectField.getText().trim();
        Path workspace;
        try {
            workspace = Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            currentGitSnapshot = null;
            currentGitWorkspace = null;
            sourceControlLabel.setText("<html><font color='#F87171'>Invalid workspace path</font></html>");
            refreshSourceControlButton.setEnabled(false);
            return;
        }
        currentGitWorkspace = workspace;
        refreshSourceControlButton.setEnabled(true);
        sourceControlLabel.setText("<html><font color='#94A3B8'>Reading Git pre-flight state...</font></html>");
        new SwingWorker<GitSnapshot, Void>() {
            @Override
            protected GitSnapshot doInBackground() {
                return sessions.sourceControl(workspace);
            }

            @Override
            protected void done() {
                try {
                    if (refreshGeneration != sourceControlRefreshGeneration
                            || !workspace.equals(currentGitWorkspace)) {
                        return;
                    }
                    currentGitSnapshot = get();
                    renderSourceControl();
                } catch (Exception exception) {
                    if (refreshGeneration != sourceControlRefreshGeneration) {
                        return;
                    }
                    currentGitSnapshot = GitSnapshot.unavailable(workspace);
                    sourceControlLabel.setText("<html><font color='#F87171'>Git status could not be read: "
                            + escape(exception.getMessage()) + "</font></html>");
                    renderPreflight();
                }
            }
        }.execute();
    }

    private void renderSourceControl() {
        GitSnapshot snapshot = currentGitSnapshot;
        if (snapshot == null) {
            return;
        }
        if (!snapshot.repository()) {
            sourceControlLabel.setToolTipText(null);
            sourceControlLabel.setText("<html><font color='#FBBF24'><b>No Git repository detected</b></font>"
                    + "<br><font color='#94A3B8'>This is valid; EngineOS will continue without Git baseline evidence.</font></html>");
            workspaceSummaryLabel.setText(workspaceSummaryText(isValidWorkspace()));
            renderPreflight();
            return;
        }
        Path workspace = currentGitWorkspace;
        sourceControlLabel.setToolTipText(snapshot.repositoryRoot().toString());
        boolean workspaceRepository = workspace != null
                && snapshot.repositoryRoot().toString().equalsIgnoreCase(workspace.toString());
        String scope = workspaceRepository ? "Workspace repository" : "Parent repository";
        String shortSha = snapshot.headSha().isBlank()
                ? "unavailable"
                : snapshot.headSha().substring(0, Math.min(7, snapshot.headSha().length()));
        String state = snapshot.clean() ? "CLEAN" : "CHANGED";
        String color = snapshot.clean() ? "#45D483" : "#FBBF24";
        String indicator = snapshot.clean() ? "\u2713 READY" : "\u26A0 Existing changes detected";
        String implications = requestImplication(snapshot.clean());
        String changes = snapshot.clean() ? "" : "<br>" + snapshot.changes().size()
                + " files modified &nbsp; +" + snapshot.linesAdded() + " / -" + snapshot.linesRemoved();
        sourceControlLabel.setText("<html><b>Git</b> &bull; " + escape(snapshot.branch())
                + " &bull; " + shortSha + " &nbsp; <font color='" + color + "'><b>"
                + state + " &nbsp; " + indicator + "</b></font>"
                + changes + "<br><font color='#94A3B8'>Repository: "
                + escape(compactPath(snapshot.repositoryRoot().toString(), 72)) + " &nbsp; Scope: " + scope
                + "</font><br><font color='" + color + "'>" + implications + "</font></html>");
        workspaceSummaryLabel.setText(workspaceSummaryText(isValidWorkspace()));
        renderPreflight();
    }

    private String requestImplication(boolean clean) {
        String request = promptArea.getText().toLowerCase();
        String kind = request.contains("investigat") || request.contains("understand")
                ? "investigation" : request.contains("verif")
                ? "verification" : "implementation";
        return clean
                ? "\u2713 Good baseline for " + kind
                : "\u26A0 Existing changes will be present during " + kind;
    }

    private void renderPreflight() {
        if (sessions == null || serviceError != null) {
            preflightLabel.setText(preflightHtml(
                    "\uD83D\uDD34 NOT READY", ERROR,
                    "AGENT", "\u26A0 Connect to EngineOS to continue"));
            setPreflightSummary("\uD83D\uDD34 NOT READY", "Connect to EngineOS to continue", ERROR);
            return;
        }
        String project = projectField.getText().trim();
        Path workspace;
        try {
            workspace = project.isBlank() ? null : Path.of(project).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            preflightLabel.setText(preflightHtml(
                    "\uD83D\uDD34 NOT READY", ERROR,
                    "TARGET", "\u26A0 Invalid workspace path"));
            setPreflightSummary("\uD83D\uDD34 NOT READY", "Invalid workspace", ERROR);
            return;
        }
        boolean requestValid = EngineeringRequestValidator.isValid(promptArea.getText());
        boolean workspaceValid = workspace != null && isValidWorkspace();
        String request = promptArea.getText().trim();
        String requestKind = request.toLowerCase().contains("investigat")
                || request.toLowerCase().contains("understand")
                ? "Investigation"
                : request.toLowerCase().contains("verif") ? "Verification" : "Code Change";
        var plan = workspaceValid ? sessions.verificationPlan(workspace, request) : null;
        boolean verificationAvailable = plan != null && plan.resolved();
        boolean sourceLoaded = currentGitSnapshot != null
                && workspace.equals(currentGitWorkspace);
        boolean ready = workspaceValid && requestValid
                && sourceLoaded && codingAgent.getSelectedItem() != null
                && model.getSelectedItem() != null;
        boolean review = ready && !currentGitSnapshot.clean();
        String overall = review ? "\uD83D\uDFE1 REVIEW" : ready ? "\uD83D\uDFE2 READY" : "\uD83D\uDD34 NOT READY";
        Color overallColor = review ? ACTIVE_AMBER : ready ? SUCCESS : ERROR;
        String overallSummary = review ? "Existing changes detected"
                : ready ? "Request ready"
                : preflightBlocker(requestValid, workspaceValid, verificationAvailable,
                        sourceLoaded, codingAgent.getSelectedItem() != null,
                        model.getSelectedItem() != null);

        String targetName = workspace == null || project.isBlank()
                ? "Unavailable"
                : workspace.getFileName() == null ? workspace.toString() : workspace.getFileName().toString();
        String target = requestValid && workspaceValid
                ? "\u2713 " + targetName + "<br><font color='#94A3B8'>" + escape(project) + "</font>"
                : "\u26A0 Select a workspace and enter a request";

        String source;
        if (!sourceLoaded) {
            source = "\u26A0 Reading Git state...";
        } else if (!currentGitSnapshot.repository()) {
            source = "\u26A0 No Git repository detected";
        } else {
            String scope = currentGitSnapshot.repositoryRoot().toString()
                    .equalsIgnoreCase(workspace.toAbsolutePath().normalize().toString())
                    ? "Workspace repository" : "Parent repository";
            String sha = currentGitSnapshot.headSha().isBlank()
                    ? "unavailable"
                    : currentGitSnapshot.headSha().substring(0,
                    Math.min(7, currentGitSnapshot.headSha().length()));
            source = (currentGitSnapshot.clean() ? "\u2713" : "\u26A0")
                    + " Git &bull; " + escape(currentGitSnapshot.repositoryRoot().toString())
                    + "<br>&nbsp;&nbsp;Scope: " + scope
                    + " &nbsp; Branch: " + escape(currentGitSnapshot.branch())
                    + " &nbsp; HEAD: " + sha
                    + "<br>&nbsp;&nbsp;Working tree: <b>"
                    + (currentGitSnapshot.clean() ? "CLEAN" : "CHANGED") + "</b>"
                    + (currentGitSnapshot.clean() ? "" : " &nbsp; "
                    + currentGitSnapshot.changes().size() + " files &nbsp; +"
                    + currentGitSnapshot.linesAdded() + " / -"
                    + currentGitSnapshot.linesRemoved());
        }

        String agent = codingAgent.getSelectedItem() == null
                ? "\u26A0 Choose a coding agent"
                : "\u2713 " + escape(String.valueOf(codingAgent.getSelectedItem()))
                + " &nbsp; Model: " + escape(String.valueOf(model.getSelectedItem()));
        String collapsedSummary = overallSummary;
        if (review && currentGitSnapshot != null) {
            collapsedSummary = "Existing changes: " + currentGitSnapshot.changes().size()
                    + " files, +" + currentGitSnapshot.linesAdded() + " / -"
                    + currentGitSnapshot.linesRemoved() + " \u2022 "
                    + String.valueOf(codingAgent.getSelectedItem()) + " \u2022 Model: "
                    + String.valueOf(model.getSelectedItem());
        } else if (ready) {
            collapsedSummary = "Request ready \u2022 "
                    + String.valueOf(codingAgent.getSelectedItem()) + " \u2022 Model: "
                    + String.valueOf(model.getSelectedItem());
        }
        String verification = plan == null || !plan.resolved()
                ? "\u26A0 Verification details are still loading"
                : verificationAvailable
                ? "\u2713 Available &nbsp; Target: "
                        + (plan.targetKind() == null ? "WORKSPACE DEFAULT" : escape(plan.targetKind().name()))
                : "\u26A0 Limited &nbsp; No separate behavioral verification target";
        String reviewNote = review
                ? "<br><font color='#FBBF24'>Existing changes detected before this request.</font>" : "";
        preflightLabel.setText("<html><font color='" + colorHex(overallColor)
                + "'><b>" + overall + "</b></font>"
                + "<br><b>TARGET</b> " + target
                + "<br><b>SOURCE CONTROL</b> " + source + reviewNote
                + "<br><b>AGENT</b> " + agent
                + "<br><b>REQUEST</b> <font color='#94A3B8'>"
                + escape(requestKind) + "</font> &nbsp; " + (requestValid ? "\u2713" : "\u26A0")
                + " " + escape(compact(request))
                + "<br><b>VERIFICATION</b> " + verification + "</html>");
        setPreflightSummary(overall, collapsedSummary, overallColor);
    }

    private void setPreflightSummary(String overall, String summary, Color color) {
        preflightSummaryLabel.setText("<html><nobr><font color='" + colorHex(color)
                + "'><b>" + overall + "</b></font> &nbsp; " + escape(summary) + "</nobr></html>");
    }

    private static String preflightHtml(String overall, Color color, String section, String detail) {
        return "<html><font color='" + colorHex(color) + "'><b>" + overall + "</b></font>"
                + "<br><br><b>" + section + "</b><br>" + detail + "</html>";
    }

    private static String preflightBlocker(
            boolean requestValid,
            boolean workspaceValid,
            boolean verificationAvailable,
            boolean sourceLoaded,
            boolean codingAgentAvailable,
            boolean modelAvailable) {
        if (!workspaceValid) {
            return "Select a valid workspace";
        }
        if (!requestValid) {
            return "Enter an engineering request";
        }
        if (!sourceLoaded) {
            return "Reading Git status";
        }
        if (!verificationAvailable) {
            return "Verification details are still loading";
        }
        if (!codingAgentAvailable) {
            return "Choose a coding agent";
        }
        if (!modelAvailable) {
            return "Select a model";
        }
        return "Complete the highlighted setup";
    }

    private static String compact(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 120 ? normalized.substring(0, 117) + "..." : normalized;
    }

    private static String colorHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private void recoverPersistedObservation() {
        if (!isValidWorkspace()) {
            return;
        }
        var recovered = packages.findLatestCompleted().orElse(null);
        if (recovered == null || recovered.runId() == null) {
            return;
        }
        var record = findHistoryRecord(recovered.runId());
        if (record == null || record.workspace() == null
                || !Path.of(record.workspace()).toAbsolutePath().normalize()
                .equals(Path.of(projectField.getText().trim()).toAbsolutePath().normalize())) {
            return;
        }
        currentObservation = recovered;
        currentRunId = recovered.runId();
        currentHistoryRecord = record;
        requestLifecycle.serviceState(EngineOsService.ExecutionState.GREEN);
        requestStatus.setText(requestLifecycle.displayText());
        requestStatus.setForeground(SUCCESS);
        resultStatus.setText("Engineering Result available for the current request.");
        resultStatus.setForeground(SUCCESS);
        resultArea.setText(HistoryPresentation.format(record));
        resultArea.setCaretPosition(0);
        resultPanel.setVisible(true);
        setOutputActionsVisible(true);
        observationStatus.setText("Observation ready for current request.");
        observationStatus.setForeground(SUCCESS);
        refreshObservationClipboardState();
    }

    private void updateVerificationPlan() {
        if (sessions == null || serviceError != null) {
            workspaceTargetLabel.setText("");
            verificationTargetLabel.setText("");
            targetSelector.setEnabled(false);
            return;
        }
        String project = projectField.getText().trim();
        Path projectPath;
        try {
            projectPath = project.isBlank() ? null : Path.of(project);
        } catch (RuntimeException exception) {
            workspaceTargetLabel.setText("<html><font color='#94A3B8'>WORKSPACE</font><br>"
                    + escape(project) + "</html>");
            verificationTargetLabel.setText("<html><font color='#F87171'>VERIFICATION TARGET</font><br>"
                    + "Invalid workspace path</html>");
            targetSelector.setEnabled(false);
            return;
        }
        var plan = sessions.verificationPlan(projectPath, promptArea.getText());
        if (!plan.resolved()) {
            workspaceTargetLabel.setText("<html><font color='#94A3B8'>" + escape(project) + "</font></html>");
            verificationTargetLabel.setText("<html><font color='#F87171'>" + escape(plan.error()) + "</font></html>");
            targetSelector.setEnabled(false);
            return;
        }
        targetSelector.setEnabled(plan.targetKind() != null);
        updatingTargetSelector = true;
        try {
            targetSelector.setSelectedItem(plan.explicitTarget()
                    ? plan.targetKind().name() : "WORKSPACE DEFAULT");
        } finally {
            updatingTargetSelector = false;
        }
        if (plan.targetKind() == null) {
            workspaceTargetLabel.setText("<html><font color='#94A3B8'>" + escape(project) + "</font></html>");
            verificationTargetLabel.setText("<html><font color='#FBBF24'>Repository investigation target</font></html>");
            return;
        }
        String workspaceKind = plan.workspaceKind().name();
        String targetKind = plan.targetKind().name();
        String workspacePath = plan.workspaceRoot().toString();
        workspaceTargetLabel.setToolTipText(workspacePath);
        String workspaceName = plan.workspaceRoot().getFileName() == null
                ? workspacePath : plan.workspaceRoot().getFileName().toString();
        workspaceTargetLabel.setText(workspaceValue(workspaceKind, workspaceName));
        String targetSuffix = plan.explicitTarget()
                ? ""
                : " (workspace default)";
        String warning = plan.mismatch()
                ? "<br><font color='#FBBF24'>\u26A0 Different from workspace target</font>"
                : "";
        verificationTargetLabel.setText(targetValue(targetKind + targetSuffix, null, warning));
    }

    private void browseForWorkspace() {
        var chooser = new JFileChooser();
        chooser.setDialogTitle("Select EngineOS workspace");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        try {
            Path current = Path.of(projectField.getText().trim());
            if (java.nio.file.Files.isDirectory(current)) {
                chooser.setCurrentDirectory(current.toFile());
            }
        } catch (IllegalArgumentException ignored) {
            // Keep the native chooser usable when the manually entered path is invalid.
        }
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            workspacePickerExpanded = false;
            projectField.setText(chooser.getSelectedFile().getAbsolutePath());
            updateVerificationPlan();
        }
    }

    private static String targetValue(String name, String path, String warning) {
        String pathHtml = path == null ? "" : "<br><font size='-2' color='#94A3B8'>"
                + escape(path) + "</font>";
        return "<html><font size='-1'><b>" + escape(name) + "</b></font>"
                + pathHtml + (warning == null ? "" : warning) + "</html>";
    }

    private static String workspaceValue(String name, String path) {
        return "<html><font size='-1'><b>" + escape(name) + "</b></font>"
                + "&nbsp;&nbsp;<font size='-1' color='#94A3B8'>"
                + escape(path) + "</font></html>";
    }

    private static String compactPath(String path, int maxLength) {
        if (path == null || path.length() <= maxLength) {
            return path;
        }
        int tailLength = Math.max(12, maxLength - 3);
        return "..." + path.substring(path.length() - tailLength);
    }

    private static JPanel targetCard(String title, JLabel value, SectionGlyph.Kind kind) {
        var panel = new JPanel(new BorderLayout(10, 0));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(0, 0, 0, 0));
        panel.add(new SectionGlyph(kind), BorderLayout.WEST);
        var text = new JPanel(new BorderLayout(0, 3));
        text.setOpaque(false);
        text.add(label(title, SMALL_FONT, MUTED), BorderLayout.NORTH);
        text.add(value, BorderLayout.CENTER);
        panel.add(text, BorderLayout.CENTER);
        value.setForeground(TEXT);
        return panel;
    }

    private void applyTargetSelection() {
        String selection = (String) targetSelector.getSelectedItem();
        if (selection == null || selection.equals("WORKSPACE DEFAULT")) {
            promptArea.setText(promptWithoutVerificationTarget(promptArea.getText()));
            return;
        }
        String request = promptWithoutVerificationTarget(promptArea.getText()).trim();
        promptArea.setText(request + (request.isBlank() ? "" : System.lineSeparator())
                + "Verification Target: " + selection);
    }

    private static String promptWithoutVerificationTarget(String prompt) {
        if (prompt == null) {
            return "";
        }
        return prompt.replaceAll("(?im)^\\s*Verification\\s+Target\\s*:\\s*\\S.*(?:\\R|$)", "")
                .replaceAll("(?m)\\n{3,}", "\n\n")
                .trim();
    }

    private String promptWithDeclaredBoundary() {
        String prompt = promptArea.getText().trim();
        String boundary = boundaryField.getText().trim();
        return boundary.isBlank()
                ? prompt
                : prompt + "\n\nBOUNDARY:\n" + boundary;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void startSession() {
        if (sessions == null || requestLifecycle.active()) {
            return;
        }
        DesktopDiagnostics.stage("startSession");
        if (currentGitSnapshot != null && !currentGitSnapshot.clean()) {
            int choice = JOptionPane.showConfirmDialog(
                    frame,
                    "Existing changes are present in the selected workspace.\n"
                            + "EngineOS will preserve them and include them in the pre-flight baseline.\n\n"
                            + "Continue with this request?",
                    "Review Existing Changes",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
        }
        proposalProgressBanner.setVisible(true);
        proposalProgressBanner.getParent().revalidate();
        proposalProgressBanner.getParent().repaint();
        startButton.setText("PREPARING PROPOSAL\u2026");
        long generation = ++requestGeneration;
        String project = projectField.getText().trim();
        String prompt = promptWithDeclaredBoundary();
        Path submissionWorkspace = Path.of(project).toAbsolutePath().normalize();
        GitSnapshot preflightBaseline = submissionWorkspace.equals(currentGitWorkspace)
                && currentGitSnapshot != null
                ? currentGitSnapshot
                : sessions.sourceControl(submissionWorkspace);
        startButton.setEnabled(false);
        cancelButton.setEnabled(true);
        sendButton.setEnabled(false);
        viewTargetButton.setEnabled(false);
        currentObservation = null;
        setOutputActionsVisible(false);
        currentRunId = null;
        currentHistoryRecord = null;
        observationStatus.setText("No observation package available for the current request.");
        observationStatus.setForeground(MUTED);
        activityArea.setText("");
        liveActivityCount = 0;
        activityRetention.setText("");
        activityRetention.setVisible(false);
        activityScroll.setVisible(true);
        activityPanel.setVisible(true);
        stopHistoryTransition();
        setActivityActive(false);
        resultStatus.setText("No result yet — submit a request to begin.");
        resultStatus.setForeground(MUTED);
        resultPanel.setVisible(false);
        requestLifecycle.submitted();
        activityArea.setText("\u2022 " + ENGINEOS_STARTUP_ACTIVITY);
        activityArea.setCaretPosition(activityArea.getDocument().getLength());
        requestStatus.setText(requestLifecycle.displayText());
        requestStatus.setForeground(MUTED);
        updateActionControls(true);
        activityPanel.revalidate();
        activityPanel.repaint();
        new SwingWorker<EngineOsService.TaskProposal, Void>() {
            @Override
            protected EngineOsService.TaskProposal doInBackground() {
                return sessions.propose(
                        java.util.UUID.randomUUID().toString(),
                        Path.of(project),
                        prompt,
                        (String) model.getSelectedItem(),
                        preflightBaseline,
                        state -> { },
                        activity -> SwingUtilities.invokeLater(() -> appendActivity(activity)));
            }
            @Override
            protected void done() {
                if (generation != requestGeneration) {
                    return;
                }
                try {
                    var proposal = get();
                    proposalProgressBanner.setVisible(false);
                    startButton.setText("SUBMIT ENGINEERING REQUEST");
                    requestLifecycle.serviceState(EngineOsService.ExecutionState.WAITING_FOR_APPROVAL);
                    requestStatus.setText("Proposal ready — review before coding.");
                    requestStatus.setForeground(ACTIVE_AMBER);
                    updateActionControls(EngineeringRequestValidator.isValid(promptArea.getText()));
                    showProposalDecision(proposal, generation);
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    proposalProgressBanner.setVisible(false);
                    startButton.setText("SUBMIT ENGINEERING REQUEST");
                    requestLifecycle.serviceState(EngineOsService.ExecutionState.FAILED);
                    setActivityActive(false);
                    requestStatus.setText("Request failed/terminated: " + cause.getMessage());
                    requestStatus.setForeground(ERROR);
                    activityPanel.setVisible(true);
                    activityScroll.setVisible(false);
                    activityRetention.setVisible(true);
                    activityRetention.setText(activityRetentionText(liveActivityCount));
                    currentHistoryRecord = null;
                    updateActionControls(EngineeringRequestValidator.isValid(promptArea.getText()));
                } finally {
                    refreshObservationClipboardState();
                    updateRequestValidation();
                }
            }
        }.execute();
    }

    private void showProposalDecision(EngineOsService.TaskProposal proposal, long generation) {
        var heading = new JPanel(new BorderLayout(14, 0));
        heading.setOpaque(false);
        heading.setBorder(new EmptyBorder(4, 8, 0, 8));
        var title = label(proposal.title(), new Font("Segoe UI", Font.BOLD, 22), TEXT);
        title.setBorder(new EmptyBorder(0, 0, 2, 0));
        heading.add(title, BorderLayout.CENTER);
        var status = label(proposal.status().name(), SMALL_FONT, new Color(120, 53, 15));
        status.setOpaque(true);
        status.setHorizontalAlignment(JLabel.CENTER);
        status.setBorder(new CompoundBorder(
                new LineBorder(ACTIVE_AMBER_BRIGHT, 1, true),
                new EmptyBorder(5, 10, 5, 10)));
        status.setBackground(new Color(254, 243, 199));
        heading.add(status, BorderLayout.EAST);

        var reviewIntro = new JPanel(new BorderLayout(10, 0));
        reviewIntro.setBackground(new Color(39, 31, 18));
        reviewIntro.setBorder(new CompoundBorder(
                new MatteBorder(0, 3, 0, 0, ACTIVE_AMBER_BRIGHT),
                new EmptyBorder(11, 12, 11, 12)));
        var warningIcon = label("\u26A0", new Font("Segoe UI Symbol", Font.BOLD, 19), ACTIVE_AMBER_BRIGHT);
        reviewIntro.add(warningIcon, BorderLayout.WEST);
        String reviewMessage = proposal.nonModifying()
                ? "NON-MODIFYING REQUEST: Any file changes made by the Coder will be automatically reverted "
                        + "and recorded as a boundary violation. The human engineer should review this proposal, "
                        + "share it with their LLM of choice for confirmation, then approve only when both agree."
                : "The human engineer should review this proposal, share it with their LLM of choice for confirmation, "
                        + "then approve only when both agree. The proposal remains subject to EngineOS safety boundaries; "
                        + "no Coding Task will be created until you approve.";
        reviewIntro.add(label("<html><b>" + (proposal.nonModifying()
                ? "Non-modifying request" : "Approval required") + "</b><br>"
                + escapeHtml(reviewMessage) + "</html>", BODY_FONT, TEXT), BorderLayout.CENTER);

        var overview = new JPanel(new BorderLayout(0, 12));
        overview.setOpaque(false);
        overview.add(heading, BorderLayout.NORTH);
        overview.add(reviewIntro, BorderLayout.CENTER);

        var sections = new JPanel();
        sections.setLayout(new javax.swing.BoxLayout(sections, javax.swing.BoxLayout.Y_AXIS));
        sections.setOpaque(false);
        var goalSection = proposalSection("GOAL", proposal.goal());
        var scopeSection = proposalSection("IN SCOPE", proposal.scope());
        var outOfScopeSection = proposalSection("OUT OF SCOPE", proposal.outOfScope());
        sections.add(goalSection);
        sections.add(javax.swing.Box.createVerticalStrut(10));
        sections.add(scopeSection);
        sections.add(javax.swing.Box.createVerticalStrut(10));
        sections.add(outOfScopeSection);
        if (proposal.declaredBoundary() != null && !proposal.declaredBoundary().isBlank()) {
            sections.add(javax.swing.Box.createVerticalStrut(10));
            sections.add(proposalSection("DECLARED BOUNDARY", proposal.declaredBoundary()));
        }
        if (proposal.rulesPath() != null && !proposal.rulesPath().isBlank()) {
            String rules = proposal.rulesSummary()
                    + (proposal.rulesSha256() == null || proposal.rulesSha256().isBlank()
                    ? "" : "\nSHA-256: " + shortHash(proposal.rulesSha256()));
            sections.add(javax.swing.Box.createVerticalStrut(10));
            sections.add(proposalRulesSection(proposal, rules));
        }
        sections.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        var remaining = new JTextArea(proposalDetailsWithoutOverview(proposal), 10, 56);
        remaining.setEditable(false);
        remaining.setLineWrap(true);
        remaining.setWrapStyleWord(true);
        styleTextArea(remaining);
        var remainingScroll = new JScrollPane(remaining);
        styleScrollPane(remainingScroll);
        remainingScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        remainingScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        remainingScroll.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, BORDER),
                new EmptyBorder(12, 0, 0, 0)));

        var body = new JPanel();
        body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.add(sections);
        body.add(javax.swing.Box.createVerticalStrut(12));
        body.add(remainingScroll);
        body.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        var scroll = new JScrollPane(body);
        styleScrollPane(scroll);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(new LineBorder(BORDER, 1, true));
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        scroll.getViewport().setScrollMode(javax.swing.JViewport.SIMPLE_SCROLL_MODE);

        var helper = label("<html>Human engineer review + LLM confirmation required before approval.<br>"
                        + "Approving creates the coding task; rejecting discards this proposal.</html>",
                new Font("Segoe UI", Font.BOLD, 14), new Color(203, 213, 225));
        helper.setBorder(new EmptyBorder(12, 10, 12, 10));
        var copyProposalButton = new JButton("COPY PROPOSAL TO SHARE WITH YOUR LLM");
        styleSecondaryButton(copyProposalButton);
        copyProposalButton.addActionListener(event -> {
            boolean copied = EngineeringPartnerClipboard.copyToClipboard(proposalClipboardText(proposal));
            copyProposalButton.setText(copied ? "COPIED FOR YOUR LLM" : "COPY FAILED");
        });
        var dialogFooter = new JPanel(new BorderLayout(12, 0));
        dialogFooter.setOpaque(false);
        dialogFooter.add(helper, BorderLayout.CENTER);
        dialogFooter.add(copyProposalButton, BorderLayout.EAST);
        var dialogContent = new JPanel(new BorderLayout(0, 12));
        dialogContent.setBackground(SURFACE);
        dialogContent.add(overview, BorderLayout.NORTH);
        dialogContent.add(scroll, BorderLayout.CENTER);
        dialogContent.add(dialogFooter, BorderLayout.SOUTH);

        var approveButton = new JButton("Approve Task");
        stylePrimaryButton(approveButton);
        var rejectButton = new JButton("Reject Task");
        styleSecondaryButton(rejectButton);
        Object[] options = {approveButton, rejectButton};
        var pane = new JOptionPane(dialogContent, JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, options, rejectButton);
        var dialog = pane.createDialog(frame, "Review Proposed Task");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                pane.setValue(rejectButton);
            }
        });
        rejectButton.addActionListener(event -> {
            pane.setValue(rejectButton);
            dialog.dispose();
        });
        dialog.getContentPane().setBackground(SURFACE);
        dialog.setMinimumSize(new Dimension(700, 620));
        dialog.setSize(840, 780);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
        Object decision = pane.getValue();
        dialog.dispose();
        if (generation != requestGeneration) {
            return;
        }
        if (options[0].equals(decision)) {
            approveProposal(proposal, generation);
        } else if (options[1].equals(decision)) {
            rejectProposal(proposal, generation);
        }
    }

    private static JPanel proposalSection(String heading, String value) {
        var section = new JPanel(new BorderLayout(0, 9));
        section.setBackground(SURFACE_RAISED);
        section.setBorder(new CompoundBorder(
                new MatteBorder(0, 3, 0, 0, new Color(65, 183, 142)),
                new EmptyBorder(15, 16, 15, 16)));
        section.add(label(heading, SECTION_FONT, ACCENT_HOVER), BorderLayout.NORTH);
        var text = new JTextArea(value == null || value.isBlank() ? "Not specified." : value, 0, 60);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(BODY_FONT);
        text.setForeground(TEXT);
        text.setBackground(SURFACE_RAISED);
        text.setBorder(null);
        text.setMinimumSize(new Dimension(0, 0));
        text.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        section.add(text, BorderLayout.CENTER);
        section.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return section;
    }

    private static JPanel proposalRulesSection(EngineOsService.TaskProposal proposal, String summary) {
        var section = proposalSection(
                "PROJECT RULES (LOWER PRIORITY THAN REQUEST AND SAFETY BOUNDARIES)", summary);
        var details = new JTextArea(proposal.rulesContent(), 8, 60);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(BODY_FONT);
        details.setForeground(TEXT);
        details.setBackground(SURFACE_RAISED);
        details.setBorder(new EmptyBorder(8, 0, 0, 0));
        details.setVisible(true);
        var toggle = new JButton("HIDE FULL RULES");
        styleSecondaryButton(toggle);
        var footer = new JPanel(new BorderLayout(0, 6));
        footer.setOpaque(false);
        toggle.addActionListener(event -> {
            boolean visible = !details.isVisible();
            details.setVisible(visible);
            toggle.setText(visible ? "HIDE FULL RULES" : "SHOW FULL RULES");
            footer.revalidate();
            footer.repaint();
        });
        footer.add(details, BorderLayout.CENTER);
        footer.add(toggle, BorderLayout.SOUTH);
        section.add(footer, BorderLayout.SOUTH);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return section;
    }

    private static String proposalDetailsWithoutOverview(EngineOsService.TaskProposal proposal) {
        return "Acceptance criteria:\n- " + String.join("\n- ", proposal.acceptanceCriteria())
                + (proposal.declaredBoundary() == null || proposal.declaredBoundary().isBlank()
                        ? "" : "\n\nDeclared boundary:\n" + proposal.declaredBoundary())
                + "\n\nConstraints:\n" + proposal.constraints()
                + "\n\nRationale:\n" + proposal.rationale()
                + "\n\nSuggested verification:\n" + proposal.verificationApproach()
                + "\n\nProposal source:\n" + proposal.proposalSource()
                + "\n\nWhat the Coder may do:\n" + proposal.allowedActions()
                + (proposal.nonModifying()
                        ? "\n\nNON-MODIFYING REQUEST:\nAny file changes made by the Coder will be "
                                + "automatically reverted and recorded as a boundary violation."
                        : "");
    }

    private static String shortHash(String hash) {
        return hash.length() <= 12 ? hash : hash.substring(0, 12);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static String proposalText(EngineOsService.TaskProposal proposal) {
        return "Task: " + proposal.title()
                + "\nStatus: " + proposal.status()
                + "\n\nGoal:\n" + proposal.goal()
                + "\n\nIn scope:\n" + proposal.scope()
                + "\n\nOut of scope:\n" + proposal.outOfScope()
                + (proposal.declaredBoundary() == null || proposal.declaredBoundary().isBlank()
                        ? "" : "\n\nDeclared boundary:\n" + proposal.declaredBoundary())
                + "\n\nAcceptance criteria:\n- " + String.join("\n- ", proposal.acceptanceCriteria())
                + "\n\nConstraints:\n" + proposal.constraints()
                + "\n\nRationale:\n" + proposal.rationale()
                + "\n\nSuggested verification:\n" + proposal.verificationApproach()
                + "\n\nProposal source:\n" + proposal.proposalSource()
                + "\n\nWhat the Coder may do:\n" + proposal.allowedActions()
                + (proposal.nonModifying()
                        ? "\n\nNON-MODIFYING REQUEST:\nAny file changes made by the Coder will be "
                                + "automatically reverted and recorded as a boundary violation."
                        : "");
    }

    private void approveProposal(EngineOsService.TaskProposal proposal, long generation) {
        requestLifecycle.serviceState(EngineOsService.ExecutionState.APPROVED);
        requestStatus.setText(requestLifecycle.displayText());
        requestStatus.setForeground(ACTIVE_AMBER);
        startButton.setEnabled(false);
        cancelButton.setEnabled(true);
        new SwingWorker<EngineOsService.ExecutionResult, EngineOsService.ExecutionState>() {
            @Override
            protected EngineOsService.ExecutionResult doInBackground() {
                return sessions.approve(proposal.requestId(), this::publish,
                        activity -> SwingUtilities.invokeLater(() -> appendActivity(activity)));
            }

            @Override
            protected void process(java.util.List<EngineOsService.ExecutionState> states) {
                if (generation == requestGeneration && !states.isEmpty()) {
                    var state = states.getLast();
                    requestLifecycle.serviceState(state);
                    requestStatus.setText(requestLifecycle.displayText());
                    requestStatus.setForeground(state == EngineOsService.ExecutionState.FAILED
                            ? ERROR : MUTED);
                    if (state == EngineOsService.ExecutionState.CODING_AGENT_WORKING) {
                        activityPanel.setVisible(true);
                        activityScroll.setVisible(true);
                        setActivityActive(true);
                    } else if (state == EngineOsService.ExecutionState.RUNNING_OBSERVATION) {
                        setActivityActive(false);
                        viewTargetButton.setEnabled(true);
                    }
                }
            }

            @Override
            protected void done() {
                if (generation != requestGeneration) {
                    return;
                }
                try {
                    finishSession(get(), generation);
                } catch (Exception exception) {
                    showSessionFailure(exception, generation);
                }
            }
        }.execute();
    }

    private void rejectProposal(EngineOsService.TaskProposal proposal, long generation) {
        startButton.setEnabled(false);
        cancelButton.setEnabled(false);
        requestStatus.setText("Recording rejection...");
        new SwingWorker<EngineOsService.ExecutionResult, Void>() {
            @Override
            protected EngineOsService.ExecutionResult doInBackground() {
                return sessions.reject(proposal.requestId());
            }

            @Override
            protected void done() {
                if (generation != requestGeneration) {
                    return;
                }
                try {
                    finishSession(get(), generation);
                } catch (Exception exception) {
                    showSessionFailure(exception, generation);
                }
            }
        }.execute();
    }

    private void finishSession(EngineOsService.ExecutionResult result, long generation) {
        if (generation != requestGeneration) {
            return;
        }
        requestLifecycle.serviceState(result.state());
        setActivityActive(false);
        requestStatus.setText(requestLifecycle.displayText());
        requestStatus.setForeground(result.state() == EngineOsService.ExecutionState.GREEN
                || result.state() == EngineOsService.ExecutionState.REJECTED ? SUCCESS : ERROR);
        currentRunId = result.engineeringResult() == null
                || result.engineeringResult().history() == null
                ? null : result.engineeringResult().history().runId();
        currentHistoryRecord = findHistoryRecord(currentRunId);
        boolean declaredBoundaryViolation = result.detail() != null
                && result.detail().contains("DECLARED BOUNDARY VIOLATION");
        boolean resultAvailable = currentResultMatches(currentRunId, currentHistoryRecord);
        resultStatus.setText(declaredBoundaryViolation
                ? "\u26A0 BOUNDARY VIOLATION — changes outside the declared boundary were blocked."
                : resultAvailable
                ? "Engineering Result available for the current request."
                : "No result yet — submit a request to begin.");
        resultStatus.setForeground(declaredBoundaryViolation ? ERROR
                : resultAvailable
                ? SUCCESS : MUTED);
        resultStatus.setFont(declaredBoundaryViolation
                ? new Font("Segoe UI", Font.BOLD, 13) : SMALL_FONT);
        resultStatus.setBorder(declaredBoundaryViolation
                ? new CompoundBorder(new LineBorder(ERROR, 1, true),
                new EmptyBorder(8, 10, 8, 10)) : null);
        if (declaredBoundaryViolation) {
            resultArea.setText("BOUNDARY VIOLATION\n\n" + result.detail()
                    + "\n\nReview the listed files before treating this result as successful.");
            resultArea.setCaretPosition(0);
            resultPanel.setVisible(true);
        } else if (resultAvailable) {
            resultArea.setText(HistoryPresentation.format(currentHistoryRecord));
            resultArea.setCaretPosition(0);
            resultPanel.setVisible(true);
        }
        if (result.state() == EngineOsService.ExecutionState.GREEN) {
            currentObservation = packages.findCompletedForRun(currentRunId).orElse(null);
            observationStatus.setText(currentObservation == null
                    ? "Request completed; no observation package available for this run."
                    : "Observation ready for current request.");
            observationStatus.setForeground(currentObservation == null ? ERROR : SUCCESS);
        } else {
            currentObservation = null;
            observationStatus.setText("No observation package available for the current request.");
            observationStatus.setForeground(MUTED);
        }
        setOutputActionsVisible(resultAvailable || currentObservation != null);
        if (result.coding() != null && result.coding().usage().available()) {
            var sessionLine = result.coding().sessionId() == null ? ""
                    : "\nSession: " + result.coding().sessionId();
            usageArea.setText(result.coding().usage().display() + sessionLine);
        } else if (result.coding() != null) {
            usageArea.setText("AI usage was not provided by the Copilot runtime.\n"
                    + "Requested model: " + result.coding().requestedModel()
                    + (result.coding().sessionId() == null ? ""
                    : "\nSession: " + result.coding().sessionId()));
        }
        activityPanel.setVisible(true);
        activityScroll.setVisible(false);
        activityRetention.setVisible(true);
        activityRetention.setText(activityRetentionText(result.activity()));
        startHistoryTransition();
        viewTargetButton.setEnabled(false);
        refreshObservationClipboardState();
        refreshSourceControl();
        updateRequestValidation();
        updateActionControls(EngineeringRequestValidator.isValid(promptArea.getText()));
    }

    private void showSessionFailure(Exception exception, long generation) {
        if (generation != requestGeneration) {
            return;
        }
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        requestLifecycle.serviceState(EngineOsService.ExecutionState.FAILED);
        setActivityActive(false);
        requestStatus.setText("Request failed/terminated: " + cause.getMessage());
        requestStatus.setForeground(ERROR);
        activityPanel.setVisible(true);
        activityScroll.setVisible(false);
        activityRetention.setVisible(true);
        activityRetention.setText(activityRetentionText(liveActivityCount));
        currentHistoryRecord = null;
        refreshSourceControl();
        updateActionControls(EngineeringRequestValidator.isValid(promptArea.getText()));
    }

    private void appendActivity(String activity) {
        liveActivityCount++;
        if (activityArea.getText().isBlank()) {
            activityArea.setText("• " + activity);
        } else {
            activityArea.append(System.lineSeparator() + "• " + activity);
        }
        activityArea.setCaretPosition(activityArea.getDocument().getLength());
    }

    private void invalidateCurrentRequestPresentation() {
        if (requestLifecycle.active()) {
            return;
        }
        requestGeneration++;
        currentObservation = null;
        currentRunId = null;
        currentHistoryRecord = null;
        setOutputActionsVisible(false);
        resultArea.setText("The Coding Agent observation/result will appear here after submission.");
        resultPanel.setVisible(false);
        resultStatus.setText("No result yet — submit a request to begin.");
        resultStatus.setForeground(MUTED);
        observationStatus.setText("No observation package available for the current request.");
        observationStatus.setForeground(MUTED);
        refreshObservationClipboardState();
    }

    private EngineeringHistoryRecord findHistoryRecord(String runId) {
        if (runId == null || sessions == null) {
            return null;
        }
        return sessions.historyRecords().stream()
                .filter(record -> runId.equals(record.runId()))
                .findFirst()
                .orElse(null);
    }

    static boolean currentResultMatches(String runId, EngineeringHistoryRecord record) {
        return runId != null && record != null && runId.equals(record.runId())
                && "GREEN".equalsIgnoreCase(record.finalStatus())
                && record.engineeringResult() != null && !record.engineeringResult().isBlank();
    }

    private void pulseActivityIndicator() {
        if (!requestLifecycle.codingAgentActive()) {
            setActivityActive(false);
            return;
        }
        activityPulseBright = !activityPulseBright;
        activityIndicator.setForeground(activityPulseBright ? ACTIVE_AMBER_BRIGHT : ACTIVE_AMBER);
        activityHeadingLabel.setForeground(activityPulseBright ? ACTIVE_AMBER_BRIGHT : ACTIVE_AMBER);
        activityHeading.setBorder(new CompoundBorder(
                new LineBorder(activityPulseBright ? ACTIVE_AMBER_BRIGHT : ACTIVE_AMBER, 1, true),
                new EmptyBorder(6, 10, 6, 10)));
        activityPanel.revalidate();
        activityPanel.repaint();
    }

    private void setActivityActive(boolean active) {
        if (active) {
            activityPulseBright = true;
            activityPulseTimer.start();
            activityIndicator.setForeground(ACTIVE_AMBER_BRIGHT);
            activityHeadingLabel.setForeground(ACTIVE_AMBER_BRIGHT);
            activityHeading.setBorder(new CompoundBorder(
                    new LineBorder(ACTIVE_AMBER_BRIGHT, 1, true),
                    new EmptyBorder(6, 10, 6, 10)));
        } else {
            activityPulseTimer.stop();
            activityPulseBright = false;
            activityIndicator.setForeground(MUTED);
            activityHeadingLabel.setForeground(MUTED);
            activityHeading.setBorder(new CompoundBorder(
                    new LineBorder(BORDER, 1, true),
                    new EmptyBorder(6, 10, 6, 10)));
        }
        activityPanel.revalidate();
        activityPanel.repaint();
    }

    private void startHistoryTransition() {
        stopHistoryTransition();
        historyTransitionStartedAt = System.currentTimeMillis();
        historyTransitionTimer.setRepeats(true);
        historyTransitionTimer.start();
        animateHistoryTransition();
    }

    private void stopHistoryTransition() {
        historyTransitionTimer.stop();
        historyTransitionStartedAt = 0;
        activityRetention.setForeground(MUTED);
        activityHeading.setBorder(new CompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)));
        historyButton.setForeground(TEXT);
        historyButton.setBackground(SURFACE_RAISED);
        historyButton.setBorder(new CompoundBorder(
                new LineBorder(HISTORY_BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)));
    }

    private void animateHistoryTransition() {
        long elapsed = System.currentTimeMillis() - historyTransitionStartedAt;
        if (elapsed >= HISTORY_TRANSITION_DURATION_MS) {
            stopHistoryTransition();
            return;
        }

        double progress = elapsed / (double) HISTORY_TRANSITION_DURATION_MS;
        double pulse = Math.sin(progress * Math.PI);
        Color transitionAccent = blend(MUTED, SUCCESS, pulse);
        activityRetention.setForeground(transitionAccent);

        if (progress < .68) {
            activityHeading.setBorder(new CompoundBorder(
                    new LineBorder(blend(BORDER, SUCCESS, pulse), 1, true),
                    new EmptyBorder(6, 10, 6, 10)));
        } else {
            double historyProgress = (progress - .68) / .32;
            double historyPulse = Math.sin(historyProgress * Math.PI);
            historyButton.setForeground(blend(TEXT, Color.WHITE, historyPulse));
            historyButton.setBackground(blend(SURFACE_RAISED, new Color(37, 67, 65), historyPulse));
            historyButton.setBorder(new CompoundBorder(
                    new LineBorder(blend(HISTORY_BORDER, SUCCESS, historyPulse), 1, true),
                    new EmptyBorder(8, 12, 8, 12)));
        }
        activityPanel.repaint();
        historyButton.repaint();
    }

    private static Color blend(Color start, Color end, double amount) {
        double clamped = Math.max(0, Math.min(1, amount));
        return new Color(
                (int) Math.round(start.getRed() + (end.getRed() - start.getRed()) * clamped),
                (int) Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * clamped),
                (int) Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * clamped));
    }

    static String activityRetentionText(java.util.List<String> activity) {
        return activityRetentionText(activity == null ? 0 : activity.size());
    }

    private static String activityRetentionText(int activityCount) {
        return activityCount == 0
                ? "No Coding Agent activity was recorded for this run."
                : "Activity retained in Engineering History \u2014 "
                + activityCount + " event" + (activityCount == 1 ? "" : "s")
                + ". Use HISTORY to review.";
    }

    private void showHistory() {
        if (!isValidWorkspace()) {
            return;
        }
        var records = sessions.historyRecords();
        var labels = records.stream()
                .map(record -> compactTimestamp(record.completedAt()) + "  |  "
                        + HistoryPresentation.summary(record) + "  |  "
                        + historyStatus(record))
                .toArray(String[]::new);
        var list = new JList<>(labels);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(INPUT);
        list.setForeground(TEXT);
        list.setFont(BODY_FONT);
        list.setBorder(new EmptyBorder(6, 6, 6, 6));

        var details = new JTextArea();
        styleTextArea(details);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setText(records.isEmpty()
                ? "No Engineering History runs are available."
                : "Select a run to inspect its structured history.");
        var copyResultButton = new JButton("COPY ENGINEERING RESULT");
        stylePrimaryButton(copyResultButton);
        copyResultButton.setEnabled(false);
        var copyStatus = label("", SMALL_FONT, MUTED);
        copyResultButton.addActionListener(event -> {
            int selected = list.getSelectedIndex();
            if (selected < 0 || selected >= records.size()) {
                copyStatus.setText("Select a run before copying its result.");
                copyStatus.setForeground(ERROR);
                return;
            }
            if (!hasEngineeringResult(records.get(selected))) {
                copyStatus.setText("No Engineering Result is available for the selected run.");
                copyStatus.setForeground(MUTED);
                return;
            }
            boolean copied = EngineeringPartnerClipboard.copyToClipboard(
                    HistoryPresentation.handoff(records.get(selected)));
            copyStatus.setText(copied
                    ? "\u2713 Engineering Result copied for the selected run."
                    : "Unable to copy Engineering Result to clipboard.");
            copyStatus.setForeground(copied ? SUCCESS : ERROR);
        });
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                int selected = list.getSelectedIndex();
                if (selected >= 0 && selected < records.size()) {
                    details.setText(HistoryPresentation.format(records.get(selected)));
                    details.setCaretPosition(0);
                    copyStatus.setText("");
                    copyResultButton.setEnabled(hasEngineeringResult(records.get(selected)));
                } else {
                    details.setText("Select a run to inspect its structured history.");
                    copyStatus.setText("");
                    copyResultButton.setEnabled(false);
                }
            }
        });

        var listScroll = new JScrollPane(list);
        var detailsScroll = new JScrollPane(details);
        styleScrollPane(listScroll);
        styleScrollPane(detailsScroll);
        listScroll.setBorder(new CompoundBorder(
                new MatteBorder(1, 1, 1, 1, BORDER),
                new EmptyBorder(0, 0, 0, 0)));
        detailsScroll.setBorder(new CompoundBorder(
                new MatteBorder(1, 1, 1, 1, BORDER),
                new EmptyBorder(0, 0, 0, 0)));

        var listPanel = new JPanel(new BorderLayout(0, 8));
        listPanel.setOpaque(false);
        listPanel.add(label("RUN HISTORY", MAJOR_SECTION_FONT, TEXT), BorderLayout.NORTH);
        listPanel.add(listScroll, BorderLayout.CENTER);

        var detailsPanel = new JPanel(new BorderLayout(0, 8));
        detailsPanel.setOpaque(false);
        var detailsHeader = new JPanel(new BorderLayout(8, 0));
        detailsHeader.setOpaque(false);
        detailsHeader.add(label("RESULT / STATUS", MAJOR_SECTION_FONT, TEXT), BorderLayout.WEST);
        var detailActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        detailActions.setOpaque(false);
        detailActions.add(copyStatus);
        detailActions.add(copyResultButton);
        detailsHeader.add(detailActions, BorderLayout.EAST);
        detailsPanel.add(detailsHeader, BorderLayout.NORTH);
        detailsPanel.add(detailsScroll, BorderLayout.CENTER);

        var splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, detailsPanel);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);
        splitPane.setDividerSize(8);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.35);

        var dialog = new JDialog(frame, "Engineering History", false);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(BACKGROUND);
        dialog.add(splitPane, BorderLayout.CENTER);
        dialog.setSize(1040, 600);
        dialog.setMinimumSize(new Dimension(780, 420));
        dialog.setLocationRelativeTo(frame);
        if (!records.isEmpty()) {
            list.setSelectedIndex(0);
        }
        dialog.setVisible(true);
    }

    private static String compactTimestamp(java.time.Instant timestamp) {
        if (timestamp == null) {
            return "unknown time";
        }
        return timestamp.toString().replace('T', ' ').replaceFirst("\\.\\d+Z$", "Z");
    }

    private void showGitStatus() {
        Path workspace;
        try {
            workspace = Path.of(projectField.getText().trim());
        } catch (RuntimeException exception) {
            return;
        }
        gitButton.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return gitStatusText(workspace);
            }

            @Override
            protected void done() {
                gitButton.setEnabled(isValidWorkspace());
                try {
                    var details = new JTextArea(get());
                    details.setEditable(false);
                    details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                    details.setForeground(TEXT);
                    details.setBackground(SURFACE);
                    details.setBorder(new EmptyBorder(14, 14, 14, 14));
                    var provenance = label(gitChangeProvenance(), SMALL_FONT, MUTED);
                    provenance.setBorder(new EmptyBorder(10, 14, 2, 14));
                    provenance.setVerticalAlignment(SwingConstants.TOP);
                    var commitMessage = new JTextField();
                    styleTextField(commitMessage);
                    if (currentHistoryRecord != null) {
                        String suggestedMessage = commitMessageFor(currentHistoryRecord);
                        if (isUsableCommitMessage(suggestedMessage)) {
                            commitMessage.setText(suggestedMessage);
                        }
                    }
                    var stageAndCommitButton = new JButton("STAGE ALL & COMMIT");
                    stylePrimaryButton(stageAndCommitButton);
                    var commitButton = new JButton("COMMIT STAGED");
                    styleSecondaryButton(commitButton);
                    commitButton.setEnabled(false);
                    var pushButton = new JButton("PUSH");
                    styleSecondaryButton(pushButton);
                    var feedback = label("", SMALL_FONT, MUTED);
                    boolean hasStagedChanges = git.status(workspace).changes().stream()
                            .anyMatch( GitFileChange::staged);
                    commitMessage.getDocument().addDocumentListener(new DocumentListener() {
                        private void update() {
                            boolean hasMessage = !commitMessage.getText().trim().isEmpty();
                            stageAndCommitButton.setEnabled(hasMessage);
                            commitButton.setEnabled(hasMessage && hasStagedChanges);
                        }

                        @Override
                        public void insertUpdate(DocumentEvent event) {
                            update();
                        }

                        @Override
                        public void removeUpdate(DocumentEvent event) {
                            update();
                        }

                        @Override
                        public void changedUpdate(DocumentEvent event) {
                            update();
                        }
                    });
                    stageAndCommitButton.setEnabled(!commitMessage.getText().trim().isEmpty());
                    commitButton.setEnabled(!commitMessage.getText().trim().isEmpty()
                            && hasStagedChanges);
                    var commitPanel = new JPanel(new BorderLayout(8, 0));
                    commitPanel.setOpaque(false);
                    commitPanel.setBorder(new EmptyBorder(10, 14, 10, 14));
                    commitPanel.add(label("COMMIT MESSAGE", SMALL_FONT, MUTED), BorderLayout.WEST);
                    commitPanel.add(commitMessage, BorderLayout.CENTER);
                    var gitActions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
                    gitActions.setOpaque(false);
                    gitActions.add(stageAndCommitButton);
                    gitActions.add(commitButton);
                    gitActions.add(pushButton);
                    commitPanel.add(gitActions, BorderLayout.EAST);
                    var dialog = new JDialog(frame, "Git Status", false);
                    dialog.setLayout(new BorderLayout());
                    dialog.getContentPane().setBackground(BACKGROUND);
                    var scroll = new JScrollPane(details);
                    styleScrollPane(scroll);
                    var content = new JPanel(new BorderLayout());
                    content.setOpaque(false);
                    content.add(provenance, BorderLayout.NORTH);
                    content.add(scroll, BorderLayout.CENTER);
                    dialog.add(content, BorderLayout.CENTER);
                    var bottom = new JPanel(new BorderLayout(0, 4));
                    bottom.setOpaque(false);
                    bottom.add(commitPanel, BorderLayout.NORTH);
                    bottom.add(feedback, BorderLayout.SOUTH);
                    bottom.setBorder(new EmptyBorder(0, 0, 10, 0));
                    dialog.add(bottom, BorderLayout.SOUTH);
                    commitButton.addActionListener(event -> commitFromGitDialog(
                            workspace, commitMessage, commitButton, feedback, details));
                    stageAndCommitButton.addActionListener(event -> stageAndCommitFromGitDialog(
                            workspace, commitMessage, stageAndCommitButton, commitButton,
                            feedback, details));
                    pushButton.addActionListener(event -> pushFromGitDialog(
                            workspace, pushButton, feedback, details));
                    dialog.setTitle("Git Review");
                    dialog.setSize(900, 600);
                    dialog.setMinimumSize(new Dimension(620, 360));
                    dialog.setLocationRelativeTo(frame);
                    dialog.setVisible(true);
                } catch (Exception exception) {
                    status.setText("Couldn't read Git status: " + exception.getMessage());
                    status.setForeground(ERROR);
                }
            }
        }.execute();
    }

    private void commitFromGitDialog(
            Path workspace,
            JTextField commitMessage,
            JButton commitButton,
            JLabel feedback,
            JTextArea details) {
        String message = commitMessage.getText().trim();
            if (!isUsableCommitMessage(message)) {
            commitButton.setEnabled(false);
            feedback.setText("Commit message is required.");
            feedback.setForeground(ERROR);
            return;
        }
        commitButton.setEnabled(false);
        if (JOptionPane.showConfirmDialog(
                frame,
                "Commit the staged changes to the local repository?\n\n" + message,
                "Confirm Git Commit",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            commitButton.setEnabled(true);
            return;
        }
        feedback.setText("Committing staged changes...");
        feedback.setForeground(MUTED);
        new SwingWorker<com.engineos.infrastructure.git.GitCommitResult, Void>() {
            @Override
            protected com.engineos.infrastructure.git.GitCommitResult doInBackground() {
                return sessions == null
                        ? git.commit(workspace, message)
                        : sessions.commitWorkspace(workspace, currentRunId, message);
            }

            @Override
            protected void done() {
                try {
                    var result = get();
                    if (!result.succeeded()) {
                        feedback.setText("Commit failed: " + result.error());
                        feedback.setForeground(ERROR);
                        commitButton.setEnabled(!commitMessage.getText().trim().isEmpty());
                        return;
                    }
                    feedback.setText("\u2713 Commit created: " + result.commitHash());
                    feedback.setForeground(SUCCESS);
                    details.setText(gitStatusText(workspace));
                    details.setCaretPosition(0);
                    commitMessage.setText("");
                    currentHistoryRecord = findHistoryRecord(currentRunId);
                    if (currentHistoryRecord != null) {
                        resultArea.setText(HistoryPresentation.format(currentHistoryRecord));
                        resultArea.setCaretPosition(0);
                    }
                    refreshSourceControl();
                } catch (Exception | LinkageError exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    feedback.setText("Commit failed: " + cause.getMessage());
                    feedback.setForeground(ERROR);
                    commitButton.setEnabled(!commitMessage.getText().trim().isEmpty());
                }
            }
        }.execute();
    }

    private void stageAndCommitFromGitDialog(
            Path workspace,
            JTextField commitMessage,
            JButton stageAndCommitButton,
            JButton commitButton,
            JLabel feedback,
            JTextArea details) {
        String message = commitMessage.getText().trim();
        if (!isUsableCommitMessage(message)) {
            feedback.setText("Commit message is required.");
            feedback.setForeground(ERROR);
            return;
        }
        stageAndCommitButton.setEnabled(false);
        commitButton.setEnabled(false);
        if (JOptionPane.showConfirmDialog(
                frame,
                "Stage all listed changes and commit them to the local repository?\n\n" + message,
                "Confirm Git Commit",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            stageAndCommitButton.setEnabled(true);
            commitButton.setEnabled(true);
            return;
        }
        feedback.setText("Staging changes...");
        feedback.setForeground(MUTED);
        new SwingWorker<com.engineos.infrastructure.git.GitCommitResult, Void>() {
            @Override
            protected com.engineos.infrastructure.git.GitCommitResult doInBackground() {
                var staged = git.stageAll(workspace);
                if (!staged.succeeded()) {
                    return new com.engineos.infrastructure.git.GitCommitResult(
                            false, "", staged.output(), staged.error());
                }
                return sessions == null
                        ? git.commit(workspace, message)
                        : sessions.commitWorkspace(workspace, currentRunId, message);
            }

            @Override
            protected void done() {
                try {
                    var result = get();
                    if (!result.succeeded()) {
                        feedback.setText("Commit failed: " + result.error());
                        feedback.setForeground(ERROR);
                        stageAndCommitButton.setEnabled(true);
                        return;
                    }
                    feedback.setText("\u2713 Changes staged and committed: " + result.commitHash());
                    feedback.setForeground(SUCCESS);
                    details.setText(gitStatusText(workspace));
                    details.setCaretPosition(0);
                    commitMessage.setText("");
                    refreshSourceControl();
                } catch (Exception | LinkageError exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    feedback.setText("Commit failed: " + cause.getMessage());
                    feedback.setForeground(ERROR);
                    stageAndCommitButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void pushFromGitDialog(
            Path workspace,
            JButton pushButton,
            JLabel feedback,
            JTextArea details) {
        pushButton.setEnabled(false);
        if (JOptionPane.showConfirmDialog(
                frame,
                "Push the current local commits to the configured remote?",
                "Confirm Git Push",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            pushButton.setEnabled(true);
            return;
        }
        feedback.setText("Pushing to the configured remote...");
        feedback.setForeground(MUTED);
        new SwingWorker<com.engineos.infrastructure.git.GitPushResult, Void>() {
            @Override
            protected com.engineos.infrastructure.git.GitPushResult doInBackground() {
                return git.push(workspace);
            }

            @Override
            protected void done() {
                try {
                    var result = get();
                    if (!result.succeeded()) {
                        feedback.setText("Push failed: " + result.error());
                        feedback.setForeground(ERROR);
                        pushButton.setEnabled(true);
                        return;
                    }
                    feedback.setText("\u2713 Push completed.");
                    feedback.setForeground(SUCCESS);
                    details.setText(gitStatusText(workspace));
                    details.setCaretPosition(0);
                    refreshSourceControl();
                } catch (Exception | LinkageError exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    feedback.setText("Push failed: " + cause.getMessage());
                    feedback.setForeground(ERROR);
                    pushButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private String gitStatusText(Path workspace) {
        GitStatus status = git.status(workspace);
        if (!status.repository()) {
            return "GIT STATUS\n\nThis workspace is not a Git repository.\n"
                    + (status.error().isBlank() ? "" : "\n" + status.error());
        }
        GitSnapshot snapshot = git.snapshot(workspace);
        var text = new StringBuilder();
        text.append("GIT STATUS\n\n")
                .append("Branch: ").append(status.branch()).append('\n')
                .append("HEAD: ").append(snapshot.headSha()).append('\n')
                .append("Working tree: ").append(status.clean() ? "clean" : "changed")
                .append('\n')
                .append("Summary: ").append(status.changes().size()).append(" files, +")
                .append(snapshot.linesAdded()).append(" / -").append(snapshot.linesRemoved())
                .append("\n\n");
        if (status.changes().isEmpty()) {
            text.append("No changed files.\n");
        } else {
            text.append("CHANGES\n");
            status.changes().forEach(change -> text.append(change.indexStatus())
                    .append(change.workTreeStatus()).append(" ")
                    .append(change.originalPath().isBlank()
                            ? change.path()
                            : change.originalPath() + " -> " + change.path())
                    .append('\n'));
        }
        text.append("\nRECENT COMMITS\n");
        var commits = git.recentCommits(workspace, 5);
        if (commits.isEmpty()) {
            text.append("No commits available.\n");
        } else {
            commits.forEach(commit -> text.append(commit.shortHash()).append(" ")
                    .append(commit.message()).append(" — ")
                    .append(commit.author()).append(" — ")
                    .append(compactTimestamp(commit.timestamp())).append('\n'));
        }
        text.append("\nLOCAL BRANCHES\n");
        for (GitBranch branch : git.branches(workspace)) {
            text.append(branch.current() ? "* " : "  ").append(branch.name()).append('\n');
        }
        text.append("\nDIFF\n").append(git.workingTreeDiff(workspace).text());
        return text.toString();
    }

    private String gitChangeProvenance() {
        if (currentHistoryRecord == null) {
            return "<html><font color='#FBBF24'><b>No EngineOS request associated</b></font>"
                    + " &mdash; changes originated outside EngineOS.<br>"
                    + "<font color='#94A3B8'>The generating tool (for example Copilot) is likely to "
                    + "produce a better commit message. You can still commit or push here.</font></html>";
        }
        String request = currentHistoryRecord.requestSummary();
        if (request == null || request.isBlank()) {
            request = currentHistoryRecord.displaySummary();
        }
        return "<html><font color='#45D483'><b>EngineOS request associated</b></font>"
                + " &mdash; run " + escape(currentHistoryRecord.runId()) + ".<br>"
                + "<font color='#94A3B8'>Prefer committing from this EngineOS request or its History entry. "
                + "Suggested message: <b>" + escape(commitMessageFor(currentHistoryRecord))
                + "</b><br>Request: " + escape(compact(request)) + "</font></html>";
    }

    private static String historyStatus(EngineeringHistoryRecord record) {
        var assessment = com.engineos.history.EngineeringAssessment.from(record);
        return assessment.verificationStatus() + " / " + assessment.implementationStatus();
    }

    private static String commitMessageFor(EngineeringHistoryRecord record) {
        if (record != null && record.sourceControl() != null
                && !record.sourceControl().commitMessage().isBlank()) {
            return record.sourceControl().commitMessage();
        }
        String summary = record == null ? "" : record.requestSummary();
        if (summary == null || summary.isBlank()) {
            summary = record == null ? "EngineOS engineering change" : record.displaySummary();
        }
        summary = summary.replaceAll("\\s+", " ").trim();
        return summary.length() > 72 ? summary.substring(0, 69) + "..." : summary;
    }

    private static boolean isUsableCommitMessage(String message) {
        if (message == null || message.isBlank() || message.contains("\n")
                || message.contains("\r")) {
            return false;
        }
        String normalized = message.trim().toLowerCase();
        return !normalized.equals("git status")
                && !normalized.equals("changes")
                && !normalized.startsWith("branch:")
                && !normalized.startsWith("working tree:")
                && !normalized.startsWith("summary:");
    }

    private static boolean hasEngineeringResult(EngineeringHistoryRecord record) {
        return record != null && record.engineeringResult() != null
                && !record.engineeringResult().isBlank();
    }

    private static String labelFor(EngineOsService.ExecutionState state) {
        return switch (state) {
            case STARTING -> "Starting...";
            case WAITING_FOR_APPROVAL -> "Awaiting human approval...";
            case APPROVED -> "Approved; starting...";
            case CODING_AGENT_WORKING -> "Coding agent working...";
            case RUNNING_OBSERVATION -> "Running observation...";
            case GREEN -> "GREEN - Observation ready";
            case FAILED -> "FAILED - See details";
            case CANCELLED -> "CANCELLED - Implementation not complete";
            case REJECTED -> "REJECTED - Coding not run";
        };
    }

    private void send(CompletedObservation observation) {
        sendButton.setEnabled(false);
        status.setText("Creating observation ZIP...");
        status.setForeground(MUTED);
        new SwingWorker<ObservationAttachmentPreparer.PreparationResult, Void>() {
            @Override
            protected ObservationAttachmentPreparer.PreparationResult doInBackground() {
                return attachments.prepare(observation,
                        message -> SwingUtilities.invokeLater(() -> observationStatus.setText(message)));
            }

            @Override
            protected void done() {
                try {
                    var result = get();
                    observationStatus.setText(result.chatGptForegrounded()
                            ? "Observation copied to clipboard"
                            : "Observation copied to clipboard; " + result.foregroundDetail());
                    observationStatus.setForeground(SUCCESS);
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    observationStatus.setText("Unable to prepare attachment: " + cause.getMessage());
                    observationStatus.setForeground(ERROR);
                } finally {
                    refreshObservationClipboardState();
                }
            }
        }.execute();
    }

    private void copyCurrentEngineeringResult() {
        if (!hasEngineeringResult(currentHistoryRecord)) {
            observationStatus.setText("No Engineering Result is available for the current request.");
            observationStatus.setForeground(MUTED);
            return;
        }
        boolean copied = EngineeringPartnerClipboard.copyToClipboard(
                HistoryPresentation.handoff(currentHistoryRecord));
        observationStatus.setText(copied
                ? "\u2713 Engineering Result copied for the current request."
                : "Unable to copy Engineering Result to clipboard.");
        observationStatus.setForeground(copied ? SUCCESS : ERROR);
    }

    private void refreshObservationClipboardState() {
        sendButton.setEnabled(currentObservation != null);
        copyResultButton.setEnabled(hasEngineeringResult(currentHistoryRecord));
        setOutputActionsVisible(hasEngineeringResult(currentHistoryRecord)
                || currentObservation != null);
    }

    private void setOutputActionsVisible(boolean visible) {
        if (outputActions == null) {
            return;
        }
        outputActions.setVisible(visible);
        outputActions.getParent().revalidate();
        outputActions.getParent().repaint();
    }

    private static final class HeaderAtmosphere extends JPanel {
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            var g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setPaint(new GradientPaint(0, 0, new Color(9, 25, 35), getWidth(), getHeight(),
                    BACKGROUND));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(43, 189, 146, 35));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Line2D.Double(getWidth() * .35, getHeight(),
                    getWidth() * .62, 0));
            g.draw(new Line2D.Double(getWidth() * .48, getHeight(),
                    getWidth() * .76, 0));
            g.dispose();
        }
    }

    private static final class EngineLogo extends JComponent {
        EngineLogo() {
            setPreferredSize(new Dimension(92, 72));
            setMinimumSize(new Dimension(92, 72));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0;
            g.setColor(new Color(62, 220, 159, 35));
            g.fill(new Ellipse2D.Double(cx - 34, cy - 34, 68, 68));
            g.setColor(new Color(190, 214, 220));
            for (int tooth = 0; tooth < 8; tooth++) {
                g.rotate(Math.PI / 4, cx, cy);
                g.fillRoundRect((int) cx - 5, (int) cy - 37, 10, 11, 3, 3);
            }
            g.rotate(-Math.PI * 2, cx, cy);
            g.setStroke(new BasicStroke(4f));
            g.draw(new Ellipse2D.Double(cx - 28, cy - 28, 56, 56));
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(3f));
            g.draw(new Ellipse2D.Double(cx - 20, cy - 20, 40, 40));
            g.setColor(TEXT);
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(cx + 13, cy - 13, cx - 12, cy - 13));
            g.draw(new Line2D.Double(cx - 12, cy - 13, cx - 12, cy + 14));
            g.draw(new Line2D.Double(cx - 12, cy, cx + 7, cy));
            g.draw(new Line2D.Double(cx - 12, cy + 14, cx + 13, cy + 14));
            g.dispose();
        }
    }

    private static final class SectionGlyph extends JComponent {
        enum Kind { REQUEST, CONFIGURATION, WORKSPACE, TARGET, OBSERVATION }

        private final Kind kind;

        SectionGlyph(Kind kind) {
            this.kind = kind;
            setPreferredSize(new Dimension(34, 34));
            setMinimumSize(new Dimension(34, 34));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (kind == Kind.REQUEST) {
                g.drawRoundRect(8, 4, 18, 25, 3, 3);
                g.draw(new Line2D.Double(12, 11, 22, 11));
                g.draw(new Line2D.Double(12, 16, 22, 16));
                g.draw(new Line2D.Double(12, 21, 19, 21));
            } else if (kind == Kind.CONFIGURATION) {
                g.draw(new Line2D.Double(7, 9, 15, 4));
                g.draw(new Line2D.Double(15, 4, 24, 9));
                g.draw(new Line2D.Double(10, 25, 24, 25));
                g.draw(new Line2D.Double(10, 25, 7, 20));
                g.draw(new Line2D.Double(7, 20, 12, 16));
                g.draw(new Line2D.Double(12, 16, 17, 20));
                g.draw(new Line2D.Double(17, 20, 24, 14));
            } else if (kind == Kind.WORKSPACE) {
                g.drawRoundRect(5, 10, 25, 18, 3, 3);
                g.drawLine(6, 10, 12, 10);
                g.drawLine(12, 10, 15, 6);
                g.drawLine(15, 6, 24, 6);
                g.drawLine(24, 6, 29, 10);
            } else {
                g.drawOval(5, 5, 25, 25);
                g.drawOval(10, 10, 15, 15);
                g.fillOval(15, 15, 5, 5);
            }
            g.dispose();
        }
    }

    private static final class StatusDotIcon implements javax.swing.Icon {
        private final Color color;

        StatusDotIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y) {
            var g = (Graphics2D) graphics.create();
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 55));
            g.fillOval(x, y, 14, 14);
            g.setColor(color);
            g.fillOval(x + 3, y + 3, 8, 8);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }

    private static JPanel cardPanel() {
        var panel = new JPanel();
        panel.setBackground(SURFACE);
        panel.setBorder(new EmptyBorder(14, 16, 14, 16));
        return panel;
    }

    private static final class ScrollablePanel extends JPanel implements javax.swing.Scrollable {
        ScrollablePanel(java.awt.LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(
                java.awt.Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(
                java.awt.Rectangle visibleRect, int orientation, int direction) {
            return Math.max(visibleRect.height - 24, 24);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return true;
        }
    }

    private final class WindowSelectionDialog {
        private final JDialog dialog;
        private final DefaultListModel<DisplayWindow> model = new DefaultListModel<>();
        private final JList<DisplayWindow> list = new JList<>(model);
        private final JButton launchButton = new JButton();
        private final Path workspace;
        private LiveTestRecorder.WindowInfo selected;

        WindowSelectionDialog(JFrame owner, Path workspace) {
            this.workspace = workspace;
            dialog = new JDialog(owner, "Record which window?", true);
            dialog.setLayout(new BorderLayout(8, 8));
            JPanel intro = new JPanel(new BorderLayout(0, 4));
            intro.setBorder(new EmptyBorder(10, 12, 0, 12));
            intro.add(new JLabel("Choose the running application window to capture:"), BorderLayout.NORTH);
            intro.add(new JLabel("Captures only the selected window. A stop overlay will appear on that window."), BorderLayout.SOUTH);
            dialog.add(intro, BorderLayout.NORTH);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            dialog.add(new JScrollPane(list), BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton refresh = new JButton("Refresh");
            launchButton.setText("Launch " + LiveTestRecorder.targetName(workspace));
            launchButton.setEnabled(LiveTestRecorder.targetLaunchDescription(workspace).isPresent());
            JButton cancel = new JButton("Cancel");
            JButton start = new JButton("Start recording");
            actions.add(launchButton);
            actions.add(refresh);
            actions.add(cancel);
            actions.add(start);
            dialog.add(actions, BorderLayout.SOUTH);

            refresh.addActionListener(event -> refreshWindows());
            launchButton.addActionListener(event -> {
                if (LiveTestRecorder.launchTarget(workspace)) {
                    launchButton.setText("Launched — refreshing...");
                    launchButton.setEnabled(false);
                    Timer refreshTimer = new Timer(1200, ignored -> {
                        refreshWindows();
                        launchButton.setText("Launch " + LiveTestRecorder.targetName(workspace));
                        launchButton.setEnabled(
                                LiveTestRecorder.targetLaunchDescription(workspace).isPresent());
                    });
                    refreshTimer.setRepeats(false);
                    refreshTimer.start();
                }
            });
            cancel.addActionListener(event -> dialog.dispose());
            start.addActionListener(event -> {
                DisplayWindow choice = list.getSelectedValue();
                selected = choice == null ? null : choice.window();
                if (selected != null) {
                    dialog.dispose();
                }
            });
            dialog.setSize(560, 360);
            dialog.setLocationRelativeTo(owner);
            refreshWindows();
        }

        LiveTestRecorder.WindowInfo showAndSelect() {
            dialog.setVisible(true);
            return selected;
        }

        private void refreshWindows() {
            new SwingWorker<java.util.List<LiveTestRecorder.WindowInfo>, Void>() {
                @Override
                protected java.util.List<LiveTestRecorder.WindowInfo> doInBackground() {
                    return LiveTestRecorder.runningWindows();
                }

                @Override
                protected void done() {
                    try {
                        String targetName = LiveTestRecorder.targetName(workspace);
                        model.clear();
                        int preferred = -1;
                        var windows = get();
                        for (int index = 0; index < windows.size(); index++) {
                            LiveTestRecorder.WindowInfo window = windows.get(index);
                            model.addElement(new DisplayWindow(window, window.title().toLowerCase()
                                    .contains(targetName.toLowerCase())));
                            if (preferred < 0 && window.title().toLowerCase()
                                    .contains(targetName.toLowerCase())) {
                                preferred = index;
                            }
                        }
                        if (preferred < 0 && !model.isEmpty()) {
                            preferred = 0;
                        }
                        if (preferred >= 0) {
                            list.setSelectedIndex(preferred);
                            list.ensureIndexIsVisible(preferred);
                        }
                    } catch (Exception exception) {
                        status.setText("Unable to list running windows: " + exception.getMessage());
                        status.setForeground(ERROR);
                    }
                }
            }.execute();
        }

        private record DisplayWindow(LiveTestRecorder.WindowInfo window, boolean workspaceTarget) {
            @Override
            public String toString() {
                return workspaceTarget ? "Workspace app (" + window.title() + ")" : window.toString();
            }
        }
    }

    private static final class RecordingOverlay {
        private final JFrame frame = new JFrame();
        private final Timer repositionTimer;
        private final JLabel countdown = new JLabel();
        private final Timer countdownTimer;

        RecordingOverlay(LiveTestRecorder.WindowInfo window, Runnable stopAction, LiveTestRecorder recorder) {
            frame.setUndecorated(true);
            frame.setAlwaysOnTop(true);
            frame.setType(java.awt.Window.Type.UTILITY);
            JPanel panel = new JPanel(new BorderLayout(8, 4));
            panel.setBorder(new EmptyBorder(10, 12, 10, 12));
            panel.setBackground(new Color(127, 29, 29));
            JLabel title = new JLabel("LIVE TEST RECORDING");
            title.setForeground(Color.WHITE);
            title.setFont(new Font("Segoe UI", Font.BOLD, 13));
            countdown.setForeground(Color.WHITE);
            countdown.setFont(new Font("Segoe UI", Font.BOLD, 12));
            JPanel text = new JPanel(new BorderLayout(0, 2));
            text.setOpaque(false);
            text.add(title, BorderLayout.NORTH);
            text.add(countdown, BorderLayout.SOUTH);
            JButton stop = new JButton("Stop recording");
            stop.setForeground(Color.WHITE);
            stop.setBackground(new Color(185, 28, 28));
            stop.setFocusPainted(false);
            stop.addActionListener(event -> stopAction.run());
            stop.setFont(new Font("Segoe UI", Font.BOLD, 13));
            panel.add(text, BorderLayout.CENTER);
            panel.add(stop, BorderLayout.EAST);
            frame.add(panel);
            updateCountdown(recorder);
            countdownTimer = new Timer(1000, event -> updateCountdown(recorder));
            countdownTimer.start();
            frame.pack();
            repositionTimer = new Timer(500, event -> position(window));
            repositionTimer.start();
            position(window);
            frame.setVisible(true);
        }

        private void updateCountdown(LiveTestRecorder recorder) {
            countdown.setText(recorder.remainingSeconds() + " seconds remaining");
        }

        private void position(LiveTestRecorder.WindowInfo window) {
            LiveTestRecorder.windowBounds(window).ifPresent(bounds ->
                    frame.setLocation(bounds.x + Math.max(0, bounds.width - frame.getWidth() - 12), bounds.y + 12));
        }

        void close() {
            repositionTimer.stop();
            countdownTimer.stop();
            frame.dispose();
        }
    }

    private static JLabel label(String text, Font font, Color color) {
        var label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static void stylePrimaryButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(ACCENT);
        button.setBorder(new CompoundBorder(
                new LineBorder(ACCENT, 1, true),
                new EmptyBorder(10, 16, 10, 16)));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static void styleSecondaryButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 11));
        button.setForeground(TEXT);
        button.setBackground(SURFACE_RAISED);
        button.setBorder(new CompoundBorder(
                new LineBorder(new Color(52, 70, 95), 1, true),
                new EmptyBorder(8, 12, 8, 12)));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static void styleSecondaryButton(JComboBox<String> comboBox) {
        comboBox.setFont(BODY_FONT);
        comboBox.setForeground(TEXT);
        comboBox.setBackground(INPUT);
        comboBox.setBorder(new LineBorder(BORDER, 1, true));
    }

    private static void styleTextField(JTextField field) {
        field.setFont(BODY_FONT);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setBackground(INPUT);
        field.setBorder(new CompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(8, 10, 8, 10)));
    }

    private static void styleTextArea(JTextArea area) {
        area.setFont(BODY_FONT);
        area.setForeground(TEXT);
        area.setCaretColor(TEXT);
        area.setSelectionColor(new Color(54, 91, 153));
        area.setBackground(INPUT);
        area.setBorder(new EmptyBorder(12, 12, 12, 12));
    }

    private static void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setBorder(new LineBorder(BORDER, 1, true));
        scrollPane.getViewport().setBackground(INPUT);
        scrollPane.setBackground(INPUT);
    }
}
