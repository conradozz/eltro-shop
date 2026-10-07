(() => {
    'use strict';
    const dom = window.document;
    const translations = window.eltroTranslations || {};
    const originals = new WeakMap();
    const key = 'eltro.language';
    let language = 'pl';
    try {
        language = localStorage.getItem(key) === 'en' ? 'en' : 'pl';
    } catch (_) { /* The switch also works when browser storage is disabled. */ }

    const normalize = value => value.trim().replace(/\s+/g, ' ');
    const translate = value => language === 'en'
        ? (translations[value] ?? translations[normalize(value)] ?? value) : value;
    // Receives only static HTML literals from source code; interpolated business data
    // are appended separately and are never passed through this translator.
    function fragment(value) {
        if (language !== 'en') return value;
        return value.replace(/(^|>)([^<>]*)(?=<|$)/g, (all, tag, text) => {
            const original = text.trim();
            const translated = translations[original];
            return translated ? tag + text.replace(original, translated) : all;
        }).replace(/placeholder="([^"]*)"/g, (all, text) => {
            return translations[text] ? 'placeholder="' + translations[text] + '"' : all;
        });
    }

    function apply() {
        dom.documentElement.lang = language;
        dom.querySelectorAll('[data-i18n]').forEach(element => {
            if (!originals.has(element)) originals.set(element, element.textContent);
            const text = translate(originals.get(element));
            if (element.textContent !== text) element.textContent = text;
        });
        dom.querySelectorAll('[data-i18n-placeholder]').forEach(element => {
            if (!originals.has(element)) originals.set(element, element.getAttribute('placeholder') || '');
            element.setAttribute('placeholder', translate(originals.get(element)));
        });
        // Only translate known presentation elements. Customer/product data are untouched.
        ['eltro-demo-banner', 'eltro-demo-printmark'].forEach(id => {
            const element = dom.getElementById(id);
            if (!element) return;
            if (!originals.has(element)) originals.set(element, element.textContent);
            const text = translate(originals.get(element));
            if (element.textContent !== text) element.textContent = text;
        });
        const watermark = dom.querySelector('#eltro-demo-watermark > div:first-child');
        if (watermark) {
            if (!originals.has(watermark)) originals.set(watermark, watermark.textContent);
            const text = translate(originals.get(watermark));
            if (watermark.textContent !== text) watermark.textContent = text;
        }
        dom.querySelectorAll('[data-language-switch] button').forEach(button => {
            const selected = button.dataset.language === language;
            button.setAttribute('aria-pressed', String(selected));
            button.style.background = selected ? '#ed1118' : '#fff';
            button.style.color = selected ? '#fff' : '#20252d';
        });
    }

    function setLanguage(value) {
        const next = value === 'en' ? 'en' : 'pl';
        if (next === language) return;
        if (window.eltroBeforeLanguageChange?.() === false) return;
        language = next;
        try { localStorage.setItem(key, language); } catch (_) { }
        apply();
        dom.dispatchEvent(new CustomEvent('eltro:languagechange', { detail: { language } }));
    }

    function start() {
        dom.querySelectorAll('[data-language-switch]').forEach(container => {
            container.setAttribute('role', 'group');
            container.setAttribute('aria-label', 'Language / Język');
            container.style.cssText = 'display:flex;gap:6px;flex-shrink:0';
            for (const value of ['pl', 'en']) {
                const button = dom.createElement('button');
                button.type = 'button';
                button.textContent = value.toUpperCase();
                button.dataset.language = value;
                button.setAttribute('aria-label', value === 'pl' ? 'Polski' : 'English');
                button.style.cssText = 'width:auto;min-height:36px;margin:0;padding:6px 12px;border:1px solid #ddd;border-radius:8px;font:700 13px Arial;cursor:pointer';
                button.addEventListener('click', () => setLanguage(value));
                container.append(button);
            }
        });
        apply();
        // Demo branding is loaded asynchronously by eltro-demo.js.
        new MutationObserver(apply).observe(dom.body, { childList: true, subtree: true });
    }

    window.EltroI18n = Object.freeze({ t: translate, fragment, setLanguage, getLanguage: () => language });
    if (dom.readyState === 'loading') dom.addEventListener('DOMContentLoaded', start, { once: true });
    else start();
})();
