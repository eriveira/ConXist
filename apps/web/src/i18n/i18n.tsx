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
    // A key the app has no words for (a tool the server added since) shows as the key, never breaks the page.
    return (messages[chosen] ?? key).replace(/\{(\w+)\}/g, (match, name: string) =>
      name in values ? String(values[name]) : match);
  };
}

/** A tool's name for people, or its id when this version of the app has no words for it yet. */
export function toolLabel(t: Translate, id: string): string {
  const key = `agent.tool.${id}` as MessageKey;
  const label = t(key);
  return label === key ? id : label;
}

/** What a tool does, or nothing when this version of the app has no words for it yet. */
export function toolDescription(t: Translate, id: string): string {
  const key = `agent.tool.${id}.desc` as MessageKey;
  const text = t(key);
  return text === key ? '' : text;
}

const I18nContext = createContext<Translate>(translator('en'));
const LanguageContext = createContext<Language>('en');

export function I18nProvider({ language, children }: { language: Language; children: ReactNode }) {
  const translate = useMemo(() => translator(language), [language]);
  return (
    <LanguageContext.Provider value={language}>
      <I18nContext.Provider value={translate}>{children}</I18nContext.Provider>
    </LanguageContext.Provider>
  );
}

/** The person's language, for dates, numbers and sorting by name. */
export function useLanguage(): Language {
  return useContext(LanguageContext);
}

export function useT(): Translate {
  return useContext(I18nContext);
}
