import { useEffect, useRef, useState } from 'react';
import { Icon, type IconName } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { canBeAdded, isCategoryOpen, providersIn } from '../../domain/providers';
import { CATEGORIES, type Category, type Provider, type ProviderId } from '../../domain/model';
import { useT } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';
import { providerDescription, providerName } from './providerText';

const CATEGORY_ICON: Record<Category, IconName> = { CAPTURE: 'mic', REPO: 'repo', STORAGE: 'storage' };
const PROVIDER_ICON: Partial<Record<ProviderId, IconName>> = { UPLOAD: 'upload', PLAUD: 'mic', EMAIL: 'mail' };

interface AddMenuProps {
  providers: readonly Provider[];
  onChoose: (provider: Provider) => void;
  /** Set with onOpenChange when something else on the page also opens this menu. */
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}

/**
 * Add button with a two-level menu: category first, then the providers in it. Only what can be added is listed; with
 * one category open the menu goes straight to its providers.
 */
export function AddMenu({ providers, onChoose, open: openFromPage, onOpenChange }: AddMenuProps) {
  const t = useT();
  const [ownOpen, setOwnOpen] = useState(false);
  const open = openFromPage ?? ownOpen;
  const setOpen = (next: boolean) => {
    setOwnOpen(next);
    onOpenChange?.(next);
  };
  const [category, setCategory] = useState<Category | null>(null);
  const root = useRef<HTMLDivElement>(null);
  const openCategories = CATEGORIES.filter((id) => isCategoryOpen(id, providers));
  const shown = category ?? (openCategories.length === 1 ? openCategories[0] : null);
  const button = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    root.current?.querySelector<HTMLElement>('.menu button:not([disabled])')?.focus();
  }, [open, category]);

  useEffect(() => {
    if (!open) return;
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!root.current?.contains(event.target as Node)) close();
    };
    document.addEventListener('mousedown', closeOnOutsideClick);
    return () => document.removeEventListener('mousedown', closeOnOutsideClick);
  }, [open]);

  function close() {
    setOpen(false);
    setCategory(null);
  }

  function choose(provider: Provider) {
    close();
    onChoose(provider);
  }

  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key !== 'Escape') return;
    event.stopPropagation();
    if (category) {
      setCategory(null);
    } else {
      close();
      button.current?.focus();
    }
  }

  return (
    <div className="add" ref={root} onKeyDown={onKeyDown}>
      <button
        ref={button}
        type="button"
        className="btn primary lg add-main"
        aria-expanded={open}
        aria-haspopup="true"
        onClick={() => (open ? close() : setOpen(true))}
      >
        <Icon name="plus" /> {t('add.button')}
      </button>
      {open && (
        <div className="menu" role="group" aria-label={t('add.menu.title')}>
          {shown ? (
            <ProviderList category={shown} providers={providers} onBack={openCategories.length > 1 ? () => setCategory(null) : undefined} onChoose={choose} />
          ) : (
            <>
              <div className="menu-h">{t('add.menu.title')}</div>
              {openCategories.map((id) => (
                <button key={id} type="button" className="mitem" onClick={() => setCategory(id)}>
                  <span className="nico"><Icon name={CATEGORY_ICON[id]} /></span>
                  <span className="mtext">
                    <span className="mname">{t(`category.${id}` as MessageKey)}</span>
                    <span className="mdesc">{t(`category.${id}.desc` as MessageKey)}</span>
                  </span>
                  <span className="chev"><Icon name="chevron" size={18} /></span>
                </button>
              ))}
            </>
          )}
          <div className="menu-foot">{t('add.footer')}</div>
        </div>
      )}
    </div>
  );
}

interface ProviderListProps {
  category: Category;
  providers: readonly Provider[];
  /** Left out when this is the only category, so there is nothing to go back to. */
  onBack?: () => void;
  onChoose: (provider: Provider) => void;
}

function ProviderList({ category, providers, onBack, onChoose }: ProviderListProps) {
  const t = useT();
  return (
    <>
      <div className="menu-h">
        {onBack && (
          <button type="button" className="back" onClick={onBack} aria-label={t('add.back')}>
            <Icon name="back" size={18} />
          </button>
        )}
        {t(`category.${category}` as MessageKey)}
      </div>
      {providersIn(category, providers).filter(canBeAdded).map((provider) => {
        const description = providerDescription(provider, t);
        return (
          <button key={provider.provider} type="button" className="mitem" onClick={() => onChoose(provider)}>
            <span className="nico"><Icon name={PROVIDER_ICON[provider.provider] ?? CATEGORY_ICON[category]} /></span>
            <span className="mtext">
              <span className="mname">{providerName(provider, t)}</span>
              {description && <span className="mdesc">{description}</span>}
            </span>
            {provider.availability === 'PREVIEW' && <Pill tone="preview">{t('add.preview')}</Pill>}
          </button>
        );
      })}
    </>
  );
}
