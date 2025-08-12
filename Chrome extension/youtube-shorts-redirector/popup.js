const checkbox = document.getElementById("toggleRedirect");

chrome.storage.local.get(["enabled"], (result) => {
  checkbox.checked = result.enabled !== false; // default is enabled
});

checkbox.addEventListener("change", () => {
  chrome.storage.local.set({ enabled: checkbox.checked });
});
