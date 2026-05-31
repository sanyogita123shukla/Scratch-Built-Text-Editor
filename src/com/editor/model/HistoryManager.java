package com.editor.model;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages the Undo and Redo histories.
 * Implements standard stack limits and character-by-character command merging.
 *
 * <p><b>Optimizations:</b></p>
 * <ul>
 *   <li>Both stacks use {@link ArrayDeque} instead of {@link java.util.Stack}
 *       ({@code Stack} extends the synchronized {@code Vector}; {@code ArrayDeque}
 *       is ~2× faster for push/pop and avoids lock contention on the EDT).</li>
 *   <li>Oldest-entry eviction uses {@code pollFirst()} — O(1) — instead of
 *       {@code remove(0)} on a {@code Vector} — O(n).</li>
 *   <li>{@link #MAX_HISTORY} raised from 100 to 500: at normal typing speed
 *       the old cap was exhausted in under a minute of editing.</li>
 * </ul>
 */
public class HistoryManager {
    private static final int MAX_HISTORY = 500;
    private final Deque<Command> undoStack;
    private final Deque<Command> redoStack;
    private String lastActionString = "No actions yet";

    public HistoryManager() {
        undoStack = new ArrayDeque<>();
        redoStack = new ArrayDeque<>();
    }

    /**
     * Executes a command on the document and records it in history.
     * Clears the redo stack.
     */
    public void executeCommand(Document doc, Command cmd) {
        cmd.execute(doc);
        pushCommand(cmd);
        lastActionString = cmd.toString();
    }

    /**
     * Records a pre-executed command into the undo stack and clears redo stack.
     * Supports intelligent command merging.
     */
    public void pushCommand(Command cmd) {
        redoStack.clear(); // Clear redo stack on any new edit action

        if (!undoStack.isEmpty()) {
            Command top = undoStack.peekLast();
            if (top.mergeWith(cmd)) {
                // Merged successfully in-place, no need to push
                lastActionString = top.toString();
                return;
            }
        }

        undoStack.addLast(cmd);
        lastActionString = cmd.toString();

        // Enforce maximum history depth — O(1) removal of the oldest entry
        if (undoStack.size() > MAX_HISTORY) {
            undoStack.pollFirst();
        }
    }

    /**
     * Undoes the last action.
     */
    public void undo(Document doc) {
        if (!undoStack.isEmpty()) {
            Command cmd = undoStack.pollLast();
            cmd.undo(doc);
            redoStack.addLast(cmd);
            lastActionString = "Undid: " + cmd.toString();
        }
    }

    /**
     * Redoes the last undone action.
     */
    public void redo(Document doc) {
        if (!redoStack.isEmpty()) {
            Command cmd = redoStack.pollLast();
            cmd.execute(doc);
            undoStack.addLast(cmd);
            lastActionString = "Redid: " + cmd.toString();
        }
    }

    public int getUndoSize() {
        return undoStack.size();
    }

    public int getRedoSize() {
        return redoStack.size();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public String getLastActionString() {
        return lastActionString;
    }

    /**
     * Force-flushes any merging command on the next keystroke (e.g. after space, tab, enter or arrow keys).
     * We accomplish this by pushing a special marker or just clear/re-initialize our merge tracking.
     * In our command merge implementation, top.mergeWith(cmd) checks if characters are contiguous,
     * so if we move the cursor or change types, the merge automatically fails.
     * Hence, no extra flush call is strictly necessary, but this method is here for architecture completeness.
     */
    public void flushMerge() {
        // Handled naturally by contiguous checks in mergeWith()
    }
}

