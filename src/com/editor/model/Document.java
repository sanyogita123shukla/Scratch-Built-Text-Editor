package com.editor.model;

/**
 * Manages the text buffer (Doubly Linked List of LineNodes) and cursor position.
 * Implements low-level operations that are safe, transactional, and easy to undo.
 *
 * <p><b>Optimization:</b> {@link #getNodeAtRow(int)} now uses a flat
 * {@code LineNode[]} index for O(1) random access instead of an O(n) linked-list
 * walk.  The index is rebuilt lazily only when the line count changes
 * (i.e. after {@link #splitLine} or {@link #mergeLines}), which is far less
 * frequent than render or cursor-movement calls.</p>
 */
public class Document {
    private LineNode head;
    private LineNode tail;
    private LineNode curLine;
    private int curCol;
    private int curRow;
    private int lineCount;

    // ── O(1) row-index ───────────────────────────────────────────────────────
    /** Flat array mapping row → LineNode; null means the index is stale. */
    private LineNode[] rowIndex;

    public Document() {
        head = new LineNode();
        tail = head;
        curLine = head;
        curCol = 0;
        curRow = 0;
        lineCount = 1;
        rebuildIndex();
    }

    // Getters and Setters
    public LineNode getHead() { return head; }
    public LineNode getTail() { return tail; }
    public LineNode getCurLine() { return curLine; }
    public int getCurCol() { return curCol; }
    public int getCurRow() { return curRow; }
    public int getLineCount() { return lineCount; }

    // ── Index maintenance ─────────────────────────────────────────────────────

    /**
     * Rebuilds the row → LineNode flat index by walking the DLL once.
     * Called only when the structure changes (split / merge), not on every edit.
     * O(n) amortised over many subsequent O(1) lookups.
     */
    private void rebuildIndex() {
        rowIndex = new LineNode[lineCount];
        LineNode n = head;
        for (int i = 0; i < lineCount && n != null; i++) {
            rowIndex[i] = n;
            n = n.next;
        }
    }

    /**
     * O(1) row lookup using the flat index.
     * Falls back gracefully if the index is somehow stale.
     */
    public LineNode getNodeAtRow(int row) {
        if (row < 0 || row >= lineCount) return null;
        if (rowIndex == null || rowIndex.length != lineCount) rebuildIndex();
        return rowIndex[row];
    }

    /**
     * Safe cursor positioning. Updates row and col, ensuring they are within bounds.
     */
    public void setCursor(int row, int col) {
        if (row < 0) row = 0;
        if (row >= lineCount) row = lineCount - 1;

        curRow = row;
        curLine = getNodeAtRow(row);

        if (curLine != null) {
            if (col < 0) col = 0;
            if (col > curLine.length()) col = curLine.length();
            curCol = col;
        } else {
            curCol = 0;
        }
    }

    // Cursor Movement Commands
    public void moveLeft() {
        if (curCol > 0) {
            curCol--;
        } else if (curRow > 0) {
            curRow--;
            curLine = curLine.prev;
            curCol = curLine.length();
        }
    }

    public void moveRight() {
        if (curCol < curLine.length()) {
            curCol++;
        } else if (curRow < lineCount - 1) {
            curRow++;
            curLine = curLine.next;
            curCol = 0;
        }
    }

    public void moveUp() {
        if (curRow > 0) {
            curRow--;
            curLine = curLine.prev;
            if (curCol > curLine.length()) {
                curCol = curLine.length();
            }
        }
    }

    public void moveDown() {
        if (curRow < lineCount - 1) {
            curRow++;
            curLine = curLine.next;
            if (curCol > curLine.length()) {
                curCol = curLine.length();
            }
        }
    }

    public void moveLineStart() {
        curCol = 0;
    }

    public void moveLineEnd() {
        curCol = curLine.length();
    }

    // --- Absolute Transactional Modification APIs (for Command execution) ---

    /**
     * Inserts a string at a specific row and column.
     */
    public void insertString(int row, int col, String str) {
        LineNode node = getNodeAtRow(row);
        if (node != null) {
            if (col < 0) col = 0;
            if (col > node.length()) col = node.length();
            node.insert(col, str);

            // If editing at cursor, adjust cursor
            if (node == curLine && curCol >= col) {
                curCol += str.length();
            }
        }
    }

    /**
     * Deletes a range of text on a specific row.
     */
    public void deleteString(int row, int col, int length) {
        LineNode node = getNodeAtRow(row);
        if (node != null && length > 0) {
            if (col < 0) col = 0;
            if (col + length > node.length()) length = node.length() - col;

            node.delete(col, col + length);

            // If editing at cursor, adjust cursor
            if (node == curLine) {
                if (curCol > col + length) {
                    curCol -= length;
                } else if (curCol > col) {
                    curCol = col;
                }
            }
        }
    }

    /**
     * Splits a line at a specific column, creating a new line node.
     * Invalidates the row index (rebuilt on next access).
     */
    public void splitLine(int row, int col) {
        LineNode node = getNodeAtRow(row);
        if (node != null) {
            if (col < 0) col = 0;
            if (col > node.length()) col = node.length();

            String rightText = node.getString().substring(col);
            node.delete(col, node.length());

            LineNode newLine = new LineNode(rightText);

            // Insert in DLL
            newLine.next = node.next;
            newLine.prev = node;
            if (node.next != null) {
                node.next.prev = newLine;
            } else {
                tail = newLine;
            }
            node.next = newLine;

            lineCount++;
            rebuildIndex(); // structural change — rebuild O(1) index

            // Adjust cursor if we split the cursor line or lines above it
            if (row < curRow) {
                curRow++;
            } else if (row == curRow && curCol >= col) {
                curRow++;
                curLine = newLine;
                curCol = curCol - col;
            }
        }
    }

    /**
     * Merges a line with the line below it.
     * Invalidates the row index (rebuilt on next access).
     */
    public void mergeLines(int row) {
        LineNode firstNode = getNodeAtRow(row);
        if (firstNode != null && firstNode.next != null) {
            LineNode secondNode = firstNode.next;
            int oldLength = firstNode.length();

            // Append second node text to first node
            firstNode.append(secondNode.getString());

            // Remove secondNode from DLL
            firstNode.next = secondNode.next;
            if (secondNode.next != null) {
                secondNode.next.prev = firstNode;
            } else {
                tail = firstNode;
            }

            lineCount--;
            rebuildIndex(); // structural change — rebuild O(1) index

            // Adjust cursor if we merged the cursor line or lines above it
            if (curRow > row + 1) {
                curRow--;
            } else if (curRow == row + 1) {
                curRow = row;
                curLine = firstNode;
                curCol = oldLength + curCol;
            } else if (curRow == row && curCol > oldLength) {
                // Should not happen normally under user backspace, but for robustness:
                curCol = Math.min(curCol, firstNode.length());
            }
        }
    }

    // Helper to get raw document text for debugging/saving
    public String getFullText() {
        StringBuilder sb = new StringBuilder();
        LineNode node = head;
        while (node != null) {
            sb.append(node.getString());
            if (node.next != null) {
                sb.append("\n");
            }
            node = node.next;
        }
        return sb.toString();
    }
}
