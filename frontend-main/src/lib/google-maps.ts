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
