import { themes as prismThemes } from "prism-react-renderer";
import type { Config } from "@docusaurus/types";
import type * as Preset from "@docusaurus/preset-classic";

const config: Config = {
  title: "Keycloak 2FA Messaging Authenticator",
  tagline: "OTP authentication for Keycloak via SMS, Telegram, WhatsApp, and Signal",
  favicon: "img/favicon.svg",
  future: { v4: true },
  markdown: { mermaid: true },
  themes: ["@docusaurus/theme-mermaid"],
  url: "https://mesutpiskin.github.io",
  baseUrl: "/keycloak-2fa-messaging-authenticator/",
  organizationName: "mesutpiskin",
  projectName: "keycloak-2fa-messaging-authenticator",
  trailingSlash: false,
  onBrokenLinks: "warn",
  i18n: { defaultLocale: "en", locales: ["en"] },
  presets: [["classic", {
    docs: {
      sidebarPath: "./sidebars.ts",
      routeBasePath: "/",
      editUrl: "https://github.com/mesutpiskin/keycloak-2fa-messaging-authenticator/edit/main/website/",
    },
    blog: false,
    theme: { customCss: "./src/css/custom.css" },
  } satisfies Preset.Options]],
  themeConfig: {
    colorMode: { defaultMode: "light", disableSwitch: false, respectPrefersColorScheme: true },
    image: "img/social-card.svg",
    navbar: {
      title: "Keycloak 2FA Messaging",
      logo: { alt: "Keycloak 2FA Messaging Authenticator", src: "img/logo.svg" },
      items: [
        { type: "docSidebar", sidebarId: "docs", position: "left", label: "Docs" },
        { href: "https://github.com/mesutpiskin/keycloak-2fa-messaging-authenticator", position: "right", className: "header-github-link", "aria-label": "GitHub repository" },
      ],
    },
    footer: {
      style: "dark",
      links: [
        { title: "Docs", items: [
          { label: "Introduction", to: "/intro" },
          { label: "Get Started", to: "/get-started" },
          { label: "Installation", to: "/installation/local" },
          { label: "Configuration", to: "/configuration/authentication-flow" },
        ]},
        { title: "Community", items: [
          { label: "GitHub Issues", href: "https://github.com/mesutpiskin/keycloak-2fa-messaging-authenticator/issues" },
          { label: "Pull Requests", href: "https://github.com/mesutpiskin/keycloak-2fa-messaging-authenticator/pulls" },
        ]},
        { title: "More", items: [
          { label: "GitHub", href: "https://github.com/mesutpiskin/keycloak-2fa-messaging-authenticator" },
          { label: "Maven Central", href: "https://central.sonatype.com/artifact/io.github.mesutpiskin/keycloak-2fa-messaging-authenticator" },
        ]},
      ],
      copyright: `Licensed under the <a href="https://github.com/mesutpiskin/keycloak-2fa-messaging-authenticator/blob/main/LICENSE" target="_blank">Apache License 2.0</a>. Built with Docusaurus.`,
    },
    prism: { theme: prismThemes.github, darkTheme: prismThemes.dracula, additionalLanguages: ["bash", "java", "groovy"] },
  } satisfies Preset.ThemeConfig,
};

export default config;
