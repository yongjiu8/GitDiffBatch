package com.teixing.gitdiffbatch;

import com.intellij.diff.DiffDialogHints;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Project:gitdiffbatch<br>
 * Title:BatchShowDiffAction.java<br>
 * Description: <br>
 * <p>
 * Copyrigth:Baosight Software LTD.co Copyright (c) 2026 .<br>
 *
 * @author chenYongJin
 * @version 1.0
 * @history 2026/2/6 chenYongJin create
 * @since 1.8
 */
public class BatchShowDiffAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
        if (log == null) return;

        List<CommitId> commits = log.getSelectedCommits();
        if (commits.isEmpty()) return;

        GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
        VirtualFile baseDir = ProjectRootManager.getInstance(project).getContentRoots()[0];
        GitRepository repository = manager.getRepositoryForFileQuick(baseDir);
        if (repository == null) return;

        ExecutorService executor = AppExecutorUtil.getAppExecutorService();
        executor.submit(() -> {
            try {
                Set<ModifiedFileEntry> files = getFilesModifiedByCommits(repository, commits);
                for (ModifiedFileEntry fileEntry : files) {
                    showCommitDiff(project, manager, fileEntry);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private void showCommitDiff(Project project, GitRepositoryManager manager, ModifiedFileEntry fileEntry) {
        String filePath = fileEntry.filePath;
        CommitId commitId = fileEntry.commitId;
        VirtualFile root = commitId.getRoot();
        GitRepository repoForCommit = manager.getRepositoryForRootQuick(root);
        if (repoForCommit == null) return;

        Hash commitHash = commitId.getHash();
        if (commitHash == null || commitHash.asString().isEmpty()) return;

        DiffTargetMode diffTargetMode = GitDiffBatchSettings.getInstance().getDiffTargetMode();
        GitRevisionNumber targetRevision = resolveTargetRevision(project, repoForCommit, root, commitHash.asString(), diffTargetMode);
        if (targetRevision == null) return;

        File absoluteFile = new File(root.getPath(), filePath);
        FilePath vcsFilePath = VcsUtil.getFilePath(absoluteFile, false);
        ContentRevision workingTreeRevision = absoluteFile.exists()
                ? CurrentContentRevision.create(vcsFilePath)
                : null;
        ContentRevision targetRevisionContent = existsInRevision(repoForCommit, targetRevision.asString(), filePath)
                ? GitContentRevision.createRevision(vcsFilePath, targetRevision, project)
                : null;
        if (workingTreeRevision == null && targetRevisionContent == null) return;

        Change change = new Change(workingTreeRevision, targetRevisionContent);
        ShowDiffContext diffContext = new ShowDiffContext(DiffDialogHints.DEFAULT);

        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() ->
                ShowDiffAction.showDiffForChange(
                        project,
                        Collections.singletonList(change),
                        0,
                        diffContext
                )
        );
    }

    private Set<ModifiedFileEntry> getFilesModifiedByCommits(GitRepository repo, Collection<CommitId> commits) {
        Set<ModifiedFileEntry> files = new LinkedHashSet<>();
        try {
            for (CommitId commit : commits) {
                String hash = commit.getHash().asString();
                Process process = Runtime.getRuntime().exec(
                        "git diff-tree --no-commit-id --name-only -r " + hash,
                        null,
                        new File(commit.getRoot().getPath())
                );
                try (Scanner scanner = new Scanner(process.getInputStream())) {
                    while (scanner.hasNextLine()) {
                        String filePath = scanner.nextLine().trim();
                        if (!filePath.isEmpty()) {
                            files.add(new ModifiedFileEntry(filePath, commit));
                        }
                    }
                }
                process.waitFor();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return files;
    }

    private GitRevisionNumber resolveTargetRevision(Project project,
                                                    GitRepository repo,
                                                    VirtualFile root,
                                                    String commitHash,
                                                    DiffTargetMode diffTargetMode) {
        if (diffTargetMode == DiffTargetMode.SELECTED_COMMIT) {
            return new GitRevisionNumber(commitHash);
        }
        String branchName = resolveTargetBranchName(repo, commitHash);
        if (branchName == null || branchName.isEmpty()) return null;
        return resolveRevision(project, root, branchName);
    }

    private String resolveTargetBranchName(GitRepository repo, String commitHash) {
        List<String> branches = getContainingLocalBranches(repo, commitHash);
        if (branches.isEmpty()) return null;

        if (repo.getCurrentBranch() != null) {
            String currentBranchName = repo.getCurrentBranch().getName();
            if (branches.contains(currentBranchName)) {
                return currentBranchName;
            }
        }
        return branches.get(0);
    }

    private GitRevisionNumber resolveRevision(Project project, VirtualFile root, String revision) {
        try {
            return GitRevisionNumber.resolve(project, root, revision);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private List<String> getContainingLocalBranches(GitRepository repo, String commitHash) {
        List<String> branches = new ArrayList<>();
        try {
            Process process = new ProcessBuilder(
                    "git",
                    "for-each-ref",
                    "--contains=" + commitHash,
                    "--format=%(refname:short)",
                    "refs/heads"
            ).directory(new File(repo.getRoot().getPath())).start();
            try (Scanner scanner = new Scanner(process.getInputStream(), StandardCharsets.UTF_8)) {
                while (scanner.hasNextLine()) {
                    String branchName = scanner.nextLine().trim();
                    if (!branchName.isEmpty()) {
                        branches.add(branchName);
                    }
                }
            }
            process.waitFor();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return branches;
    }

    private boolean existsInRevision(GitRepository repo, String revision, String filePath) {
        try {
            Process process = new ProcessBuilder(
                    "git",
                    "cat-file",
                    "-e",
                    revision + ":" + filePath
            ).directory(new File(repo.getRoot().getPath())).start();
            return process.waitFor() == 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private static final class ModifiedFileEntry {
        private final String filePath;
        private final CommitId commitId;
        private final String uniqueKey;

        private ModifiedFileEntry(String filePath, CommitId commitId) {
            this.filePath = filePath;
            this.commitId = commitId;
            this.uniqueKey = commitId.getRoot().getPath() + '\n' + filePath;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ModifiedFileEntry)) return false;
            ModifiedFileEntry other = (ModifiedFileEntry) obj;
            return Objects.equals(uniqueKey, other.uniqueKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uniqueKey);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
        boolean enabled = false;
        if (project != null && log != null) {
            List<CommitId> commits = log.getSelectedCommits();
            enabled = commits != null && !commits.isEmpty();
        }
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}
