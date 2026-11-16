(function () {
  try {
    var stored = JSON.parse(localStorage.getItem('pretupid-settings') || '{}');
    var root = document.documentElement;
    root.dataset.theme = stored.theme === 'light' ? 'light' : 'dark';
    if (typeof stored.accentColor === 'string' && /^#[0-9a-fA-F]{6}$/.test(stored.accentColor)) {
      root.style.setProperty('--boot-accent', stored.accentColor);
    }
  } catch (error) {
    document.documentElement.dataset.theme = 'dark';
  }
})();
