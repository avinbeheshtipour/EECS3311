package com.studyagent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.util.concurrent.ExecutionException;

/**
 * Swing view - a stub to be filled in with the rest of the GUI. Like
 * {@link CLIView}, it only talks to {@link StudyController}.
 */
public class MainGUI {

    private final JFrame frame = new JFrame("AI Study Agent");
    private final StudyController controller;
    private final JButton uploadButton = new JButton("Upload");
    private final JLabel statusLabel = new JLabel("Upload a PDF or TXT file of course material.");

    public MainGUI(StudyController controller) {
        this.controller = controller;

        uploadButton.addActionListener(e -> chooseAndUpload());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(uploadButton);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        frame.setLayout(new BorderLayout());
        frame.add(toolbar, BorderLayout.NORTH);
        frame.add(statusLabel, BorderLayout.SOUTH);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(640, 420);
        frame.setLocationRelativeTo(null);
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

    private void chooseAndUpload() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PDF or text files", "pdf", "txt"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();

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
