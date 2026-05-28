// Curated ISO-3166-1 alpha-2 list for national + international haulage in
// Europe + the wider TMS-relevant set. Order is rough-frequency for UK/IE
// operators so the dropdown is keyboard-friendly without scrolling.
export const HAULAGE_COUNTRIES: { code: string; name: string }[] = [
	{ code: 'IE', name: 'Ireland' },
	{ code: 'GB', name: 'United Kingdom' },
	{ code: 'FR', name: 'France' },
	{ code: 'DE', name: 'Germany' },
	{ code: 'NL', name: 'Netherlands' },
	{ code: 'BE', name: 'Belgium' },
	{ code: 'LU', name: 'Luxembourg' },
	{ code: 'ES', name: 'Spain' },
	{ code: 'PT', name: 'Portugal' },
	{ code: 'IT', name: 'Italy' },
	{ code: 'AT', name: 'Austria' },
	{ code: 'CH', name: 'Switzerland' },
	{ code: 'PL', name: 'Poland' },
	{ code: 'CZ', name: 'Czechia' },
	{ code: 'SK', name: 'Slovakia' },
	{ code: 'HU', name: 'Hungary' },
	{ code: 'RO', name: 'Romania' },
	{ code: 'DK', name: 'Denmark' },
	{ code: 'SE', name: 'Sweden' },
	{ code: 'NO', name: 'Norway' },
	{ code: 'FI', name: 'Finland' },
	{ code: 'US', name: 'United States' },
	{ code: 'CA', name: 'Canada' }
];

export function countryName(code: string | undefined | null): string {
	if (!code) return '';
	const hit = HAULAGE_COUNTRIES.find(c => c.code === code.toUpperCase());
	return hit ? hit.name : code;
}
