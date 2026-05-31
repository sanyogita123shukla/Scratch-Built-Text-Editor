package com.editor.model;

import java.util.Map;
import java.util.TreeMap;

/**
 * Represents a single node in the Autocomplete Trie.
 *
 * <p><b>Optimization:</b> Children are stored in a {@link TreeMap} (sorted by
 * character) instead of a {@link java.util.HashMap}.  This means the BFS in
 * {@link Trie#getSuggestions} can iterate children in alphabetical order for
 * free, eliminating the {@code Collections.sort()} call that was previously
 * invoked for every node visited during every autocomplete query.</p>
 */
public class TrieNode {
    private final Map<Character, TrieNode> children;
    private boolean isWord;
    private String word; // Caches the full word at this node for instant retrieval

    public TrieNode() {
        this.children = new TreeMap<>();
        this.isWord = false;
        this.word = null;
    }

    public Map<Character, TrieNode> getChildren() {
        return children;
    }

    public boolean isWord() {
        return isWord;
    }

    public void setWord(boolean word) {
        isWord = word;
    }

    public String getWord() {
        return word;
    }

    public void setWordString(String wordString) {
        this.word = wordString;
    }
}
