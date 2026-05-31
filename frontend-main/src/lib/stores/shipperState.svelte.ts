import { api } from '$lib/api';
import type { Job } from '$lib/types';

// Shared reactive state for shipper jobs + ratings so accept/reject/start/
// complete/cancel actions in the ShipperDashboard refresh the StatsRow tiles.
function createShipperState() {
	let jobs = $state<Job[]>([]);
	let averageRating = $state<number | null>(null);
	let loaded = $state(false);

	async function reloadJobs() {
		try {
			jobs = await api.get<Job[]>('/api/shipper/jobs');
			loaded = true;
		} catch {
			jobs = [];
		}
	}

	async function reloadRatings() {
		try {
			const r = await api.get<{ averageRating: number; totalRatings: number }>('/api/shipper/ratings');
			averageRating = r.totalRatings > 0 ? r.averageRating : null;
		} catch {
			averageRating = null;
		}
	}

	return {
		get jobs() { return jobs; },
		get averageRating() { return averageRating; },
		get loaded() { return loaded; },
		reloadJobs,
		reloadRatings
	};
}

export const shipperState = createShipperState();
