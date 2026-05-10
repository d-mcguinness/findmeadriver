import { api } from '$lib/api';
import type { Job } from '$lib/types';

// Shared reactive state for employer jobs + ratings so accept/reject/start/
// complete/cancel actions in the EmployerDashboard refresh the StatsRow tiles.
function createEmployerState() {
	let jobs = $state<Job[]>([]);
	let averageRating = $state<number | null>(null);
	let loaded = $state(false);

	async function reloadJobs() {
		try {
			jobs = await api.get<Job[]>('/api/employer/jobs');
			loaded = true;
		} catch {
			jobs = [];
		}
	}

	async function reloadRatings() {
		try {
			const r = await api.get<{ averageRating: number; totalRatings: number }>('/api/employer/ratings');
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

export const employerState = createEmployerState();
