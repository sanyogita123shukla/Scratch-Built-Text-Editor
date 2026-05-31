package com.editor.view;

import javax.swing.*;
import java.awt.*;

/**
 * Standard JFrame hosting the main editor component.
 */
public class EditorFrame extends JFrame {

    public EditorFrame(EditorPanel panel) {
        setTitle("Scratch-Built Text Editor with Real-Time Autocomplete (Java)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Add the custom render panel
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);

        // Standard default size
        setPreferredSize(new Dimension(900, 650));
        pack();
        
        // Center window on screen
        setLocationRelativeTo(null);
    }
}
