package ui;

import downloader.DownloadInfo;
import downloader.DownloadManager;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class DownloadFrame extends JFrame implements DownloadManager.ProgressCallback {

    private final DownloadManager downloadManager;
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JTextField urlField;
    private final JSpinner threadSpinner;
    private final JTextField saveDirField;
    private final JButton addButton;
    private final JButton pauseButton;
    private final JButton resumeButton;
    private final JButton cancelButton;
    private final JButton removeButton;
    private final JButton pasteButton;
    private final JLabel statusLabel;
    private final JLabel activeLabel;

    private final List<DownloadInfo> downloadList;

    private static final String[] COLUMNS = {
        "File Name", "Progress", "Size", "Speed", "Status", "Threads"
    };

    public DownloadFrame() {
        this.downloadManager = new DownloadManager();
        this.downloadList = new ArrayList<>();

        setTitle("Java Download Manager");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0;
        topPanel.add(new JLabel("URL:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        gbc.weightx = 1.0;
        urlField = new JTextField();
        urlField.setToolTipText("Enter download URL or Ctrl+V to paste");
        topPanel.add(urlField, gbc);

        pasteButton = new JButton("Paste");
        pasteButton.addActionListener(e -> pasteFromClipboard());
        gbc.gridx = 2; gbc.gridy = 0;
        gbc.weightx = 0;
        topPanel.add(pasteButton, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0;
        topPanel.add(new JLabel("Threads:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1;
        gbc.weightx = 1.0;
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(4, 1, 16, 1);
        threadSpinner = new JSpinner(spinnerModel);
        topPanel.add(threadSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        gbc.weightx = 0;
        topPanel.add(new JLabel("Save to:"), gbc);

        gbc.gridx = 1; gbc.gridy = 2;
        gbc.weightx = 1.0;
        saveDirField = new JTextField(System.getProperty("user.home"));
        topPanel.add(saveDirField, gbc);

        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> chooseSaveDir());
        gbc.gridx = 2; gbc.gridy = 2;
        gbc.weightx = 0;
        topPanel.add(browseButton, gbc);

        addButton = new JButton("+ Add Download");
        addButton.setFont(addButton.getFont().deriveFont(Font.BOLD));
        addButton.addActionListener(e -> addDownload());
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 3;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        topPanel.add(addButton, gbc);

        add(topPanel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(32);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);

        TableColumnModel colModel = table.getColumnModel();
        colModel.getColumn(0).setPreferredWidth(200);
        colModel.getColumn(1).setPreferredWidth(250);
        colModel.getColumn(2).setPreferredWidth(90);
        colModel.getColumn(3).setPreferredWidth(80);
        colModel.getColumn(4).setPreferredWidth(100);
        colModel.getColumn(5).setPreferredWidth(60);

        colModel.getColumn(1).setCellRenderer(new ProgressCellRenderer());

        table.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLoweredSoftBevelBorder());
        add(scrollPane, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new GridLayout(5, 1, 4, 4));
        actionPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        pauseButton = new JButton("Pause");
        pauseButton.setEnabled(false);
        pauseButton.addActionListener(e -> pauseDownload());
        actionPanel.add(pauseButton);

        resumeButton = new JButton("Resume");
        resumeButton.setEnabled(false);
        resumeButton.addActionListener(e -> resumeDownload());
        actionPanel.add(resumeButton);

        cancelButton = new JButton("Cancel");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancelDownload());
        actionPanel.add(cancelButton);

        removeButton = new JButton("Remove");
        removeButton.setEnabled(false);
        removeButton.addActionListener(e -> removeDownload());
        actionPanel.add(removeButton);

        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem pauseItem = new JMenuItem("Pause");
        pauseItem.addActionListener(e -> pauseDownload());
        popupMenu.add(pauseItem);
        JMenuItem resumeItem = new JMenuItem("Resume");
        resumeItem.addActionListener(e -> resumeDownload());
        popupMenu.add(resumeItem);
        JMenuItem cancelItem = new JMenuItem("Cancel");
        cancelItem.addActionListener(e -> cancelDownload());
        popupMenu.add(cancelItem);
        JMenuItem removeItem = new JMenuItem("Remove");
        removeItem.addActionListener(e -> removeDownload());
        popupMenu.add(removeItem);
        table.setComponentPopupMenu(popupMenu);

        add(actionPanel, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        statusLabel = new JLabel("Ready");
        activeLabel = new JLabel("Active: 0");

        bottomPanel.add(statusLabel, BorderLayout.WEST);
        bottomPanel.add(activeLabel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        KeyStroke pasteKey = KeyStroke.getKeyStroke(KeyEvent.VK_V,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
        getRootPane().registerKeyboardAction(
            e -> pasteFromClipboard(), pasteKey, JComponent.WHEN_IN_FOCUSED_WINDOW);

        urlField.addActionListener(e -> addDownload());
    }

    private void pasteFromClipboard() {
        try {
            String text = (String) Toolkit.getDefaultToolkit()
                .getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (text != null && (text.startsWith("http://") || text.startsWith("https://"))) {
                urlField.setText(text.trim());
            }
        } catch (Exception ignored) {}
    }

    private void chooseSaveDir() {
        JFileChooser chooser = new JFileChooser(saveDirField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose Save Directory");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            saveDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void addDownload() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a URL.",
                "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            JOptionPane.showMessageDialog(this, "URL must start with http:// or https://",
                "Invalid URL", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DownloadInfo info = new DownloadInfo();
        info.setUrl(url);
        info.setThreadCount((Integer) threadSpinner.getValue());

        String saveDir = saveDirField.getText().trim();
        if (saveDir.isEmpty()) saveDir = System.getProperty("user.home");
        info.setSaveDir(saveDir);

        downloadList.add(info);
        tableModel.addRow(new Object[]{
            "Connecting...", "0%", "Unknown", "0 KB/s", "PENDING", info.getThreadCount()
        });

        updateActiveCount();
        urlField.setText("");
        statusLabel.setText("Probing: " + url);
        downloadManager.startDownload(info, this);
    }

    private void pauseDownload() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= downloadList.size()) return;
        DownloadInfo info = downloadList.get(row);
        if (info.getStatus() == DownloadInfo.Status.DOWNLOADING) {
            downloadManager.pauseDownload(info);
            updateRow(row, info);
            updateButtonStates();
        }
    }

    private void resumeDownload() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= downloadList.size()) return;
        DownloadInfo info = downloadList.get(row);
        if (info.getStatus() == DownloadInfo.Status.PAUSED) {
            info.setStatus(DownloadInfo.Status.PENDING);
            downloadManager.resumeDownload(info, this);
            updateButtonStates();
        }
    }

    private void cancelDownload() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= downloadList.size()) return;
        DownloadInfo info = downloadList.get(row);
        if (info.getStatus() == DownloadInfo.Status.DOWNLOADING
            || info.getStatus() == DownloadInfo.Status.PAUSED) {
            downloadManager.cancelDownload(info);
            updateRow(row, info);
            updateButtonStates();
            updateActiveCount();
        }
    }

    private void removeDownload() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= downloadList.size()) return;
        DownloadInfo info = downloadList.get(row);
        if (info.getStatus() == DownloadInfo.Status.DOWNLOADING) {
            downloadManager.cancelDownload(info);
        }
        downloadList.remove(row);
        tableModel.removeRow(row);
        updateActiveCount();
    }

    private void updateRow(final int row, final DownloadInfo info) {
        SwingUtilities.invokeLater(() -> {
            if (row < 0 || row >= downloadList.size()) return;
            tableModel.setValueAt(
                info.getFileName() != null ? info.getFileName() : "...", row, 0);
            tableModel.setValueAt(info.getProgress() + "%", row, 1);
            tableModel.setValueAt(formatSize(info.getFileSize()), row, 2);
            tableModel.setValueAt(formatSpeed(info.getSpeed()), row, 3);
            tableModel.setValueAt(info.getStatus().name(), row, 4);
            tableModel.setValueAt(info.getThreadCount(), row, 5);
        });
    }

    private void updateButtonStates() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= downloadList.size()) {
            pauseButton.setEnabled(false);
            resumeButton.setEnabled(false);
            cancelButton.setEnabled(false);
            removeButton.setEnabled(false);
            return;
        }

        DownloadInfo.Status status = downloadList.get(row).getStatus();
        pauseButton.setEnabled(status == DownloadInfo.Status.DOWNLOADING);
        resumeButton.setEnabled(status == DownloadInfo.Status.PAUSED);
        cancelButton.setEnabled(
            status == DownloadInfo.Status.DOWNLOADING || status == DownloadInfo.Status.PAUSED);
        removeButton.setEnabled(status != DownloadInfo.Status.DOWNLOADING);
    }

    private void updateActiveCount() {
        long active = downloadList.stream()
            .filter(d -> d.getStatus() == DownloadInfo.Status.DOWNLOADING)
            .count();
        activeLabel.setText("Active: " + active);
    }

    @Override
    public void onStart(DownloadInfo info) {
        int row = downloadList.indexOf(info);
        if (row < 0) return;
        updateRow(row, info);
        updateActiveCount();
        statusLabel.setText("Downloading: " + info.getFileName()
            + "  [" + formatSize(info.getFileSize()) + "]");
        SwingUtilities.invokeLater(this::updateButtonStates);
    }

    @Override
    public void onProgress(DownloadInfo info) {
        int row = downloadList.indexOf(info);
        if (row < 0) return;
        updateRow(row, info);
    }

    @Override
    public void onComplete(DownloadInfo info) {
        int row = downloadList.indexOf(info);
        if (row < 0) return;
        updateRow(row, info);
        updateActiveCount();
        statusLabel.setText("Completed: " + info.getFullPath());
        SwingUtilities.invokeLater(this::updateButtonStates);
    }

    @Override
    public void onError(DownloadInfo info) {
        int row = downloadList.indexOf(info);
        if (row < 0) return;
        updateRow(row, info);
        updateActiveCount();
        statusLabel.setText("Error: " + info.getErrorMessage());
        SwingUtilities.invokeLater(this::updateButtonStates);
        JOptionPane.showMessageDialog(this,
            "Download failed:\n" + info.getUrl()
                + "\n\nError: " + info.getErrorMessage(),
            "Download Error", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void dispose() {
        downloadManager.shutdown();
        super.dispose();
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "Unknown";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return new DecimalFormat("#,##0.##").format(size) + " " + units[unitIndex];
    }

    private static String formatSpeed(long bytesPerSec) {
        return formatSize(bytesPerSec) + "/s";
    }

    private static class ProgressCellRenderer extends DefaultTableCellRenderer {
        private final JProgressBar progressBar;

        ProgressCellRenderer() {
            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            progressBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            if (value instanceof String) {
                String text = (String) value;
                int percent = 0;
                try {
                    percent = Integer.parseInt(text.replace("%", ""));
                } catch (NumberFormatException ignored) {}
                progressBar.setValue(percent);
                progressBar.setString(text);
            }

            if (isSelected) {
                progressBar.setBackground(table.getSelectionBackground());
            } else {
                progressBar.setBackground(table.getBackground());
            }
            return progressBar;
        }
    }
}