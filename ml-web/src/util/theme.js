const THEME_KEY = 'ml-theme';

export function getSavedTheme() {
    return localStorage.getItem(THEME_KEY) || 'light';
}

export function isDarkTheme() {
    return getSavedTheme() === 'dark';
}

export function applyTheme(theme) {
    const nextTheme = theme === 'dark' ? 'dark' : 'light';
    document.documentElement.classList.toggle('dark', nextTheme === 'dark');
    localStorage.setItem(THEME_KEY, nextTheme);
    window.dispatchEvent(new CustomEvent('ml-theme-change', {detail: {theme: nextTheme}}));
}

export function currentThemeColors() {
    const styles = getComputedStyle(document.documentElement);
    return {
        background: styles.getPropertyValue('--ml-bg').trim(),
        surface: styles.getPropertyValue('--ml-surface').trim(),
        border: styles.getPropertyValue('--ml-border').trim(),
        text: styles.getPropertyValue('--ml-text').trim(),
        muted: styles.getPropertyValue('--ml-muted').trim(),
        primary: styles.getPropertyValue('--ml-primary').trim(),
    };
}
