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

export type RouteTravelMode = 'DRIVE' | 'TRANSIT';

/**
 * Real-world routed path (road driving, or best-effort public transit as a
 * rail proxy) through an ordered sequence of stops. Computed leg-by-leg
 * (intermediates aren't supported for TRANSIT by the Routes API, so a single
 * multi-waypoint call won't work for both travel modes) and concatenated
 * into one path. Returns null if any leg can't be routed — callers should
 * fall back to a straight/geodesic line between stops.
 */
export async function calculateRoutePath(
	stops: google.maps.LatLngLiteral[],
	travelMode: RouteTravelMode = 'DRIVE'
): Promise<google.maps.LatLngLiteral[] | null> {
	if (stops.length < 2) return null;
	try {
		const g = await loadGoogleMaps();
		const { encoding } = (await g.maps.importLibrary('geometry')) as google.maps.GeometryLibrary;
		const fullPath: google.maps.LatLngLiteral[] = [stops[0]];

		for (let i = 0; i < stops.length - 1; i++) {
			const toWaypoint = (p: google.maps.LatLngLiteral) => ({
				location: { latLng: { latitude: p.lat, longitude: p.lng } }
			});
			const response = await fetch(
				`https://routes.googleapis.com/directions/v2:computeRoutes`,
				{
					method: 'POST',
					headers: {
						'Content-Type': 'application/json',
						'X-Goog-Api-Key': PUBLIC_GOOGLE_MAPS_API_KEY,
						'X-Goog-FieldMask': 'routes.polyline.encodedPolyline'
					},
					body: JSON.stringify({
						origin: toWaypoint(stops[i]),
						destination: toWaypoint(stops[i + 1]),
						travelMode
					})
				}
			);
			if (!response.ok) return null;
			const data = await response.json();
			const encoded = data?.routes?.[0]?.polyline?.encodedPolyline;
			if (!encoded) return null;
			const legPath = encoding.decodePath(encoded);
			// Drop the leg's first point — it duplicates the previous leg's last point.
			fullPath.push(...legPath.slice(1).map((p) => ({ lat: p.lat(), lng: p.lng() })));
		}
		return fullPath;
	} catch (err) {
		console.error('Route path calculation failed:', err);
		return null;
	}
}

// ---- Approximate sea-lane routing ----
// There's no maritime routing API available here, so a "recommended shipping
// lane" is approximated by inserting the canonical canal/strait a real vessel
// would transit whenever the origin and destination sit in different ocean
// basins — e.g. Suez between Europe and Asia, Panama between the Atlantic and
// Pacific Americas — rather than drawing a straight line that can cut across
// entire continents. This is a coarse geographic approximation, not a real
// router: it won't reflect closures, vessel-size restrictions, or coastal
// hugging, and captions on the map say so.

interface SeaChokepoint {
	lat: number;
	lng: number;
	name: string;
}

const GIBRALTAR: SeaChokepoint = { lat: 36.14, lng: -5.35, name: 'Strait of Gibraltar' };
const SUEZ: SeaChokepoint = { lat: 30.5, lng: 32.35, name: 'Suez Canal' };
const PANAMA: SeaChokepoint = { lat: 9.08, lng: -79.68, name: 'Panama Canal' };

type SeaRegion = 'MEDITERRANEAN' | 'PACIFIC_AMERICAS' | 'ATLANTIC_AMERICAS' | 'ATLANTIC_OLD_WORLD' | 'INDO_PACIFIC';

/** Coarse ocean-basin bucket for a point, by lat/lng bounding box — good
 *  enough to decide which canal (if any) a leg between two points needs. */
function seaRegionOf(p: google.maps.LatLngLiteral): SeaRegion {
	if (p.lat >= 28 && p.lat <= 47 && p.lng >= -6 && p.lng <= 37) return 'MEDITERRANEAN';
	if (p.lng >= -170 && p.lng < -95) return 'PACIFIC_AMERICAS';
	if (p.lng >= -95 && p.lng < -30) return 'ATLANTIC_AMERICAS';
	if (p.lng >= -30 && p.lng < 60) return 'ATLANTIC_OLD_WORLD';
	return 'INDO_PACIFIC';
}

function isRegionPair(a: SeaRegion, b: SeaRegion, x: SeaRegion, y: SeaRegion): boolean {
	return (a === x && b === y) || (a === y && b === x);
}

/** Which canal/strait (if any) connects two ocean-basin regions. Empty when
 *  both ends share a basin, or the crossing is a realistic open-ocean run
 *  (e.g. trans-Atlantic, trans-Pacific) that needs no chokepoint. */
function chokepointsBetween(a: SeaRegion, b: SeaRegion): SeaChokepoint[] {
	if (a === b) return [];
	if (isRegionPair(a, b, 'MEDITERRANEAN', 'ATLANTIC_OLD_WORLD')) return [GIBRALTAR];
	if (isRegionPair(a, b, 'MEDITERRANEAN', 'ATLANTIC_AMERICAS')) return [GIBRALTAR];
	if (isRegionPair(a, b, 'MEDITERRANEAN', 'PACIFIC_AMERICAS')) return [GIBRALTAR];
	if (isRegionPair(a, b, 'MEDITERRANEAN', 'INDO_PACIFIC')) return [SUEZ];
	if (isRegionPair(a, b, 'ATLANTIC_OLD_WORLD', 'INDO_PACIFIC')) return [SUEZ];
	if (isRegionPair(a, b, 'ATLANTIC_AMERICAS', 'PACIFIC_AMERICAS')) return [PANAMA];
	if (isRegionPair(a, b, 'ATLANTIC_AMERICAS', 'INDO_PACIFIC')) return [PANAMA];
	if (isRegionPair(a, b, 'PACIFIC_AMERICAS', 'ATLANTIC_OLD_WORLD')) return [PANAMA];
	return [];
}

export interface SeaLaneResult {
	path: google.maps.LatLngLiteral[];
	viaNames: string[];
}

/** Approximate recommended sea route through an ordered list of stops: for
 *  each consecutive pair, insert the chokepoint(s) a real vessel would
 *  transit between their ocean basins. Pure/synchronous — no API call. */
export function recommendedSeaLane(stops: google.maps.LatLngLiteral[]): SeaLaneResult {
	if (stops.length < 2) return { path: stops.slice(), viaNames: [] };
	const path: google.maps.LatLngLiteral[] = [stops[0]];
	const viaNames: string[] = [];
	for (let i = 0; i < stops.length - 1; i++) {
		const a = seaRegionOf(stops[i]);
		const b = seaRegionOf(stops[i + 1]);
		for (const cp of chokepointsBetween(a, b)) {
			path.push({ lat: cp.lat, lng: cp.lng });
			if (!viaNames.includes(cp.name)) viaNames.push(cp.name);
		}
		path.push(stops[i + 1]);
	}
	return { path, viaNames };
}

export type TransferModeKey = 'ROAD' | 'RAIL' | 'OCEAN' | 'AIR';

export interface TransferOption {
	mode: TransferModeKey;
	available: boolean;
	name?: string;
	distanceKm?: number;
	/** Coordinates of the transfer point (so it can be plotted on a map). */
	location?: google.maps.LatLngLiteral;
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

export function haversineKm(a: google.maps.LatLngLiteral, b: google.maps.LatLngLiteral): number {
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
					out.push({
						mode,
						available: true,
						name: p.displayName ?? undefined,
						distanceKm: Math.round(km),
						location: { lat: loc.lat(), lng: loc.lng() }
					});
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
