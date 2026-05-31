# Scratch Text Editor

A scratch-built desktop text editor implemented in Java Swing. The goal of this project was to understand and implement the internal systems behind a text editor instead of depending on a ready-made text component.

The editor includes a custom document model, custom rendering, undo/redo, trie-based autocomplete, writing-mode-based suggestion ranking, and several memory, concurrency, scalability, and rendering optimizations.

## What I Made

I built a local desktop text editor from scratch using Java.

The editor supports:

- custom text editing,
- line insertion and deletion,
- cursor movement,
- line splitting with Enter,
- line merging with Backspace/Delete,
- undo and redo,
- real-time autocomplete,
- writing-mode-based autocomplete prioritization,
- custom UI rendering,
- line numbers,
- active-line highlighting,
- a custom blinking cursor,
- an autocomplete popup,
- and a status bar showing editor metadata.

The important part of the project is that the core editor behavior is implemented manually. I did not rely on `JTextArea` for the editor engine. Instead, I built my own model for storing text, handling cursor movement, processing keyboard events, rendering the UI, managing history, and generating autocomplete suggestions.

## Why I Made It

I made this project to understand how a text editor works internally.

A normal text editor looks simple from the outside, but internally it has many interesting engineering problems:

- How should text be stored efficiently?
- How should cursor movement work across lines?
- How should undo and redo be implemented?
- How can autocomplete be fast enough to update while typing?
- How can rendering avoid unnecessary work?
- How can memory growth be controlled during long editing sessions?
- How can a Swing application stay responsive while following the Event Dispatch Thread model?

This project gave me a way to apply core computer science concepts in a practical application. It combines data structures, object-oriented design, UI rendering, event handling, memory management, and performance optimization.

## Tech Stack

| Area | Technology Used | Purpose |
|---|---|---|
| Programming language | Java | Main implementation language. |
| Desktop UI | Java Swing | Window, dialog, panel, keyboard handling, and timer support. |
| Rendering | AWT `Graphics2D` | Custom drawing of editor text, cursor, line numbers, popup, and status bar. |
| Input handling | `KeyListener` | Handles typing, navigation, undo/redo, and autocomplete actions. |
| Text storage | Doubly linked list + `StringBuilder` | Stores the document line by line. |
| Fast row access | `LineNode[]` row index | Allows O(1) lookup of a line by row number. |
| Undo/redo | Command pattern + `ArrayDeque` | Stores reversible edit operations. |
| Autocomplete | Trie | Provides fast prefix-based word suggestions. |
| Writing modes | Enum + category tries | Prioritizes suggestions based on selected writing mode. |
| Configuration | `.properties` file | Stores category vocabulary outside Java source code. |

## Data Structures I Focused On

### Doubly Linked List

The document is stored as a doubly linked list of lines.

Each line is represented by a `LineNode`. Every `LineNode` has:

- text content,
- a previous-line pointer,
- and a next-line pointer.

I focused on this structure because text editors perform many line-based operations. Pressing Enter splits one line into two. Pressing Backspace at the start of a line merges it with the previous line. A doubly linked list makes these operations natural because nodes can be inserted, removed, split, and merged by updating links.

### StringBuilder

Each `LineNode` stores its text in a `StringBuilder`.

I used `StringBuilder` because Java `String` objects are immutable. If every character edit created a new string, editing would create unnecessary temporary objects. `StringBuilder` allows mutation inside a line, which is more suitable for an editor.

### Row Index Array

A linked list is good for inserting and merging lines, but it is weak for random access. Getting line 5000 from a pure linked list would require walking through many nodes.

To solve this, I added a `LineNode[]` row index.

This gives:

- O(1) lookup for `getNodeAtRow(row)`,
- faster rendering from the first visible row,
- faster cursor positioning,
- and better scalability as the number of lines grows.

This is a memory-for-speed tradeoff: the editor stores one extra reference per line to make row lookup much faster.

### Command Pattern

Undo and redo are implemented using the Command pattern.

Each edit operation is represented as a command object:

- `InsertTextCommand`
- `DeleteTextCommand`
- `SplitLineCommand`
- `MergeLineCommand`

Every command knows how to:

- execute itself,
- undo itself,
- and sometimes merge with another command.

This makes undo/redo clean because the history manager does not need to know the details of each edit. It only calls `execute()` and `undo()`.

### ArrayDeque

Undo and redo stacks use `ArrayDeque`.

I used `ArrayDeque` instead of `Stack` because `Stack` is an older class based on `Vector`. `ArrayDeque` is faster and gives efficient operations at both ends.

This is useful because the undo history is bounded. When the history limit is exceeded, the oldest command can be removed in O(1).

### Trie

Autocomplete is implemented using a Trie.

A Trie is useful because autocomplete is a prefix-search problem. When the user types a prefix, the editor can walk the trie to that prefix and then collect possible completions.

This avoids scanning the entire dictionary on every keystroke.

### Category Tries and LRU Cache

The writing-mode system uses category-specific tries.

Each writing mode, such as Creative or Professional, has its own vocabulary. `CategoryManager` checks whether a suggestion belongs to the active category.

To make repeated checks faster, category results are stored in an LRU cache. This keeps recently used lookups fast while preventing the cache from growing forever.

## Memory Management

I handled memory management by bounding structures that can grow during user interaction and reducing unnecessary object allocation.

Key memory decisions:

- The undo stack is capped at 500 commands.
- The redo stack is cleared when a new edit happens.
- Insert command merging is capped at 1000 characters.
- User-learned autocomplete words are capped at 2000.
- Category lookup cache is capped at 4096 entries.
- Each line uses a dirty string cache, so `StringBuilder.toString()` is not called repeatedly during every repaint.
- Common rendering objects such as colors and strokes are stored as static constants.
- The Swing cursor timer is stopped in `removeNotify()` so it does not keep detached panels in memory.

The main principle is that user input should not cause unbounded memory growth. History, learned words, caches, and merged commands all have limits.

## Concurrency

The project follows the Swing Event Dispatch Thread model.

Swing is single-threaded, so UI work should happen on the Event Dispatch Thread.

In this project:

- `Main` loads the dictionary and creates the core model objects.
- `SwingUtilities.invokeLater()` starts UI creation on the EDT.
- Keyboard events run on the EDT.
- The cursor blink timer runs on the EDT.
- `paintComponent()` runs on the EDT.
- Document mutations happen through the controller on the EDT.

This keeps the design simple and avoids race conditions. Since the document is not being modified from multiple background threads, the project does not need heavy synchronization in its current form.

If large file saving or loading is added later, it should use `SwingWorker` so disk I/O does not block the UI thread.

## Scalability and Optimization

I added several optimizations to keep the editor responsive as the document grows.

| Area | Optimization | Benefit |
|---|---|---|
| Line lookup | `LineNode[]` row index | O(1) access to any row. |
| Rendering | Render only visible lines | Work depends on viewport size, not total file size. |
| Cursor blink | Repaint only cursor rectangle | Avoids repainting the whole editor every blink. |
| Font metrics | Cached `charWidth`, `lineHeight`, and `fontAscent` | Avoids repeated metric calculation in the paint path. |
| Line strings | Dirty string cache | Avoids repeated string allocation while rendering. |
| Autocomplete | Trie prefix lookup | Avoids scanning all dictionary words. |
| Trie traversal | `TreeMap` children | Keeps suggestions alphabetically ordered without sorting on every lookup. |
| Suggestion search | Bounded BFS candidate pool | Prevents autocomplete from scanning too much per keystroke. |
| Undo history | Bounded `ArrayDeque` | Keeps history operations fast and memory usage controlled. |
| Category lookup | LRU cache | Makes repeated category checks O(1). |

## What More Can Be Done

The project is functional as a custom editor prototype, but it can be improved further.

Possible improvements:

- Add file open and save support.
- Use `SwingWorker` for large file I/O.
- Add paste handling as one bulk command instead of many character inserts.
- Add automated tests for `Document`, commands, history, trie, and category ranking.
- Add mouse-based cursor placement.
- Add text selection.
- Add horizontal scrolling or soft wrapping.
- Add syntax highlighting.
- Add search and replace.
- Add support for larger files using a rope, gap buffer, or piece table.
- Add a proper build system such as Maven or Gradle.

For very large files, the current `StringBuilder` per line approach is understandable and practical, but a production-grade editor would usually use a more advanced text buffer such as a rope, gap buffer, or piece table.

## Project Status

This project is a working local desktop editor prototype focused on editor internals.

It demonstrates:

- custom text-buffer design,
- custom rendering,
- command-based undo/redo,
- trie-backed autocomplete,
- writing-mode suggestion ranking,
- memory-conscious design,
- and Swing EDT-based event handling.

The project is not positioned as a full production IDE. It is a focused implementation of the core systems that make a text editor work.
