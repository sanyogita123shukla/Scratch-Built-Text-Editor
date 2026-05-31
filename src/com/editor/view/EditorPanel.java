package com.editor.view;

import com.editor.model.Document;
import com.editor.model.HistoryManager;
import com.editor.model.LineNode;
import com.editor.model.Trie;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom JPanel that renders the text editor from scratch.
 * Handles the display of the DLL text buffer, the custom cursor,
 * scroll alignment, autocomplete dropdown, and bottom status bar.
 *
 * <p><b>Optimizations applied:</b></p>
 * <ul>
 *   <li><b>#4 — Targeted cursor repaint:</b> The blink timer calls
 *       {@code repaint(x, y, w, h)} scoped to just the cursor cell instead
 *       of repainting the entire panel on every 500 ms tick.</li>
 *   <li><b>#5 — Cached FontMetrics:</b> {@code charWidth}, {@code lineHeight},
 *       and {@code fontAscent} are computed once in {@link #addNotify()} (when
 *       the component is attached to a real graphics context) and reused across
 *       all paint calls, eliminating repeated {@code getFontMetrics()} calls
 *       inside the hot render path.</li>
 *   <li><b>#6 — Static Color/Stroke constants:</b> {@code new Color(0,0,0,80)}
 *       and {@code new BasicStroke(1.5f)} are now {@code static final} fields,
 *       so they are allocated exactly once at class-load time rather than on
 *       every paint frame.</li>
 * </ul>
 */
public class EditorPanel extends JPanel {
    private final Document doc;
    private final HistoryManager history;
    private final Trie trie;

    // Font Configuration
    private final Font font;
    // Cached font metrics — computed once in addNotify(), not per-frame (#5)
    private int charWidth;
    private int lineHeight;
    private int fontAscent;
    // Cached gutter width — recomputed only when line count changes (#5)
    private int cachedGutterWidth = -1;
    private int cachedLineCount   = -1;

    // Scrolling Offset (Vertical)
    private int topVisibleRow = 0;

    // Cursor Blink State
    private boolean cursorVisible = true;
    private final Timer cursorTimer;

    // Autocomplete State
    private List<String> suggestions = new ArrayList<>();
    private boolean showSuggestions = false;
    private int selectedSuggestionIndex = 0;
    private String currentPrefix = "";

    // ── Color Palette (Premium Slate Theme) ──────────────────────────────────
    private static final Color BG_COLOR          = new Color(30, 30, 46);
    private static final Color TEXT_COLOR        = new Color(205, 214, 244);
    private static final Color ACTIVE_LINE_BG    = new Color(43, 44, 61);
    private static final Color GUTTER_BG         = new Color(24, 24, 37);
    private static final Color GUTTER_TEXT       = new Color(108, 112, 134);
    private static final Color SEPARATOR_COLOR   = new Color(49, 50, 68);
    private static final Color CURSOR_COLOR      = new Color(137, 180, 250);
    private static final Color STATUS_BG         = new Color(17, 17, 27);
    private static final Color STATUS_TEXT       = new Color(166, 173, 200);

    // Autocomplete dropdown colors
    private static final Color POPUP_BG          = new Color(24, 24, 37);
    private static final Color POPUP_BORDER      = new Color(137, 180, 250);
    private static final Color POPUP_SELECT_BG   = new Color(49, 50, 68);
    private static final Color POPUP_SELECT_TEXT = new Color(245, 194, 231);

    // ── Static rendering constants — allocated once, not per-frame (#6) ──────
    private static final Color  POPUP_SHADOW  = new Color(0, 0, 0, 80);
    private static final Stroke POPUP_STROKE  = new BasicStroke(1.5f);
    private static final int    STATUS_HEIGHT = 28;

    public EditorPanel(Document doc, HistoryManager history, Trie trie) {
        this.doc = doc;
        this.history = history;
        this.trie = trie;

        // Load premium monospaced font
        this.font = new Font(Font.MONOSPACED, Font.PLAIN, 15);
        this.setFocusable(true);
        this.setFocusTraversalKeysEnabled(false); // Enable intercepting TAB key

        // Set up cursor blinking timer — started in addNotify() once the
        // component has a real graphics context; stopped in removeNotify(). (#4)
        this.cursorTimer = new Timer(500, e -> {
            cursorVisible = !cursorVisible;
            // Repaint only the cursor rectangle, not the whole panel
            repaintCursorCell();
        });
    }

    /**
     * Called by Swing when the component is added to a real window.
     * We compute font metrics here (once) rather than inside paintComponent
     * on every frame. (#5)
     */
    @Override
    public void addNotify() {
        super.addNotify();
        updateFontMetrics();
        cursorTimer.start();
    }

    /**
     * Called by Swing when the component is removed from its container.
     * Stops the cursor timer so it cannot prevent this panel from being
     * garbage-collected (the Timer's ActionListener holds a strong reference
     * to the enclosing EditorPanel instance).
     */
    @Override
    public void removeNotify() {
        cursorTimer.stop();
        super.removeNotify();
    }

    /** Computes and caches charWidth / lineHeight / fontAscent. */
    private void updateFontMetrics() {
        FontMetrics fm = getFontMetrics(font);
        if (fm != null) {
            charWidth  = fm.charWidth(' ');
            lineHeight = fm.getHeight();
            fontAscent = fm.getAscent();
        }
    }

    /**
     * Returns the gutter width for the current line count.
     * Caches the result so it is not recomputed on every render frame
     * (only when the line count actually changes).
     */
    private int getGutterWidth() {
        int lc = doc.getLineCount();
        if (lc != cachedLineCount) {
            cachedLineCount = lc;
            int maxLinesLength = String.valueOf(lc).length();
            int gutterPad = 12;
            cachedGutterWidth = Math.max(4, maxLinesLength) * charWidth + gutterPad * 2;
        }
        return cachedGutterWidth;
    }

    /**
     * Repaints only the cell occupied by the cursor — used by the blink timer
     * to avoid a full-panel repaint on every 500 ms tick. (#4)
     */
    private void repaintCursorCell() {
        if (lineHeight <= 0 || charWidth <= 0) {
            repaint(); // metrics not ready yet; fall back
            return;
        }
        int gutterWidth = getGutterWidth();
        int gutterPad   = 12;
        int relRow      = doc.getCurRow() - topVisibleRow;
        int cursorX     = gutterWidth + gutterPad + doc.getCurCol() * charWidth - 2;
        int cursorY     = relRow * lineHeight;
        // Width: one character cell + a small margin
        repaint(cursorX, cursorY, charWidth + 4, lineHeight + 2);
    }

    /**
     * Resets the blinking cursor visibility to solid when the user types or navigates.
     */
    public void resetCursorBlink() {
        cursorTimer.restart();
        cursorVisible = true;
        repaint();
    }

    /**
     * Checks if the cursor is out of visible bounds and adjusts scroll offset accordingly.
     */
    public void checkScrollPosition(int visibleHeightRows) {
        int curRow = doc.getCurRow();
        if (visibleHeightRows <= 0) return;

        if (curRow < topVisibleRow) {
            topVisibleRow = curRow;
        } else if (curRow >= topVisibleRow + visibleHeightRows) {
            topVisibleRow = curRow - visibleHeightRows + 1;
        }
    }

    // Autocomplete UI controllers
    public void updateAutocomplete() {
        String lineStr = doc.getCurLine().getString();
        int col = doc.getCurCol();

        // Trace back from curCol to find the start of the current word prefix
        int start = col;
        while (start > 0 && Character.isJavaIdentifierPart(lineStr.charAt(start - 1))) {
            start--;
        }

        currentPrefix = lineStr.substring(start, col);

        if (currentPrefix.isEmpty()) {
            showSuggestions = false;
            suggestions.clear();
        } else {
            // Fetch suggestions (up to 5) from Trie
            suggestions = trie.getSuggestions(currentPrefix, 5);
            if (!suggestions.isEmpty()) {
                showSuggestions = true;
                // Bound index
                selectedSuggestionIndex = Math.min(selectedSuggestionIndex, suggestions.size() - 1);
                selectedSuggestionIndex = Math.max(0, selectedSuggestionIndex);
            } else {
                showSuggestions = false;
            }
        }
    }

    public void dismissAutocomplete() {
        showSuggestions = false;
        suggestions.clear();
        selectedSuggestionIndex = 0;
        repaint();
    }

    public boolean isShowSuggestions() {
        return showSuggestions;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public int getSelectedSuggestionIndex() {
        return selectedSuggestionIndex;
    }

    public void moveSuggestionSelection(int dir) {
        if (showSuggestions && !suggestions.isEmpty()) {
            selectedSuggestionIndex = (selectedSuggestionIndex + dir + suggestions.size()) % suggestions.size();
            repaint();
        }
    }

    public String getCurrentPrefix() {
        return currentPrefix;
    }

    public Trie getTrie() {
        return trie;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Guard: font metrics must be ready
        if (lineHeight == 0) updateFontMetrics();
        if (lineHeight == 0) return;

        Graphics2D g2d = (Graphics2D) g;
        // Enable anti-aliased font rendering
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2d.setFont(font);

        int width  = getWidth();
        int height = getHeight();

        int mainAreaHeight = height - STATUS_HEIGHT;
        int visibleRows    = mainAreaHeight / lineHeight;

        // Auto-align vertical scroll bounds based on current cursor
        checkScrollPosition(visibleRows - 1);

        // 1. Draw Editor Canvas Background
        g2d.setColor(BG_COLOR);
        g2d.fillRect(0, 0, width, mainAreaHeight);

        // 2. Draw Line Number Gutter
        int gutterPad   = 12;
        int gutterWidth = getGutterWidth();

        g2d.setColor(GUTTER_BG);
        g2d.fillRect(0, 0, gutterWidth, mainAreaHeight);

        g2d.setColor(SEPARATOR_COLOR);
        g2d.drawLine(gutterWidth, 0, gutterWidth, mainAreaHeight);

        // 3. Highlight Current Active Line Background
        int relativeCursorRow = doc.getCurRow() - topVisibleRow;
        if (relativeCursorRow >= 0 && relativeCursorRow < visibleRows) {
            g2d.setColor(ACTIVE_LINE_BG);
            g2d.fillRect(gutterWidth + 1, relativeCursorRow * lineHeight, width - gutterWidth - 1, lineHeight);
        }

        // 4. Render Text Lines and Line Numbers
        LineNode line = doc.getNodeAtRow(topVisibleRow);
        int currentRowIndex = topVisibleRow;
        int drawY = fontAscent + 2; // Vertical offset cushion

        while (line != null && (drawY - fontAscent) < mainAreaHeight) {
            // Draw gutter line number
            g2d.setFont(font);
            g2d.setColor(GUTTER_TEXT);
            String lineNumStr = String.valueOf(currentRowIndex + 1);
            int numberX = gutterWidth - gutterPad - (lineNumStr.length() * charWidth);
            g2d.drawString(lineNumStr, numberX, drawY);

            // Draw line text content (cached getString() — no allocation if unmodified)
            g2d.setColor(TEXT_COLOR);
            g2d.drawString(line.getString(), gutterWidth + gutterPad, drawY);

            line = line.next;
            currentRowIndex++;
            drawY += lineHeight;
        }

        // 5. Render Custom Blinking Cursor
        if (cursorVisible && relativeCursorRow >= 0 && relativeCursorRow < visibleRows) {
            int cursorX = gutterWidth + gutterPad + (doc.getCurCol() * charWidth);
            int cursorY = relativeCursorRow * lineHeight + 3; // Cushion
            g2d.setColor(CURSOR_COLOR);
            // Beautiful vertical caret bar (2 pixels thick)
            g2d.fillRect(cursorX, cursorY, 2, lineHeight - 4);
        }

        // 6. Render Floating Autocomplete Box
        if (showSuggestions && !suggestions.isEmpty() && relativeCursorRow >= 0 && relativeCursorRow < visibleRows) {
            int wordStartCol = doc.getCurCol() - currentPrefix.length();
            int popupX = gutterWidth + gutterPad + (wordStartCol * charWidth);
            int popupY = (relativeCursorRow + 1) * lineHeight + 4; // Right below line

            int popupItemHeight = 22;
            int popupWidth      = 180;
            int popupHeight     = suggestions.size() * popupItemHeight + 6;

            // Shift autocomplete upward if it falls offscreen
            if (popupY + popupHeight > mainAreaHeight) {
                popupY = relativeCursorRow * lineHeight - popupHeight - 4;
            }

            // Draw Popup Container Shadow/Box (POPUP_SHADOW is a static final — no allocation) (#6)
            g2d.setColor(POPUP_SHADOW);
            g2d.fillRoundRect(popupX + 4, popupY + 4, popupWidth, popupHeight, 8, 8);

            g2d.setColor(POPUP_BG);
            g2d.fillRoundRect(popupX, popupY, popupWidth, popupHeight, 8, 8);

            g2d.setColor(POPUP_BORDER);
            g2d.setStroke(POPUP_STROKE); // static final — no allocation (#6)
            g2d.drawRoundRect(popupX, popupY, popupWidth, popupHeight, 8, 8);

            // Draw Autocomplete Items
            for (int i = 0; i < suggestions.size(); i++) {
                int itemY = popupY + 3 + i * popupItemHeight;

                if (i == selectedSuggestionIndex) {
                    // Highlight selected item
                    g2d.setColor(POPUP_SELECT_BG);
                    g2d.fillRoundRect(popupX + 4, itemY, popupWidth - 8, popupItemHeight, 4, 4);

                    g2d.setColor(POPUP_SELECT_TEXT);
                    g2d.setFont(font.deriveFont(Font.BOLD));
                    g2d.drawString(">", popupX + 10, itemY + fontAscent - 2);
                } else {
                    g2d.setColor(TEXT_COLOR);
                    g2d.setFont(font);
                }

                g2d.drawString(suggestions.get(i), popupX + 24, itemY + fontAscent - 2);
            }
        }

        // 7. Render Bottom Status Bar
        g2d.setColor(STATUS_BG);
        g2d.fillRect(0, mainAreaHeight, width, STATUS_HEIGHT);

        g2d.setColor(STATUS_TEXT);
        Font statusFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        g2d.setFont(statusFont);

        String posInfo    = String.format("Ln %d, Col %d", doc.getCurRow() + 1, doc.getCurCol());
        String modeInfo   = "Mode: " + trie.getActiveCategory().toString();
        String metaInfo   = String.format("Lines: %d | Undo: %d | Redo: %d",
                                doc.getLineCount(), history.getUndoSize(), history.getRedoSize());
        String actionInfo = "Action: " + history.getLastActionString();

        FontMetrics sfm = g2d.getFontMetrics();
        int statusY = mainAreaHeight + 18; // Vertical alignment

        g2d.drawString(posInfo, 15, statusY);

        // Highlight active writing mode in status bar
        g2d.setColor(POPUP_SELECT_TEXT);
        g2d.drawString(modeInfo, 125, statusY);

        g2d.setColor(STATUS_TEXT);
        g2d.drawString(metaInfo, 290, statusY);

        // Draw last action on the right side
        int actionX = width - sfm.stringWidth(actionInfo) - 15;
        if (actionX > 540) { // Avoid overlapping if window is narrow
            g2d.drawString(actionInfo, actionX, statusY);
        }
    }
}
