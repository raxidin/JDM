import ui.DownloadFrame;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            DownloadFrame frame = new DownloadFrame();
            frame.setVisible(true);
        });
    }
}