package com.studyagent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * Swing view - a stub to be filled in with the rest of the GUI. Like
 * {@link CLIView}, it only talks to {@link StudyController}.
 */
public class MainGUI {

    private final JFrame frame = new JFrame("AI Study Agent");
    private final StudyController controller;
    private final JButton uploadButton = new JButton("Upload");
    private final JButton exportButton = new JButton("Export");
    private final JLabel statusLabel = new JLabel("Upload a PDF or TXT file of course material.");

    // Quiz tab
    private final JTextField quizTopicField = new JTextField(24);
    private final JButton generateQuizButton = new JButton("Generate quiz");
    private final JPanel quizQuestionsPanel = new JPanel();
    private final JButton submitQuizButton = new JButton("Submit answers");
    private final JTextArea quizResultArea = new JTextArea(4, 40);
    private final List<ButtonGroup> quizAnswerGroups = new ArrayList<>();
    private Quiz currentQuiz;

    // Summarize tab
    private final JButton summarizeButton = new JButton("Summarize a file...");
    private final JTextArea summaryArea = new JTextArea();

    // Progress tab
    private final JTextArea progressArea = new JTextArea();

    public MainGUI(StudyController controller) {
        this.controller = controller;

        uploadButton.addActionListener(e -> chooseAndUpload());
        exportButton.addActionListener(e -> chooseAndExport());
        exportButton.setToolTipText("Save your latest study plan or progress summary as Markdown or PDF");

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(uploadButton);
        toolbar.add(exportButton);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Quiz", buildQuizTab());
        tabs.addTab("Summarize", buildSummarizeTab());
        tabs.addTab("Progress", buildProgressTab());
        // Refresh whenever the Progress tab is opened, so it includes quizzes taken since.
        tabs.addChangeListener(e -> {
            if ("Progress".equals(tabs.getTitleAt(tabs.getSelectedIndex()))) {
                refreshProgress();
            }
        });

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        frame.setLayout(new BorderLayout());
        frame.add(toolbar, BorderLayout.NORTH);
        frame.add(tabs, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(760, 600);
        frame.setLocationRelativeTo(null);
    }

    private JPanel buildQuizTab() {
        JPanel topicRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topicRow.add(new JLabel("Topic:"));
        topicRow.add(quizTopicField);
        topicRow.add(generateQuizButton);
        generateQuizButton.addActionListener(e -> generateQuiz());
        quizTopicField.addActionListener(e -> generateQuiz());

        quizQuestionsPanel.setLayout(new BoxLayout(quizQuestionsPanel, BoxLayout.Y_AXIS));
        quizQuestionsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane questionsScroll = new JScrollPane(quizQuestionsPanel);
        questionsScroll.getVerticalScrollBar().setUnitIncrement(16);

        submitQuizButton.setEnabled(false);
        submitQuizButton.addActionListener(e -> submitQuiz());
        quizResultArea.setEditable(false);
        quizResultArea.setLineWrap(true);
        quizResultArea.setWrapStyleWord(true);

        JPanel bottom = new JPanel(new BorderLayout());
        JPanel submitRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        submitRow.add(submitQuizButton);
        bottom.add(submitRow, BorderLayout.NORTH);
        bottom.add(new JScrollPane(quizResultArea), BorderLayout.CENTER);

        JPanel tab = new JPanel(new BorderLayout());
        tab.add(topicRow, BorderLayout.NORTH);
        tab.add(questionsScroll, BorderLayout.CENTER);
        tab.add(bottom, BorderLayout.SOUTH);
        return tab;
    }

    public void show() {
        frame.setVisible(true);
    }

    public static void main(String[] args) {
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

    private JPanel buildSummarizeTab() {
        summarizeButton.addActionListener(e -> chooseAndSummarize());
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonRow.add(summarizeButton);

        summaryArea.setEditable(false);
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        summaryArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel tab = new JPanel(new BorderLayout());
        tab.add(buttonRow, BorderLayout.NORTH);
        tab.add(new JScrollPane(summaryArea), BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildProgressTab() {
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshProgress());
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonRow.add(refreshButton);

        progressArea.setEditable(false);
        progressArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        progressArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel tab = new JPanel(new BorderLayout());
        tab.add(buttonRow, BorderLayout.NORTH);
        tab.add(new JScrollPane(progressArea), BorderLayout.CENTER);
        return tab;
    }

    private void refreshProgress() {
        progressArea.setText(controller.progress().message());
        progressArea.setCaretPosition(0);
    }

    private void chooseAndSummarize() {
        File file = chooseCourseFile();
        if (file == null) {
            return;
        }

        summarizeButton.setEnabled(false);
        summaryArea.setText("");
        statusLabel.setText("Summarizing " + file.getName() + "...");

        new SwingWorker<StudyController.Response, String>() {
            @Override
            protected StudyController.Response doInBackground() {
                return controller.summarizeFile(file.getPath(), (done, total) ->
                        publish("Summarizing " + file.getName() + ": section " + done + " of " + total + " done..."));
            }

            @Override
            protected void process(List<String> updates) {
                statusLabel.setText(updates.get(updates.size() - 1));
            }

            @Override
            protected void done() {
                summarizeButton.setEnabled(true);
                try {
                    StudyController.Response response = get();
                    if (response.success()) {
                        summaryArea.setText(response.message());
                        summaryArea.setCaretPosition(0);
                        statusLabel.setText("Summarized " + file.getName() + ".");
                    } else {
                        statusLabel.setText(response.message());
                        JOptionPane.showMessageDialog(frame, response.message(),
                                "Summary failed", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    statusLabel.setText("Summary failed: " + e.getMessage());
                }
            }
        }.execute();
    }

    /** Asks the user to pick a PDF or TXT file; returns null if they cancel. */
    private File chooseCourseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PDF or text files", "pdf", "txt"));
        return chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    private void generateQuiz() {
        String topic = quizTopicField.getText().trim();
        if (topic.isEmpty()) {
            statusLabel.setText("Enter a quiz topic first.");
            return;
        }

        generateQuizButton.setEnabled(false);
        submitQuizButton.setEnabled(false);
        quizResultArea.setText("");
        statusLabel.setText("Generating a quiz on " + topic + "...");

        new SwingWorker<StudyController.QuizResponse, Void>() {
            @Override
            protected StudyController.QuizResponse doInBackground() {
                return controller.generateQuiz(topic);
            }

            @Override
            protected void done() {
                generateQuizButton.setEnabled(true);
                try {
                    StudyController.QuizResponse response = get();
                    statusLabel.setText(response.message());
                    if (response.success()) {
                        showQuiz(response.quiz());
                    } else {
                        JOptionPane.showMessageDialog(frame, response.message(),
                                "Quiz failed", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    statusLabel.setText("Quiz failed: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void showQuiz(Quiz quiz) {
        currentQuiz = quiz;
        quizAnswerGroups.clear();
        quizQuestionsPanel.removeAll();

        for (int i = 0; i < quiz.size(); i++) {
            JTextArea question = new JTextArea((i + 1) + ". " + quiz.questions().get(i));
            question.setEditable(false);
            question.setLineWrap(true);
            question.setWrapStyleWord(true);
            question.setOpaque(false);
            question.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEFT));
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
            return;
        }
        List<String> answers = new ArrayList<>();
        for (ButtonGroup group : quizAnswerGroups) {
            ButtonModel selected = group.getSelection();
            answers.add(selected == null ? "" : selected.getActionCommand());
        }
        quizResultArea.setText(controller.submitQuiz(currentQuiz, answers).message());
        submitQuizButton.setEnabled(false);
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
        StudyController.Response response = controller.export(asPdf ? "pdf" : "md", path);
        statusLabel.setText(response.message());
        if (!response.success()) {
            JOptionPane.showMessageDialog(frame, response.message(), "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void chooseAndUpload() {
        File file = chooseCourseFile();
        if (file == null) {
            return;
        }

        uploadButton.setEnabled(false);
        statusLabel.setText("Uploading " + file.getName() + "...");

        // Parsing a large PDF can take a while, so keep it off the UI thread.
        new SwingWorker<StudyController.Response, Void>() {
            @Override
            protected StudyController.Response doInBackground() {
                return controller.uploadFile(file.getPath());
            }

            @Override
            protected void done() {
                uploadButton.setEnabled(true);
                try {
                    StudyController.Response response = get();
                    statusLabel.setText(response.message());
                    if (!response.success()) {
                        JOptionPane.showMessageDialog(frame, response.message(),
                                "Upload failed", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    statusLabel.setText("Upload failed: " + e.getMessage());
                }
            }
        }.execute();
    }
}
