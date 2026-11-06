package com.studyagent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Swing view with a tab for every feature. Like {@link CLIView}, it only
 * talks to {@link StudyController}. Slow requests run off the UI thread, and
 * every button is guarded so an unexpected error shows a message instead of
 * failing silently.
 */
public class MainGUI {

    private final JFrame frame = new JFrame("AI Study Agent");
    private final StudyController controller;
    private final JTabbedPane tabs = new JTabbedPane();
    private final JButton uploadButton = new JButton("Upload");
    private final JButton exportButton = new JButton("Export");
    private final JLabel statusLabel = new JLabel("Upload a PDF or TXT file of course material, or pick a tab to start.");

    // Ask tab
    private final JTextField questionField = new JTextField(40);
    private final JButton askButton = new JButton("Ask");
    private final JTextArea answerArea = textArea();

    // Quiz tab
    private final JTextField quizTopicField = new JTextField(24);
    private final JButton generateQuizButton = new JButton("Generate quiz");
    private final JPanel quizQuestionsPanel = new JPanel();
    private final JButton submitQuizButton = new JButton("Submit answers");
    private final JTextArea quizResultArea = textArea();
    private final List<ButtonGroup> quizAnswerGroups = new ArrayList<>();
    private Quiz currentQuiz;

    // Flashcards tab
    private final JTextField flashcardTopicField = new JTextField(24);
    private final JButton generateFlashcardsButton = new JButton("Generate flashcards");
    private final JTextArea cardArea = textArea();
    private final JButton flipButton = new JButton("Flip");
    private final JButton gotItButton = new JButton("Got it");
    private final JButton missedItButton = new JButton("Missed it");
    private final JButton endSessionButton = new JButton("End session");
    private final JLabel deckStatusLabel = new JLabel(" ");
    private List<Flashcard> deck;
    private String deckTopic;
    private Flashcard currentCard;

    // Study Plan tab
    private final JTextField planTopicField = new JTextField(24);
    private final JSpinner planDaysSpinner = new JSpinner(new SpinnerNumberModel(7, 1, 365, 1));
    private final JButton generatePlanButton = new JButton("Generate plan");
    private final JTextArea planArea = textArea();

    // Summarize tab
    private final JButton summarizeButton = new JButton("Summarize a file...");
    private final JTextArea summaryArea = textArea();

    // Graph tab
    private final JTextField graphTopicField = new JTextField(24);
    private final JButton buildGraphButton = new JButton("Build graph");
    private final JTextArea graphArea = textArea();

    // Progress tab
    private final JTextArea progressArea = textArea();

    // Recommend tab
    private final JButton recommendButton = new JButton("Get recommendation");
    private final JTextArea recommendationArea = textArea();

    // History tab
    private final JTextArea historyArea = textArea();

    public MainGUI(StudyController controller) {
        this.controller = controller;

        uploadButton.addActionListener(guarded(this::chooseAndUpload));
        uploadButton.setToolTipText("Add a PDF or TXT file of course material for answers to draw on");
        exportButton.addActionListener(guarded(this::chooseAndExport));
        exportButton.setToolTipText("Save your latest study plan or progress summary as Markdown or PDF");

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(uploadButton);
        toolbar.add(exportButton);

        tabs.addTab("Ask", buildAskTab());
        tabs.addTab("Quiz", buildQuizTab());
        tabs.addTab("Flashcards", buildFlashcardsTab());
        tabs.addTab("Study Plan", buildPlanTab());
        tabs.addTab("Summarize", buildSummarizeTab());
        tabs.addTab("Graph", buildGraphTab());
        tabs.addTab("Progress", buildProgressTab());
        tabs.addTab("Recommend", buildRecommendTab());
        tabs.addTab("History", buildHistoryTab());
        // Progress and History refresh when opened, so they include anything done since.
        tabs.addChangeListener(e -> guarded(() -> {
            String title = tabs.getTitleAt(tabs.getSelectedIndex());
            if (title.equals("Progress")) {
                refreshProgress();
            } else if (title.equals("History")) {
                refreshHistory();
            }
        }).actionPerformed(null));

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        frame.setLayout(new BorderLayout());
        frame.add(toolbar, BorderLayout.NORTH);
        frame.add(tabs, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(820, 620);
        frame.setLocationRelativeTo(null);
    }

    public void show() {
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        // Last resort for anything not caught elsewhere: tell the user rather than only printing a stack trace.
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null, "Something went wrong: " + describe(e),
                        "AI Study Agent", JOptionPane.ERROR_MESSAGE)));

        SwingUtilities.invokeLater(() -> {
            StudyController controller;
            try {
                controller = new StudyController(new StudyService());
            } catch (IllegalStateException e) {
                JOptionPane.showMessageDialog(null, e.getMessage(), "AI Study Agent", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
                return;
            }
            new MainGUI(controller).show();
        });
    }

    // ---------------------------------------------------------------- Ask

    private JPanel buildAskTab() {
        ActionListener ask = guarded(this::ask);
        askButton.addActionListener(ask);
        questionField.addActionListener(ask);
        return tab(row(new JLabel("Question:"), questionField, askButton), answerArea);
    }

    private void ask() {
        String question = questionField.getText().trim();
        if (question.isEmpty()) {
            statusLabel.setText("Type a question first.");
            return;
        }
        answerArea.setText("");
        runInBackground(askButton, "Thinking...", progress -> controller.askQuestion(question), response -> {
            showText(answerArea, response.message());
            statusLabel.setText(response.success() ? "Answered." : response.message());
        });
    }

    // ---------------------------------------------------------------- Quiz

    private JPanel buildQuizTab() {
        ActionListener generate = guarded(this::generateQuiz);
        generateQuizButton.addActionListener(generate);
        quizTopicField.addActionListener(generate);

        quizQuestionsPanel.setLayout(new BoxLayout(quizQuestionsPanel, BoxLayout.Y_AXIS));
        quizQuestionsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane questionsScroll = new JScrollPane(quizQuestionsPanel);
        questionsScroll.getVerticalScrollBar().setUnitIncrement(16);

        submitQuizButton.setEnabled(false);
        submitQuizButton.addActionListener(guarded(this::submitQuiz));
        quizResultArea.setRows(4);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(row(submitQuizButton), BorderLayout.NORTH);
        bottom.add(new JScrollPane(quizResultArea), BorderLayout.CENTER);

        JPanel tab = new JPanel(new BorderLayout());
        tab.add(row(new JLabel("Topic:"), quizTopicField, generateQuizButton), BorderLayout.NORTH);
        tab.add(questionsScroll, BorderLayout.CENTER);
        tab.add(bottom, BorderLayout.SOUTH);
        return tab;
    }

    private void generateQuiz() {
        String topic = quizTopicField.getText().trim();
        if (topic.isEmpty()) {
            statusLabel.setText("Enter a quiz topic first.");
            return;
        }
        submitQuizButton.setEnabled(false);
        quizResultArea.setText("");
        runInBackground(generateQuizButton, "Generating a quiz on " + topic + "...",
                progress -> controller.generateQuiz(topic), response -> {
                    statusLabel.setText(response.message());
                    if (response.success()) {
                        showQuiz(response.quiz());
                    } else {
                        showError("Quiz failed", response.message());
                    }
                });
    }

    private void showQuiz(Quiz quiz) {
        currentQuiz = quiz;
        quizAnswerGroups.clear();
        quizQuestionsPanel.removeAll();

        for (int i = 0; i < quiz.size(); i++) {
            JTextArea question = textArea();
            question.setText((i + 1) + ". " + quiz.questions().get(i));
            question.setOpaque(false);
            question.setBorder(null);
            question.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel choices = row();
            choices.setAlignmentX(Component.LEFT_ALIGNMENT);
            ButtonGroup group = new ButtonGroup();
            for (String letter : List.of("A", "B", "C", "D")) {
                JRadioButton choice = new JRadioButton(letter);
                choice.setActionCommand(letter);
                group.add(choice);
                choices.add(choice);
            }
            quizAnswerGroups.add(group);

            quizQuestionsPanel.add(question);
            quizQuestionsPanel.add(choices);
            quizQuestionsPanel.add(Box.createVerticalStrut(8));
        }

        quizQuestionsPanel.revalidate();
        quizQuestionsPanel.repaint();
        submitQuizButton.setEnabled(true);
    }

    private void submitQuiz() {
        if (currentQuiz == null) {
            statusLabel.setText("Generate a quiz first.");
            return;
        }
        List<String> answers = new ArrayList<>();
        for (ButtonGroup group : quizAnswerGroups) {
            ButtonModel selected = group.getSelection();
            answers.add(selected == null ? "" : selected.getActionCommand());
        }
        showText(quizResultArea, controller.submitQuiz(currentQuiz, answers).message());
        submitQuizButton.setEnabled(false);
        statusLabel.setText("Quiz submitted. Your score is saved in the Progress tab.");
    }

    // ---------------------------------------------------------------- Flashcards

    private JPanel buildFlashcardsTab() {
        ActionListener generate = guarded(this::generateFlashcards);
        generateFlashcardsButton.addActionListener(generate);
        flashcardTopicField.addActionListener(generate);
        flipButton.addActionListener(guarded(this::flipCard));
        gotItButton.addActionListener(guarded(() -> answerCard(true)));
        missedItButton.addActionListener(guarded(() -> answerCard(false)));
        endSessionButton.addActionListener(guarded(this::finishFlashcards));

        cardArea.setFont(cardArea.getFont().deriveFont(16f));
        setCardButtons(false, false);

        JPanel controls = new JPanel(new BorderLayout());
        controls.add(row(flipButton, gotItButton, missedItButton, endSessionButton), BorderLayout.WEST);
        controls.add(deckStatusLabel, BorderLayout.EAST);
        deckStatusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        JPanel tab = tab(row(new JLabel("Topic:"), flashcardTopicField, generateFlashcardsButton), cardArea);
        tab.add(controls, BorderLayout.SOUTH);
        return tab;
    }

    private void generateFlashcards() {
        String topic = flashcardTopicField.getText().trim();
        if (topic.isEmpty()) {
            statusLabel.setText("Enter a flashcard topic first.");
            return;
        }
        if (deck != null) {
            finishFlashcards();
        }
        runInBackground(generateFlashcardsButton, "Generating flashcards on " + topic + "...",
                progress -> controller.generateFlashcards(topic), response -> {
                    statusLabel.setText(response.message());
                    if (!response.success()) {
                        showError("Flashcards failed", response.message());
                        return;
                    }
                    deck = response.cards();
                    deckTopic = topic;
                    currentCard = null;
                    showNextCard();
                });
    }

    private void showNextCard() {
        currentCard = controller.nextFlashcard(deck, currentCard);
        if (currentCard == null) {
            finishFlashcards();
            return;
        }
        showText(cardArea, "Front:\n" + currentCard.front());
        setCardButtons(true, false);
        updateDeckStatus();
    }

    private void flipCard() {
        if (currentCard == null) {
            return;
        }
        showText(cardArea, "Front:\n" + currentCard.front() + "\n\nBack:\n" + currentCard.back());
        setCardButtons(false, true);
    }

    private void answerCard(boolean correct) {
        if (currentCard == null) {
            return;
        }
        controller.recordFlashcardAnswer(currentCard, correct);
        showNextCard();
    }

    /** Ends the session (also when every card is learned): saves the result and shows the summary. */
    private void finishFlashcards() {
        if (deck == null) {
            return;
        }
        showText(cardArea, "Session complete.\n\n" + controller.finishFlashcards(deckTopic, deck).message());
        deck = null;
        currentCard = null;
        setCardButtons(false, false);
        deckStatusLabel.setText(" ");
        statusLabel.setText("Flashcard session saved to the Progress tab.");
    }

    private void setCardButtons(boolean canFlip, boolean canAnswer) {
        flipButton.setEnabled(canFlip);
        gotItButton.setEnabled(canAnswer);
        missedItButton.setEnabled(canAnswer);
        endSessionButton.setEnabled(deck != null);
    }

    private void updateDeckStatus() {
        long learned = deck.stream().filter(Flashcard::isLearned).count();
        deckStatusLabel.setText("Learned " + learned + " of " + deck.size());
    }

    // ---------------------------------------------------------------- Study Plan

    private JPanel buildPlanTab() {
        ActionListener generate = guarded(this::generatePlan);
        generatePlanButton.addActionListener(generate);
        planTopicField.addActionListener(generate);
        return tab(row(new JLabel("Topic:"), planTopicField, new JLabel("Days:"), planDaysSpinner, generatePlanButton),
                planArea);
    }

    private void generatePlan() {
        String topic = planTopicField.getText().trim();
        if (topic.isEmpty()) {
            statusLabel.setText("Enter a topic for the study plan first.");
            return;
        }
        try {
            planDaysSpinner.commitEdit();
        } catch (ParseException e) {
            statusLabel.setText("Days must be a whole number from 1 to 365.");
            return;
        }
        int days = ((Number) planDaysSpinner.getValue()).intValue();

        planArea.setText("");
        runInBackground(generatePlanButton, "Generating a " + days + "-day study plan...",
                progress -> controller.generateStudyPlan(topic, days), response -> {
                    if (!response.success()) {
                        statusLabel.setText(response.message());
                        showError("Study plan failed", response.message());
                        return;
                    }
                    StringBuilder text = new StringBuilder(response.message()).append("\n\n");
                    for (String milestone : response.plan().milestones()) {
                        text.append(milestone).append('\n');
                    }
                    showText(planArea, text.toString());
                    statusLabel.setText("Study plan ready. Use Export to save it.");
                });
    }

    // ---------------------------------------------------------------- Summarize

    private JPanel buildSummarizeTab() {
        summarizeButton.addActionListener(guarded(this::chooseAndSummarize));
        return tab(row(summarizeButton, new JLabel("Summarizes a PDF or TXT file without adding it to your notes.")),
                summaryArea);
    }

    private void chooseAndSummarize() {
        File file = chooseCourseFile();
        if (file == null) {
            return;
        }
        summaryArea.setText("");
        runInBackground(summarizeButton, "Summarizing " + file.getName() + "...",
                progress -> controller.summarizeFile(file.getPath(), (done, total) ->
                        progress.accept("Summarizing " + file.getName() + ": section " + done + " of " + total + " done...")),
                response -> {
                    if (response.success()) {
                        showText(summaryArea, response.message());
                        statusLabel.setText("Summarized " + file.getName() + ".");
                    } else {
                        statusLabel.setText(response.message());
                        showError("Summary failed", response.message());
                    }
                });
    }

    // ---------------------------------------------------------------- Graph

    private JPanel buildGraphTab() {
        ActionListener build = guarded(this::buildGraph);
        buildGraphButton.addActionListener(build);
        graphTopicField.addActionListener(build);
        graphArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        return tab(row(new JLabel("Topic:"), graphTopicField, buildGraphButton), graphArea);
    }

    private void buildGraph() {
        String topic = graphTopicField.getText().trim();
        if (topic.isEmpty()) {
            statusLabel.setText("Enter a topic for the concept graph first.");
            return;
        }
        graphArea.setText("");
        runInBackground(buildGraphButton, "Building a concept graph for " + topic + "...",
                progress -> controller.buildGraph(topic), response -> {
                    if (!response.success()) {
                        statusLabel.setText(response.message());
                        showError("Graph failed", response.message());
                        return;
                    }
                    StringBuilder text = new StringBuilder(response.message()).append("\n\n");
                    response.graph().forEach((concept, related) -> text.append(concept).append(" -> ")
                            .append(related.isEmpty() ? "(none)" : String.join(", ", related)).append('\n'));
                    showText(graphArea, text.toString());
                    statusLabel.setText("Concept graph ready.");
                });
    }

    // ---------------------------------------------------------------- Progress

    private JPanel buildProgressTab() {
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(guarded(this::refreshProgress));
        progressArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        return tab(row(refreshButton), progressArea);
    }

    private void refreshProgress() {
        showText(progressArea, controller.progress().message());
    }

    // ---------------------------------------------------------------- Recommend

    private JPanel buildRecommendTab() {
        recommendButton.addActionListener(guarded(this::recommend));
        return tab(row(recommendButton, new JLabel("Based on your quiz scores and flashcard accuracy this session.")),
                recommendationArea);
    }

    private void recommend() {
        recommendationArea.setText("");
        runInBackground(recommendButton, "Finding your weakest topic...", progress -> controller.recommend(),
                response -> {
                    showText(recommendationArea, response.message());
                    statusLabel.setText(response.success() ? "Recommendation ready." : response.message());
                });
    }

    // ---------------------------------------------------------------- History

    private JPanel buildHistoryTab() {
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(guarded(this::refreshHistory));
        return tab(row(refreshButton), historyArea);
    }

    private void refreshHistory() {
        List<ConversationMemory.Exchange> history = controller.history();
        if (history.isEmpty()) {
            showText(historyArea, "No questions asked yet this session. Use the Ask tab to ask one.");
            return;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            ConversationMemory.Exchange exchange = history.get(i);
            text.append(i + 1).append(". Q: ").append(exchange.question()).append('\n')
                    .append("A: ").append(exchange.answer()).append("\n\n");
        }
        showText(historyArea, text.toString());
    }

    // ---------------------------------------------------------------- Upload and Export

    private void chooseAndUpload() {
        File file = chooseCourseFile();
        if (file == null) {
            return;
        }
        runInBackground(uploadButton, "Uploading " + file.getName() + "...",
                progress -> controller.uploadFile(file.getPath()), response -> {
                    statusLabel.setText(response.message());
                    if (!response.success()) {
                        showError("Upload failed", response.message());
                    }
                });
    }

    /** Saves the latest study plan or progress summary; the chosen file type decides Markdown or PDF. */
    private void chooseAndExport() {
        String description = controller.exportableDescription();
        if (description == null) {
            JOptionPane.showMessageDialog(frame,
                    "Nothing to export yet. Open the Progress tab or generate a study plan first.",
                    "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        FileNameExtensionFilter markdown = new FileNameExtensionFilter("Markdown (*.md)", "md");
        FileNameExtensionFilter pdf = new FileNameExtensionFilter("PDF (*.pdf)", "pdf");
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export " + description);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.addChoosableFileFilter(markdown);
        chooser.addChoosableFileFilter(pdf);
        chooser.setFileFilter(markdown);
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String path = chooser.getSelectedFile().getPath();
        boolean asPdf = path.toLowerCase(Locale.ROOT).endsWith(".pdf")
                || (chooser.getFileFilter() == pdf && !path.toLowerCase(Locale.ROOT).endsWith(".md"));
        runInBackground(exportButton, "Exporting " + description + "...",
                progress -> controller.export(asPdf ? "pdf" : "md", path), response -> {
                    statusLabel.setText(response.message());
                    if (!response.success()) {
                        showError("Export failed", response.message());
                    }
                });
    }

    /** Asks the user to pick a PDF or TXT file; returns null if they cancel. */
    private File chooseCourseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PDF or text files", "pdf", "txt"));
        return chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    // ---------------------------------------------------------------- Helpers

    /** A controller request run in the background; it may report progress messages for the status bar. */
    @FunctionalInterface
    private interface BackgroundCall<T> {
        T run(Consumer<String> progress);
    }

    /**
     * Runs a slow controller call off the UI thread with the button disabled, then hands
     * the result to {@code onDone} on the UI thread. Unexpected errors become a dialog.
     */
    private <T> void runInBackground(JButton button, String status, BackgroundCall<T> call, Consumer<T> onDone) {
        button.setEnabled(false);
        statusLabel.setText(status);

        new SwingWorker<T, String>() {
            @Override
            protected T doInBackground() {
                return call.run(this::publish);
            }

            @Override
            protected void process(List<String> updates) {
                statusLabel.setText(updates.get(updates.size() - 1));
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                try {
                    onDone.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("The request was interrupted.");
                } catch (ExecutionException e) {
                    reportUnexpected(e.getCause() == null ? e : e.getCause());
                } catch (RuntimeException e) {
                    reportUnexpected(e);
                }
            }
        }.execute();
    }

    /** Wraps a button action so an unexpected exception is shown to the user instead of escaping. */
    private ActionListener guarded(Runnable action) {
        return event -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                reportUnexpected(e);
            }
        };
    }

    private void reportUnexpected(Throwable e) {
        String message = "Something went wrong: " + describe(e) + ". Please try again.";
        statusLabel.setText(message);
        showError("AI Study Agent", message);
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(frame, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static void showText(JTextArea area, String text) {
        area.setText(text);
        area.setCaretPosition(0);
    }

    private static JTextArea textArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return area;
    }

    private static JPanel row(JComponent... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (JComponent component : components) {
            row.add(component);
        }
        return row;
    }

    /** A tab with a row of controls on top and a scrolling output area filling the rest. */
    private static JPanel tab(JPanel controls, JTextArea output) {
        JPanel tab = new JPanel(new BorderLayout());
        tab.add(controls, BorderLayout.NORTH);
        tab.add(new JScrollPane(output), BorderLayout.CENTER);
        return tab;
    }
}
