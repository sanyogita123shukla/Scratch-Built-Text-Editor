package com.editor.model;

import java.util.*;

/**
 * Autocomplete prefix tree that stores dictionary words.
 * Traverses using lowercase characters to be case-insensitive,
 * but returns originally cased words stored in the nodes.
 *
 * <p><b>Optimizations:</b></p>
 * <ul>
 *   <li>The BFS no longer copies and sorts the child key-set on every visited
 *       node.  Because {@link TrieNode} now uses a {@link TreeMap}, iterating
 *       {@code node.getChildren().values()} already yields children in
 *       alphabetical order — zero allocation, zero sort per node.</li>
 *   <li>The BFS candidate cap is tightened from the hard-coded value of 100
 *       to {@code limit * 4}.  We only ever need {@code limit} suggestions
 *       (typically 5), so collecting 100 candidates wasted ~20× work on
 *       category-filtering and list building.</li>
 * </ul>
 */
public class Trie {
    private final TrieNode root;
    private Category activeCategory = Category.ALL;

    public Trie() {
        this.root = new TrieNode();
    }

    public Category getActiveCategory() {
        return activeCategory;
    }

    public void setActiveCategory(Category category) {
        this.activeCategory = category;
    }

    /**
     * Inserts a word into the Trie.
     */
    public void insert(String word) {
        if (word == null || word.trim().isEmpty()) return;

        TrieNode current = root;
        String lower = word.toLowerCase();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            current.getChildren().putIfAbsent(ch, new TrieNode());
            current = current.getChildren().get(ch);
        }
        current.setWord(true);
        current.setWordString(word.trim()); // Save the original word casing
    }

    /**
     * Gets a list of autocomplete suggestions matching the prefix.
     * Prioritizes words matching the active category.
     *
     * <p>The BFS collects at most {@code limit * 4} candidates so the
     * subsequent category-partition step has a small, bounded list to scan.
     * Children are iterated in alphabetical order for free because
     * {@link TrieNode} uses a {@link TreeMap} internally.</p>
     */
    public List<String> getSuggestions(String prefix, int limit) {
        List<String> suggestions = new ArrayList<>();
        if (prefix == null || prefix.trim().isEmpty()) return suggestions;

        TrieNode current = root;
        String lower = prefix.toLowerCase();

        // Traverse to the end of the prefix
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            current = current.getChildren().get(ch);
            if (current == null) {
                return suggestions; // Prefix not in Trie
            }
        }

        // BFS to collect candidates — cap at limit*4 (enough to fill the
        // suggestion list after category-based promotion, without over-scanning).
        int bfsCap = limit * 4;
        List<String> candidates = new ArrayList<>(bfsCap);
        Queue<TrieNode> queue = new LinkedList<>();
        queue.add(current);

        while (!queue.isEmpty() && candidates.size() < bfsCap) {
            TrieNode node = queue.poll();

            if (node.isWord()) {
                candidates.add(node.getWord());
            }

            // TreeMap children are already in sorted (alphabetical) order —
            // no copy or sort needed here.
            queue.addAll(node.getChildren().values());
        }

        // Prioritize words in the active writing category
        if (activeCategory != null && activeCategory != Category.ALL) {
            List<String> promoted = new ArrayList<>();
            List<String> others   = new ArrayList<>();
            for (String word : candidates) {
                if (CategoryManager.isInMode(word, activeCategory)) {
                    promoted.add(word);
                } else {
                    others.add(word);
                }
            }
            promoted.addAll(others);
            candidates = promoted;
        }

        // Limit results
        int count = Math.min(candidates.size(), limit);
        for (int i = 0; i < count; i++) {
            suggestions.add(candidates.get(i));
        }

        return suggestions;
    }
}
