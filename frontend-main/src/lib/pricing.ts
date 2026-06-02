import { api } from '$lib/api';

export interface ModePricing {
	mode: string;
	label: string;
	commissionPercent: number;
	basis: string;
	tagline: string;
}

// Client-side fallback so the marketing pages render instantly (and during SSR
// / if the API is unreachable). The backend GET /api/pricing/modes is
// authoritative — its commissionPercent comes from PricingPolicy, the same
// source the pricing engine charges against — and overrides these at runtime.
export const FALLBACK_MODE_PRICING: ModePricing[] = [
	{
		mode: 'ROAD',
		label: 'Road',
		commissionPercent: 10,
		basis: 'Per kilometre + minimum charge',
		tagline: 'Spare tachograph hours and full road haulage, matched in minutes.'
	},
	{
		mode: 'RAIL',
		label: 'Rail',
		commissionPercent: 12,
		basis: 'Per container / wagon, terminal-to-terminal',
		tagline: 'Low-carbon line-haul along the rail corridors.'
	},
	{
		mode: 'OCEAN',
		label: 'Sea',
		commissionPercent: 15,
		basis: 'Per container (FCL) or per W/M revenue-ton (LCL)',
		tagline: 'Port-to-port containers and groupage.'
	},
	{
		mode: 'AIR',
		label: 'Air',
		commissionPercent: 20,
		basis: 'Per chargeable-kg (IATA volumetric weight)',
		tagline: 'Time-critical freight, delivered fast.'
	}
];

/** Fetch the live per-mode pricing; fall back to the static table on error. */
export async function loadModePricing(): Promise<ModePricing[]> {
	try {
		const modes = await api.get<ModePricing[]>('/api/pricing/modes');
		return modes?.length ? modes : FALLBACK_MODE_PRICING;
	} catch {
		return FALLBACK_MODE_PRICING;
	}
}

/** Fallback commission % for a mode — used by the post-a-load live estimate.
 *  The backend recomputes the authoritative figure on save. */
export function fallbackCommissionPct(mode: string | null | undefined): number {
	const m = FALLBACK_MODE_PRICING.find((x) => x.mode === (mode ?? 'ROAD'));
	return m ? m.commissionPercent : 10;
}

// ---- Per-mode carrier-cost rate cards (mirror of backend PricingPolicy) ----
// Used ONLY for the live builder preview; the server recomputes the
// authoritative carrier cost on submit. Keep in sync with PricingPolicy.

export type ChargeUnit =
	| 'PER_KM'
	| 'PER_CONTAINER'
	| 'PER_CHARGEABLE_KG'
	| 'PER_PIECE'
	| 'PER_HOUR'
	| 'FLAT';

interface RateCardSpec {
	unit: ChargeUnit;
	base: number;
	rate: number;
	min: number;
}

const FALLBACK_RATE_CARDS: Record<string, RateCardSpec> = {
	ROAD: { unit: 'PER_KM', base: 50, rate: 1.2, min: 150 },
	RAIL: { unit: 'PER_CONTAINER', base: 0, rate: 600, min: 600 },
	OCEAN: { unit: 'PER_CONTAINER', base: 350, rate: 1800, min: 1800 },
	AIR: { unit: 'PER_CHARGEABLE_KG', base: 0, rate: 3.2, min: 75 },
	PARCEL: { unit: 'PER_PIECE', base: 0, rate: 8.5, min: 8.5 }
};

const AIR_VOLUMETRIC_DIVISOR = 6000;

export interface LegQuantities {
	distanceKm?: number;
	weightKg?: number;
	volumeM3?: number;
	containerCount?: number;
	pieceCount?: number;
}

/** The charge unit a mode is priced on (PER_HOUR fallback for unknown modes). */
export function chargeUnitForMode(mode: string): ChargeUnit {
	return FALLBACK_RATE_CARDS[mode]?.unit ?? 'PER_HOUR';
}

/** Chargeable quantity for the mode's basis (air uses max(actual, volumetric)). */
export function chargeableQuantity(mode: string, q: LegQuantities): number | null {
	const card = FALLBACK_RATE_CARDS[mode];
	if (!card) return null;
	switch (card.unit) {
		case 'PER_KM':
			return q.distanceKm ?? null;
		case 'PER_CONTAINER':
			return q.containerCount ?? null;
		case 'PER_PIECE':
			return q.pieceCount ?? null;
		case 'PER_CHARGEABLE_KG': {
			const actual = q.weightKg ?? null;
			const volumetric = q.volumeM3 != null ? (q.volumeM3 * 1_000_000) / AIR_VOLUMETRIC_DIVISOR : null;
			if (actual == null && volumetric == null) return null;
			return Math.max(actual ?? 0, volumetric ?? 0);
		}
		default:
			return null;
	}
}

/** Estimated carrier cost for a leg via its mode's rate card; null if the
 *  required quantity is missing (server would then fall back to rate × hours). */
export function estimateLegCarrierCost(mode: string, q: LegQuantities): number | null {
	const card = FALLBACK_RATE_CARDS[mode];
	if (!card) return null;
	const qty = chargeableQuantity(mode, q);
	if (qty == null || qty <= 0) return null;
	return Math.max(card.min, card.base + card.rate * qty);
}