package com.editor.model;

import java.io.InputStream;
import java.util.*;

/**
 * Manages the vocabulary associated with each Category writing mode.
 *
 * <h3>Design goals</h3>
 * <ul>
 *   <li><b>O(k) lookup</b> – each category keeps its own {@link CategoryTrie}
 *       so prefix/stem matching traverses at most {@code k} trie nodes, where
 *       {@code k} is the length of the query word, instead of scanning every
 *       word in a flat set.</li>
 *   <li><b>O(1) repeated lookup</b> – a bounded {@link ConcurrentHashMap}
 *       cache memoises {@code (word, category)} results so the same query
 *       during a typing session costs a single hash lookup.</li>
 *   <li><b>Data-code separation</b> – word lists live in
 *       {@code resources/category_words.properties}. Adding or tweaking words
 *       requires no Java recompilation.</li>
 * </ul>
 */
public class CategoryManager {

    // ── Trie-per-category index ──────────────────────────────────────────────

    /**
     * Lightweight trie that stores category words and supports both exact
     * lookup and prefix/stem queries in O(word-length) time.
     */
    private static final class CategoryTrie {
        private static final int ALPHABET = 26;
        private final int[][] children;  // [nodeId][0..25]
        private final boolean[] terminal;
        private int size = 1;            // node 0 is root

        CategoryTrie(int capacity) {
            // Allocate flat arrays – much cheaper than object-per-node for
            // small, read-only tries; capacity = max expected nodes.
            children = new int[capacity][ALPHABET];
            terminal  = new boolean[capacity];
            // Java zero-initialises arrays; 0 in children[] means "no child"
            // (root is node 0, so we reserve node 0 and start children at 1)
        }

        /** Insert a lower-case word. */
        void insert(String word) {
            int node = 0;
            for (int i = 0; i < word.length(); i++) {
                int c = word.charAt(i) - 'a';
                if (c < 0 || c >= ALPHABET) continue; // skip non-alpha chars
                if (children[node][c] == 0) {
                    children[node][c] = size++;
                }
                node = children[node][c];
            }
            terminal[node] = true;
        }

        /**
         * Returns {@code true} if {@code word} is an exact match OR a stem
         * match (the word is a prefix of a dictionary entry, or a dictionary
         * entry is a prefix of the word) within a ±{@code tolerance} length
         * window.
         */
        boolean matchesStem(String word, int tolerance) {
            int node = 0;
            int len  = word.length();

            for (int i = 0; i < len; i++) {
                int c = word.charAt(i) - 'a';
                if (c < 0 || c >= ALPHABET) return false;

                int next = children[node][c];
                if (next == 0) {
                    // word is longer than any stored entry that shares this prefix.
                    // The stored entry (at current node) may be a valid stem.
                    return terminal[node] && (len - i) <= tolerance;
                }
                node = next;
            }

            // We consumed all chars of 'word'.
            if (terminal[node]) return true;          // exact match

            // Check if any child path (stored word) is within tolerance length
            // of 'word' — i.e., stored word is a longer form with few extra chars.
            return hasTerminalWithin(node, tolerance);
        }

        /** DFS to find if there is any terminal node within {@code maxDepth}
         *  hops from {@code start}. Uses an iterative stack for efficiency. */
        private boolean hasTerminalWithin(int start, int maxDepth) {
            // Stack stores (nodeId, depthRemaining)
            int[] stackNode  = new int[maxDepth * ALPHABET + 1];
            int[] stackDepth = new int[stackNode.length];
            int top = 0;
            stackNode[top]  = start;
            stackDepth[top] = maxDepth;
            top++;

            while (top > 0) {
                top--;
                int node  = stackNode[top];
                int depth = stackDepth[top];
                if (terminal[node]) return true;
                if (depth == 0)     continue;
                for (int c = 0; c < ALPHABET; c++) {
                    int child = children[node][c];
                    if (child != 0) {
                        stackNode[top]  = child;
                        stackDepth[top] = depth - 1;
                        top++;
                    }
                }
            }
            return false;
        }
    }

    // ── Static state ─────────────────────────────────────────────────────────

    private static final int STEM_TOLERANCE = 3;
    private static final int CACHE_MAX_SIZE = 4096;

    /** One trie per category (null entry ⇒ no words registered, e.g. ALL). */
    private static final Map<Category, CategoryTrie> categoryTries =
            new EnumMap<>(Category.class);

    /**
     * Memoisation cache: key = "word\0category.name()", value = result.
     *
     * <p><b>Optimization:</b> Uses an LRU {@link LinkedHashMap} (access-order
     * mode) instead of a {@link java.util.concurrent.ConcurrentHashMap} with
     * random 10% eviction.  The old strategy could discard frequently-used
     * entries; LRU guarantees the most recently accessed results are retained.
     * {@link Collections#synchronizedMap} preserves thread-safety on the EDT.</p>
     */
    private static final Map<String, Boolean> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(CACHE_MAX_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > CACHE_MAX_SIZE;
                }
            }
    );

    static {
        loadFromProperties();
    }

    // ── Resource loading ─────────────────────────────────────────────────────

    /**
     * Loads word lists from {@code resources/category_words.properties}.
     * Falls back to an empty state (no crash) if the file is absent, so the
     * editor can still start.
     */
    private static void loadFromProperties() {
        Properties props = new Properties();
        try (InputStream in = CategoryManager.class.getResourceAsStream(
                "/com/editor/resources/category_words.properties")) {
            if (in == null) {
                System.err.println("[CategoryManager] category_words.properties not found – no category words loaded.");
                return;
            }
            props.load(in);
        } catch (Exception e) {
            System.err.println("[CategoryManager] Failed to load category words: " + e.getMessage());
            return;
        }

        for (Category cat : Category.values()) {
            if (cat == Category.ALL) continue;

            String line = props.getProperty(cat.name());
            if (line == null || line.isBlank()) continue;

            String[] words = line.split(",");
            // Each trie node can have at most ALPHABET children; a conservative
            // upper bound for node count is (total chars + 1 per word).
            int charCount = line.length(); // over-estimate, but safe
            CategoryTrie trie = new CategoryTrie(charCount + words.length + 1);

            for (String w : words) {
                String trimmed = w.trim().toLowerCase();
                if (!trimmed.isEmpty()) {
                    trie.insert(trimmed);
                }
            }
            categoryTries.put(cat, trie);
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Checks whether {@code word} belongs to the vocabulary of the given
     * {@code category} writing mode.
     *
     * <p><b>Complexity</b></p>
     * <ul>
     *   <li>Cache hit  : O(1)</li>
     *   <li>Cache miss : O(k) trie traversal where k = word length</li>
     * </ul>
     *
     * <p>Matching is case-insensitive and stem-aware: a word is considered
     * "in mode" if it is an exact match or a prefix/suffix variant within
     * ±{@value #STEM_TOLERANCE} characters of a registered word.</p>
     *
     * <p>Always returns {@code false} for {@link Category#ALL}.</p>
     *
     * @param word     the word to test; must not be {@code null}
     * @param category the category to test against; must not be {@code null}
     * @return {@code true} if the word matches a registered category word
     */
    public static boolean isInMode(String word, Category category) {
        if (category == null || category == Category.ALL || word == null || word.isEmpty()) {
            return false;
        }

        // ── Cache lookup ─────────────────────────────────────────────────────
        String cacheKey = word.toLowerCase() + '\0' + category.name();
        Boolean cached  = cache.get(cacheKey);
        if (cached != null) return cached;

        // ── Trie lookup ──────────────────────────────────────────────────────
        CategoryTrie trie = categoryTries.get(category);
        boolean result = (trie != null) && trie.matchesStem(word.toLowerCase(), STEM_TOLERANCE);

        // ── Memoize — LRU LinkedHashMap auto-evicts the eldest entry ─────────
        cache.put(cacheKey, result);

        return result;
    }

    /**
     * Adds a new word to the given category at runtime without requiring a
     * restart. The cache is cleared for the affected category so subsequent
     * lookups reflect the update.
     *
     * @param word     the word to register (case-insensitive)
     * @param category the target category; {@link Category#ALL} is ignored
     */
    public static void addWord(String word, Category category) {
        if (category == null || category == Category.ALL || word == null || word.isBlank()) return;

        CategoryTrie trie = categoryTries.get(category);
        if (trie == null) {
            // Bootstrap a new trie for this category with room for growth
            trie = new CategoryTrie(512);
            categoryTries.put(category, trie);
        }
        trie.insert(word.trim().toLowerCase());

        // Invalidate cached results for this category
        String suffix = '\0' + category.name();
        cache.keySet().removeIf(k -> k.endsWith(suffix));
    }

    /**
     * Returns the set of categories that contain {@code word}.
     * Useful for highlighting or multi-mode analysis.
     *
     * @param word the word to classify
     * @return an unmodifiable set of matching categories (never {@code null})
     */
    public static Set<Category> categoriesFor(String word) {
        if (word == null || word.isBlank()) return Collections.emptySet();
        Set<Category> result = EnumSet.noneOf(Category.class);
        for (Category cat : Category.values()) {
            if (cat != Category.ALL && isInMode(word, cat)) {
                result.add(cat);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /** Clears the memoisation cache (useful in tests or after bulk updates). */
    public static void clearCache() {
        cache.clear();
    }
}
