import { setOptions, importLibrary } from '@googlemaps/js-api-loader';
import { PUBLIC_GOOGLE_MAPS_API_KEY } from '$env/static/public';

let mapsLoaded = false;
let optionsSet = false;

export async function loadGoogleMaps(): Promise<typeof google> {
	if (mapsLoaded) return google;

	if (!optionsSet) {
		setOptions({ key: PUBLIC_GOOGLE_MAPS_API_KEY, v: 'weekly' });
		optionsSet = true;
	}

	await importLibrary('places');
	await importLibrary('core');
	await importLibrary('maps');
	mapsLoaded = true;
	return google;
}

export interface RouteInfo {
	distanceKm: number;
	distanceText: string;
	durationSeconds: number;
	durationText: string;
}

export async function calculateRoute(
	origin: google.maps.LatLngLiteral,
	destination: google.maps.LatLngLiteral,
	intermediates: google.maps.LatLngLiteral[] = []
): Promise<RouteInfo | null> {
	try {
		const toWaypoint = (p: google.maps.LatLngLiteral) => ({
			location: { latLng: { latitude: p.lat, longitude: p.lng } }
		});

		// computeRoutes (not the distance matrix) so the totals follow the full
		// ordered route through any intermediate waypoints, not just origin→dest.
		const response = await fetch(
			`https://routes.googleapis.com/directions/v2:computeRoutes`,
			{
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
					'X-Goog-Api-Key': PUBLIC_GOOGLE_MAPS_API_KEY,
					'X-Goog-FieldMask': 'routes.distanceMeters,routes.duration'
				},
				body: JSON.stringify({
					origin: toWaypoint(origin),
					destination: toWaypoint(destination),
					intermediates: intermediates.map(toWaypoint),
					travelMode: 'DRIVE'
				})
			}
		);

		if (!response.ok) {
			console.error('Routes API error:', response.status, await response.text());
			return null;
		}

		const data = await response.json();
		const route = data?.routes?.[0];
		if (!route || route.distanceMeters === undefined || !route.duration) return null;

		const distanceMeters: number = route.distanceMeters;
		// duration is a string like "1234s"
		const totalSeconds = parseInt(String(route.duration).replace('s', ''), 10);

		const distanceKm = distanceMeters / 1000;
		const hours = Math.floor(totalSeconds / 3600);
		const minutes = Math.floor((totalSeconds % 3600) / 60);
		const seconds = totalSeconds % 60;

		const parts: string[] = [];
		if (hours > 0) parts.push(`${hours}h`);
		if (minutes > 0) parts.push(`${minutes}m`);
		if (seconds > 0 && hours === 0) parts.push(`${seconds}s`);

		return {
			distanceKm,
			distanceText: `${distanceKm.toFixed(1)} km`,
			durationSeconds: totalSeconds,
			durationText: parts.join(' ')
		};
	} catch (err) {
		console.error('Route calculation failed:', err);
		return null;
	}
}

export type TransferModeKey = 'ROAD' | 'RAIL' | 'OCEAN' | 'AIR';

export interface TransferOption {
	mode: TransferModeKey;
	available: boolean;
	name?: string;
	distanceKm?: number;
}

// Google Place primary type per non-road mode. Google has no true "seaport"
// type, so ferry_terminal is the best-effort sea transfer point (approximate).
const TRANSFER_TYPE: Record<'RAIL' | 'OCEAN' | 'AIR', string> = {
	AIR: 'airport',
	RAIL: 'train_station',
	OCEAN: 'ferry_terminal'
};

// searchNearby caps the location-restriction radius at 50 km.
const TRANSFER_RADIUS_M = 50000;

function haversineKm(a: google.maps.LatLngLiteral, b: google.maps.LatLngLiteral): number {
	const R = 6371;
	const dLat = ((b.lat - a.lat) * Math.PI) / 180;
	const dLng = ((b.lng - a.lng) * Math.PI) / 180;
	const lat1 = (a.lat * Math.PI) / 180;
	const lat2 = (b.lat * Math.PI) / 180;
	const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
	return 2 * R * Math.asin(Math.sqrt(h));
}

/**
 * Check which transport modes have a transfer point near a stop's coordinates,
 * via the Google Places API (nearest airport / rail station / ferry terminal
 * within 50 km). Road is always available (the marketplace baseline). Each
 * non-road lookup fails soft — an error or no result just marks that mode
 * unavailable rather than breaking the caller.
 */
export async function findNearbyTransfers(
	coords: google.maps.LatLngLiteral
): Promise<TransferOption[]> {
	const out: TransferOption[] = [{ mode: 'ROAD', available: true }];
	try {
		await loadGoogleMaps();
		const { Place, SearchNearbyRankPreference } =
			(await google.maps.importLibrary('places')) as google.maps.PlacesLibrary;

		for (const mode of ['AIR', 'RAIL', 'OCEAN'] as const) {
			try {
				const { places } = await Place.searchNearby({
					fields: ['displayName', 'location'],
					locationRestriction: { center: coords, radius: TRANSFER_RADIUS_M },
					includedPrimaryTypes: [TRANSFER_TYPE[mode]],
					maxResultCount: 1,
					rankPreference: SearchNearbyRankPreference.DISTANCE
				});
				const p = places?.[0];
				const loc = p?.location;
				if (p && loc) {
					const km = haversineKm(coords, { lat: loc.lat(), lng: loc.lng() });
					out.push({ mode, available: true, name: p.displayName ?? undefined, distanceKm: Math.round(km) });
				} else {
					out.push({ mode, available: false });
				}
			} catch (err) {
				console.error(`Transfer lookup failed for ${mode}:`, err);
				out.push({ mode, available: false });
			}
		}
	} catch (err) {
		console.error('Transfer lookup failed to load Places:', err);
	}
	return out;
}
