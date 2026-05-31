package com.editor.model;

/**
 * Command to delete text at a specific coordinate.
 */
public class DeleteTextCommand implements Command {
    private final int row;
    private int col;
    private String text;

    public DeleteTextCommand(int row, int col, String text) {
        this.row = row;
        this.col = col;
        this.text = text;
    }

    @Override
    public void execute(Document doc) {
        doc.deleteString(row, col, text.length());
        doc.setCursor(row, col);
    }

    @Override
    public void undo(Document doc) {
        doc.insertString(row, col, text);
        doc.setCursor(row, col + text.length());
    }

    @Override
    public boolean mergeWith(Command nextCommand) {
        if (nextCommand instanceof DeleteTextCommand) {
            DeleteTextCommand next = (DeleteTextCommand) nextCommand;
            // For consecutive backspaces, the next delete is at this.col - 1 (deleting backwards)
            if (next.row == this.row && next.col == this.col - next.text.length()) {
                this.text = next.text + this.text; // Prepend deleted character
                this.col = next.col; // Move the start column of the deletion back
                return true;
            } else if (next.row == this.row && next.col == this.col) {
                // Holding the "Delete" key deletes from the same column index consecutively
                this.text += next.text;
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Delete \"" + text.replace("\n", "\\n") + "\" at (" + row + ", " + col + ")";
    }
}
