package com.engineos.ui;

import com.engineos.application.EngineOsService;
import com.engineos.agent.CopilotModelCatalog;
import com.engineos.history.EngineeringHistoryRecord;
import com.engineos.history.HistoryPresentation;
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
    private static final String ENGINEOS_STARTUP_ACTIVITY = "EngineOS: Starting Coding Agent...";

    private static final Font DISPLAY_FONT = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font WORDMARK_FONT = new Font("Segoe UI", Font.BOLD | Font.ITALIC, 36);
    private static final Font MAJOR_SECTION_FONT = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font SECTION_FONT = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font SMALL_FONT = new Font("Segoe UI", Font.PLAIN, 12);

    private final ObservationPackageService packages;
    private final ObservationAttachmentPreparer attachments;
    private final EngineOsService sessions;
    private final JLabel status = new JLabel();
    private final JLabel requestStatus = new JLabel();
    private final JLabel resultStatus = new JLabel();
    private final JLabel observationStatus = new JLabel();
    private final JButton sendButton = new JButton("COPY OBSERVATION");
    private final JButton startButton = new JButton("SEND TO CODING AGENT");
    private final JButton cancelButton = new JButton("STOP CODING AGENT");
    private final JButton viewTargetButton = new JButton("VIEW ACTIVE TARGET");
    private final JButton historyButton = new JButton("HISTORY");
    private final JButton browseButton = new JButton("BROWSE...");
    private final JLabel requestValidation = new JLabel();
    private final JLabel workspaceTargetLabel = new JLabel();
    private final JLabel verificationTargetLabel = new JLabel();
    private final JTextField projectField = new JTextField(DEFAULT_PROJECT);
    private final JTextArea promptArea = new JTextArea(8, 60);
    private final JTextArea activityArea = new JTextArea(5, 60);
    private final JTextArea usageArea = new JTextArea(8, 30);
    private final JScrollPane activityScroll = new JScrollPane(activityArea);
    private final JPanel activityPanel = new JPanel(new BorderLayout(0, 6));
    private final JPanel activityHeading = new JPanel(new BorderLayout(8, 0));
    private final JLabel activityIndicator = new JLabel("●");
    private final JLabel activityHeadingLabel = new JLabel("CODING AGENT ACTIVITY");
    private final JLabel activityRetention = new JLabel();
    private final JComboBox<String> codingAgent = new JComboBox<>(new String[]{"GitHub Copilot"});
    private final JComboBox<String> model = new JComboBox<>();
    private final JPanel onboardingPanel = new JPanel(new BorderLayout(16, 12));
    private final OnboardingPreferences onboarding = new OnboardingPreferences();
    private final JFrame frame = new JFrame("EngineOS");
    private final DesktopRequestLifecycle requestLifecycle = new DesktopRequestLifecycle();
    private CompletedObservation currentObservation;
    private String currentRunId;
    private EngineeringHistoryRecord currentHistoryRecord;
    private String serviceError;
    private int liveActivityCount;
    private Timer serviceRetryTimer;
    private final Timer activityPulseTimer = new Timer(900, event -> pulseActivityIndicator());
    private final Timer historyTransitionTimer = new Timer(
            HISTORY_TRANSITION_TICK_MS, event -> animateHistoryTransition());
    private boolean activityPulseBright;
    private long historyTransitionStartedAt;

    public ObservationWindow(Path observationsRoot) {
        this(observationsRoot, null);
    }

    public ObservationWindow(Path observationsRoot, EngineOsService sessions) {
        packages = new ObservationPackageService(observationsRoot);
        attachments = new ObservationAttachmentPreparer(packages);
        this.sessions = sessions;
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

        configureControls();
        configureRequestValidation();
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
        styleSecondaryButton(historyButton);
        styleSecondaryButton(browseButton);
        styleSecondaryButton(codingAgent);
        styleTextField(projectField);
        styleTextArea(promptArea);
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
        usageArea.setText("AI usage will appear here after a completed run.");
        styleScrollPane(activityScroll);
        activityScroll.setVisible(false);
        viewTargetButton.setEnabled(false);
        historyButton.setEnabled(serviceError == null && isValidWorkspace());
        historyButton.addActionListener(event -> showHistory());

        model.addActionListener(event -> onboarding.saveModel((String) model.getSelectedItem()));
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
                requestStatus.setText("Stopping coding agent...");
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
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                requestLifecycle.replaceWithRecognizedRequest();
                updateRequestValidation();
                updateVerificationPlan();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                requestLifecycle.replaceWithRecognizedRequest();
                updateRequestValidation();
                updateVerificationPlan();
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
        var workflow = new JPanel(new BorderLayout(0, 10));
        workflow.setOpaque(false);
        workflow.add(buildAgentCard(), BorderLayout.NORTH);
        workflow.add(buildRequestCard(), BorderLayout.CENTER);
        content.add(workflow, BorderLayout.CENTER);
        return content;
    }

    private JPanel buildRequestCard() {
        var card = cardPanel();
        card.setLayout(new BorderLayout(0, 10));

        var heading = buildSectionHeading(
                SectionGlyph.Kind.REQUEST,
                "ENGINEERING REQUEST",
                "Paste the request prepared by your Engineering Partner (ChatGPT, Claude, etc.).");

        var editorScroll = new JScrollPane(promptArea);
        styleScrollPane(editorScroll);
        editorScroll.setPreferredSize(new Dimension(0, 260));
        editorScroll.setMinimumSize(new Dimension(0, 210));
        card.setMinimumSize(new Dimension(0, 330));
        configureOnboarding();
        var requestEditor = new JPanel(new BorderLayout(0, 10));
        requestEditor.setOpaque(false);
        requestEditor.add(onboardingPanel, BorderLayout.NORTH);
        requestEditor.add(editorScroll, BorderLayout.CENTER);

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
        requestFooter.add(lower, BorderLayout.NORTH);
        requestFooter.add(buildActionRow(), BorderLayout.SOUTH);

        card.add(heading, BorderLayout.NORTH);
        card.add(requestEditor, BorderLayout.CENTER);
        card.add(requestFooter, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildAgentCard() {
        var card = cardPanel();
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(10, 16, 10, 16));
        card.setPreferredSize(new Dimension(0, 240));
        card.setMinimumSize(new Dimension(0, 220));

        var heading = buildSectionHeading(
                SectionGlyph.Kind.CONFIGURATION,
                "EXECUTION CONFIGURATION",
                "Configure the coding agent, workspace, and verification target.");

        var controls = new JPanel(new GridBagLayout());
        controls.setOpaque(false);
        var constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;

        var projectLabel = label("PROJECT / WORKSPACE", SMALL_FONT, MUTED);
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 1;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        constraints.insets = new Insets(0, 0, 0, 12);
        controls.add(projectLabel, constraints);

        var workspaceInput = new JPanel(new BorderLayout(10, 0));
        workspaceInput.setOpaque(false);
        workspaceInput.add(browseButton, BorderLayout.WEST);
        workspaceInput.add(projectField, BorderLayout.CENTER);
        projectField.setColumns(36);
        constraints.gridx = 1;
        constraints.gridwidth = 3;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = new Insets(0, 0, 0, 0);
        controls.add(workspaceInput, constraints);
        constraints.gridx = 4;
        constraints.gridwidth = 1;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        constraints.insets = new Insets(0, 0, 0, 0);
        controls.add(historyButton, constraints);

        var targetDetails = new JPanel(new GridBagLayout());
        targetDetails.setOpaque(false);
        targetDetails.setBorder(new CompoundBorder(
                new LineBorder(new Color(38, 68, 78), 1, true),
                new EmptyBorder(4, 12, 4, 12)));
        var targetConstraints = new GridBagConstraints();
        targetConstraints.anchor = GridBagConstraints.WEST;
        targetConstraints.fill = GridBagConstraints.HORIZONTAL;
        targetConstraints.weightx = 1;
        targetConstraints.insets = new Insets(0, 24, 0, 0);
        targetDetails.add(targetCard("WORKSPACE", workspaceTargetLabel, SectionGlyph.Kind.WORKSPACE),
                targetConstraints);
        workspaceTargetLabel.setBorder(new EmptyBorder(1, 0, 1, 0));
        targetConstraints.gridx = 1;
        targetConstraints.insets = new Insets(0, 0, 0, 0);
        targetDetails.add(targetCard("VERIFICATION TARGET", verificationTargetLabel, SectionGlyph.Kind.TARGET),
                targetConstraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 5;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = new Insets(4, 0, 0, 0);
        controls.add(targetDetails, constraints);

        var agentTitle = label("CODING AGENT", SECTION_FONT, ACCENT);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 1;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        constraints.insets = new Insets(4, 0, 0, 12);
        controls.add(agentTitle, constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = new Insets(4, 0, 0, 24);
        controls.add(codingAgent, constraints);

        var modelTitle = label("MODEL", SECTION_FONT, ACCENT);
        constraints.gridx = 2;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        constraints.insets = new Insets(4, 0, 0, 12);
        controls.add(modelTitle, constraints);
        constraints.gridx = 3;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = new Insets(4, 0, 0, 0);
        controls.add(model, constraints);

        var agentDescription = label(
                "EngineOS sends the request to the selected agent, then runs observation.",
                SMALL_FONT, MUTED);
        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 5;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = new Insets(4, 0, 0, 0);
        controls.add(agentDescription, constraints);

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
                new EmptyBorder(18, 20, 18, 20)));

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

        var onboardingHeading = new JPanel(new BorderLayout(0, 5));
        onboardingHeading.setOpaque(false);
        onboardingHeading.add(label("CHOOSE YOUR ENGINEERING PARTNER", SECTION_FONT, TEXT),
                BorderLayout.NORTH);
        onboardingHeading.add(explanation, BorderLayout.CENTER);

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
        onboardingPanel.add(instructionsPanel, BorderLayout.CENTER);
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
        onboardingPanel.setVisible(!valid);
        onboardingPanel.getParent().revalidate();
        onboardingPanel.getParent().repaint();
    }

    private void updateVerificationPlan() {
        if (sessions == null || serviceError != null) {
            workspaceTargetLabel.setText("");
            verificationTargetLabel.setText("");
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
            return;
        }
        var plan = sessions.verificationPlan(projectPath, promptArea.getText());
        if (!plan.resolved()) {
            workspaceTargetLabel.setText("<html><font color='#94A3B8'>" + escape(project) + "</font></html>");
            verificationTargetLabel.setText("<html><font color='#F87171'>" + escape(plan.error()) + "</font></html>");
            return;
        }
        if (plan.targetKind() == null) {
            workspaceTargetLabel.setText("<html><font color='#94A3B8'>" + escape(project) + "</font></html>");
            verificationTargetLabel.setText("<html><font color='#FBBF24'>No supported target resolved</font></html>");
            return;
        }
        String workspaceKind = plan.workspaceKind().name();
        String targetKind = plan.targetKind().name();
        workspaceTargetLabel.setText(workspaceValue(workspaceKind, plan.workspaceRoot().toString()));
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
        String project = projectField.getText().trim();
        String prompt = promptArea.getText().trim();
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
        setActivityActive(true);
        resultStatus.setText("No Engineering Result available for the current request.");
        resultStatus.setForeground(MUTED);
        requestLifecycle.submitted();
        activityArea.setText("\u2022 " + ENGINEOS_STARTUP_ACTIVITY);
        activityArea.setCaretPosition(activityArea.getDocument().getLength());
        requestStatus.setText(requestLifecycle.displayText());
        requestStatus.setForeground(MUTED);
        updateActionControls(true);
        activityPanel.revalidate();
        activityPanel.repaint();
        new SwingWorker<EngineOsService.ExecutionResult, EngineOsService.ExecutionState>() {
            @Override
            protected EngineOsService.ExecutionResult doInBackground() {
                return sessions.submit(
                        Path.of(project),
                        prompt,
                        (String) model.getSelectedItem(),
                        this::publish,
                        activity -> SwingUtilities.invokeLater(() -> appendActivity(activity)));
            }
            @Override
            protected void process(java.util.List<EngineOsService.ExecutionState> states) {
                if (!states.isEmpty()) {
                    var state = states.get(states.size() - 1);
                    requestLifecycle.serviceState(state);
                    requestStatus.setText(requestLifecycle.displayText());
                    requestStatus.setForeground(state == EngineOsService.ExecutionState.FAILED
                            ? ERROR : MUTED);
                    updateActionControls(EngineeringRequestValidator.isValid(promptArea.getText()));
                    if (state == EngineOsService.ExecutionState.CODING_AGENT_WORKING) {
                        activityPanel.setVisible(true);
                        activityScroll.setVisible(true);
                        activityScroll.revalidate();
                        setActivityActive(true);
                    }
                    if (state == EngineOsService.ExecutionState.RUNNING_OBSERVATION) {
                        setActivityActive(false);
                        viewTargetButton.setEnabled(true);
                    }
                }
            }

            @Override
            protected void done() {
                DesktopDiagnostics.stage("done-entered");
                try {
                    var result = get();
                    DesktopDiagnostics.stage("result-obtained");
                    requestLifecycle.serviceState(result.state());
                    setActivityActive(false);
                    requestStatus.setText(requestLifecycle.displayText());
                    requestStatus.setForeground(result.state() == EngineOsService.ExecutionState.GREEN
                            ? SUCCESS : ERROR);
                    DesktopDiagnostics.stage("lifecycle-updated");
                    currentRunId = result.engineeringResult() == null
                            || result.engineeringResult().history() == null
                            ? null : result.engineeringResult().history().runId();
                    currentHistoryRecord = findHistoryRecord(currentRunId);
                    DesktopDiagnostics.stage("history-loaded");
                    resultStatus.setText(currentResultMatches(currentRunId, currentHistoryRecord)
                            ? "Engineering Result available for the current request."
                            : "No Engineering Result available for the current request.");
                    resultStatus.setForeground(currentResultMatches(currentRunId, currentHistoryRecord)
                            ? SUCCESS : MUTED);
                    if (result.state() == EngineOsService.ExecutionState.GREEN) {
                        currentObservation = packages.findCompletedForRun(currentRunId).orElse(null);
                        observationStatus.setText(currentObservation == null
                                ? "Request completed; no observation package available for this run."
                                : "Observation ready for current request.");
                        observationStatus.setForeground(currentObservation == null ? ERROR : SUCCESS);
                    } else {
                        currentObservation = null;
                        observationStatus.setText(
                                "No observation package available for the current request.");
                        observationStatus.setForeground(MUTED);
                    }
                    DesktopDiagnostics.stage("observation-located");
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
                    DesktopDiagnostics.stage("usage-updated");
                    activityPanel.setVisible(true);
                    activityScroll.setVisible(false);
                    activityRetention.setVisible(true);
                    activityRetention.setText(activityRetentionText(result.activity()));
                    DesktopDiagnostics.stage("activity-retained");
                    startHistoryTransition();
                    DesktopDiagnostics.stage("history-transition-started");
                    viewTargetButton.setEnabled(false);
                    DesktopDiagnostics.stage("done-completed");
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    DesktopDiagnostics.exception("done", exception);
                    requestLifecycle.serviceState(EngineOsService.ExecutionState.FAILED);
                    setActivityActive(false);
                    requestStatus.setText("Request failed/terminated: " + cause.getMessage());
                    requestStatus.setForeground(ERROR);
                    activityPanel.setVisible(true);
                    activityScroll.setVisible(false);
                    activityRetention.setVisible(true);
                    activityRetention.setText(activityRetentionText(liveActivityCount));
                    startHistoryTransition();
                    currentHistoryRecord = null;
                } finally {
                    refreshObservationClipboardState();
                    updateRequestValidation();
                    DesktopDiagnostics.stage("controls-refreshed");
                }
            }
        }.execute();
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

    private static String historyStatus(EngineeringHistoryRecord record) {
        var assessment = com.engineos.history.EngineeringAssessment.from(record);
        return assessment.verificationStatus() + " / " + assessment.implementationStatus();
    }

    private static boolean hasEngineeringResult(EngineeringHistoryRecord record) {
        return record != null && record.engineeringResult() != null
                && !record.engineeringResult().isBlank();
    }

    private static String labelFor(EngineOsService.ExecutionState state) {
        return switch (state) {
            case STARTING -> "Starting...";
            case CODING_AGENT_WORKING -> "Coding agent working...";
            case RUNNING_OBSERVATION -> "Running observation...";
            case GREEN -> "GREEN - Observation ready";
            case FAILED -> "FAILED - See details";
            case CANCELLED -> "CANCELLED - Implementation not complete";
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
        enum Kind { REQUEST, CONFIGURATION, WORKSPACE, TARGET }

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
