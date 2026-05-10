import { api } from '$lib/api';
import type { JobApplication } from '$lib/types';

// Shared reactive state for driver-scoped data so the dashboard tabs and the
// StatsRow tiles stay in sync when applications change (apply / re-apply /
// withdraw). Anything that touches applications should call `reloadApplications`.
function createDriverState() {
	let applications = $state<JobApplication[]>([]);
	let loaded = $state(false);

	async function reloadApplications() {
		try {
			applications = await api.get<JobApplication[]>('/api/driver/applications');
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

export const driverState = createDriverState();
