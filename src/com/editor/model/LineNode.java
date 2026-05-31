package com.editor.model;

/**
 * Represents a single line of text in the document.
 * Serves as a node in the Doubly Linked List (DLL) text buffer.
 *
 * <p><b>Optimization:</b> {@link #getString()} uses a dirty-cache: the
 * {@code String} representation is only re-built from the underlying
 * {@link StringBuilder} when the content has actually changed.  During
 * rendering every visible line calls {@code getString()} once per frame;
 * without caching that allocates a new {@code String} object each time.</p>
 */
public class LineNode {
    private final StringBuilder text;
    public LineNode prev;
    public LineNode next;

    // ── Dirty-cache ──────────────────────────────────────────────────────────
    private String cachedString = "";
    private boolean dirty = true;

    public LineNode() {
        this("");
    }

    public LineNode(String text) {
        this.text = new StringBuilder(text);
        this.prev = null;
        this.next = null;
    }

    public StringBuilder getText() {
        return text;
    }

    /**
     * Returns the string content of this line.
     * Result is cached and only recomputed when the line has been mutated,
     * avoiding redundant {@code StringBuilder.toString()} allocations during
     * rendering.
     */
    public String getString() {
        if (dirty) {
            cachedString = text.toString();
            dirty = false;
        }
        return cachedString;
    }

    public int length() {
        return text.length();
    }

    public void insert(int col, char c) {
        text.insert(col, c);
        dirty = true;
    }

    public void insert(int col, String str) {
        text.insert(col, str);
        dirty = true;
    }

    public void deleteCharAt(int col) {
        text.deleteCharAt(col);
        dirty = true;
    }

    public void delete(int start, int end) {
        text.delete(start, end);
        dirty = true;
    }

    public void append(String str) {
        text.append(str);
        dirty = true;
    }
}
