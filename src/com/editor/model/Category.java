package com.editor.model;

/**
 * Defines the available writing categories/modes in the editor.
 */
public enum Category {
    HAPPY("Happy", "😊"),
    SAD("Sad", "😢"),
    DETAIL("Detail", "🔍"),
    EMOTIONS("Emotions", "❤️"),
    PERSONAL("Personal", "📝"),
    CREATIVE("Creative", "🎨"),
    PROFESSIONAL("Professional", "💼"),
    ACTION("Action", "⚔️"),
    ALL("General / All", "🌐");

    private final String displayName;
    private final String emoji;

    Category(String displayName, String emoji) {
        this.displayName = displayName;
        this.emoji = emoji;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    @Override
    public String toString() {
        return emoji + " " + displayName;
    }
}
