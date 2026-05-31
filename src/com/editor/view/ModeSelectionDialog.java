package com.editor.view;

import com.editor.model.Category;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A beautiful, premium Slate-Dark themed selection dialog.
 * Prompts the user to pick a writing mode at startup.
 */
public class ModeSelectionDialog extends JDialog {
    private Category selectedCategory = Category.ALL;

    // Premium Slate theme palette
    private static final Color BG_COLOR = new Color(30, 30, 46);          // Dark slate canvas
    private static final Color CARD_BG = new Color(24, 24, 37);           // Rich deep card background
    private static final Color HOVER_BG = new Color(49, 50, 68);          // Slate gray hover highlight
    private static final Color TEXT_COLOR = new Color(205, 214, 244);      // Cream white text
    private static final Color SUBTEXT_COLOR = new Color(166, 173, 200);   // Muted ash gray subtext
    private static final Color BORDER_COLOR = new Color(137, 180, 250);    // Calm pastel blue accent border
    private static final Color ACCENT_COLOR = new Color(245, 194, 231);    // Pastel pink text highlight

    public ModeSelectionDialog(Frame owner) {
        super(owner, "Select Writing Mode", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        
        // Use system look and feel for sizing metrics but custom colors
        getContentPane().setBackground(BG_COLOR);

        // Main Layout setup
        JPanel contentPanel = new JPanel(new BorderLayout(15, 15));
        contentPanel.setBackground(BG_COLOR);
        contentPanel.setBorder(new EmptyBorder(25, 25, 25, 25));
        setContentPane(contentPanel);

        // Header Panel (Title and description)
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(BG_COLOR);
        
        JLabel titleLabel = new JLabel("Choose Writing Mode");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subLabel = new JLabel("Segregates autocomplete vocabulary to prioritize words matching your context.");
        subLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subLabel.setForeground(SUBTEXT_COLOR);
        subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        subLabel.setBorder(new EmptyBorder(8, 0, 15, 0));

        headerPanel.add(titleLabel);
        headerPanel.add(subLabel);
        contentPanel.add(headerPanel, BorderLayout.NORTH);

        // Grid Panel for the 9 categories
        JPanel gridPanel = new JPanel(new GridLayout(3, 3, 12, 12));
        gridPanel.setBackground(BG_COLOR);

        for (Category category : Category.values()) {
            JButton btn = createCategoryButton(category);
            gridPanel.add(btn);
        }
        contentPanel.add(gridPanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(owner);
    }

    private JButton createCategoryButton(Category category) {
        JButton button = new JButton();
        button.setLayout(new BorderLayout());
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setBackground(CARD_BG);
        button.setForeground(TEXT_COLOR);
        button.setBorder(BorderFactory.createLineBorder(new Color(49, 50, 68), 1));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(150, 95));

        // Emoji label
        JLabel emojiLabel = new JLabel(category.getEmoji());
        emojiLabel.setFont(new Font("SansSerif", Font.PLAIN, 28));
        emojiLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emojiLabel.setForeground(TEXT_COLOR);
        emojiLabel.setBorder(new EmptyBorder(10, 0, 2, 0));

        // Category Name label
        JLabel nameLabel = new JLabel(category.getDisplayName());
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        nameLabel.setForeground(SUBTEXT_COLOR);
        nameLabel.setBorder(new EmptyBorder(0, 0, 10, 0));

        button.add(emojiLabel, BorderLayout.CENTER);
        button.add(nameLabel, BorderLayout.SOUTH);

        // Hover & click animations and states
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(HOVER_BG);
                button.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
                nameLabel.setForeground(ACCENT_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(CARD_BG);
                button.setBorder(BorderFactory.createLineBorder(new Color(49, 50, 68), 1));
                nameLabel.setForeground(SUBTEXT_COLOR);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                button.setBackground(new Color(17, 17, 27));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                button.setBackground(HOVER_BG);
            }
        });

        button.addActionListener(e -> {
            selectedCategory = category;
            dispose();
        });

        return button;
    }

    public Category getSelectedCategory() {
        return selectedCategory;
    }
}
