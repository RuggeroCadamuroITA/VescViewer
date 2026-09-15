# VescViewer — Product Design System

## Product identity

VescViewer is a read-only instrument for people riding and tuning a VESC-powered vehicle. The interface must feel like a **premium industrial cockpit**, not a generic dashboard: calm when everything is normal, immediately legible in motion, and explicit when a connection, temperature, battery, or recording state changes.

## Design direction: Signal Cockpit

The visual language is **Signal Cockpit**: an obsidian instrument surface with a restrained warm accent and semantic telemetry colors. Large numbers carry the hierarchy; labels stay quiet; surfaces are used to group information rather than decorate every element. Portrait is a focused inspection view. Landscape is a glanceable driving cockpit.

Do not use generic SaaS patterns, purple gradients, decorative blobs, or unnecessary card nesting. Glass is limited to meaningful elevation boundaries and must never reduce contrast or steal layout space.

## Tokens

### Color roles

- `background`: deepest obsidian, used behind the whole app.
- `surface`: elevated charcoal for primary groups.
- `surfaceVariant`: secondary charcoal for controls and metric cells.
- `onSurface`: warm white for all primary copy and data.
- `onSurfaceVariant`: cool muted gray for labels and metadata.
- `primary`: user accent, used for actions, selected navigation, and active telemetry.
- `secondary`: mint for healthy/efficient telemetry.
- `tertiary`: sky for electrical and informational telemetry.
- `error`: red for dangerous states and destructive actions.
- `warning`: amber for caution and reconnecting states.

Light mode uses the same roles with ink text and warm paper surfaces; it is not a separate visual language.

### Spacing

Use the 4dp rhythm: 4, 8, 12, 16, 20, 24, 32. Screen gutters are 16dp on phones and 24dp on wide layouts. Touch targets are at least 48dp unless an icon is part of a larger interactive row.

### Shape and depth

- Small controls: 12–16dp radius.
- Group surfaces: 20–24dp radius.
- Pills: only for compact status or segmented controls.
- One rim-light border per elevated surface; no stacked borders.
- One soft contextual glow per major surface; no global blur decoration.

### Typography

- Display/data: bold, compact, high contrast, tabular-looking when possible.
- Heading: short and strong.
- Body: 14–16sp with comfortable line height.
- Label/metadata: 11–12sp, muted and uppercase only for true telemetry labels.

### Motion

Motion communicates state: 180–320ms fades/slides for navigation and reveal, 200–350ms value interpolation for live data, spring press compression around 0.98. No perpetual decorative motion. Reduced-motion preferences should disable non-essential transitions.

## Screen intent

- **Setup:** one decision at a time; the current step and next action are obvious; keyboard never covers the active field or action rail.
- **Authentication:** secure and calm; input remains readable in both themes; last entered digit is briefly visible then masked.
- **Dashboard portrait:** speed and connection first, then the minimum useful telemetry, then history and recording.
- **Dashboard landscape:** fixed glanceable cockpit; no scrolling for the primary driving metrics; large speed, clear battery, power, ERPM, temperature, signal, and recording state.
- **Devices:** scan state and permission state must be distinct; scan controls have horizontal icon/text alignment; each device is one clear selectable row.
- **Rides:** archive rows prioritize date, distance, and the action to inspect the route; map icon is centered with its label.
- **Map:** route is primary; point inspector is contextual and never obscures the selected point unnecessarily.
- **Settings:** grouped by user intent, with concise descriptions and explicit controls.

## Accessibility and QA

Every text color is explicit for a non-default surface. Every control has a disabled state and a minimum touch target. State is communicated by text/icon as well as color. Check portrait, landscape, light, dark, small screens, keyboard open, empty/loading/error states, and reduced motion before release.

## Audit baseline (2026-09-13)

Good foundations already present: the BLE/VESC/Room/recording/security layers are separated from Compose UI; the app has explicit telemetry math, persistent settings, OpenStreetMap history, and a dedicated landscape dashboard. The main weaknesses are visual repetition, overuse of bordered rounded containers, hard-coded copy, implicit Material colors, duplicated layout code between orientations, and no render-level verification on a physical device. The redesign therefore keeps the data flow intact and concentrates changes in shared tokens, shell, primitives, and screen composition.
