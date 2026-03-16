package com.teixing.gitdiffbatch;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.vcs.log.*;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;


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
        if (log == null) {
            // 没有打开Git Log窗口
            return;
        }

        // 获取选中的Commit
        @NotNull List<CommitId> commits = log.getSelectedCommits();
        if (commits.isEmpty()) {
            // 提示选中提交
            return;
        }

        // 获取当前Git仓库
        GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
        VirtualFile baseDir = ProjectRootManager.getInstance(project).getContentRoots()[0];
        GitRepository repository = manager.getRepositoryForFileQuick(baseDir);
        if (repository == null) return;


        // 使用后台线程避免UI卡顿
        ExecutorService executor = AppExecutorUtil.getAppExecutorService();

        executor.submit(() -> {
            try {
                // 这里用Git命令行示例：获取所有文件
                Set<ModifiedFileEntry> files = getFilesModifiedByCommits(project, repository, commits);

                for (ModifiedFileEntry fileEntry : files) {
                    String filePath = fileEntry.filePath;
                    CommitId thisCommitId = fileEntry.commitId;
                    VirtualFile root = thisCommitId.getRoot();
                    GitRepository repoForCommit = manager.getRepositoryForRootQuick(root);
                    if (repoForCommit == null) return;
                    VirtualFile vf = root.findFileByRelativePath(filePath);
                    if (vf == null) {
                        //文件不存在创建
                        vf = createEmptyFile(project, filePath);
                    }

                    // 获取commit版本的文件内容
                    Hash commitHash = thisCommitId.getHash();

                    if (commitHash == null || commitHash.asString().isEmpty()) {
                        continue; // 跳过无效提交
                    }

                    // 获取选中提交所在的分支
                    List<String> branches = getContainingLocalBranches(repoForCommit, commitHash.asString());
                    if (branches.isEmpty()) {
                        continue; // 如果没有找到分支，则跳过
                    }
                    String branchName = branches.get(0);

                    // 获取分支在该提交时的文件内容
                    //String latestCommitHash = GitHistoryUtils.getCurrentRevision(project, new LocalFilePath(vf.getPath(), false), branchName).asString();
                    String branchContent = loadFileContentAtBranch(project, repoForCommit, vf, branchName);
                    assert vf != null;
                    String workingContent = decodeFileContent(vf.contentsToByteArray(), vf.getCharset());

                    // 创建DiffContent
                    DiffContentFactory contentFactory = DiffContentFactory.getInstance();
                    DocumentContent leftContent = contentFactory.create(project, workingContent);
                    DocumentContent rightContent = contentFactory.create(project, branchContent, vf.getFileType());

                    String leftTitle = "Working Tree";
                    String rightTitle = branchName + "@" + commitHash;
                    String[] pathParts = filePath.split("/");
                    String fileName = pathParts[pathParts.length - 1];
                    String title = fileName;

                    // contents 顺序：LEFT / BASE / RIGHT
                    /*List<DocumentContent> contents = Arrays.asList(leftContent, leftContent, rightContent);
                    List<String> contentTitles = Arrays.asList(leftTitle, leftTitle, rightTitle);*/

                    // output/result：绑定到真实文件，Apply 写回工作区文件
                    DocumentContent output = contentFactory.createDocument(project, vf);

                    /*Document doc = FileDocumentManager.getInstance().getDocument(vf);
                    CharSequence originalContent =
                            doc != null ? doc.getImmutableCharSequence() : workingContent;

                    TextMergeRequestImpl req = new TextMergeRequestImpl(
                            project,
                            output,
                            originalContent,
                            contents,
                            title,
                            contentTitles
                    );*/

                    DiffRequest req1 = new SimpleDiffRequest(
                            title,
                            output,
                            rightContent,
                            leftTitle,
                            rightTitle
                    );

                    // UI线程显示diff窗口
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                                //DiffManager.getInstance().showMerge(project, req);
                                DiffManager.getInstance().showDiff(project, req1, DiffDialogHints.DEFAULT);
                            }
                    );
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    /**
     * 创建空文件
     */
    private VirtualFile createEmptyFile(Project project, String filePath) {
        try {
            VirtualFile baseDir = ProjectRootManager.getInstance(project).getContentRoots()[0];
            String[] pathParts = filePath.split("/");
            AtomicReference<VirtualFile> currentDir = new AtomicReference<>(baseDir);

            // 递归创建目录
            for (int i = 0; i < pathParts.length - 1; i++) {
                final VirtualFile dirToCheck = currentDir.get();
                final String dirName = pathParts[i];

                // 在 EDT 线程中检查并创建目录
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(() -> {
                    VirtualFile childDir = dirToCheck.findChild(dirName);
                    if (childDir == null) {
                        try {
                            childDir = com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(
                                    (Computable<VirtualFile>) () -> {
                                        try {
                                            return dirToCheck.createChildDirectory(this, dirName);
                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                            );
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                    currentDir.set(childDir); // 更新 currentDir
                });
            }


            // 创建文件
            AtomicReference<VirtualFile> resultFile = new AtomicReference<>(baseDir);
            final VirtualFile parentDir = currentDir.get();
            final String fileName = pathParts[pathParts.length - 1];
            // 使用 invokeLater 确保在 EDT 线程中执行写操作
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(() -> {
                        VirtualFile res = com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(
                                (Computable<VirtualFile>) () -> {
                                    try {
                                        return parentDir.createChildData(this, fileName);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                        );
                        resultFile.set(res);
                    }
            );
            return resultFile.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * 伪代码示例：用git命令行获取多个commit修改的文件列表
     */
    private Set<ModifiedFileEntry> getFilesModifiedByCommits(Project project, GitRepository repo, Collection<CommitId> commits) {
        Set<ModifiedFileEntry> files = new LinkedHashSet<>();
        try {
            for (CommitId commit : commits) {
                String hash = commit.getHash().asString();
                String cmd = "git diff-tree --no-commit-id --name-only -r " + hash;
                Process process = Runtime.getRuntime().exec(cmd, null, new java.io.File(commit.getRoot().getPath()));
                try (Scanner scanner = new Scanner(process.getInputStream())) {
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        String filePath = line.trim();
                        if (!filePath.isEmpty()) {
                            files.add(new ModifiedFileEntry(filePath, commit));
                        }
                    }
                }
                process.waitFor();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return files;
    }


    /**
     * 获取指定分支在某个提交时的文件内容
     */
    private String loadFileContentAtBranch(Project project, GitRepository repo, VirtualFile file, String branchName) {
        try {
            String filePath = file.getPath().substring(repo.getRoot().getPath().length() + 1);
            String cmd = "git show " + branchName + ":" + filePath;
            Process process = Runtime.getRuntime().exec(cmd, null, new java.io.File(repo.getRoot().getPath()));
            try (InputStream inputStream = process.getInputStream()) {
                byte[] contentBytes = inputStream.readAllBytes();
                process.waitFor();
                return decodeFileContent(contentBytes, file.getCharset());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
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
            ).directory(new java.io.File(repo.getRoot().getPath())).start();
            try (Scanner scanner = new Scanner(process.getInputStream(), StandardCharsets.UTF_8)) {
                while (scanner.hasNextLine()) {
                    String branchName = scanner.nextLine().trim();
                    if (!branchName.isEmpty()) {
                        branches.add(branchName);
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return branches;
    }

    private String decodeFileContent(byte[] contentBytes, Charset charset) {
        Charset targetCharset = charset != null ? charset : StandardCharsets.UTF_8;
        String content = new String(contentBytes, targetCharset);
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            return content.substring(1);
        }
        return content;
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
            @NotNull List<CommitId> commits = log.getSelectedCommits();
            enabled = commits != null && !commits.isEmpty();
        }
        e.getPresentation().setEnabledAndVisible(enabled);
    }


}
