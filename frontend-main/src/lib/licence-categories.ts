// Country-keyed licence category lookup. Keys are ISO-3166-1 alpha-2 country
// codes; values are the categories valid for posting jobs / registering drivers
// in that country. The backend just stores the string verbatim and equality-
// matches it during job application — no validation server-side.
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

// "Covers" lattice — mirrors com.driverdirect.model.LicenceCategory.COVERS so
// the UI can pre-check eligibility before offering an apply action. Keyed by
// the enum *name* the backend stores (EU "C+E" is the enum CE; tokens not in
// this map fall back to plain equality, exactly like the server).
const COVERS: Record<string, string[]> = {
	CE: ['C', 'C1', 'C1E', 'HGV_CLASS_1', 'HGV_CLASS_2'],
	C: ['C1', 'HGV_CLASS_2'],
	C1E: ['C1'],
	C1: [],
	DE: ['D', 'D1', 'D1E'],
	D: ['D1'],
	D1E: ['D1'],
	D1: [],
	HGV_CLASS_1: ['CE', 'C', 'C1', 'C1E', 'HGV_CLASS_2'],
	HGV_CLASS_2: ['C', 'C1'],
	CLASS_A: ['CLASS_B', 'CLASS_C', 'NON_CDL'],
	CLASS_B: ['CLASS_C', 'NON_CDL'],
	CLASS_C: ['NON_CDL'],
	NON_CDL: []
};

/**
 * True when a driver holding `have` may take a job requiring `required`.
 * Mirrors LicenceCategory.satisfies(): no requirement → true; missing licence →
 * false; same value → true; otherwise the covers lattice, with unknown tokens
 * (e.g. "C+E") falling back to plain equality.
 */
export function licenceSatisfies(have?: string | null, required?: string | null): boolean {
	if (!required) return true;
	if (!have) return false;
	if (have === required) return true;
	const covered = COVERS[have];
	if (covered === undefined || COVERS[required] === undefined) return have === required;
	return covered.includes(required);
}
