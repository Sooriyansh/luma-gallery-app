(function () {
  "use strict";

  const root = document.documentElement;
  const toggle = document.querySelector(".theme-toggle");
  const themeKey = "luma-gallery-policy-theme";
  const links = Array.from(document.querySelectorAll(".toc a"));
  const sections = links.map((link) => document.querySelector(link.getAttribute("href"))).filter(Boolean);

  function applyTheme(theme) {
    root.dataset.theme = theme;
    toggle.setAttribute("aria-pressed", String(theme === "dark"));
    toggle.querySelector(".theme-icon").textContent = theme === "dark" ? "☀" : "◐";
  }

  const savedTheme = localStorage.getItem(themeKey);
  applyTheme(savedTheme || (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"));

  toggle.addEventListener("click", () => {
    const nextTheme = root.dataset.theme === "dark" ? "light" : "dark";
    localStorage.setItem(themeKey, nextTheme);
    applyTheme(nextTheme);
  });

  const observer = new IntersectionObserver((entries) => {
    const visible = entries.filter((entry) => entry.isIntersecting).sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
    if (!visible) return;
    links.forEach((link) => link.setAttribute("aria-current", String(link.getAttribute("href") === `#${visible.target.id}`)));
  }, { rootMargin: "-18% 0px -68% 0px", threshold: [0, .2, .6] });

  sections.forEach((section) => observer.observe(section));
}());
