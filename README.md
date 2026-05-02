🧩 一个真实到不能再真实的场景：IDEA 里人工合并代码如果你用的是 IntelliJ IDEA（或其它 JetBrains IDE），并且经常需要人工合并代码，那你大概率经历过下面这个流程👇🧠 实际工作流程是这样的：在 Git Log 窗口里

🔍 通过关键字 / 作者 / 时间范围搜索出一批提交为了搞清楚这些提交都改了什么

👉 右键选择 Show Diff with Working TreeIDEA 会把所有有差异的文件一次性列出来

你开始人工分析：哪些文件需要合并哪些可以忽略哪些存在冲突风险接下来是最痛苦的部分 😵

你想逐个文件在独立 Tab 里对比代码，于是你只能：在 Diff 列表里找到一个文件右键 👉 Show Diff in a New Tab再回到 Git Log再找这个提交到底改了哪个文件重复 N 次……👉 每一个文件都要点一遍，每一次都要在 Log 和 Diff 之间来回跳。💡 GitDiffBatch：就是为了解决这个问题而生的GitDiffBatch 插件的目标非常明确：让“一批提交 / 一批文件”的 diff，可以“一次性在独立 Tab 中全部打开”。🎯 它解决的正是这个核心需求：能不能直接从 Git Log 里，把这批提交涉及的所有文件：

👉 批量 Show Diff in a New Tab？答案是：可以。🚀 使用 GitDiffBatch 后，流程变成这样✅ 新的、更合理的流程：在 Git Log 中

🔍 搜索出你要人工合并的一批提交选中这些提交

👉 使用 GitDiffBatch 提供的批量 Diff 操作所有相关文件的 Diff：自动打开每个文件一个独立 Tab顺序清晰、可连续浏览你只需要做一件事：

👉 专心看代码，决定怎么合并✨ 这个优化对“人工合并”意味着什么？

🧠 1. 连续思考，不再被打断你可以像翻书一样：一个 Tab 看完Ctrl + Tab 到下一个大脑始终聚焦在“这批改动整体在干嘛”⚡ 

2. 合并效率直线上升不再反复点右键不再在 Log 和 Diff 之间来回跳对十几个、几十个文件的改动尤其明显🛡️
   
3. 更安全的代码合并人工合并最怕“漏看”：GitDiffBatch 把所有相关 diff 摆在你面前更容易发现隐藏改动、潜在冲突
  
 🧩 总结：这是一个“IDE 本来就该有，但没有”的能力如果你经常遇到以下场景：在 IDEA 的 Git Log 里筛选出一批提交需要人工合并 / 人工审查想把所有相关文件的 Diff 一次性打开到独立 Tab那 GitDiffBatch 几乎是“刚需级插件”。🚀 如何获取插件？在 JetBrains 插件市场搜索并安装 GitDiffBatch
 
 🔍 使用方法
 
1. 选中需要对比分支的提交记录（支持多选）

2. 右键菜单中点击“Batch Show Diff”

3. 选中的差异文件会全部展示在tab中（差异可以Review合入当前分支）

[<img width="720" height="240" alt="image" src="https://github.com/user-attachments/assets/f4abd1c3-2b9e-4aa3-bab6-a11f663ce6a9" />](https://linux.do/)
