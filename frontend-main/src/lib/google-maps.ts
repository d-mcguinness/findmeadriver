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
	destination: google.maps.LatLngLiteral
): Promise<RouteInfo | null> {
	try {
		// Call the Routes API REST endpoint directly to avoid JS SDK wrapper issues
		const response = await fetch(
			`https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix`,
			{
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
					'X-Goog-Api-Key': PUBLIC_GOOGLE_MAPS_API_KEY,
					'X-Goog-FieldMask': 'originIndex,destinationIndex,distanceMeters,duration'
				},
				body: JSON.stringify({
					origins: [{
						waypoint: { location: { latLng: { latitude: origin.lat, longitude: origin.lng } } }
					}],
					destinations: [{
						waypoint: { location: { latLng: { latitude: destination.lat, longitude: destination.lng } } }
					}],
					travelMode: 'DRIVE'
				})
			}
		);

		if (!response.ok) {
			console.error('Routes API error:', response.status, await response.text());
			return null;
		}

		const text = await response.text();

		// Response may be a JSON array or newline-delimited JSON objects
		let elements: any[];
		try {
			const parsed = JSON.parse(text);
			elements = Array.isArray(parsed) ? parsed : [parsed];
		} catch {
			// Newline-delimited JSON: parse each non-empty line separately
			elements = text.split('\n')
				.map(line => line.trim())
				.filter(line => line.length > 0 && line !== '[' && line !== ']')
				.map(line => {
					// Strip trailing commas from array-style NDJSON
					const clean = line.replace(/,\s*$/, '');
					return JSON.parse(clean);
				});
		}
		elements = elements.filter((e: any) => e.distanceMeters !== undefined);

		const element = elements[0];
		if (!element || !element.distanceMeters || !element.duration) return null;

		const distanceMeters: number = element.distanceMeters;
		// duration is a string like "1234s"
		const totalSeconds = parseInt(String(element.duration).replace('s', ''), 10);

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
