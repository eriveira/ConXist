import { Icon } from '../../components/Icon';
import { providerLabel, type Translate } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';

/** What ConXist knows to show for a provider beyond its id: its mark and its short name for sentences. */
const KNOWN: Record<string, { mark: string; brand: MessageKey }> = {
  // Anthropic's mark (Simple Icons, CC0), drawn in the text color so it follows the theme.
  anthropic: {
    mark: 'M17.3041 3.541h-3.6718l6.696 16.918H24Zm-10.6082 0L0 20.459h3.7442l1.3693-3.5527h7.0052l1.3693 3.5528h3.7442L10.5363 3.5409Zm-.3712 10.2232 2.2914-5.9456 2.2914 5.9456Z',
    brand: 'settings.brand.anthropic',
  },
};

/** The provider's own mark where ConXist has it, else a generic one. Decorative: the provider's name is beside it. */
export function ProviderMark({ provider, size = 22 }: { provider: string; size?: number }) {
  const known = KNOWN[provider];
  if (!known) return <Icon name="vendor" size={size} />;
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">
      <path d={known.mark} />
    </svg>
  );
}

/** The provider's short name, such as Anthropic, for sentences; its full label when ConXist has no short one. */
export function providerBrand(t: Translate, provider: string): string {
  const known = KNOWN[provider];
  return known ? t(known.brand) : providerLabel(t, provider);
}
