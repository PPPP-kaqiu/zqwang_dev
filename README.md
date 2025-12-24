# 软件开发合集

这里是我的软件开发项目合集。每个项目都位于其独立的子目录中。

## 目录

- [字节跳动简历自动填写助手 (AutoResume)](#字节跳动简历自动填写助手-autoresume)
- [Scholar Scraper - Google 学术助手](#scholar-scraper---google-学术助手)
- [FlashPick - 安卓自动化与数据采集](#flashpick---安卓自动化与数据采集)
- [Context Engine (ReadingMyself) - 个人量化与语音洞察系统](#context-engine-readingmyself---个人量化与语音洞察系统)
- [PaperMind - 智能学术阅读助手](#papermind---智能学术阅读助手)

---

## 字节跳动简历自动填写助手 (AutoResume)

**路径**: [`./AutoResume/`](./AutoResume/)

### 简介
这是一个 Chrome 浏览器扩展，采用了现代化的**侧边栏 (Side Panel)** 设计，旨在帮助你在字节跳动校园招聘网站上自动填写简历信息。

### 主要功能
- **侧边栏设计**：常驻显示，方便管理数据。
- **可视化表单**：直观的输入框界面，无需编辑 JSON。
- **智能匹配**：基于评分的算法，精准识别字段。
- **数据隐私**：数据仅保存在本地。

[查看详细文档](./AutoResume/README.md)

---

## Scholar Scraper - Google 学术助手

**路径**: [`./ScholarScraper/`](./ScholarScraper/)

### 简介
一个专为科研人员打造的 Chrome 插件，提供 Google Scholar 引用数据追踪、一作论文自动筛选及学术看板功能。

### 主要功能
- **学术看板**：Material Design 风格侧边栏，实时监控引用数与 h-index。
- **一作追踪**：自动筛选并展示第一作者论文列表。
- **无感刷新**：后台静默更新数据，支持自动巡检。
- **智能交互**：支持卡片式管理、快速跳转及详情展开。

[查看详细文档](./ScholarScraper/README.md)

---

## FlashPick - 安卓自动化与数据采集

**路径**: [`./FlashPick/`](./FlashPick/)

### 简介
FlashPick 是一款安卓自动化工具，集成了桌宠悬浮窗、屏幕录制、语音笔记等功能，用于高效收集和管理应用使用数据。
 
### 主要功能
- **哨兵服务**：自动监控白名单应用，智能启动录制。
- **桌宠悬浮窗**：便捷的手势操作，支持快速回溯录制。
- **语音笔记**：支持录制语音并自动关联视频片段。
- **本地存储**：视频与音频数据保存在本地，保护隐私。

[查看详细文档](./FlashPick/README.md)

---

## Context Engine (ReadingMyself) - 个人量化与语音洞察系统

**路径**: [`./ReadingMyself/`](./ReadingMyself/)

### 简介
一个完全基于 OSS 的语音洞察系统，包含 PC 端数据加工、FastAPI 网关与 Flutter 移动端。

### 主要组件
- **FastAPI 网关**：负责签发 OSS 临时凭证和提供洞察 API。
- **PC Context Engine**：集成 Qt 桌面端与 Python 脚本，处理音频转写（DashScope）、洞察生成与数据备份。
- **Flutter 移动端**：支持手机录音直传 OSS，并实时查看生成的 AI 洞察报告。

[查看详细文档](./ReadingMyself/README.md)

---

## PaperMind - 智能学术阅读助手

**路径**: [`./PaperMind/`](./PaperMind/)

### 简介
PaperMind 是一款专为学术科研打造的 Chrome 扩展程序，融合了 PDF 阅读器与大语言模型（LLM），提供沉浸式、智能化的阅读体验。

### 主要功能
- **深度科研伴读**：AI 解析论文逻辑和上下文关联。
- **极速阅读体验**：懒加载技术，瞬间加载大文件。
- **沉浸式高亮与笔记**：可视化高亮，统一时间轴管理笔记。
- **结构化阅读报告**：生成全文核心摘要和阅读回顾。

[查看详细文档](./PaperMind/README.md)
