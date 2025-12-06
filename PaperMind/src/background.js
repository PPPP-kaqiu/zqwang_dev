chrome.action.onClicked.addListener((tab) => {
  chrome.tabs.create({ url: 'src/viewer.html' });
});

// Context Menu
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: "open-in-papermind",
    title: "📂 在 PaperMind 中阅读",
    contexts: ["link"],
    targetUrlPatterns: ["*://*/*.pdf"]
  });
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === "open-in-papermind") {
    const pdfUrl = info.linkUrl;
    const viewerUrl = chrome.runtime.getURL(`src/viewer.html?file=${encodeURIComponent(pdfUrl)}`);
    chrome.tabs.create({ url: viewerUrl });
  }
});

// Fetch PDF to bypass CORS
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === 'fetch_pdf') {
    fetch(request.url)
      .then(response => {
        if (!response.ok) throw new Error('Network response was not ok');
        return response.blob();
      })
      .then(blob => {
        const reader = new FileReader();
        reader.onloadend = () => {
          sendResponse({ success: true, data: reader.result });
        };
        reader.readAsDataURL(blob);
      })
      .catch(error => {
        console.error('Fetch error:', error);
        sendResponse({ success: false, error: error.toString() });
      });

    return true; // Keep channel open
  }
});

