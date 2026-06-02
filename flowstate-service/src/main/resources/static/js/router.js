/* ─── Router: Hash-based SPA navigation ─── */
(function () {
    'use strict';

    const navLinks = document.querySelectorAll('.nav-link-sidebar');
    const pageLinks = document.querySelectorAll('[data-page-link]');
    const pages = document.querySelectorAll('.page');

    function setActivePage(pageId, pushHistory = true) {
        pages.forEach(page => {
            page.classList.toggle('active', page.dataset.page === pageId);
        });

        navLinks.forEach(link => {
            link.classList.toggle('active', link.getAttribute('href') === `#${pageId}`);
        });

        if (pushHistory) {
            history.pushState(null, '', `#${pageId}`);
        }

        closeSidebar();
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    function loadPageFromHash() {
        const pageId = window.location.hash.substring(1);
        const exists = [...pages].some(page => page.dataset.page === pageId);
        setActivePage(exists ? pageId : 'overview', false);
    }

    // Sidebar open/close (mobile)
    const sidebar = document.getElementById('sidebar');
    const sidebarBackdrop = document.getElementById('sidebarBackdrop');
    const mobileMenuBtn = document.getElementById('mobileMenuBtn');
    const sidebarCloseBtn = document.getElementById('sidebarClose');

    function openSidebar() {
        sidebar.classList.add('active');
        sidebarBackdrop.classList.add('active');
    }

    function closeSidebar() {
        sidebar.classList.remove('active');
        sidebarBackdrop.classList.remove('active');
    }

    // Bind nav links
    navLinks.forEach(link => {
        link.addEventListener('click', event => {
            event.preventDefault();
            setActivePage(link.getAttribute('href').substring(1));
        });
    });

    pageLinks.forEach(link => {
        link.addEventListener('click', event => {
            event.preventDefault();
            setActivePage(link.dataset.pageLink);
        });
    });

    mobileMenuBtn.addEventListener('click', openSidebar);
    sidebarCloseBtn.addEventListener('click', closeSidebar);
    sidebarBackdrop.addEventListener('click', closeSidebar);
    window.addEventListener('popstate', loadPageFromHash);

    // Auto-fill current domain in code examples
    const currentDomain = window.location.origin;
    document.querySelectorAll('pre code').forEach(block => {
        block.textContent = block.textContent.replace(/https:\/\/your-domain/g, currentDomain);
    });

    // Copy buttons
    document.querySelectorAll('.copy-btn').forEach(button => {
        button.addEventListener('click', async () => {
            const targetId = button.dataset.target;
            const targetElement = targetId ? document.getElementById(targetId) : button.nextElementSibling;
            const code = targetElement?.innerText?.trim() || targetElement?.textContent?.trim() || '';
            const previous = button.textContent;
            try {
                await navigator.clipboard.writeText(code);
                button.textContent = 'Copied';
                button.classList.add('copied');
                setTimeout(() => { button.textContent = previous; button.classList.remove('copied'); }, 1800);
            } catch (error) {
                button.textContent = 'Failed';
                setTimeout(() => { button.textContent = previous; button.classList.remove('copied'); }, 1800);
            }
        });
    });

    // Boot
    loadPageFromHash();
})();
