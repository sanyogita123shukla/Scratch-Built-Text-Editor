package com.editor;

import com.editor.controller.EditorController;
import com.editor.model.Category;
import com.editor.model.Document;
import com.editor.model.HistoryManager;
import com.editor.model.Trie;
import com.editor.view.EditorFrame;
import com.editor.view.EditorPanel;
import com.editor.view.ModeSelectionDialog;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Entry point and bootstrapper for the Scratch-Built Text Editor.
 * Loads the autocomplete dictionary, instantiates all components,
 * sets up listeners, and displays the UI window.
 */
public class Main {
    public static void main(String[] args) {
        // Set beautiful cross-platform Look and Feel if available
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fallback to default Swing Look and Feel
        }

        // Initialize core models
        Document doc = new Document();
        HistoryManager history = new HistoryManager();
        Trie trie = new Trie();

        // Load Autocomplete dictionary from local file
        int loadedWords = loadDictionary(trie, "dictionary.txt");
        if (loadedWords == 0) {
            // Fallback: Populate some default programming keywords if dictionary.txt is missing
            String[] fallbackKeywords = {
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", 
                "continue", "default", "double", "else", "enum", "extends", "final", "finally", 
                "float", "for", "if", "implements", "import", "instanceof", "int", "interface", 
                "long", "new", "package", "private", "protected", "public", "return", "short", 
                "static", "super", "switch", "synchronized", "this", "throw", "throws", "try", 
                "void", "volatile", "while", "String", "System", "out", "println", "ArrayList", 
                "LinkedList", "HashMap", "HashSet", "dijkstra", "quicksort", "mergesort", "binarytree"
            };
            for (String kw : fallbackKeywords) {
                trie.insert(kw);
            }
            loadedWords = fallbackKeywords.length;
            System.out.println("Loaded " + loadedWords + " fallback autocomplete keywords.");
        } else {
            System.out.println("Loaded " + loadedWords + " words into autocomplete Trie from dictionary.txt.");
        }

        // Initialize view and controller on the Swing Event Dispatch Thread (EDT)
        int finalLoadedWords = loadedWords;
        SwingUtilities.invokeLater(() -> {
            // Show writing mode selection dialog BEFORE opening the editor
            ModeSelectionDialog modeDialog = new ModeSelectionDialog(null);
            modeDialog.setVisible(true);
            Category selectedCategory = modeDialog.getSelectedCategory();

            // Apply the chosen category to the Trie for prioritized suggestions
            trie.setActiveCategory(selectedCategory);
            System.out.println("Writing mode selected: " + selectedCategory);

            EditorPanel panel = new EditorPanel(doc, history, trie);
            EditorController controller = new EditorController(doc, history, panel);
            
            // Wire controller to panel
            panel.addKeyListener(controller);
            
            // Frame container setup
            EditorFrame frame = new EditorFrame(panel);
            frame.setVisible(true);

            System.out.println("Scratch-Built Text Editor initialized with " + finalLoadedWords + " dictionary words.");
        });
    }

    /**
     * Reads a list of words from a dictionary file and inserts them into the Trie.
     */
    private static int loadDictionary(Trie trie, String filePath) {
        int count = 0;
        File file = new File(filePath);
        if (!file.exists()) {
            // Check in parent directory or current classpath path
            file = new File("scratch-editor-java/" + filePath);
        }
        
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String word = line.trim();
                    if (!word.isEmpty()) {
                        trie.insert(word);
                        count++;
                    }
                }
            } catch (IOException e) {
                System.err.println("Error reading dictionary file: " + e.getMessage());
            }
        }
        return count;
    }
}
