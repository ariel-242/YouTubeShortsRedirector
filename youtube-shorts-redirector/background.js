chrome.tabs.onUpdated.addListener(async (tabId, changeInfo, tab) => {
  if (changeInfo.status !== "complete" || !tab.url) return;

  const isShorts = tab.url.startsWith("https://www.youtube.com/shorts/");
  if (!isShorts) return;

  chrome.storage.local.get(["enabled"], (result) => {
    if (result.enabled === false) return; // user turned it off

    const videoId = tab.url.split("/shorts/")[1].split(/[?&]/)[0];
    const redirectUrl = `https://www.youtube.com/watch?v=${videoId}`;
    chrome.tabs.update(tabId, { url: redirectUrl });
  });
});
