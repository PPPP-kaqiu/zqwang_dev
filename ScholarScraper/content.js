// Google Scholar Content Script
console.log("[Scholar Scraper] Content Script Loaded");

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    console.log("[Scholar Scraper] Received message:", request.action);
    
    if (request.action === "scrape_scholar") {
        try {
            // Check for CAPTCHA
            if (document.querySelector('form[id="captcha-form"]') || 
                document.body.innerText.includes("Please show you're not a robot") ||
                document.body.innerText.includes("系统检测到您的计算机网络中存在异常流量")) {
                console.warn("[Scholar Scraper] CAPTCHA detected.");
                sendResponse({ success: false, error: "CAPTCHA", isProfile: false });
                return true;
            }

            // Improved profile detection
            const isProfile = window.location.href.includes("citations") && window.location.href.includes("user=");
            
            // Check if it's a 404 or profile not found
            if (document.body.innerText.includes("404 Not Found") || 
                document.body.innerText.includes("The user has not created a public profile") ||
                document.title.includes("Error")) {
                sendResponse({ success: false, error: "PROFILE_NOT_FOUND", isProfile });
                return true;
            }

            // Always try to scrape summary if it looks like a profile, or even if not (as fallback)
            let results = isProfile ? scrapeProfile() : scrapePage();
            let summary = null;
            
            // Try to scrape summary if we are on a profile page OR if we find the stats table
            if (isProfile || document.querySelector('.gsc_rsb_std')) {
                summary = scrapeProfileSummary();
            }
            
            // If we expected a summary (isProfile) but didn't get one, return a specific error
            if (isProfile && (!summary || summary.citations === "-")) {
                 console.warn("[Scholar Scraper] Profile detected but failed to scrape summary.");
                 sendResponse({ success: false, error: "NO_STATS_FOUND", isProfile, summary });
                 return true;
            }
            
            console.log("[Scholar Scraper] Scrape completed. Summary:", summary);
            sendResponse({ success: true, data: results, isProfile, summary });
        } catch (error) {
            console.error("[Scholar Scraper] Scrape Error:", error);
            sendResponse({ success: false, error: error.message });
        }
        return true;
    }

    if (request.action === "load_more_papers") {
        const moreButton = document.getElementById('gsc_bpf_more');
        if (moreButton && !moreButton.disabled && moreButton.style.display !== 'none') {
            console.log("[Scholar Scraper] Clicking 'Show more'...");
            moreButton.click();
            sendResponse({ success: true, clicked: true });
        } else {
            console.log("[Scholar Scraper] 'Show more' button not found or disabled.");
            sendResponse({ success: true, clicked: false });
        }
        return true;
    }

    if (request.action === "load_all_profile") {
        loadAllProfilePapers().then(success => {
            sendResponse({ success });
        }).catch(err => {
            sendResponse({ success: false, error: err.message });
        });
        return true;
    }
});

function scrapePage() {
    const papers = [];
    const elements = document.querySelectorAll('.gs_r.gs_or.gs_scl');
    elements.forEach((el) => {
        const paper = {};
        const titleEl = el.querySelector('.gs_rt a');
        if (titleEl) {
            paper.title = titleEl.innerText;
            paper.url = titleEl.href;
        } else {
            const titleOnly = el.querySelector('.gs_rt');
            paper.title = titleOnly ? titleOnly.innerText.replace(/\[[A-Z]+\]\s*/, '') : "Unknown Title";
            paper.url = "";
        }
        const metaEl = el.querySelector('.gs_a');
        if (metaEl) {
            paper.meta = metaEl.innerText;
            const parts = metaEl.innerText.split(' - ');
            paper.authors = parts[0] || "";
            paper.publication = parts[1] || "";
        }
        const links = el.querySelectorAll('.gs_fl a');
        paper.citations = 0;
        links.forEach(link => {
            const text = link.innerText;
            const match = text.match(/(Cited by|被引用次数：)\s*(\d+)/);
            if (match) paper.citations = parseInt(match[2], 10);
        });
        papers.push(paper);
    });
    return papers;
}

async function loadAllProfilePapers() {
    let moreButton = document.getElementById('gsc_bpf_more');
    while (moreButton && !moreButton.disabled && moreButton.style.display !== 'none') {
        moreButton.click();
        await new Promise(r => setTimeout(r, 1500));
        moreButton = document.getElementById('gsc_bpf_more');
    }
    return true;
}

function scrapeProfile() {
    const papers = [];
    const rows = document.querySelectorAll('.gsc_a_tr');
    rows.forEach((row) => {
        const paper = {};
        const titleEl = row.querySelector('.gsc_a_at');
        if (titleEl) {
            paper.title = titleEl.innerText;
            let href = titleEl.getAttribute('href') || titleEl.getAttribute('data-href');
            if (href && !href.startsWith('http')) href = window.location.origin + href;
            paper.url = href || "";
        } else {
            paper.title = "Unknown Title";
            paper.url = "";
        }
        const grayEls = row.querySelectorAll('.gs_gray');
        if (grayEls.length >= 1) paper.authors = grayEls[0].innerText;
        if (grayEls.length >= 2) paper.publication = grayEls[1].innerText;
        const citationEl = row.querySelector('.gsc_a_ac');
        paper.citations = citationEl ? parseInt(citationEl.innerText) || 0 : 0;
        const yearEl = row.querySelector('.gsc_a_y');
        paper.year = yearEl ? yearEl.innerText : "";
        papers.push(paper);
    });
    return papers;
}

function scrapeProfileSummary() {
    console.log("[Scholar Scraper] Attempting to scrape summary stats...");
    let summary = { citations: "-", hIndex: "-", i10Index: "-", lastUpdate: new Date().toLocaleDateString() };
    
    // 1. 位置定位 (Standard Table)
    const stds = document.querySelectorAll('.gsc_rsb_std');
    if (stds.length >= 2) {
        summary.citations = stds[0].innerText.trim();
        summary.hIndex = stds[2] ? stds[2].innerText.trim() : "-";
        summary.i10Index = stds[4] ? stds[4].innerText.trim() : "-";
        console.log("[Scholar Scraper] Found summary via .gsc_rsb_std:", summary);
        return summary;
    }

    // 2. 文本兜底 (Regex scan)
    const bodyText = document.body.innerText;
    const citeMatch = bodyText.match(/(Citations|引用次数|被引用次数|引用|Cited by)[\s：:]*([0-9,]+)/i);
    if (citeMatch) {
        summary.citations = citeMatch[2].replace(/,/g, '');
        const hMatch = bodyText.match(/h-index[\s：:]*(\d+)/i) || bodyText.match(/h\s*指数[\s：:]*(\d+)/i);
        if (hMatch) summary.hIndex = hMatch[1];
        console.log("[Scholar Scraper] Found summary via regex scan:", summary);
        return summary;
    }

    // 3. 深度 DOM 遍历 (Deep Search) - 针对 DOM 结构变化的情况
    try {
        const allCells = document.querySelectorAll('td, div');
        for (let cell of allCells) {
            if (/^\d+$/.test(cell.innerText.trim())) { // 如果是纯数字
                // 检查其兄弟节点或前一个节点是否包含 "Citations"
                let sibling = cell.previousElementSibling;
                if (sibling && /(Citations|引用|Cited by)/i.test(sibling.innerText)) {
                     summary.citations = cell.innerText.trim();
                     console.log("[Scholar Scraper] Found citations via deep DOM search:", summary.citations);
                     // 尝试找 h-index (通常在 citations 下面两行)
                     let parentRow = cell.parentElement;
                     if (parentRow) {
                         let nextRow = parentRow.nextElementSibling; // h-index row?
                         if (nextRow && nextRow.nextElementSibling) { // i10-index row?
                             let hIndexRow = nextRow; 
                             // 有时候中间会有间隔，简单粗暴往下找数字
                             let hCell = hIndexRow.querySelector('td:nth-child(2), .gsc_rsb_std');
                             if (hCell) summary.hIndex = hCell.innerText.trim();
                         }
                     }
                     break;
                }
            }
        }
    } catch(e) { console.error(e); }

    if (summary.citations !== "-") return summary;

    console.warn("[Scholar Scraper] Could not find any summary stats on this page.");
    return null;
}
