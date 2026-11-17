import type { Provider, ProviderId } from '../../domain/model';
import type { Translate } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';

/** Providers whose name is translated. The rest are brand names and keep the name the API gives. */
const TRANSLATED: Partial<Record<ProviderId, { name: MessageKey; description: MessageKey }>> = {
  UPLOAD: { name: 'provider.UPLOAD', description: 'provider.UPLOAD.desc' },
  PLAUD: { name: 'provider.PLAUD', description: 'provider.PLAUD.desc' },
  EMAIL: { name: 'provider.EMAIL', description: 'provider.EMAIL.desc' },
};

export function providerName(provider: Provider, t: Translate): string {
  const keys = TRANSLATED[provider.provider];
  return keys ? t(keys.name) : provider.displayName;
}

export function providerDescription(provider: Provider, t: Translate): string | null {
  const keys = TRANSLATED[provider.provider];
  return keys ? t(keys.description) : null;
}
