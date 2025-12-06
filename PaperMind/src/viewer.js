// Global variables
let pdfDoc = null;
let currentFileId = null;
let currentScale = 1.2;
let currentSelection = null; // { text, page, rect, normRect }
let currentNotes = []; // Store notes globally for rendering highlights
let pageTextCache = {}; // Cache for page text content
let pageObserver = null; // IntersectionObserver instance
let pageRenderStatus = {}; // Track rendering status of pages: 'pending', 'rendering', 'rendered'

// Initialize PDF.js worker
pdfjsLib.GlobalWorkerOptions.workerSrc = '../lib/pdf.worker.js';

document.addEventListener('DOMContentLoaded', async () => {
    initEventListeners();
    checkUrlParams();
    await loadSettings();
    initIntersectionObserver();
    
    // Auto-restore last opened file if no URL param
    if (!new URLSearchParams(window.location.search).get('file')) {
        await restoreLastOpenedFile();
    }
});

async function restoreLastOpenedFile() {
    const lastFileId = await Storage.getLastOpenedFileId();
    if (lastFileId) {
        showStatus("正在恢复上次打开的文件...");
        try {
            const fileData = await Storage.getFile(lastFileId);
            if (fileData) {
                currentFileId = lastFileId;
                await loadPDF(fileData);
                await loadNotes(currentFileId);
                hideStatus();
            } else {
                hideStatus();
                console.log("No file data found for last opened file ID");
            }
        } catch (e) {
            console.error("Failed to restore file", e);
            hideStatus();
        }
    }
}

function initIntersectionObserver() {
    const options = {
        root: document.getElementById('pdf-viewer'),
        rootMargin: '200px', // Preload 200px before viewport
        threshold: 0.1
    };

    pageObserver = new IntersectionObserver((entries, observer) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const pageNum = parseInt(entry.target.dataset.pageNumber);
                if (pageRenderStatus[pageNum] !== 'rendered' && pageRenderStatus[pageNum] !== 'rendering') {
                    renderPage(pageNum, entry.target);
                }
            }
        });
    }, options);
}

function initEventListeners() {
    // File Input
    document.getElementById('open-file-btn').addEventListener('click', () => {
        document.getElementById('file-input').click();
    });

    document.getElementById('file-input').addEventListener('change', handleFileSelect);

    // URL Modal
    document.getElementById('open-url-btn').addEventListener('click', () => {
        document.getElementById('url-modal').classList.remove('hidden');
        document.getElementById('url-input').focus();
    });

    document.getElementById('btn-cancel-url').addEventListener('click', () => {
        document.getElementById('url-modal').classList.add('hidden');
    });

    document.getElementById('btn-confirm-url').addEventListener('click', () => {
        const url = document.getElementById('url-input').value.trim();
        if (url) {
            document.getElementById('url-modal').classList.add('hidden');
            showStatus("正在下载网络 PDF...");
            loadOnlinePDF(url);
        }
    });

    // Settings Modal
    document.getElementById('settings-btn').addEventListener('click', async () => {
        const settings = await Storage.getSettings();
        if (settings.openai_key) document.getElementById('settings-key').value = settings.openai_key;
        if (settings.model) document.getElementById('settings-model').value = settings.model;
        
        document.getElementById('settings-modal').classList.remove('hidden');
    });

    document.getElementById('btn-cancel-settings').addEventListener('click', () => {
        document.getElementById('settings-modal').classList.add('hidden');
    });

    document.getElementById('btn-save-settings').addEventListener('click', async () => {
        const key = document.getElementById('settings-key').value.trim();
        const model = document.getElementById('settings-model').value.trim();
        
        await Storage.saveSettings({
            openai_key: key,
            model: model
        });
        
        document.getElementById('settings-modal').classList.add('hidden');
        alert('设置已保存！');
    });

    // Zoom Controls
    document.getElementById('zoom-in-btn').addEventListener('click', () => changeZoom(0.2));
    document.getElementById('zoom-out-btn').addEventListener('click', () => changeZoom(-0.2));

    // Floating Menu
    document.getElementById('btn-add-note').addEventListener('click', handleAddNote);
    document.getElementById('btn-ai-explain').addEventListener('click', handleAIExplain);

    // Generate Report
    document.getElementById('btn-generate-report').addEventListener('click', handleGenerateReport);

    // Selection listener
    document.addEventListener('mouseup', handleSelection);
    
    // Hide floating menu on click elsewhere
    document.addEventListener('mousedown', (e) => {
        if (!e.target.closest('#floating-menu')) {
            hideFloatingMenu();
        }
    });

    // Note actions delegation & Jump to page
    document.getElementById('notes-list').addEventListener('click', async (e) => {
        // Handle jump click
        const clickable = e.target.closest('.note-content-clickable');
        if (clickable && !e.target.closest('button')) {
            const page = parseInt(clickable.dataset.page);
            if (page) {
                // Scroll to page
                const pageEl = document.getElementById(`page-${page}`);
                if (pageEl) {
                    pageEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    // Visual feedback
                    pageEl.style.outline = '4px solid rgba(255, 235, 59, 0.5)';
                    setTimeout(() => pageEl.style.outline = 'none', 2000);
                }
            }
            return;
        }

        const btn = e.target.closest('button');
        if (!btn) return;
        
        const noteId = btn.dataset.id;
        
        if (btn.classList.contains('delete-btn')) {
            if (!noteId) return;
            if (confirm('确定删除这条笔记吗？')) {
                await Storage.deleteNote(currentFileId, noteId);
                loadNotes(currentFileId);
            }
        } else if (btn.classList.contains('ai-btn')) {
            if (!noteId) return;
            // Find note content
            const noteCard = btn.closest('.note-card');
            const text = noteCard.querySelector('.note-highlight').innerText;
            
            // Use dataset for robust page extraction (immune to CSS text-transform)
            const clickable = noteCard.querySelector('.note-content-clickable');
            const pageNum = parseInt(clickable.dataset.page);
            
            if (isNaN(pageNum)) {
                 console.error("Failed to parse page number from note card");
                 return;
            }
            
            handleAIExplainFromNote(text, pageNum, noteId);
        }
    });

    // Resizable Sidebar
    const sidebar = document.getElementById('sidebar');
    const resizeHandle = document.getElementById('resize-handle');
    let isResizing = false;

    if (resizeHandle) {
        resizeHandle.addEventListener('mousedown', (e) => {
            isResizing = true;
            document.body.style.cursor = 'col-resize';
            document.body.style.userSelect = 'none';
            // Add overlay to prevent iframe interference if any, and smooth dragging
            const overlay = document.createElement('div');
            overlay.id = 'resize-overlay';
            overlay.style.position = 'fixed';
            overlay.style.top = 0;
            overlay.style.left = 0;
            overlay.style.width = '100%';
            overlay.style.height = '100%';
            overlay.style.zIndex = '9999';
            overlay.style.cursor = 'col-resize';
            document.body.appendChild(overlay);
        });

        document.addEventListener('mousemove', (e) => {
            if (!isResizing) return;
            
            // Throttle using requestAnimationFrame
            requestAnimationFrame(() => {
                let newWidth = e.clientX;
                if (newWidth < 250) newWidth = 250;
                if (newWidth > 600) newWidth = 600;
                sidebar.style.width = newWidth + 'px';
            });
        });

        document.addEventListener('mouseup', () => {
            if (isResizing) {
                isResizing = false;
                document.body.style.cursor = 'default';
                document.body.style.userSelect = '';
                const overlay = document.getElementById('resize-overlay');
                if (overlay) overlay.remove();
            }
        });
    }
}

function changeZoom(delta) {
    if (!pdfDoc) return;
    const newScale = currentScale + delta;
    if (newScale >= 0.5 && newScale <= 4.0) {
        currentScale = newScale;
        updateZoomDisplay();
        
        // Reset render status to force re-render
        pageRenderStatus = {};
        renderAllPages();
    }
}

function updateZoomDisplay() {
    document.getElementById('zoom-level').innerText = Math.round(currentScale * 100) + '%';
}

function checkUrlParams() {
    const urlParams = new URLSearchParams(window.location.search);
    const pdfUrl = urlParams.get('file');

    if (pdfUrl) {
        showStatus("正在下载网络 PDF...");
        loadOnlinePDF(pdfUrl);
    }
}

async function loadSettings() {
    await Storage.getSettings();
}

async function handleFileSelect(e) {
    const file = e.target.files[0];
    if (!file) return;

    showStatus("正在打开文件...");
    try {
        const arrayBuffer = await file.arrayBuffer();
        currentFileId = SparkMD5.ArrayBuffer.hash(arrayBuffer);
        
        // Save to IndexedDB
        await Storage.saveFile(currentFileId, arrayBuffer);
        await Storage.setLastOpenedFile(currentFileId);

        // Clear caches
        pageTextCache = {};
        pageRenderStatus = {};
        
        await loadPDF(arrayBuffer);
        await loadNotes(currentFileId);
        hideStatus();
    } catch (err) {
        console.error(err);
        showStatus("文件打开失败");
    }
}

async function loadOnlinePDF(url) {
    chrome.runtime.sendMessage({ action: 'fetch_pdf', url: url }, (response) => {
        if (response && response.success) {
            currentFileId = SparkMD5.hash(response.data); 
            
            // Save to IndexedDB (in async way, don't block render)
            Storage.saveFile(currentFileId, response.data).then(() => {
                return Storage.setLastOpenedFile(currentFileId);
            });

            // Clear caches
            pageTextCache = {};
            pageRenderStatus = {};

            loadPDF(response.data).then(() => {
                loadNotes(currentFileId);
                hideStatus();
            });
        } else {
            showStatus('PDF 下载失败: ' + (response ? response.error : 'Unknown error'));
        }
    });
}

async function loadPDF(data) {
    try {
        const loadingTask = pdfjsLib.getDocument(data);
        pdfDoc = await loadingTask.promise;
        renderAllPages();
    } catch (e) {
        console.error(e);
        showStatus("PDF 加载失败");
    }
}

async function renderAllPages() {
    const viewer = document.getElementById('pdf-viewer');
    viewer.innerHTML = ''; 
    pageRenderStatus = {}; // Reset render status

    // Disconnect existing observer to avoid memory leaks or duplicate observations
    if (pageObserver) {
        pageObserver.disconnect();
    }
    // Re-initialize observer (it was disconnected)
    initIntersectionObserver();

    // Use the first page to get a rough estimate of size if possible, 
    // but better to get each page's viewport size.
    // Optimization: For large docs, we might want to assume uniform size, 
    // but for now, let's just get basic viewport info.
    
    // We will create placeholders for all pages.
    for (let pageNum = 1; pageNum <= pdfDoc.numPages; pageNum++) {
        const pageContainer = document.createElement('div');
        pageContainer.className = 'pdf-page';
        pageContainer.id = `page-${pageNum}`;
        pageContainer.dataset.pageNumber = pageNum;
        
        // Initial simplified sizing (will be corrected when rendered if different)
        // Ideally we would fetch dimensions here, but awaiting getPage for all 
        // might be slow. Let's try to fetch dimensions for the first page
        // and apply to all, then update on demand.
        // Or, we can just let them be empty and observer will catch them 
        // (but scrollbar might jump).
        // Best practice: Fetch all page sizes in background or parallel?
        // Let's do lazy fetching of dimensions too if we accept jumpy scrollbar,
        // OR fetch all dimensions now. Fetching dimensions is fast.
        
        viewer.appendChild(pageContainer);
        pageObserver.observe(pageContainer);
    }

    // Optimization: Fetch first page dimensions to set a default size for smoother UX
    try {
        const page1 = await pdfDoc.getPage(1);
        const viewport = page1.getViewport({ scale: currentScale });
        const width = Math.floor(viewport.width);
        const height = Math.floor(viewport.height);
        
        // Apply default size to all containers to stabilize scrollbar
        document.querySelectorAll('.pdf-page').forEach(el => {
            if (!el.style.width) {
                el.style.width = width + 'px';
                el.style.height = height + 'px';
            }
        });
    } catch (e) {
        console.warn("Could not fetch page 1 dimensions", e);
    }
}

async function renderPage(pageNum, container) {
    if (pageRenderStatus[pageNum] === 'rendering' || pageRenderStatus[pageNum] === 'rendered') return;
    
    pageRenderStatus[pageNum] = 'rendering';
    
    try {
        const page = await pdfDoc.getPage(pageNum);
        const viewport = page.getViewport({ scale: currentScale });

        const outputScale = window.devicePixelRatio || 1;

        const canvas = document.createElement('canvas');
        const context = canvas.getContext('2d');
        
        canvas.width = Math.floor(viewport.width * outputScale);
        canvas.height = Math.floor(viewport.height * outputScale);
        
        canvas.style.width = Math.floor(viewport.width) + "px";
        canvas.style.height = Math.floor(viewport.height) + "px";

        // Update container size to match actual page size
        container.style.width = Math.floor(viewport.width) + "px";
        container.style.height = Math.floor(viewport.height) + "px";

        container.innerHTML = ''; // Clear any placeholders
        container.appendChild(canvas);

        const transform = outputScale !== 1
            ? [outputScale, 0, 0, outputScale, 0, 0]
            : null;

        await page.render({
            canvasContext: context,
            transform: transform,
            viewport: viewport
        }).promise;

        const textLayerDiv = document.createElement('div');
        textLayerDiv.className = 'textLayer';
        textLayerDiv.style.width = Math.floor(viewport.width) + 'px';
        textLayerDiv.style.height = Math.floor(viewport.height) + 'px';
        textLayerDiv.style.setProperty('--scale-factor', currentScale);
        
        container.appendChild(textLayerDiv);

        const textContent = await page.getTextContent();
        
        // Cache text content for AI usage
        pageTextCache[pageNum] = textContent.items.map(token => token.str).join(" ");

        pdfjsLib.renderTextLayer({
            textContent: textContent,
            container: textLayerDiv,
            viewport: viewport,
            textDivs: []
        });

        // Render highlights for this page
        renderHighlightsForPage(pageNum, container);
        
        pageRenderStatus[pageNum] = 'rendered';
    } catch (e) {
        console.error(`Error rendering page ${pageNum}:`, e);
        pageRenderStatus[pageNum] = 'error';
        container.innerHTML = `<div style="padding:20px; color:red;">Page error</div>`;
    }
}

function handleSelection() {
    const selection = window.getSelection();
    if (selection.isCollapsed) return;

    const range = selection.getRangeAt(0);
    const rect = range.getBoundingClientRect();
    
    const pageElement = selection.anchorNode.parentElement.closest('.pdf-page');
    if (!pageElement) return;

    const pageNum = parseInt(pageElement.id.split('-')[1]);
    const text = selection.toString();

    // Calculate normalized coordinates (relative to unscaled page)
    const pageRect = pageElement.getBoundingClientRect();
    
    // Calculate relative coordinates and clip to page boundaries
    let relX = rect.left - pageRect.left;
    let relY = rect.top - pageRect.top;
    let relW = rect.width;
    let relH = rect.height;

    // Clip left/top
    if (relX < 0) { relW += relX; relX = 0; }
    if (relY < 0) { relH += relY; relY = 0; }
    
    // Clip right/bottom
    if (relX + relW > pageRect.width) relW = pageRect.width - relX;
    if (relY + relH > pageRect.height) relH = pageRect.height - relY;

    // Ensure we don't have negative width/height after clipping
    if (relW < 0) relW = 0;
    if (relH < 0) relH = 0;

    const normRect = {
        x: relX / currentScale,
        y: relY / currentScale,
        w: relW / currentScale,
        h: relH / currentScale
    };

    if (text.trim().length > 0) {
        currentSelection = {
            text: text,
            page: pageNum,
            rect: rect,
            normRect: normRect
        };
        showFloatingMenu(rect);
    }
}

function showFloatingMenu(rect) {
    const menu = document.getElementById('floating-menu');
    const scrollTop = window.scrollY || document.documentElement.scrollTop;
    const scrollLeft = window.scrollX || document.documentElement.scrollLeft;

    menu.style.top = (rect.top + scrollTop - 40) + 'px'; 
    menu.style.left = (rect.left + scrollLeft) + 'px';
    menu.classList.remove('hidden');
}

function hideFloatingMenu() {
    document.getElementById('floating-menu').classList.add('hidden');
}

async function handleAddNote() {
    if (!currentSelection || !currentFileId) return;

    const note = {
        text: currentSelection.text,
        page: currentSelection.page,
        type: 'highlight',
        rect: currentSelection.normRect
    };

    await Storage.addNote(currentFileId, note);
    hideFloatingMenu();
    loadNotes(currentFileId); 
}

async function handleAIExplain() {
    if (!currentSelection || !currentFileId) return;
    
    const sel = currentSelection; // Capture current selection
    hideFloatingMenu();
    
    const list = document.getElementById('notes-list');
    
    // Create UI Card Immediately
    const noteId = Storage.generateId();
    const note = {
        id: noteId,
        text: sel.text,
        page: sel.page,
        type: 'ai_explanation',
        explanation: '正在分析...',
        rect: sel.normRect,
        timestamp: Date.now()
    };
    
    const card = createAICard(note);
    // Mark as streaming/loading visually
    card.querySelector('.note-text').style.opacity = '0.7';
    
    // Prepend to list
    if (list.querySelector('.empty-state')) {
        list.innerHTML = '';
        list.appendChild(card);
    } else {
        list.prepend(card);
    }

    // Scroll to top
    list.scrollTop = 0;

    const textContainer = card.querySelector('.note-text');

    const onChunk = (chunk, fullText) => {
         note.explanation = fullText;

         // Debounce/Throttle markdown parsing slightly if needed, 
         // but simple text updates are usually fine.
         if (typeof marked !== 'undefined') {
            textContainer.innerHTML = marked.parse(fullText);
        } else {
            textContainer.innerText = fullText;
        }
        textContainer.style.opacity = '1';
    };

    try {
        const context = await getTextUntilPage(sel.page);
        const explanation = await AIService.getExplanation(context, sel.text, onChunk);
        
        // Update final content
        note.explanation = explanation;
        
        // Save to storage
        await Storage.addNote(currentFileId, note);
        
        // Refresh highlights (so the new AI highlight appears)
        // We can do this without reloading all notes:
        currentNotes.push(note);
        // Only render highlight if the page is currently rendered
        const pageEl = document.getElementById(`page-${sel.page}`);
        if (pageEl && pageRenderStatus[sel.page] === 'rendered') {
            renderHighlightsForPage(sel.page, pageEl);
        }
        
    } catch (e) {
        textContainer.innerHTML = `<span style="color:red">Error: ${e.message}</span>`;
    }
}

async function handleAIExplainFromNote(text, page, noteId) {
    const list = document.getElementById('notes-list');
    
    const newNoteId = Storage.generateId();
    const note = {
        id: newNoteId,
        text: text,
        page: page,
        type: 'ai_explanation',
        explanation: '正在分析...',
        relatedNoteId: noteId,
        timestamp: Date.now()
    };
    
    const card = createAICard(note);
    card.querySelector('.note-text').style.opacity = '0.7';
    list.prepend(card);
    list.scrollTop = 0;

    const textContainer = card.querySelector('.note-text');

    const onChunk = (chunk, fullText) => {
         note.explanation = fullText;

         if (typeof marked !== 'undefined') {
            textContainer.innerHTML = marked.parse(fullText);
        } else {
            textContainer.innerText = fullText;
        }
        textContainer.style.opacity = '1';
    };

    try {
        const context = await getTextUntilPage(page);
        const explanation = await AIService.getExplanation(context, text, onChunk);
        
        note.explanation = explanation;
        await Storage.addNote(currentFileId, note);
        
    } catch (e) {
        textContainer.innerHTML = `<span style="color:red">Error: ${e.message}</span>`;
    }
}

async function handleGenerateReport() {
    if (!currentFileId) return;

    const notesData = await Storage.getNotes(currentFileId);
    if (notesData.records.length === 0) {
        alert("请先添加一些笔记或标记后再生成报告。");
        return;
    }

    const list = document.getElementById('notes-list');
    
    const maxPage = Math.max(...notesData.records.map(n => n.page));
    const noteId = Storage.generateId();
    
    const note = {
        id: noteId,
        text: "AI Reading Report",
        page: maxPage,
        type: 'report',
        explanation: '正在生成阅读报告，请稍候...',
        timestamp: Date.now()
    };

    const card = createAICard(note);
    card.querySelector('.note-text').style.opacity = '0.7';
    
    if (list.querySelector('.empty-state')) {
        list.innerHTML = '';
        list.appendChild(card);
    } else {
        list.prepend(card);
    }
    list.scrollTop = 0;

    const textContainer = card.querySelector('.note-text');

    const onChunk = (chunk, fullText) => {
         note.explanation = fullText;

         if (typeof marked !== 'undefined') {
            textContainer.innerHTML = marked.parse(fullText);
        } else {
            textContainer.innerText = fullText;
        }
        textContainer.style.opacity = '1';
    };

    try {
        const context = await getTextUntilPage(maxPage);
        const report = await AIService.generateReport(context, notesData.records, onChunk);

        note.explanation = report;
        await Storage.addNote(currentFileId, note);
        
    } catch (e) {
        textContainer.innerHTML = `<span style="color:red">Error: ${e.message}</span>`;
    }
}

async function getTextUntilPage(pageNum) {
    let text = "";
    const startPage = Math.max(1, pageNum - 10);
    
    for (let i = startPage; i <= pageNum; i++) {
        // Check cache first
        if (pageTextCache[i]) {
            text += pageTextCache[i] + "\n";
        } else {
            // If not cached, we need to load it (without rendering)
            try {
                const page = await pdfDoc.getPage(i);
                const tokenizedText = await page.getTextContent();
                const pageText = tokenizedText.items.map(token => token.str).join(" ");
                pageTextCache[i] = pageText; // Save to cache
                text += pageText + "\n";
            } catch (e) {
                console.warn(`Failed to retrieve text for page ${i}`, e);
            }
        }
    }
    return text;
}

async function loadNotes(fileId) {
    const data = await Storage.getNotes(fileId);
    currentNotes = data.records || []; // Update global notes

    const list = document.getElementById('notes-list');
    list.innerHTML = '';

    // Re-render highlights ONLY on rendered pages
    document.querySelectorAll('.pdf-page').forEach(pageEl => {
        const pageNum = parseInt(pageEl.dataset.pageNumber);
        if (pageRenderStatus[pageNum] === 'rendered') {
            renderHighlightsForPage(pageNum, pageEl);
        }
    });

    if (currentNotes.length === 0) {
        list.innerHTML = '<div class="empty-state">暂无笔记，选中文字添加</div>';
        return;
    }

    const sorted = currentNotes.sort((a, b) => b.timestamp - a.timestamp);

    sorted.forEach(note => {
        let card = null;
        if (note.type === 'highlight') {
            card = document.createElement('div');
            card.className = 'note-card';
            
            card.innerHTML = `
                <div class="note-content-clickable" data-page="${note.page}" data-text="${note.text.substring(0, 50)}" style="cursor:pointer;" title="点击跳转">
                    <div class="note-meta">Page ${note.page} • ${new Date(note.timestamp).toLocaleTimeString()}</div>
                    <div class="note-highlight">${note.text}</div>
                </div>
                <div class="note-actions">
                    <button class="ai-btn" data-id="${note.id}">✨ AI 解释</button>
                    <button class="delete-btn" data-id="${note.id}">🗑️ 删除</button>
                </div>
            `;
        } else if (note.type === 'ai_explanation' || note.type === 'report') {
            card = createAICard(note);
        }

        if (card) {
            list.appendChild(card);
        }
    });
}

function renderHighlightsForPage(pageNum, container) {
    // Clear existing highlights
    container.querySelectorAll('.highlight-rect').forEach(el => el.remove());

    currentNotes.forEach(note => {
        if (note.page === pageNum && note.rect) {
             const div = document.createElement('div');
             div.className = 'highlight-rect';
             div.style.left = (note.rect.x * currentScale) + 'px';
             div.style.top = (note.rect.y * currentScale) + 'px';
             div.style.width = (note.rect.w * currentScale) + 'px';
             div.style.height = (note.rect.h * currentScale) + 'px';
             div.style.backgroundColor = note.type === 'ai_explanation' ? 'rgba(0, 123, 255, 0.2)' : 'rgba(255, 235, 59, 0.4)'; // Blueish for AI, Yellow for Note
             div.style.position = 'absolute';
             div.style.zIndex = '1'; 
             div.style.pointerEvents = 'none';

             // Append
             const textLayer = container.querySelector('.textLayer');
             if (textLayer) {
                 container.insertBefore(div, textLayer);
             } else {
                 container.appendChild(div);
             }
        }
    });
}

function createAICard(note) {
    const card = document.createElement('div');
    card.className = 'note-card';
    
    let htmlContent = note.explanation;
    if (typeof marked !== 'undefined') {
        htmlContent = marked.parse(note.explanation);
    } else {
        htmlContent = note.explanation.replace(/\n/g, '<br>');
    }

    card.innerHTML = `
        <div class="note-meta">Page ${note.page} • ${note.type === 'report' ? 'Reading Report' : 'AI Explain'}</div>
        <div class="note-highlight">${note.text}</div>
        <div class="note-text markdown-body" style="margin-top: 8px; border-top: 1px solid #eee; padding-top: 8px;">
            ${htmlContent}
        </div>
        <div class="note-actions">
             <button class="copy-btn">📋 复制</button>
             <button class="delete-btn" data-id="${note.id}">🗑️ 删除</button>
        </div>
    `;
    
    const copyBtn = card.querySelector('.copy-btn');
    if (copyBtn) {
        copyBtn.addEventListener('click', () => {
            navigator.clipboard.writeText(note.explanation).then(() => {
                const originalText = copyBtn.innerText;
                copyBtn.innerText = '✅ 已复制';
                setTimeout(() => copyBtn.innerText = originalText, 2000);
            });
        });
    }

    return card;
}

function showStatus(msg) {
    const overlay = document.getElementById('loading-overlay');
    const text = document.getElementById('loading-text');
    if (overlay && text) {
        text.innerText = msg;
        overlay.classList.remove('hidden');
    }
    
    // Also update status bar as backup or for non-blocking info
    const bar = document.getElementById('status-bar');
    if (bar) {
        bar.innerText = msg;
        // bar.classList.remove('hidden'); // Optional: keep status bar hidden if overlay is up
    }
}

function hideStatus() {
    const overlay = document.getElementById('loading-overlay');
    if (overlay) {
        overlay.classList.add('hidden');
    }
    const bar = document.getElementById('status-bar');
    if (bar) {
        bar.classList.add('hidden');
    }
}
