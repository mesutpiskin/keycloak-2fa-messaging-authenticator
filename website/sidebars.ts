import type { SidebarsConfig } from "@docusaurus/plugin-content-docs";

const sidebars: SidebarsConfig = {
  docs: [
    "intro",
    "get-started",
    {
      type: "category",
      label: "Installation",
      collapsed: false,
      items: ["installation/local", "installation/docker"],
    },
    {
      type: "category",
      label: "Configuration",
      collapsed: false,
      items: ["configuration/authentication-flow", "configuration/provider-setup"],
    },
    "local-testing",
    "troubleshooting",
    "contributing",
  ],
};

export default sidebars;
