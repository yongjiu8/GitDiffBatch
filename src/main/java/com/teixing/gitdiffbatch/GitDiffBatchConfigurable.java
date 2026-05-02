package com.teixing.gitdiffbatch;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class GitDiffBatchConfigurable implements Configurable {
    private JPanel panel;
    private JRadioButton branchHeadButton;
    private JRadioButton selectedCommitButton;

    @Override
    public @Nls String getDisplayName() {
        return "GitDiffBatch";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (panel == null) {
            panel = new JPanel(new BorderLayout());

            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

            JLabel label = new JLabel("Right-side diff target:");
            label.setAlignmentX(Component.LEFT_ALIGNMENT);

            branchHeadButton = new JRadioButton("Latest commit of current/containing branch");
            branchHeadButton.setAlignmentX(Component.LEFT_ALIGNMENT);

            selectedCommitButton = new JRadioButton("Selected commit");
            selectedCommitButton.setAlignmentX(Component.LEFT_ALIGNMENT);

            ButtonGroup group = new ButtonGroup();
            group.add(branchHeadButton);
            group.add(selectedCommitButton);

            content.add(label);
            content.add(Box.createVerticalStrut(8));
            content.add(branchHeadButton);
            content.add(Box.createVerticalStrut(4));
            content.add(selectedCommitButton);

            panel.add(content, BorderLayout.NORTH);
        }
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        return getSelectedMode() != getSettings().getDiffTargetMode();
    }

    @Override
    public void apply() {
        getSettings().setDiffTargetMode(getSelectedMode());
    }

    @Override
    public void reset() {
        DiffTargetMode mode = getSettings().getDiffTargetMode();
        branchHeadButton.setSelected(mode == DiffTargetMode.BRANCH_HEAD);
        selectedCommitButton.setSelected(mode == DiffTargetMode.SELECTED_COMMIT);
    }

    private DiffTargetMode getSelectedMode() {
        return selectedCommitButton.isSelected() ? DiffTargetMode.SELECTED_COMMIT : DiffTargetMode.BRANCH_HEAD;
    }

    private GitDiffBatchSettings getSettings() {
        return GitDiffBatchSettings.getInstance();
    }
}
