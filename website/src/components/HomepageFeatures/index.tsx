import type { ReactNode } from "react";
import clsx from "clsx";
import styles from "./styles.module.css";

type FeatureItem = { title: string; icon: string; description: ReactNode; };

const features: FeatureItem[] = [
  { title: "Multiple Messaging Channels", icon: "💬", description: <>Deliver OTP codes through <strong>Twilio</strong>, <strong>AWS SNS</strong>, <strong>Vonage</strong>, <strong>Telegram</strong>, <strong>WhatsApp</strong>, and <strong>Signal</strong>.</> },
  { title: "Seamless Keycloak Integration", icon: "🔒", description: <>Integrates with Keycloak authentication flows as a <strong>required</strong> or <strong>conditional</strong> OTP step with an enrollment required action.</> },
  { title: "User Enrollment Support", icon: "🧾", description: <>Let users self-register their <strong>phone number</strong> or <strong>Telegram chat ID</strong> during first login.</> },
  { title: "Developer Friendly", icon: "🧪", description: <>Use <strong>simulation mode</strong> while wiring authentication flows before connecting real external messaging providers.</> },
  { title: "Extensible Sender SPI", icon: "🛠️", description: <>Add your own delivery backend with the Java <strong>ServiceLoader</strong>-based <strong>MessageSender</strong> SPI.</> },
  { title: "Automated Delivery Pipeline", icon: "🚀", description: <>Includes GitHub Actions workflows for <strong>CI</strong>, <strong>package publishing</strong>, and <strong>docs deployment</strong>.</> },
];

function Feature({ title, icon, description }: FeatureItem) {
  return (
    <div className={clsx("col col--4", styles.featureCard)}>
      <div className={styles.featureIcon}>{icon}</div>
      <h3 className={styles.featureTitle}>{title}</h3>
      <p className={styles.featureDescription}>{description}</p>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container"><div className="row">{features.map((props, idx) => <Feature key={idx} {...props} />)}</div></div>
    </section>
  );
}
