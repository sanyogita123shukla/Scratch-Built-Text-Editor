package com.editor.model;

/**
 * Command to merge two lines (Backspace at column 0 or Delete at end of line).
 */
public class MergeLineCommand implements Command {
    private final int row;
    private final int col; // Stores where the split was (length of line at row before merge)

    public MergeLineCommand(int row, int col) {
        this.row = row;
        this.col = col;
    }

    @Override
    public void execute(Document doc) {
        doc.mergeLines(row);
        doc.setCursor(row, col);
    }

    @Override
    public void undo(Document doc) {
        doc.splitLine(row, col);
        doc.setCursor(row + 1, 0);
    }

    @Override
    public boolean mergeWith(Command nextCommand) {
        return false; // Line merges are never merged
    }

    @Override
    public String toString() {
        return "Merge line at (" + row + ", " + col + ")";
    }
}
