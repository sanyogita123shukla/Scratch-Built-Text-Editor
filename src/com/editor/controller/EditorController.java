package com.editor.controller;

import com.editor.model.*;
import com.editor.view.EditorPanel;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * Controller that listens to keyboard events and routes them to the Document model,
 * updates history stacks, queries the autocomplete Trie, and refreshes the view.
 */
public class EditorController implements KeyListener {
    private final Document doc;
    private final HistoryManager history;
    private final EditorPanel panel;

    /**
     * Tracks how many user-typed words have been learned into the Trie.
     * Bounded by {@link #MAX_LEARNED_WORDS} to prevent unbounded heap growth
     * from accumulating every word typed during a session.
     */
    private int learnedWordCount = 0;
    private static final int MAX_LEARNED_WORDS = 2_000;

    public EditorController(Document doc, HistoryManager history, EditorPanel panel) {
        this.doc = doc;
        this.history = history;
        this.panel = panel;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        boolean isControl = e.isControlDown();

        panel.resetCursorBlink();

        // 1. Handle Command Shortcuts (Ctrl+Z, Ctrl+Y)
        if (isControl && keyCode == KeyEvent.VK_Z) {
            history.undo(doc);
            panel.dismissAutocomplete();
            e.consume();
            return;
        }
        if (isControl && keyCode == KeyEvent.VK_Y) {
            history.redo(doc);
            panel.dismissAutocomplete();
            e.consume();
            return;
        }

        // 2. Handle Autocomplete Interception (Tab, Enter, Up, Down, Escape)
        if (panel.isShowSuggestions()) {
            if (keyCode == KeyEvent.VK_ESCAPE) {
                panel.dismissAutocomplete();
                e.consume();
                return;
            }
            if (keyCode == KeyEvent.VK_UP) {
                panel.moveSuggestionSelection(-1);
                e.consume();
                return;
            }
            if (keyCode == KeyEvent.VK_DOWN) {
                panel.moveSuggestionSelection(1);
                e.consume();
                return;
            }
            if (keyCode == KeyEvent.VK_TAB || keyCode == KeyEvent.VK_ENTER) {
                acceptAutocomplete();
                e.consume();
                return;
            }
        }

        // 3. Handle Navigation keys when autocomplete is not active or bypassed
        if (keyCode == KeyEvent.VK_UP) {
            doc.moveUp();
            panel.dismissAutocomplete();
            e.consume();
            return;
        }
        if (keyCode == KeyEvent.VK_DOWN) {
            doc.moveDown();
            panel.dismissAutocomplete();
            e.consume();
            return;
        }
        if (keyCode == KeyEvent.VK_LEFT) {
            doc.moveLeft();
            panel.dismissAutocomplete();
            e.consume();
            return;
        }
        if (keyCode == KeyEvent.VK_RIGHT) {
            doc.moveRight();
            panel.dismissAutocomplete();
            e.consume();
            return;
        }
        if (keyCode == KeyEvent.VK_HOME) {
            doc.moveLineStart();
            panel.dismissAutocomplete();
            e.consume();
            return;
        }
        if (keyCode == KeyEvent.VK_END) {
            doc.moveLineEnd();
            panel.dismissAutocomplete();
            e.consume();
            return;
        }

        // 4. Handle Edit Operations (Backspace, Delete, Enter, Tab)
        int row = doc.getCurRow();
        int col = doc.getCurCol();
        LineNode line = doc.getCurLine();

        if (keyCode == KeyEvent.VK_BACK_SPACE) {
            if (col > 0) {
                // Delete character before cursor
                char deletedChar = line.getText().charAt(col - 1);
                Command deleteCmd = new DeleteTextCommand(row, col - 1, String.valueOf(deletedChar));
                history.executeCommand(doc, deleteCmd);
            } else if (row > 0) {
                // Merge current line with line above
                LineNode prevLine = doc.getNodeAtRow(row - 1);
                int prevLength = prevLine.length();
                Command mergeCmd = new MergeLineCommand(row - 1, prevLength);
                history.executeCommand(doc, mergeCmd);
            }
            panel.updateAutocomplete();
            e.consume();
            return;
        }

        if (keyCode == KeyEvent.VK_DELETE) {
            if (col < line.length()) {
                // Delete character at cursor
                char deletedChar = line.getText().charAt(col);
                Command deleteCmd = new DeleteTextCommand(row, col, String.valueOf(deletedChar));
                history.executeCommand(doc, deleteCmd);
            } else if (row < doc.getLineCount() - 1) {
                // Merge next line into current line
                Command mergeCmd = new MergeLineCommand(row, col);
                history.executeCommand(doc, mergeCmd);
            }
            panel.updateAutocomplete();
            e.consume();
            return;
        }

        if (keyCode == KeyEvent.VK_ENTER) {
            // Split line at cursor
            learnWordAtCursor();
            Command splitCmd = new SplitLineCommand(row, col);
            history.executeCommand(doc, splitCmd);
            panel.dismissAutocomplete();
            e.consume();
            return;
        }

        if (keyCode == KeyEvent.VK_TAB) {
            // Insert 4 spaces as a tab
            learnWordAtCursor();
            Command insertTabCmd = new InsertTextCommand(row, col, "    ");
            history.executeCommand(doc, insertTabCmd);
            panel.updateAutocomplete();
            e.consume();
            return;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        char c = e.getKeyChar();

        // Check if character is printable and not a control key
        if (c != KeyEvent.CHAR_UNDEFINED && c >= 32 && c != 127) {
            panel.resetCursorBlink();

            int row = doc.getCurRow();
            int col = doc.getCurCol();

            // Register preceding word when hitting a boundary separator character (like space/punctuation)
            if (!Character.isJavaIdentifierPart(c)) {
                learnWordAtCursor();
            }

            // Execute insert command
            Command insertCmd = new InsertTextCommand(row, col, String.valueOf(c));
            history.executeCommand(doc, insertCmd);

            // Update autocomplete suggestions based on the character entered
            panel.updateAutocomplete();
            panel.repaint();
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // Unused
    }

    /**
     * Scans the word preceding the cursor on the current line and registers it in the Trie.
     * Stops learning new words once {@link #MAX_LEARNED_WORDS} have been accumulated,
     * preventing unbounded Trie growth during long editing sessions.
     */
    private void learnWordAtCursor() {
        if (learnedWordCount >= MAX_LEARNED_WORDS) return;

        String lineStr = doc.getCurLine().getString();
        int col = doc.getCurCol();
        if (col <= 0) return;

        // Trace back to find start of the word
        int start = col;
        while (start > 0 && Character.isJavaIdentifierPart(lineStr.charAt(start - 1))) {
            start--;
        }

        if (start < col) {
            String word = lineStr.substring(start, col);
            // Insert words of length 3 or more, ignoring purely digit strings
            if (word.length() >= 3 && !word.matches("\\d+")) {
                panel.getTrie().insert(word);
                learnedWordCount++;
            }
        }
    }

    /**
     * Completes the current prefix with the selected suggestion.
     */
    private void acceptAutocomplete() {
        if (panel.isShowSuggestions() && !panel.getSuggestions().isEmpty()) {
            String prefix = panel.getCurrentPrefix();
            String suggestion = panel.getSuggestions().get(panel.getSelectedSuggestionIndex());
            
            // Extract the suffix that needs to be appended
            String suffix = suggestion.substring(prefix.length());
            if (!suffix.isEmpty()) {
                int row = doc.getCurRow();
                int col = doc.getCurCol();
                
                Command insertSuffixCmd = new InsertTextCommand(row, col, suffix);
                history.executeCommand(doc, insertSuffixCmd);
            }
            
            panel.dismissAutocomplete();
        }
    }
}
