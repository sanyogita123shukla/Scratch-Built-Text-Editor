package com.editor.model;

/**
 * Command to insert text at a specific coordinate.
 *
 * <p><b>Memory note:</b> Adjacent insertions on the same line are merged into a
 * single command so that undo works at word granularity rather than
 * character-by-character.  The merge is capped at {@link #MERGE_CHAR_LIMIT}
 * characters: beyond that the command is sealed and a new one is started.
 * Without this cap, typing a very long unbroken sequence (e.g. a URL, a paste)
 * would accumulate the entire text in a single heap-allocated {@code String},
 * doubling the memory of that content (once in the {@link LineNode}, once here).
 * </p>
 */
public class InsertTextCommand implements Command {
    /** Maximum characters that may be accumulated in one merged command. */
    private static final int MERGE_CHAR_LIMIT = 1_000;

    private final int row;
    private final int col;
    private String text;

    public InsertTextCommand(int row, int col, String text) {
        this.row = row;
        this.col = col;
        this.text = text;
    }

    @Override
    public void execute(Document doc) {
        doc.insertString(row, col, text);
        doc.setCursor(row, col + text.length());
    }

    @Override
    public void undo(Document doc) {
        doc.deleteString(row, col, text.length());
        doc.setCursor(row, col);
    }

    @Override
    public boolean mergeWith(Command nextCommand) {
        if (nextCommand instanceof InsertTextCommand next) {
            // Merge only if adjacent, continuous, and within the size cap
            if (next.row == this.row
                    && next.col == this.col + this.text.length()
                    && this.text.length() < MERGE_CHAR_LIMIT) {
                this.text += next.text;
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Insert \"" + text.replace("\n", "\\n") + "\" at (" + row + ", " + col + ")";
    }
}
