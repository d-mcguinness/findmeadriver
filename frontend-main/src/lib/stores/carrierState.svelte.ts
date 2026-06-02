import { api } from '$lib/api';
import type { LoadApplication } from '$lib/types';

// Shared reactive state for carrier-scoped data so the dashboard tabs and the
// StatsRow tiles stay in sync when applications change (apply / re-apply /
// withdraw). Anything that touches applications should call `reloadApplications`.
function createCarrierState() {
	let applications = $state<LoadApplication[]>([]);
	let loaded = $state(false);

	async function reloadApplications() {
		try {
			applications = await api.get<LoadApplication[]>('/api/carrier/applications');
			loaded = true;
		} catch {
			applications = [];
		}
	}

	return {
		get applications() { return applications; },
		get loaded() { return loaded; },
		reloadApplications
	};
}

export const carrierState = createCarrierState();
