import { api } from '$lib/api';
import type { Load } from '$lib/types';

// Shared reactive state for shipper loads + ratings so accept/reject/start/
// complete/cancel actions in the ShipperDashboard refresh the StatsRow tiles.
function createShipperState() {
	let loads = $state<Load[]>([]);
	let averageRating = $state<number | null>(null);
	let loaded = $state(false);

	async function reloadLoads() {
		try {
			loads = await api.get<Load[]>('/api/shipper/loads');
			loaded = true;
		} catch {
			loads = [];
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
		get loads() { return loads; },
		get averageRating() { return averageRating; },
		get loaded() { return loaded; },
		reloadLoads,
		reloadRatings
	};
}

export const shipperState = createShipperState();
