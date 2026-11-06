# PretupidUI

Shared SCSS design system used by the Pretupid Launcher desktop app and the
marketing website. Referred to as PretupidUI.

## Installation

Consume via Sass loadPaths. Both frontends configure the parent packages/
directory:

- frontend/vite.config.ts -> css.preprocessorOptions.scss.loadPaths
- frontend-web/nuxt.config.ts -> vite.css.preprocessorOptions.scss.loadPaths

## Usage

### Global stylesheet

    @use 'design-system';
    @use '_accent';

design-system emits the tokens, reset, typography, base rules, utilities,
and every component class. _accent is a local file that defines the accent
color for the consumer.

### Inside Vue SFC scoped styles

Only pull in mixins, so the design system's global :root and element
selectors are not duplicated inside scoped styles:

    @use 'design-system/mixins' as *;

    .my-thing {
      @include block-shadow(var(--shadow-color));
    }

### Available mixins

- block-shadow($color) - pixel-art offset shadow.
- button-base - base button styling including hover and focus states.
- card-base - base card styling.

### Available component classes

Buttons:

- .btn
- .btn--secondary, .btn--ghost, .btn--danger, .btn--warning, .btn--success
- .btn--small, .btn--large, .btn--block

Inputs:

- .input for text, email, number, password, textarea, and select
- Number inputs hide spin buttons, selects get a custom chevron

Cards:

- .card, .card--interactive
- .card__header, .card__title, .card__content

Toggle checkbox:

    <label class="settings-checkbox">
      <input type="checkbox" class="settings-checkbox__input" />
      <span class="settings-checkbox__box" aria-hidden="true"></span>
      <span class="settings-checkbox__label">Label</span>
    </label>

Add .settings-checkbox--compact for a smaller variant.

Badges:

- .badge
- .badge--accent, .badge--success, .badge--warning, .badge--danger
- .badge--muted, .badge--lg

Sections:

- .section, .section__header, .section__header--stacked
- .section__eyebrow, .section__title, .section__title--sm
- .section__subtitle, .section__actions

Panels:

- .panel, .panel--compact, .panel--dashed
- .panel--accent, .panel--success, .panel--danger
- .panel__header, .panel__title, .panel__subtitle, .panel__body

Empty states:

- .empty, .empty--centered
- .empty__icon, .empty__title, .empty__hint

Progress:

- .progress-bar, .progress-bar__fill
- .progress-bar--indeterminate for an animated placeholder

Console:

- .console, .console__line

Lists:

- .list, .list__item, .list__icon, .list__icon-placeholder
- .list__body, .list__title, .list__meta

Pagination:

- .pagination, .pagination__arrow, .pagination__page
- .pagination__page--active, .pagination__ellipsis, .pagination__summary

Forms:

- .form-group, .form-group--checkbox, .form-group--inline
- .form-label, .form-label-optional, .form-hint
- .form-error, .form-error--block, .form-grid
- .checkbox-label, .checkbox

Notifications:

- .notify, .notify__trigger, .notify__icon, .notify__badge
- .notify__chevron, .notify__panel
- .notify__item, .notify__item--info, --success, --warning, --error
- .notify__item-dismiss

Loader:

- .pixel-loader, .pixel-loader__dots, .pixel-loader__dot

## Design tokens

Tokens are CSS custom properties defined on :root:

- Colors: --bg-primary, --bg-secondary, --bg-tertiary,
  --text-primary, --text-secondary, --border-color, --shadow-color
- Accent: --accent, --accent-hover, --accent-text - defined per consumer
- Radius: --edge-radius
- Fonts: --font-pixel, --font-body
- Transitions: --transition-fast, --transition-normal

Every component uses tokens, so a consumer that overrides a token gets the
change across the entire design system without touching any component file.

## Theming at runtime

The settings stores on both frontends write to these tokens at runtime:

    document.documentElement.style.setProperty('--accent', '#4aff9e')

The launcher exposes pretupid://settings?theme=...&accent=...&edge=... as a
deep link that writes the same tokens from an external source. The website
uses this to sync its appearance settings into the desktop launcher.

## Adding a new component

1. Create packages/design-system/components/_my-component.scss using tokens
   for every value.
2. Add @forward 'components/my-component'; to packages/design-system/_index.scss.
3. Document the class names in this README under Available component classes.