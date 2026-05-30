// Shared transport-mode config for the multi-modal marketplace (M1).
// Values mirror the backend Shipment.Mode enum.

export type TransportMode = 'ROAD' | 'RAIL' | 'OCEAN' | 'AIR' | 'INTERMODAL' | 'PARCEL';

// Selectable modes in the Post-a-Job form. INTERMODAL is reserved for the
// multi-leg flow (M2); PARCEL is not offered yet.
export const TRANSPORT_MODE_OPTIONS: { value: TransportMode; label: string }[] = [
	{ value: 'ROAD', label: 'Road' },
	{ value: 'RAIL', label: 'Rail' },
	{ value: 'OCEAN', label: 'Sea / Ocean' },
	{ value: 'AIR', label: 'Air' }
];

const MODE_LABELS: Record<string, string> = {
	ROAD: 'Road',
	RAIL: 'Rail',
	OCEAN: 'Sea',
	AIR: 'Air',
	INTERMODAL: 'Intermodal',
	PARCEL: 'Parcel'
};

/** Human label for a mode, defaulting to Road when absent. */
export function transportModeLabel(mode: string | undefined | null): string {
	if (!mode) return 'Road';
	return MODE_LABELS[mode] ?? mode;
}

type CarbonTagColor = 'green' | 'teal' | 'cyan' | 'purple' | 'blue' | 'gray';

const MODE_TAG_COLOR: Record<string, CarbonTagColor> = {
	ROAD: 'green',
	RAIL: 'teal',
	OCEAN: 'cyan',
	AIR: 'purple',
	INTERMODAL: 'blue',
	PARCEL: 'gray'
};

/** Carbon Tag colour for a mode, defaulting to gray for unknown values. */
export function modeTagColor(mode: string | undefined | null): CarbonTagColor {
	return MODE_TAG_COLOR[mode ?? 'ROAD'] ?? 'gray';
}

// Estimated platform commission % for the Post-a-Job live preview. Delegates to
// the single client-side fallback table in pricing.ts (which the public
// /api/pricing/modes endpoint mirrors); the backend recomputes the authoritative
// figure on save.
export { fallbackCommissionPct as estimatedCommissionPct } from '$lib/pricing';
