package com.teixing.gitdiffbatch;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.APP)
@State(name = "GitDiffBatchSettings", storages = @Storage("gitdiffbatch.xml"))
public final class GitDiffBatchSettings implements PersistentStateComponent<GitDiffBatchSettings.State> {

    public static class State {
        public DiffTargetMode diffTargetMode = DiffTargetMode.BRANCH_HEAD;
    }

    private State state = new State();

    public static GitDiffBatchSettings getInstance() {
        return ApplicationManager.getApplication().getService(GitDiffBatchSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
        if (this.state.diffTargetMode == null) {
            this.state.diffTargetMode = DiffTargetMode.BRANCH_HEAD;
        }
    }

    public DiffTargetMode getDiffTargetMode() {
        return state.diffTargetMode;
    }

    public void setDiffTargetMode(DiffTargetMode diffTargetMode) {
        state.diffTargetMode = diffTargetMode;
    }
}
