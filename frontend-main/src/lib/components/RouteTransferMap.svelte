<script lang="ts">
	import { onMount } from 'svelte';
	import { Plane, Anchor, DeliveryTruck, TrainProfile } from 'carbon-icons-svelte';
	import {
		loadGoogleMaps,
		findNearbyTransfers,
		estimatedRoadKm,
		ROAD_CIRCUITY,
		calculateRoutePath,
		recommendedSeaLane,
		type TransferOption
	} from '$lib/google-maps';
	import { estimateLegCarrierCost } from '$lib/pricing';
	import { formatMoney } from '$lib/money';

	type Stop = { type: string; address: string; country?: string; coords: { lat: number; lng: number } | null };
	type Quantities = {
		weightKg?: number | null;
		volumeM3?: number | null;
		containerCount?: number | null;
		distanceKm?: number | null;
	};
	let {
		stops = [],
		quantities,
		mode,
		showRouteOptions = true,
		legs
	}: {
		stops?: Stop[];
		quantities?: Quantities;
		mode?: string;
		showRouteOptions?: boolean;
		// Multi-leg overview: each leg drawn with its own mode's recommended
		// route (real road/transit path, sea lane, etc.), not one line through
		// every stop. When omitted, {mode, stops} is treated as a single leg.
		legs?: { mode: string; stops: Stop[] }[];
	} = $props();

	type ComboKey = 'ROAD' | 'RAIL' | 'OCEAN' | 'AIR';
	type ComboOption = { key: ComboKey; label: string; route: string; totalPrice: number | null };

	let mapEl: HTMLDivElement;
	let map: google.maps.Map | null = null;
	let ready = false;
	let loadErr = $state('');
	let markers: google.maps.marker.AdvancedMarkerElement[] = [];
	let lines: google.maps.Polyline[] = [];
	let renderToken = 0;
	// Ranked whole-route price estimates per mode (road direct, or road +
	// long-haul + road via the nearest transfer points) — cheapest first.
	let comboOptions = $state<ComboOption[]>([]);
	// Caveat shown under the map about how the drawn route line was derived —
	// a real routed path (road/transit) vs. a great-circle approximation.
	let routeCaption = $state('');

	// Suggested-transfer marker colours per mode (match the per-stop chips).
	const MODE_COLOR: Record<string, string> = {
		AIR: '#8a3ffc',
		RAIL: '#007d79',
		OCEAN: '#0072c3'
	};
	const COMBO_COLOR: Record<ComboKey, string> = { ROAD: '#24a148', ...MODE_COLOR } as Record<ComboKey, string>;
	const DEFAULT_ROUTE_COLOR = '#0f62fe';

	// The route line's colour matches the map legend (and the combo-panel
	// icons) for that leg's mode — blue only as a fallback for a leg with no
	// recognised mode (e.g. an empty single-mode map).
	function routeColorFor(legMode: string): string {
		return (COMBO_COLOR as Record<string, string>)[legMode] ?? DEFAULT_ROUTE_COLOR;
	}

	const LONG_HAUL_MODES: { key: 'RAIL' | 'OCEAN' | 'AIR'; label: string; noun: string }[] = [
		{ key: 'RAIL', label: 'Rail', noun: 'rail terminal' },
		{ key: 'OCEAN', label: 'Sea', noun: 'port' },
		{ key: 'AIR', label: 'Air', noun: 'airport' }
	];

	function findTransfer(list: TransferOption[], transferMode: 'RAIL' | 'OCEAN' | 'AIR'): TransferOption | undefined {
		return list.find((t) => t.mode === transferMode && t.available && t.location);
	}

	// Estimated whole-route carrier cost per mode: road is priced direct on its
	// rate card; rail/sea/air are priced as road-to-terminal + the long-haul
	// leg (on that mode's own rate card, terminal-to-terminal) + road-from-terminal,
	// using the nearest transfer point already found near each end of the route.
	// A mode with no transfer point within 50 km of either end is left unpriced
	// rather than hidden, so the shipper can see it isn't a realistic option here.
	// A nearby transfer's straight-line proximity, modelled as the road haul to
	// reach it. Undefined when the proximity is unknown.
	function feederHaulKm(t: TransferOption): number | undefined {
		return t.distanceKm != null ? t.distanceKm * ROAD_CIRCUITY : undefined;
	}

	function computeComboOptions(
		originPos: google.maps.LatLngLiteral,
		destPos: google.maps.LatLngLiteral,
		originTransfers: TransferOption[],
		destTransfers: TransferOption[]
	): ComboOption[] {
		// A measured distance (the form fills it from the Routes API) is used as
		// is; otherwise model it the way the server's planner does, so this
		// panel and a route proposal don't quote road differently.
		const roadKm = quantities?.distanceKm ?? estimatedRoadKm(originPos, destPos);
		const options: ComboOption[] = [
			{
				key: 'ROAD',
				label: 'Road',
				route: `Direct road haulage · ${Math.round(roadKm)} km`,
				totalPrice: estimateLegCarrierCost('ROAD', { distanceKm: roadKm })
			}
		];

		for (const { key, label, noun } of LONG_HAUL_MODES) {
			const originT = findTransfer(originTransfers, key);
			const destT = findTransfer(destTransfers, key);
			if (!originT || !destT) {
				options.push({ key, label, route: `No ${noun} within 50 km of both ends`, totalPrice: null });
				continue;
			}
			// The transfer's own distanceKm is straight-line proximity (that's what
			// the "N km" label means); as a feeder *haul* it needs the circuity
			// factor, matching how the server prices a virtual road feeder. An
			// unknown proximity stays unknown — estimateLegCarrierCost then falls
			// back to the card minimum, exactly as before.
			const leg1 = estimateLegCarrierCost('ROAD', { distanceKm: feederHaulKm(originT) });
			const leg3 = estimateLegCarrierCost('ROAD', { distanceKm: feederHaulKm(destT) });
			const longHaul =
				key === 'AIR'
					? estimateLegCarrierCost('AIR', { weightKg: quantities?.weightKg ?? 500, volumeM3: quantities?.volumeM3 ?? undefined })
					: estimateLegCarrierCost(key, { containerCount: quantities?.containerCount ?? 1 });
			const totalPrice = leg1 != null && leg3 != null && longHaul != null ? leg1 + leg3 + longHaul : null;
			options.push({
				key,
				label,
				route: `Road → ${originT.name ?? noun} → ${label} → ${destT.name ?? noun} → Road`,
				totalPrice
			});
		}

		return options.sort((a, b) => {
			if (a.totalPrice == null) return b.totalPrice == null ? 0 : 1;
			if (b.totalPrice == null) return -1;
			return a.totalPrice - b.totalPrice;
		});
	}

	type RouteLine = { path: google.maps.LatLngLiteral[]; geodesic: boolean; dashed: boolean; caption: string };

	// The recommended route line for one leg's own mode — a real routed path
	// (roads for ROAD, best-effort transit for RAIL) where the Routes API can
	// supply one, a recommended shipping lane via major canals for OCEAN, else
	// a great-circle line as a soft fallback (dashed when routing failed for a
	// mode that should have had one). Shared by single-mode maps and, per leg,
	// by multi-leg overview maps — same routing, never a straight crow-flies
	// line for a mode that has a real router.
	async function computeRouteLine(legMode: string, legPath: google.maps.LatLngLiteral[]): Promise<RouteLine> {
		if (legMode === 'OCEAN') {
			const lane = recommendedSeaLane(legPath);
			return {
				path: lane.path,
				geodesic: true,
				dashed: false,
				caption: lane.viaNames.length
					? `Recommended shipping lane via ${lane.viaNames.join(' → ')} — approximate, not live vessel tracking.`
					: 'Recommended shipping lane — approximate, not live vessel tracking.'
			};
		}

		let routedPath: google.maps.LatLngLiteral[] | null = null;
		if (legMode === 'ROAD') routedPath = await calculateRoutePath(legPath, 'DRIVE');
		else if (legMode === 'RAIL') routedPath = await calculateRoutePath(legPath, 'TRANSIT');

		if (routedPath && routedPath.length >= 2) {
			return {
				path: routedPath,
				geodesic: false,
				dashed: false,
				caption: legMode === 'RAIL' ? 'Indicative transit route — may not reflect actual freight rail lines.' : ''
			};
		}

		const isRoutingFailure = legMode === 'ROAD' || legMode === 'RAIL';
		return {
			path: legPath,
			geodesic: true,
			dashed: isRoutingFailure,
			caption: isRoutingFailure
				? 'Live routing unavailable — showing a direct line, not the actual route.'
				: legMode === 'AIR'
					? 'Great-circle route shown — approximate flight path, not live flight tracking.'
					: ''
		};
	}

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
		comboOptions = [];
		routeCaption = '';
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
		async function resolveStops(list: Stop[]) {
			const out: { stop: Stop; pos: google.maps.LatLngLiteral }[] = [];
			for (const s of list) {
				let pos = s.coords;
				if (!pos && s.address?.trim()) {
					try {
						const res = await geocoder.geocode({
							address: s.address,
							...(s.country ? { componentRestrictions: { country: s.country } } : {})
						});
						if (token !== renderToken) return out;
						const loc = res.results?.[0]?.geometry?.location;
						if (loc) pos = { lat: loc.lat(), lng: loc.lng() };
					} catch {
						/* unresolvable address — skip this stop */
					}
				}
				if (pos) out.push({ stop: s, pos });
			}
			return out;
		}

		// A single {mode, stops} usage is treated as one implicit leg, so a
		// multi-leg overview (via the `legs` prop) reuses the exact same
		// per-leg routing below instead of a separate code path.
		const legsToRender = legs && legs.length > 0 ? legs : [{ mode: mode ?? '', stops }];

		const resolvedLegs: { legMode: string; resolved: { stop: Stop; pos: google.maps.LatLngLiteral }[] }[] = [];
		for (const leg of legsToRender) {
			const resolved = await resolveStops(leg.stops);
			if (token !== renderToken) return;
			resolvedLegs.push({ legMode: leg.mode, resolved });
		}

		const allResolved = resolvedLegs.flatMap((l) => l.resolved);
		if (allResolved.length === 0) return;

		const bounds = new g.maps.LatLngBounds();

		// Numbered stop markers across the whole sequence — all legs share one
		// running count so a multi-leg overview reads as a single door-to-door
		// route rather than several unrelated numbered routes.
		let seq = 0;
		for (const { resolved } of resolvedLegs) {
			for (const { stop: s, pos } of resolved) {
				seq++;
				bounds.extend(pos);
				markers.push(
					new AdvancedMarkerElement({
						map,
						position: pos,
						content: badge('#161616', String(seq), `${seq}. ${s.type} — ${s.address}`)
					})
				);
			}
		}

		// The physical route line(s) — a real routed path (roads for ROAD,
		// best-effort transit for RAIL) where the Routes API can supply one, a
		// recommended shipping lane via major canals for OCEAN, else a
		// great-circle line as a soft fallback. Drawn one leg at a time, so a
		// multi-leg overview shows each leg with its own mode's routing rather
		// than a single straight line across the whole itinerary.
		const captions: string[] = [];
		for (const { legMode, resolved } of resolvedLegs) {
			const legPath = resolved.map((r) => r.pos);
			if (legPath.length < 2) continue;
			const line = await computeRouteLine(legMode, legPath);
			if (token !== renderToken) return; // a newer render superseded this one
			// The routed path can bulge outside the pickup/dropoff bounding box
			// (e.g. a road detour), so widen bounds to fit the whole line.
			for (const p of line.path) bounds.extend(p);
			lines.push(
				new g.maps.Polyline({
					path: line.path,
					geodesic: line.geodesic,
					strokeColor: routeColorFor(legMode),
					strokeOpacity: line.dashed ? 0 : 0.9,
					strokeWeight: 3,
					icons: line.dashed
						? [{ icon: { path: 'M 0,-1 0,1', strokeOpacity: 1, scale: 3 }, offset: '0', repeat: '14px' }]
						: undefined,
					map
				})
			);
			if (line.caption) captions.push(line.caption);
		}
		routeCaption = captions.length === 0 ? '' : legsToRender.length > 1 ? captions.join(' · ') : captions[0];

		// Suggested transfers (nearest airport / rail station / ferry terminal) per stop.
		const transfersByStopIndex: TransferOption[][] = [];
		for (const { pos } of allResolved) {
			const transfers = await findNearbyTransfers(pos);
			if (token !== renderToken) return; // a newer render superseded this one
			transfersByStopIndex.push(transfers);
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

		// Ranked whole-route price comparison, based on the transfer points
		// already found nearest the route's first and last stop. Skipped for
		// overview maps spanning an already-priced multi-leg itinerary, where
		// "cheapest way to move this load" would be a misleading generic
		// estimate rather than the real per-leg pricing shown alongside it.
		if (showRouteOptions && allResolved.length >= 2) {
			comboOptions = computeComboOptions(
				allResolved[0].pos,
				allResolved[allResolved.length - 1].pos,
				transfersByStopIndex[0] ?? [],
				transfersByStopIndex[transfersByStopIndex.length - 1] ?? []
			);
		}

		if (token === renderToken) map.fitBounds(bounds, 56);
	}

	onMount(async () => {
		try {
			const g = await loadGoogleMaps();
			// The parent can swap this component out (e.g. a mode switch right
			// after mount, such as the ?mode=INTERMODAL redirect) before this
			// async load resolves — Svelte clears bind:this targets on destroy,
			// so bail out rather than pass a null mapDiv to the Maps API.
			if (!mapEl) return;
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

	// Re-render whenever the stops/legs, their coordinates, or the transport
	// mode(s) change.
	$effect(() => {
		const stopSig = (list: Stop[]) =>
			list.map((s) => (s.coords ? `${s.coords.lat},${s.coords.lng}:${s.type}` : '')).join('|');
		const sig =
			legs && legs.length > 0
				? legs.map((l) => `${l.mode}#${stopSig(l.stops)}`).join(';')
				: `${stopSig(stops)}#${mode ?? ''}`;
		if (ready && sig.length >= 0) render();
	});
</script>

<div class="route-map-wrap">
	{#if loadErr}<p class="map-err">{loadErr}</p>{/if}
	<div class="route-map" bind:this={mapEl}></div>
	<div class="map-legend">
		<span><span class="dot" style="background:#161616"></span> Stops</span>
		<span><span class="dot" style="background:{COMBO_COLOR.ROAD}"></span> Road</span>
		<span><span class="dot" style="background:{COMBO_COLOR.RAIL}"></span> Rail</span>
		<span><span class="dot" style="background:{COMBO_COLOR.OCEAN}"></span> Sea</span>
		<span><span class="dot" style="background:{COMBO_COLOR.AIR}"></span> Air</span>
	</div>
	{#if routeCaption}<p class="route-caption">{routeCaption}</p>{/if}
	{#if comboOptions.length}
		<div class="mode-estimates">
			<span class="est-title">Cheapest ways to move this load:</span>
			<ol class="combo-list">
				{#each comboOptions as opt, i (opt.key)}
					<li class="combo-item">
						<span class="combo-rank">{i + 1}</span>
						<span class="combo-icon" style="color:{COMBO_COLOR[opt.key]}">
							{#if opt.key === 'ROAD'}<DeliveryTruck size={16} />
							{:else if opt.key === 'RAIL'}<TrainProfile size={16} />
							{:else if opt.key === 'OCEAN'}<Anchor size={16} />
							{:else}<Plane size={16} />{/if}
						</span>
						<span class="combo-label">{opt.label}</span>
						<span class="combo-route">{opt.route}</span>
						<span class="combo-price">{opt.totalPrice != null ? formatMoney(opt.totalPrice) : 'n/a'}</span>
						{#if i === 0 && opt.totalPrice != null}<span class="combo-badge combo-badge--cheapest">Cheapest</span>{/if}
						{#if opt.key === mode}<span class="combo-badge combo-badge--selected">Selected</span>{/if}
					</li>
				{/each}
			</ol>
			<span class="est-note">estimated carrier cost only — not live schedules/fares; first/last-mile legs assume a direct line to the nearest transfer point</span>
		</div>
	{/if}
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
	.mode-estimates {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
		margin-top: 0.5rem;
		padding: 0.5rem 0.75rem;
		background: var(--cds-layer, #f4f4f4);
		border-radius: 8px;
		font-size: 0.8125rem;
	}
	.est-title { font-weight: 600; }
	.est-note { color: var(--cds-text-secondary); font-style: italic; }
	.combo-list {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 0.375rem;
	}
	.combo-item {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		flex-wrap: wrap;
	}
	.combo-rank {
		width: 1rem;
		font-weight: 600;
		color: var(--cds-text-secondary);
		text-align: right;
	}
	.combo-icon { display: inline-flex; align-items: center; }
	.combo-label { font-weight: 600; min-width: 2.75rem; }
	.combo-route { color: var(--cds-text-secondary); flex: 1 1 auto; }
	.combo-price { font-weight: 600; white-space: nowrap; }
	.combo-badge {
		font-size: 0.6875rem;
		font-weight: 600;
		padding: 0.0625rem 0.375rem;
		border-radius: 999px;
		text-transform: uppercase;
		letter-spacing: 0.02em;
		white-space: nowrap;
	}
	.combo-badge--cheapest { background: #defbe6; color: #0e6027; }
	.combo-badge--selected { background: #edf5ff; color: #0043ce; }
	.route-caption {
		font-size: 0.8125rem;
		font-style: italic;
		color: var(--cds-text-secondary);
		margin: 0.375rem 0 0;
	}
	.map-err {
		font-size: 0.8125rem;
		color: #da1e28;
		margin: 0 0 0.5rem;
	}
</style>
