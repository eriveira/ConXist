import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { MESSAGES, type Language, type MessageKey } from './messages';

export type Translate = (key: MessageKey, values?: Record<string, string | number>) => string;

/** The value that picks singular or plural wording: `count`, or `total` when a message has no count. */
const PLURAL_VALUES = ['count', 'total'] as const;

export function translator(language: Language): Translate {
  const rules = new Intl.PluralRules(language);
  const messages = MESSAGES[language];
  return (key, values = {}) => {
    const amount = PLURAL_VALUES.map((name) => values[name]).find((value) => typeof value === 'number');
    const singular = `${key}_one` as MessageKey;
    const chosen = typeof amount === 'number' && rules.select(amount) === 'one' && singular in messages ? singular : key;
    return messages[chosen].replace(/\{(\w+)\}/g, (match, name: string) =>
      name in values ? String(values[name]) : match);
  };
}

const I18nContext = createContext<Translate>(translator('en'));

export function I18nProvider({ language, children }: { language: Language; children: ReactNode }) {
  const translate = useMemo(() => translator(language), [language]);
  return <I18nContext.Provider value={translate}>{children}</I18nContext.Provider>;
}

export function useT(): Translate {
  return useContext(I18nContext);
}
