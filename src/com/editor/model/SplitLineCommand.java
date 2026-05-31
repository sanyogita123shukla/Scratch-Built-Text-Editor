package com.editor.model;

/**
 * Command to split a line into two (Enter).
 */
public class SplitLineCommand implements Command {
    private final int row;
    private final int col;

    public SplitLineCommand(int row, int col) {
        this.row = row;
        this.col = col;
    }

    @Override
    public void execute(Document doc) {
        doc.splitLine(row, col);
        doc.setCursor(row + 1, 0);
    }

    @Override
    public void undo(Document doc) {
        doc.mergeLines(row);
        doc.setCursor(row, col);
    }

    @Override
    public boolean mergeWith(Command nextCommand) {
        return false; // Line splits are never merged
    }

    @Override
    public String toString() {
        return "Split line at (" + row + ", " + col + ")";
    }
}
