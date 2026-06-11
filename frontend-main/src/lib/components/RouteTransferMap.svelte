<script lang="ts">
	import { onMount } from 'svelte';
	import { loadGoogleMaps, findNearbyTransfers } from '$lib/google-maps';

	type Stop = { type: string; address: string; country?: string; coords: { lat: number; lng: number } | null };
	let { stops = [] }: { stops?: Stop[] } = $props();

	let mapEl: HTMLDivElement;
	let map: google.maps.Map | null = null;
	let ready = false;
	let loadErr = $state('');
	let markers: google.maps.marker.AdvancedMarkerElement[] = [];
	let lines: google.maps.Polyline[] = [];
	let renderToken = 0;

	// Suggested-transfer marker colours per mode (match the per-stop chips).
	const MODE_COLOR: Record<string, string> = {
		AIR: '#8a3ffc',
		RAIL: '#007d79',
		OCEAN: '#0072c3'
	};

	function badge(bg: string, text: string, title: string): HTMLElement {
		const el = document.createElement('div');
		el.title = title;
		el.style.cssText =
			`background:${bg};color:#fff;border-radius:999px;min-width:18px;height:18px;` +
			`padding:0 6px;font:600 11px/18px sans-serif;text-align:center;` +
			`border:2px solid #fff;box-shadow:0 1px 3px rgba(0,0,0,0.4);white-space:nowrap;`;
		el.textContent = text;
		return el;
	}

	function clear() {
		for (const m of markers) m.map = null;
		markers = [];
		for (const l of lines) l.setMap(null);
		lines = [];
	}

	async function render() {
		if (!ready || !map) return;
		const token = ++renderToken;
		const g = await loadGoogleMaps();
		const { AdvancedMarkerElement } = (await g.maps.importLibrary('marker')) as google.maps.MarkerLibrary;

		clear();

		// Resolve coordinates: use the stop's saved coords, else geocode its
		// address (so seed/legacy loads whose stops lack lat/lng still plot).
		const geocoder = new g.maps.Geocoder();
		const resolved: { stop: Stop; pos: google.maps.LatLngLiteral }[] = [];
		for (const s of stops) {
			let pos = s.coords;
			if (!pos && s.address?.trim()) {
				try {
					const res = await geocoder.geocode({
						address: s.address,
						...(s.country ? { componentRestrictions: { country: s.country } } : {})
					});
					if (token !== renderToken) return;
					const loc = res.results?.[0]?.geometry?.location;
					if (loc) pos = { lat: loc.lat(), lng: loc.lng() };
				} catch {
					/* unresolvable address — skip this stop */
				}
			}
			if (pos) resolved.push({ stop: s, pos });
		}
		if (resolved.length === 0) return;

		const bounds = new g.maps.LatLngBounds();
		const path: google.maps.LatLngLiteral[] = [];

		resolved.forEach(({ stop: s, pos }, i) => {
			path.push(pos);
			bounds.extend(pos);
			markers.push(
				new AdvancedMarkerElement({
					map,
					position: pos,
					content: badge('#161616', String(i + 1), `${i + 1}. ${s.type} — ${s.address}`)
				})
			);
		});

		// The physical route line through the stops in order.
		if (path.length >= 2) {
			lines.push(
				new g.maps.Polyline({
					path,
					geodesic: true,
					strokeColor: '#0f62fe',
					strokeOpacity: 0.9,
					strokeWeight: 3,
					map
				})
			);
		}

		// Suggested transfers (nearest airport / rail station / ferry terminal) per stop.
		for (const { pos } of resolved) {
			const transfers = await findNearbyTransfers(pos);
			if (token !== renderToken) return; // a newer render superseded this one
			for (const t of transfers) {
				if (!t.available || !t.location || !MODE_COLOR[t.mode]) continue;
				bounds.extend(t.location);
				markers.push(
					new AdvancedMarkerElement({
						map,
						position: t.location,
						content: badge(
							MODE_COLOR[t.mode],
							t.mode[0],
							`${t.mode} transfer — ${t.name ?? ''} (${t.distanceKm} km)`
						)
					})
				);
			}
		}

		if (token === renderToken) map.fitBounds(bounds, 56);
	}

	onMount(async () => {
		try {
			const g = await loadGoogleMaps();
			map = new g.maps.Map(mapEl, {
				center: { lat: 53.35, lng: -6.26 },
				zoom: 6,
				mapId: 'FINDMEACARRIER_MAP',
				disableDefaultUI: true,
				zoomControl: true,
				gestureHandling: 'cooperative'
			});
			ready = true;
			await render();
		} catch (e) {
			console.error('Route map failed to load:', e);
			loadErr = 'Map failed to load.';
		}
	});

	// Re-render whenever the stops or their coordinates change.
	$effect(() => {
		const sig = stops.map((s) => (s.coords ? `${s.coords.lat},${s.coords.lng}:${s.type}` : '')).join('|');
		if (ready && sig.length >= 0) render();
	});
</script>

<div class="route-map-wrap">
	{#if loadErr}<p class="map-err">{loadErr}</p>{/if}
	<div class="route-map" bind:this={mapEl}></div>
	<div class="map-legend">
		<span><span class="dot" style="background:#161616"></span> Stops</span>
		<span><span class="dot" style="background:#8a3ffc"></span> Air</span>
		<span><span class="dot" style="background:#007d79"></span> Rail</span>
		<span><span class="dot" style="background:#0072c3"></span> Sea</span>
	</div>
</div>

<style>
	.route-map {
		width: 100%;
		height: 320px;
		border: 1px solid var(--cds-border-subtle, #e0e0e0);
		border-radius: 8px;
		background: var(--cds-layer, #f4f4f4);
	}
	.map-legend {
		display: flex;
		gap: 1rem;
		flex-wrap: wrap;
		margin-top: 0.5rem;
		font-size: 0.8125rem;
		color: var(--cds-text-secondary);
	}
	.map-legend .dot {
		display: inline-block;
		width: 0.7rem;
		height: 0.7rem;
		border-radius: 50%;
		margin-right: 0.25rem;
		vertical-align: middle;
	}
	.map-err {
		font-size: 0.8125rem;
		color: #da1e28;
		margin: 0 0 0.5rem;
	}
</style>
