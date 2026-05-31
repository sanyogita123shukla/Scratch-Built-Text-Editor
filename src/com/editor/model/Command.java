package com.editor.model;

/**
 * Command interface representing an action that can be executed and undone.
 */
public interface Command {
    void execute(Document doc);
    void undo(Document doc);
    boolean mergeWith(Command nextCommand); // Support for grouping typed characters
}
