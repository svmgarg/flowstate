/* ─── Theme Toggle ─── */
(function () {
    'use strict';

    const themeToggle = document.getElementById('themeToggle');

    function setTheme(theme) {
        const resolvedTheme = theme === 'dark' ? 'dark' : 'light';
        document.documentElement.setAttribute('data-theme', resolvedTheme);
        localStorage.setItem('flowstate-theme', resolvedTheme);
        themeToggle.textContent = resolvedTheme === 'dark' ? '🌙' : '☀️';
        themeToggle.setAttribute('aria-label', `Switch to ${resolvedTheme === 'dark' ? 'light' : 'dark'} mode`);
    }

    // Apply already-resolved theme from inline head script
    setTheme(document.documentElement.getAttribute('data-theme'));

    themeToggle.addEventListener('click', () => {
        const nextTheme = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        setTheme(nextTheme);
    });
})();
