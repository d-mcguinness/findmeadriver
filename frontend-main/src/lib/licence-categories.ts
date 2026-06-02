// Country-keyed licence category lookup. Keys are ISO-3166-1 alpha-2 country
// codes; values are the categories valid for posting loads / registering carriers
// in that country. The backend just stores the string verbatim and equality-
// matches it during load application — no validation server-side.
//
// Add a country: append an entry with the categories you want to surface.
export const LICENCE_CATEGORIES: Record<string, { code: string; label: string }[]> = {
	IE: [
		{ code: 'B', label: 'Category B (car / light van)' },
		{ code: 'C1', label: 'Category C1 (3.5–7.5t)' },
		{ code: 'C', label: 'Category C (rigid >3.5t)' },
		{ code: 'C+E', label: 'Category C+E (articulated)' },
		{ code: 'D', label: 'Category D (bus / coach)' }
	],
	GB: [
		{ code: 'B', label: 'Category B (car / light van)' },
		{ code: 'C1', label: 'LGV C1 (3.5–7.5t)' },
		{ code: 'C', label: 'LGV Class 2 (cat C)' },
		{ code: 'C+E', label: 'LGV Class 1 (cat C+E)' },
		{ code: 'D', label: 'PCV (cat D)' }
	],
	US: [
		{ code: 'CLASS_A', label: 'CDL Class A (combination)' },
		{ code: 'CLASS_B', label: 'CDL Class B (straight)' },
		{ code: 'CLASS_C', label: 'CDL Class C (placard / passenger)' },
		{ code: 'NON_CDL', label: 'Non-CDL' }
	],
	DE: [
		{ code: 'B', label: 'Klasse B' },
		{ code: 'C1', label: 'Klasse C1' },
		{ code: 'C', label: 'Klasse C' },
		{ code: 'CE', label: 'Klasse CE' },
		{ code: 'D', label: 'Klasse D' }
	],
	FR: [
		{ code: 'B', label: 'Permis B' },
		{ code: 'C1', label: 'Permis C1' },
		{ code: 'C', label: 'Permis C' },
		{ code: 'CE', label: 'Permis CE' },
		{ code: 'D', label: 'Permis D' }
	]
};

export function licenceCategoriesFor(country: string | undefined | null) {
	const key = (country ?? 'IE').toUpperCase();
	return LICENCE_CATEGORIES[key] ?? LICENCE_CATEGORIES.IE;
}

export function licenceCategoryLabel(country: string | undefined | null, code: string | undefined | null): string {
	if (!code) return '—';
	const list = licenceCategoriesFor(country);
	return list.find(c => c.code === code)?.label ?? code;
}
