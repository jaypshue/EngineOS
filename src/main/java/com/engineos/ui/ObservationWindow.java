package com.engineos.ui;

import com.engineos.application.EngineOsService;
import com.engineos.agent.CopilotModelCatalog;
import com.engineos.history.EngineeringHistoryRecord;
import com.engineos.history.HistoryPresentation;
import com.engineos.infrastructure.git.GitBranch;
import com.engineos.infrastructure.git.GitRepository;
import com.engineos.infrastructure.git.GitStatus;
import com.engineos.infrastructure.git.GitSnapshot;
import com.engineos.infrastructure.git.ProcessGitRepository;
import com.engineos.observation.CompletedObservation;
import com.engineos.observation.ObservationAttachmentPreparer;
import com.engineos.observation.ObservationPackageService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
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
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

public final class ObservationWindow {
    private static final String DEFAULT_PROJECT = "C:\\projects\\intassist";
    static final String HEADER_SUBTITLE = "ENGINEERING CONTROL ENVIRONMENT";

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
    private final JButton startButton = new JButton("SUBMIT ENGINEERING REQUEST");
    private final JButton cancelButton = new JButton("STOP CODING AGENT");
    private final JButton viewTargetButton = new JButton("VIEW ACTIVE TARGET");
    private final JButton historyButton = new JButton("HISTORY");
    private final JButton gitButton = new JButton("GIT");
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
    private final JPanel activityHeading = new JPanel(new BorderLayout(8, 0));
    private final JLabel activityIndicator = new JLabel("●");
    private final JLabel activityHeadingLabel = new JLabel("ENGINEOS ACTIVITY");
    private final JLabel activityRetention = new JLabel();
    private final JComboBox<String> codingAgent = new JComboBox<>(new String[]{"GitHub Copilot"});
    private final JComboBox<String> model = new JComboBox<>();
    private final JComboBox<String> targetSelector = new JComboBox<>();
    private final JPanel onboardingPanel = new JPanel(new BorderLayout(16, 12));
    private final OnboardingPreferences onboarding = new OnboardingPreferences();
    private final JFrame frame = new JFrame("EngineOS");
    private final DesktopRequestLifecycle requestLifecycle = new DesktopRequestLifecycle();
    private CompletedObservation currentObservation;
    private String currentRunId;
    private EngineeringHistoryRecord currentHistoryRecord;
    private GitSnapshot currentGitSnapshot;
    private Path currentGitWorkspace;
    private boolean preflightExpanded;
    private String serviceError;
    private int liveActivityCount;
    private Timer serviceRetryTimer;
    private final Timer activityPulseTimer = new Timer(900, event -> pulseActivityIndicator());
    private final Timer historyTransitionTimer = new Timer(
            HISTORY_TRANSITION_TICK_MS, event -> animateHistoryTransition());
    private boolean activityPulseBright;
    private long historyTransitionStartedAt;
    private long requestGeneration;
    private boolean updatingTargetSelector;

    public ObservationWindow(Path observationsRoot) {
        this(observationsRoot, null, new ProcessGitRepository());
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
                serviceError = "EngineOS Service unavailable. Start the Service Host before using the desktop.";
                codingAgent.addItem("Service unavailable");
            }
            var availableModels = CopilotModelCatalog.discover();
            availableModels.forEach(model::addItem);
            model.setSelectedItem(onboarding.restoreModel(availableModels, "auto"));
        }
        projectField.setText(onboarding.restoreWorkspace(DEFAULT_PROJECT));
        promptArea.setText(DEFAULT_INVESTIGATION_REQUEST);

        configureControls();
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
            }

            @Override
            public void windowClosed(WindowEvent event) {
                DesktopDiagnostics.stage("windowClosed");
            }
        });
        frame.setContentPane(root);
        frame.setBackground(BACKGROUND);
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setSize(1040, onboardingPanel.isVisible() ? 790 : 680);
        frame.setLocation(40, 0);
        startServiceReconnectTimer();
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
                status.setText("Connected to EngineOS Service.");
                status.setForeground(SUCCESS);
                status.setIcon(new StatusDotIcon(SUCCESS));
                status.setToolTipText(null);
                updateRequestValidation();
                updateWorkspaceState();
                updateVerificationPlan();
                serviceRetryTimer.stop();
            } catch (IllegalStateException exception) {
                status.setText("EngineOS Service unavailable; retrying...");
                status.setToolTipText(exception.getMessage());
            }
        });
        serviceRetryTimer.setRepeats(true);
        serviceRetryTimer.start();
    }

    private void configureControls() {
        refreshObservationClipboardState();
        status.setText(serviceError == null ? "Desktop ready" : serviceError);
        status.setForeground(MUTED);
        status.setIcon(new StatusDotIcon(SUCCESS));
        requestStatus.setText(requestLifecycle.displayText());
        requestStatus.setForeground(MUTED);
        resultStatus.setText("No Engineering Result available for the current request.");
        resultStatus.setForeground(MUTED);
        observationStatus.setText("No observation package available for the current request.");
        observationStatus.setForeground(MUTED);
        updateActionControls(false);

        stylePrimaryButton(startButton);
        styleSecondaryButton(cancelButton);
        styleSecondaryButton(sendButton);
        styleSecondaryButton(viewTargetButton);
        styleSecondaryButton(gitButton);
        styleSecondaryButton(historyButton);
        styleSecondaryButton(browseButton);
        styleSecondaryButton(codingAgent);
        styleTextField(projectField);
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
        resultArea.setText("The Coding Agent observation/result will appear here after submission.");
        resultPanel.setOpaque(false);
        resultPanel.setVisible(false);
        usageArea.setText("AI usage will appear here after a completed run.");
        styleScrollPane(activityScroll);
        activityScroll.setVisible(false);
        viewTargetButton.setEnabled(false);
        historyButton.setEnabled(serviceError == null && isValidWorkspace());
        gitButton.setEnabled(isValidWorkspace());
        historyButton.addActionListener(event -> showHistory());
        gitButton.addActionListener(event -> showGitStatus());

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
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        content.add(workflowScroll, BorderLayout.CENTER);

        return content;
    }

    private JPanel buildWorkflowFooter() {
        var footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new javax.swing.BoxLayout(
                footer, javax.swing.BoxLayout.Y_AXIS));
        footer.add(buildPreflightPanel());
        footer.add(javax.swing.Box.createVerticalStrut(8));
        footer.add(buildActionRow());
        return footer;
    }

    private JPanel buildRequestCard() {
        var card = cardPanel();
        card.setLayout(new BorderLayout(0, 10));

        var heading = buildSectionHeading(
                SectionGlyph.Kind.REQUEST,
                "ENGINEERING REQUEST",
                "Describe what you want EngineOS to establish about the selected existing system.");

        var editorScroll = new JScrollPane(promptArea);
        styleScrollPane(editorScroll);
        editorScroll.setPreferredSize(new Dimension(0, 150));
        editorScroll.setMinimumSize(new Dimension(0, 120));
        card.setMinimumSize(new Dimension(0, 280));
        configureOnboarding();
        var requestEditor = new JPanel(new BorderLayout(0, 10));
        requestEditor.setOpaque(false);
        requestEditor.add(editorScroll, BorderLayout.CENTER);
        var resultScroll = new JScrollPane(resultArea);
        styleScrollPane(resultScroll);
        resultScroll.setPreferredSize(new Dimension(0, 180));
        resultPanel.add(buildSectionHeading(
                SectionGlyph.Kind.OBSERVATION,
                "OBSERVATION / RESULT",
                "The returned Coding Agent result and EngineOS evidence for this request."),
                BorderLayout.NORTH);
        resultPanel.add(resultScroll, BorderLayout.CENTER);
        requestEditor.add(resultPanel, BorderLayout.SOUTH);

        var validationRow = new JPanel(new BorderLayout(12, 0));
        validationRow.setOpaque(false);
        validationRow.setBorder(new EmptyBorder(2, 2, 0, 2));
        validationRow.add(requestValidation, BorderLayout.WEST);

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
        lower.add(validationRow, BorderLayout.SOUTH);

        var requestFooter = new JPanel(new BorderLayout(0, 10));
        requestFooter.setOpaque(false);
        var footerStack = new JPanel();
        footerStack.setOpaque(false);
        footerStack.setLayout(new javax.swing.BoxLayout(
                footerStack, javax.swing.BoxLayout.Y_AXIS));
        footerStack.add(onboardingPanel);
        footerStack.add(lower);
        requestFooter.add(footerStack, BorderLayout.NORTH);

        card.add(heading, BorderLayout.NORTH);
        card.add(requestEditor, BorderLayout.CENTER);
        card.add(requestFooter, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildAgentCard() {
        var card = cardPanel();
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(10, 16, 10, 16));
        card.setMinimumSize(new Dimension(0, 300));

        var heading = buildSectionHeading(
                SectionGlyph.Kind.CONFIGURATION,
                "EXECUTION CONFIGURATION",
                "Configure the coding agent, workspace, and verification target.");

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
        projectActions.add(gitButton);
        projectActions.add(historyButton);
        projectRow.add(projectActions, BorderLayout.EAST);
        controls.add(projectRow);

        controls.add(javax.swing.Box.createVerticalStrut(8));

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
        controls.add(targetDetails);

        controls.add(javax.swing.Box.createVerticalStrut(8));

        var sourceRow = targetCard("SOURCE CONTROL", sourceControlLabel, SectionGlyph.Kind.TARGET);
        sourceRow.setOpaque(true);
        sourceRow.setBackground(SURFACE_RAISED);
        sourceRow.setBorder(new CompoundBorder(
                new LineBorder(new Color(38, 68, 78), 1, true),
                new EmptyBorder(8, 12, 8, 12)));
        controls.add(sourceRow);

        controls.add(javax.swing.Box.createVerticalStrut(8));

        var agentModelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        agentModelRow.setOpaque(false);
        var agentTitle = label("CODING AGENT", SECTION_FONT, ACCENT);
        var agentField = new JPanel(new BorderLayout(8, 0));
        agentField.setOpaque(false);
        agentField.add(agentTitle, BorderLayout.WEST);
        agentField.add(codingAgent, BorderLayout.CENTER);
        agentModelRow.add(agentField);
        var modelTitle = label("MODEL", SECTION_FONT, ACCENT);
        var modelField = new JPanel(new BorderLayout(8, 0));
        modelField.setOpaque(false);
        modelField.add(modelTitle, BorderLayout.WEST);
        modelField.add(model, BorderLayout.CENTER);
        agentModelRow.add(modelField);
        controls.add(agentModelRow);

        var agentDescription = label(
                "EngineOS sends the request to the selected agent, then runs observation.",
                SMALL_FONT, MUTED);
        controls.add(agentDescription);

        card.add(heading, BorderLayout.NORTH);
        card.add(controls, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildActionRow() {
        var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(startButton);
        actions.add(cancelButton);
        actions.add(sendButton);
        actions.add(viewTargetButton);
        return actions;
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
            requestStatus.setText(requestLifecycle.displayText());
        }
        requestValidation.setText(valid
                ? "\u2713 Engineering Request recognized"
                : "This request hasn't been prepared for EngineOS yet.");
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
        gitButton.setEnabled(valid);
        onboardingPanel.setVisible(!valid);
        onboardingPanel.getParent().revalidate();
        onboardingPanel.getParent().repaint();
        refreshSourceControl();
        updateVerificationPlan();
    }

    private void refreshSourceControl() {
        if (sessions == null || serviceError != null) {
            currentGitSnapshot = null;
            currentGitWorkspace = null;
            sourceControlLabel.setText("<html><font color='#94A3B8'>Unavailable until the service is connected.</font></html>");
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
            return;
        }
        currentGitWorkspace = workspace;
        sourceControlLabel.setText("<html><font color='#94A3B8'>Reading Git pre-flight state...</font></html>");
        new SwingWorker<GitSnapshot, Void>() {
            @Override
            protected GitSnapshot doInBackground() {
                return sessions.sourceControl(workspace);
            }

            @Override
            protected void done() {
                try {
                    if (!workspace.equals(currentGitWorkspace)) {
                        return;
                    }
                    currentGitSnapshot = get();
                    renderSourceControl();
                } catch (Exception exception) {
                    currentGitSnapshot = GitSnapshot.unavailable(workspace);
                    sourceControlLabel.setText("<html><font color='#F87171'>Git pre-flight unavailable: "
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
                    "AGENT", "\u26A0 EngineOS Service unavailable"));
            setPreflightSummary("\uD83D\uDD34 NOT READY", "Service unavailable", ERROR);
            return;
        }
        String project = projectField.getText().trim();
        Path workspace;
        try {
            workspace = project.isBlank() ? null : Path.of(project);
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
        boolean verificationAvailable = plan != null && plan.resolved() && plan.targetKind() != null;
        boolean sourceLoaded = currentGitSnapshot != null
                && workspace.equals(currentGitWorkspace);
        boolean ready = workspaceValid && requestValid && verificationAvailable
                && sourceLoaded && codingAgent.getSelectedItem() != null
                && model.getSelectedItem() != null;
        boolean review = ready && !currentGitSnapshot.clean();
        String overall = review ? "\uD83D\uDFE1 REVIEW" : ready ? "\uD83D\uDFE2 READY" : "\uD83D\uDD34 NOT READY";
        Color overallColor = review ? ACTIVE_AMBER : ready ? SUCCESS : ERROR;
        String overallSummary = review ? "Existing changes detected"
                : ready ? "Ready to submit" : "Required state unavailable";

        String targetName = workspace == null || project.isBlank()
                ? "Unavailable"
                : workspace.getFileName() == null ? workspace.toString() : workspace.getFileName().toString();
        String target = requestValid && workspaceValid
                ? "\u2713 " + targetName + "<br><font color='#94A3B8'>" + escape(project) + "</font>"
                : "\u26A0 Required workspace/request unavailable";

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
                ? "\u26A0 Coding Agent unavailable"
                : "\u2713 " + escape(String.valueOf(codingAgent.getSelectedItem()))
                + " &nbsp; Model: " + escape(String.valueOf(model.getSelectedItem()));
        String verification = plan == null || !plan.resolved()
                ? "\u26A0 Verification configuration unavailable"
                : verificationAvailable
                ? "\u2713 Available &nbsp; Target: " + escape(plan.targetKind().name())
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
        setPreflightSummary(overall, overallSummary, overallColor);
    }

    private void setPreflightSummary(String overall, String summary, Color color) {
        preflightSummaryLabel.setText("<html><font color='" + colorHex(color)
                + "'><b>" + overall + "</b></font> &nbsp; " + escape(summary) + "</html>");
    }

    private static String preflightHtml(String overall, Color color, String section, String detail) {
        return "<html><font color='" + colorHex(color) + "'><b>" + overall + "</b></font>"
                + "<br><br><b>" + section + "</b><br>" + detail + "</html>";
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
        long generation = ++requestGeneration;
        String project = projectField.getText().trim();
        String prompt = promptArea.getText().trim();
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
        resultStatus.setText("No Engineering Result available for the current request.");
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
                    requestLifecycle.serviceState(EngineOsService.ExecutionState.WAITING_FOR_APPROVAL);
                    requestStatus.setText(requestLifecycle.displayText());
                    requestStatus.setForeground(ACTIVE_AMBER);
                    updateActionControls(EngineeringRequestValidator.isValid(promptArea.getText()));
                    showProposalDecision(proposal, generation);
                } catch (Exception exception) {
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
                    updateActionControls(EngineeringRequestValidator.isValid(promptArea.getText()));
                } finally {
                    refreshObservationClipboardState();
                    updateRequestValidation();
                }
            }
        }.execute();
    }

    private void showProposalDecision(EngineOsService.TaskProposal proposal, long generation) {
        var details = new JTextArea(proposalText(proposal), 12, 56);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        styleTextArea(details);
        var content = new JPanel(new BorderLayout(0, 8));
        content.setBackground(SURFACE);
        content.add(label("Review the proposed task. No Coding Task will be created until you approve.",
                BODY_FONT, TEXT), BorderLayout.NORTH);
        var scroll = new JScrollPane(details);
        styleScrollPane(scroll);
        content.add(scroll, BorderLayout.CENTER);

        Object[] options = {"Approve Task", "Reject Task"};
        var pane = new JOptionPane(content, JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, options, options[1]);
        var dialog = pane.createDialog(frame, "Proposed Engineering Task");
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
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

    static String proposalText(EngineOsService.TaskProposal proposal) {
        return "Task: " + proposal.title()
                + "\nStatus: " + proposal.status()
                + "\n\nGoal:\n" + proposal.goal()
                + "\n\nIn scope:\n" + proposal.scope()
                + "\n\nOut of scope:\n" + proposal.outOfScope()
                + "\n\nAcceptance criteria:\n- " + String.join("\n- ", proposal.acceptanceCriteria())
                + "\n\nConstraints:\n" + proposal.constraints()
                + "\n\nRationale:\n" + proposal.rationale()
                + "\n\nSuggested verification:\n" + proposal.verificationApproach()
                + "\n\nProposal source:\n" + proposal.proposalSource()
                + "\n\nWhat the Coder may do:\n" + proposal.allowedActions();
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
        resultStatus.setText(currentResultMatches(currentRunId, currentHistoryRecord)
                ? "Engineering Result available for the current request."
                : "No Engineering Result available for the current request.");
        resultStatus.setForeground(currentResultMatches(currentRunId, currentHistoryRecord)
                ? SUCCESS : MUTED);
        if (currentResultMatches(currentRunId, currentHistoryRecord)) {
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
        resultArea.setText("The Coding Agent observation/result will appear here after submission.");
        resultPanel.setVisible(false);
        resultStatus.setText("No Engineering Result available for the current request.");
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
                    var commitMessage = new JTextField();
                    styleTextField(commitMessage);
                    if (currentHistoryRecord != null) {
                        commitMessage.setText(commitMessageFor(currentHistoryRecord));
                    }
                    var commitButton = new JButton("COMMIT STAGED");
                    stylePrimaryButton(commitButton);
                    commitButton.setEnabled(false);
                    var feedback = label("", SMALL_FONT, MUTED);
                    commitMessage.getDocument().addDocumentListener(new DocumentListener() {
                        private void update() {
                            commitButton.setEnabled(!commitMessage.getText().trim().isEmpty());
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
                    var commitPanel = new JPanel(new BorderLayout(8, 0));
                    commitPanel.setOpaque(false);
                    commitPanel.setBorder(new EmptyBorder(10, 14, 10, 14));
                    commitPanel.add(label("COMMIT MESSAGE", SMALL_FONT, MUTED), BorderLayout.WEST);
                    commitPanel.add(commitMessage, BorderLayout.CENTER);
                    commitPanel.add(commitButton, BorderLayout.EAST);
                    var dialog = new JDialog(frame, "Git Status", false);
                    dialog.setLayout(new BorderLayout());
                    dialog.getContentPane().setBackground(BACKGROUND);
                    var scroll = new JScrollPane(details);
                    styleScrollPane(scroll);
                    dialog.add(scroll, BorderLayout.CENTER);
                    var bottom = new JPanel(new BorderLayout(0, 4));
                    bottom.setOpaque(false);
                    bottom.add(commitPanel, BorderLayout.NORTH);
                    bottom.add(feedback, BorderLayout.SOUTH);
                    bottom.setBorder(new EmptyBorder(0, 0, 10, 0));
                    dialog.add(bottom, BorderLayout.SOUTH);
                    commitButton.addActionListener(event -> commitFromGitDialog(
                            workspace, commitMessage, commitButton, feedback, details));
                    dialog.setSize(900, 600);
                    dialog.setMinimumSize(new Dimension(620, 360));
                    dialog.setLocationRelativeTo(frame);
                    dialog.setVisible(true);
                } catch (Exception exception) {
                    status.setText("Git status unavailable: " + exception.getMessage());
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
        if (message.isEmpty()) {
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
                } catch (Exception | LinkageError exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    feedback.setText("Commit failed: " + cause.getMessage());
                    feedback.setForeground(ERROR);
                    commitButton.setEnabled(!commitMessage.getText().trim().isEmpty());
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
        var text = new StringBuilder();
        text.append("GIT STATUS\n\n")
                .append("Branch: ").append(status.branch()).append('\n')
                .append("HEAD: ").append(git.snapshot(workspace).headSha()).append('\n')
                .append("Working tree: ").append(status.clean() ? "clean" : "changed")
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

    private static String historyStatus(EngineeringHistoryRecord record) {
        var assessment = com.engineos.history.EngineeringAssessment.from(record);
        return assessment.verificationStatus() + " / " + assessment.implementationStatus();
    }

    private static String commitMessageFor(EngineeringHistoryRecord record) {
        String summary = record == null ? "" : record.requestSummary();
        if (summary == null || summary.isBlank()) {
            summary = record == null ? "EngineOS engineering change" : record.displaySummary();
        }
        summary = summary.replaceAll("\\s+", " ").trim();
        return summary.length() > 72 ? summary.substring(0, 69) + "..." : summary;
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

    private void refreshObservationClipboardState() {
        sendButton.setEnabled(currentObservation != null);
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
            return false;
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
