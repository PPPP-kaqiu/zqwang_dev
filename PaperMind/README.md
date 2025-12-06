# PaperMind - 智能学术阅读助手

PaperMind 是一款专为学术科研打造的 Chrome 扩展程序，旨在提供沉浸式、智能化的 PDF 阅读体验。它完美融合了现代化的 PDF 阅读器与大语言模型（LLM）的深度理解能力，不仅仅是一个阅读工具，更是你的**私人科研笔记员**。

![Version](https://img.shields.io/badge/version-1.1.0-blue) ![Chrome](https://img.shields.io/badge/Chrome-Extension-googlechrome) ![DeepSeek](https://img.shields.io/badge/AI-DeepSeek%2FOpenAI-green)

## ✨ 核心亮点

### 🧠 深度科研伴读 (Research Partner)
PaperMind 的 AI 不仅仅是解释名词，它具备**意图识别**能力。当你高亮一段文字时，AI 会从两个维度进行解析：
*   **🤖 AI 深度解析**: 客观分析这段话在论文逻辑中的地位（是核心假设？实验证据？还是创新方法？），并解释其上下文关联。
*   **👤 标记动机推测**: 尝试站在你的角度，总结“为什么这段话值得被标记”，帮助你快速回忆起阅读时的灵感。

### 🚀 极速阅读体验 (Performance)
*   **懒加载技术 (Lazy Loading)**: 仅渲染当前可视页面的内容，即使打开数百页的综述或书籍也能瞬间加载，流畅滚动。
*   **状态持久化**: 刷新页面或重启浏览器后，自动恢复上次打开的 PDF 文件和阅读进度，无需重新导入。

### 🖍️ 沉浸式高亮与笔记
*   **可视化高亮**: 像在纸质书上一样，选中文本即可添加高亮。高亮区域会跟随缩放自适应，刷新页面依然保留。
*   **统一时间轴**: 所有的高亮笔记、AI 深度解读、阅读报告都整合在侧边栏的统一时间轴中，方便随时回顾。
*   **Markdown 支持**: AI 生成的解释支持 Markdown 格式，公式、代码块、列表清晰呈现。

### 📊 结构化阅读报告
读完一篇论文后，只需点击“生成 AI 阅读报告”。PaperMind 会生成一份连贯的**阅读总结**。它将首先给出全文的**核心摘要**，随后**严格按照你的阅读顺序**，逐条回顾你的笔记，并结合全文背景进行深度解析，帮你还原当时的阅读思路。

### 🌐 无缝的在线与本地体验
*   **本地 PDF**: 支持拖拽打开本地 PDF 文件，利用文件指纹（Hash）技术，即使文件移动或改名，笔记依然不丢。
*   **在线 PDF**: 突破 CORS 限制，支持直接在 arXiv 或其他学术网站链接上右键，选择“📂 在 PaperMind 中阅读”。

## 🎨 现代化 UI 设计
*   **双重视角卡片**: AI 解释卡片采用分栏设计，区分客观分析与主观理解。
*   **交互细节**: 支持侧边栏拖拽调整宽度、全屏 Loading 动画、平滑滚动跳转。
*   **夜间模式友好**: 精心调整的配色与阴影，长时间阅读不累眼。

---

## 🚀 快速开始

### 1. 安装扩展
由于本项目处于开发阶段，请通过“开发者模式”安装：
1.  下载本项目代码到本地。
2.  在 Chrome 地址栏输入 `chrome://extensions/`。
3.  打开右上角的 **"开发者模式"** 开关。
4.  点击左上角 **"加载已解压的扩展程序"**，选择本项目根目录（包含 `manifest.json` 的文件夹）。

### 2. 配置 AI 模型
首次使用需要配置 API Key，推荐使用 **DeepSeek** 以获得最佳的中文学术表现和性价比。
1.  打开 PaperMind 阅读器界面。
2.  点击侧边栏顶部的 **设置图标 (⚙️)**。
3.  填写配置：
    *   **API Key**: 输入你的 Key (如 `sk-...`)。
    *   **Model Name**: 推荐输入 `deepseek-chat` (也支持 `gpt-4o`, `gpt-3.5-turbo` 等)。
4.  点击保存。

### 3. 开始阅读
*   **本地文件**: 点击侧边栏的 **"📄"** 按钮选择文件。
*   **在线文件**: 点击 **"🌐"** 输入 URL，或在网页 PDF 链接上右键选择 **"PaperMind"**。

---

## 🛠️ 技术架构

本项目基于 **Chrome Extension Manifest V3** 构建，采用纯原生 JavaScript (ES6+) 开发，追求极致的性能与轻量化。

*   **Frontend**: HTML5, CSS3 (Flexbox/Grid), Native JS
*   **PDF Core**: Mozilla PDF.js (with Lazy Loading implementation)
*   **AI Service**: Fetch API (Streaming), Custom Prompt Engineering
*   **Storage**: 
    *   `IndexedDB`: 存储 PDF 二进制文件 (用于大文件持久化)
    *   `chrome.storage.local`: 存储笔记、设置与文件元数据
*   **Utils**: SparkMD5 (File Identity), Marked.js (Markdown Rendering)

## 📂 目录结构

```text
PaperMind/
├── manifest.json        # 扩展清单文件 (MV3)
├── assets/              # 样式与图标
│   └── styles.css       # 核心样式表 (包含 Markdown 渲染样式)
├── lib/                 # 第三方依赖
│   ├── pdf.js           # PDF 渲染引擎
│   ├── marked.min.js    # Markdown 解析
│   └── spark-md5.js     # 文件哈希计算
└── src/
    ├── background.js    # Service Worker (跨域代理, 右键菜单)
    ├── viewer.html      # 应用主入口
    ├── viewer.js        # 核心交互逻辑 (渲染, 懒加载, 笔记管理)
    ├── ai-service.js    # AI 服务 (Prompt 工程, 流式请求封装)
    └── settings.html    # 设置页面
    └── storage.js       # 数据持久化层 (IndexedDB + LocalStorage)
```

---
*Created with ❤️ for Researchers & Developers.*
