import type { ReactNode } from "react";
import Link from "@docusaurus/Link";
import useDocusaurusContext from "@docusaurus/useDocusaurusContext";
import Layout from "@theme/Layout";
import HomepageFeatures from "@site/src/components/HomepageFeatures";
import styles from "./index.module.css";

function HeroBanner(): ReactNode {
  const { siteConfig } = useDocusaurusContext();
  return (
    <div className={styles.heroBanner}>
      <div className="container">
        <h1 className={styles.heroTitle}>{siteConfig.title}</h1>
        <p className={styles.heroSubtitle}>{siteConfig.tagline}</p>
        <div className={styles.heroBadges}>
          <img src="https://img.shields.io/maven-central/v/io.github.mesutpiskin/keycloak-2fa-messaging-authenticator.svg" alt="Maven Central" />
          <img src="https://img.shields.io/badge/Java-21-orange.svg" alt="Java 21" />
          <img src="https://img.shields.io/badge/License-Apache%202.0-green.svg" alt="Apache 2.0" />
        </div>
        <div className={styles.heroButtons}>
          <Link className="button button--primary button--lg" to="/intro">Get Started</Link>
          <Link className="button button--secondary button--lg" href="https://github.com/mesutpiskin/keycloak-2fa-messaging-authenticator/releases">Download</Link>
        </div>
      </div>
    </div>
  );
}

export default function Home(): ReactNode {
  return (
    <Layout description="Messaging-based OTP two-factor authentication provider for Keycloak. Supports SMS, Telegram, WhatsApp, and Signal.">
      <HeroBanner />
      <main>
        <HomepageFeatures />
      </main>
    </Layout>
  );
}
