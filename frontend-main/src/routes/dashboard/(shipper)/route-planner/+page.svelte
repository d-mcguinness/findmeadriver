<script lang="ts">
	import {
		Grid, Row, Column,
		Button, Tile, Tag, Select, SelectItem, NumberInput, TextInput,
		InlineNotification, InlineLoading
	} from 'carbon-components-svelte';
	import { ArrowLeft, Search, ArrowRight, CheckmarkOutline, Money, Cloud, Map as MapIcon } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import { goto } from '$app/navigation';
	import { onMount } from 'svelte';
	import type { RoutableLocation, RouteOption, RouteLeg } from '$lib/types';
	import { transportModeLabel, modeTagColor } from '$lib/transport-modes';
	import { formatMoney } from '$lib/money';
	import RouteTransferMap from '$lib/components/RouteTransferMap.svelte';

	// The stop shape RouteTransferMap plots (matches its Stop type).
	type MapStop = { type: string; address: string; country?: string; coords: { lat: number; lng: number } | null };

	let locations = $state<RoutableLocation[]>([]);
	let loadingLocations = $state(true);
	let locationsError = $state('');

	// The query. earliestReady defaults to today so the required date is always
	// set — the viewer's LOCAL calendar date (not UTC, which is off-by-one near
	// midnight in non-UTC zones).
	const now = new Date();
	const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
	let form = $state({
		originLocationId: '',
		destinationLocationId: '',
		weightKg: undefined as number | undefined,
		containerCount: undefined as number | undefined,
		volumeM3: undefined as number | undefined,
		pieceCount: undefined as number | undefined,
		earliestReady: today,
		latestHandover: '',
		arrivalDeadline: ''
	});

	let options = $state<RouteOption[]>([]);
	let searched = $state(false);
	let searching = $state(false);
	let error = $state('');
	let acceptingIndex = $state<number | null>(null);
	let acceptError = $state('');
	// The exact payload the shown options were searched with — Accept re-plans
	// against THIS, never the live form, so a booked itinerary always matches
	// the card the shipper clicked.
	let searchedQuery = $state<Record<string, unknown> | null>(null);
	// Which option is drawn on the map (defaults to the cheapest after a search).
	let focusedIndex = $state<number | null>(null);
	let mapWrapEl = $state<HTMLDivElement>();

	// Focus an option on the map and bring the (top-of-column) map into view, so
	// clicking "View on map" on a lower card shows the redraw it triggers.
	function viewOnMap(index: number) {
		focusedIndex = index;
		mapWrapEl?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
	}

	// Editing any query field invalidates the shown options: clear them so a
	// stale card can't be accepted (Accept would otherwise re-plan a different
	// query). Reading the fields here registers them as reactive dependencies.
	$effect(() => {
		form.originLocationId; form.destinationLocationId;
		form.weightKg; form.containerCount; form.volumeM3; form.pieceCount;
		form.earliestReady; form.latestHandover; form.arrivalDeadline;
		options = [];
		searched = false;
		searchedQuery = null;
		acceptError = '';
		focusedIndex = null;
	});

	// ---- Reactive map: selected origin/destination, or the focused option ----
	let locById = $derived(new Map(locations.map((l) => [l.id, l])));
	let originLoc = $derived(locById.get(Number(form.originLocationId)));
	let destLoc = $derived(locById.get(Number(form.destinationLocationId)));

	function coordsOf(loc: RoutableLocation | undefined): { lat: number; lng: number } | null {
		return loc && loc.latitude != null && loc.longitude != null
			? { lat: loc.latitude, lng: loc.longitude }
			: null;
	}

	// Before/without a focused option: a straight origin→destination preview.
	let previewStops = $derived<MapStop[]>(
		[
			originLoc && { type: 'PICKUP', address: originLoc.name, country: originLoc.country, coords: coordsOf(originLoc) },
			destLoc && { type: 'DELIVERY', address: destLoc.name, country: destLoc.country, coords: coordsOf(destLoc) }
		].filter(Boolean) as MapStop[]
	);

	function legStops(leg: RouteLeg): MapStop[] {
		const o = locById.get(leg.originLocationId);
		const d = locById.get(leg.destinationLocationId);
		return [
			{ type: 'PICKUP', address: leg.originLocationName, country: o?.country, coords: coordsOf(o) },
			{ type: 'DELIVERY', address: leg.destinationLocationName, country: d?.country, coords: coordsOf(d) }
		];
	}

	// The focused option drawn as a multi-leg overview (each leg its own mode).
	let focusedLegs = $derived(
		focusedIndex != null && options[focusedIndex]
			? options[focusedIndex].legs.map((l) => ({ mode: l.mode, stops: legStops(l) }))
			: null
	);
	let showMap = $derived(!!focusedLegs || previewStops.length === 2);

	async function loadLocations() {
		try {
			locations = await api.get<RoutableLocation[]>('/api/shipper/locations');
		} catch (e: any) {
			locationsError = e.message || 'Failed to load locations';
		} finally {
			loadingLocations = false;
		}
	}
	onMount(loadLocations);

	function locationLabel(l: RoutableLocation): string {
		const code = l.unlocode || l.iata;
		return `${l.name}${code ? ` (${code})` : ''} · ${l.country}`;
	}

	/** Build the query payload, omitting blank optional fields. */
	function queryPayload() {
		const p: Record<string, unknown> = {
			originLocationId: Number(form.originLocationId),
			destinationLocationId: Number(form.destinationLocationId),
			earliestReady: form.earliestReady
		};
		if (form.weightKg) p.weightKg = form.weightKg;
		if (form.containerCount) p.containerCount = form.containerCount;
		if (form.volumeM3) p.volumeM3 = form.volumeM3;
		if (form.pieceCount) p.pieceCount = form.pieceCount;
		if (form.latestHandover) p.latestHandover = form.latestHandover;
		if (form.arrivalDeadline) p.arrivalDeadline = form.arrivalDeadline;
		return p;
	}

	async function findRoutes() {
		error = '';
		acceptError = '';
		if (!form.originLocationId || !form.destinationLocationId) {
			error = 'Pick an origin and a destination.';
			return;
		}
		if (form.originLocationId === form.destinationLocationId) {
			error = 'Origin and destination must differ.';
			return;
		}
		if (!form.earliestReady) {
			error = 'Set the earliest-ready date.';
			return;
		}
		searching = true;
		const payload = queryPayload();
		try {
			options = await api.post<RouteOption[]>('/api/shipper/route-options', payload);
			searchedQuery = payload; // snapshot the query these options came from
			searched = true;
			focusedIndex = options.length > 0 ? 0 : null; // show the cheapest on the map
		} catch (e: any) {
			error = e.message || 'Route search failed';
			options = [];
		} finally {
			searching = false;
		}
	}

	async function acceptRoute(option: RouteOption, index: number) {
		acceptError = '';
		acceptingIndex = index;
		try {
			// Re-plan against the SAME query the options were searched with, not
			// the live form, so Accept books exactly the card that was shown.
			const body = {
				...(searchedQuery ?? queryPayload()),
				legs: option.legs.map((l) => ({
					originLocationId: l.originLocationId,
					destinationLocationId: l.destinationLocationId,
					mode: l.mode
				}))
			};
			const itinerary = await api.post<{ id: number }>('/api/shipper/route-options/accept', body);
			// Booked — jump to the itinerary list where the new draft appears.
			goto(`/dashboard/itineraries?accepted=${itinerary.id}`);
		} catch (e: any) {
			acceptError = e.message || 'Could not accept this route';
		} finally {
			acceptingIndex = null;
		}
	}

	function fmtDateTime(iso: string | undefined): string {
		if (!iso) return '—';
		try {
			return new Intl.DateTimeFormat('en-IE', {
				weekday: 'short', day: 'numeric', month: 'short',
				hour: '2-digit', minute: '2-digit'
			}).format(new Date(iso));
		} catch {
			return iso;
		}
	}

	// The backend flags the fastest-possible fallback it returns when no route
	// met the deadline (judged in the destination timezone — the client can't
	// re-derive that from a UTC instant, so we trust the server's verdict).
	let deadlineMissed = $derived(options.some((o) => !o.meetsDeadline));

	// The quoted figure is shipper-payable, so show what it is made of. Terminal
	// handling is part of the estimate but isn't billed on a booked itinerary
	// (handling isn't a billable item yet), so it's called out separately rather
	// than folded into the fee.
	function costBreakdown(o: RouteOption): string {
		const parts = [
			`Carrier ${formatMoney(o.carrierCostTotal)}`,
			`platform fee ${formatMoney(o.commissionTotal)} (per leg, at each mode's rate)`
		];
		if (o.transferCostTotal > 0) {
			parts.push(`terminal handling ${formatMoney(o.transferCostTotal)}`);
		}
		return `${parts.join(' + ')}. Re-priced on acceptance.`;
	}

	function optionBadge(index: number): { label: string; color: 'green' | 'teal' } | null {
		if (options.length < 2) return null;
		if (index === 0) return { label: 'Cheapest', color: 'teal' };
		if (index === options.length - 1) return { label: 'Lowest CO₂', color: 'green' };
		return null;
	}
</script>

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard/itineraries" icon={ArrowLeft}>Itineraries</Button>
				<p class="eyebrow">Routing engine</p>
				<h1 class="section-heading">Plan a route</h1>
				<p class="lede">
					Describe your cargo and where it needs to go. We propose the best door-to-door
					options trading off <strong>cost</strong> and <strong>CO₂</strong>, then turn the
					one you pick into a draft itinerary.
				</p>
			</div>
		</Column>
	</Row>

	<Row>
		<!-- Query form -->
		<Column lg={5} md={8} sm={4}>
			<Tile class="fmad-card">
				{#if locationsError}
					<InlineNotification kind="error" title="Locations" subtitle={locationsError} hideCloseButton />
				{:else if loadingLocations}
					<InlineLoading description="Loading locations…" />
				{:else}
					<Select bind:selected={form.originLocationId} labelText="Origin">
						<SelectItem value="" text="Select origin…" />
						{#each locations as l}
							<SelectItem value={String(l.id)} text={locationLabel(l)} />
						{/each}
					</Select>
					<Select bind:selected={form.destinationLocationId} labelText="Destination">
						<SelectItem value="" text="Select destination…" />
						{#each locations as l}
							<SelectItem value={String(l.id)} text={locationLabel(l)} />
						{/each}
					</Select>
					<p class="hint">Ports, airports and rail terminals (plus your own saved locations).</p>

					<div class="cargo-grid">
						<NumberInput bind:value={form.weightKg} label="Weight (kg)" min={0} step={100} />
						<NumberInput bind:value={form.containerCount} label="Containers" min={0} step={1} />
						<NumberInput bind:value={form.volumeM3} label="Volume (m³)" min={0} step={1} />
						<NumberInput bind:value={form.pieceCount} label="Pieces" min={0} step={1} />
					</div>
					<p class="hint">
						Fill the metric your cargo is measured in — sea/rail price per container, air per
						chargeable-kg, road per km. You can accept an option once its leg's metric is set.
					</p>

					<TextInput bind:value={form.earliestReady} labelText="Earliest ready (required)" type="date" />
					<TextInput bind:value={form.latestHandover}
						labelText="Latest handover (optional — searches the window)" type="date" />
					<TextInput bind:value={form.arrivalDeadline}
						labelText="Arrival deadline (optional)" type="date" />

					{#if error}
						<InlineNotification kind="error" title="Can't search" subtitle={error} on:close={() => (error = '')} />
					{/if}

					<Button icon={Search} on:click={findRoutes} disabled={searching}>
						{searching ? 'Searching…' : 'Find routes'}
					</Button>
				{/if}
			</Tile>
		</Column>

		<!-- Results -->
		<Column lg={7} md={8} sm={4}>
			{#if showMap}
				<div class="map-wrap" bind:this={mapWrapEl}>
					{#if focusedLegs}
						<RouteTransferMap legs={focusedLegs} showRouteOptions={false} />
					{:else}
						<RouteTransferMap stops={previewStops} mode="ROAD" showRouteOptions={false} />
					{/if}
				</div>
			{/if}

			{#if searching}
				<InlineLoading description="Searching for routes…" />
			{:else if searched && options.length === 0}
				<InlineNotification kind="info" title="No route found"
					subtitle="No option reaches that destination for this cargo and window. Try a wider handover window or a different origin/destination."
					hideCloseButton />
			{:else if options.length > 0}
				{#if deadlineMissed}
					<InlineNotification kind="warning" title="No route meets your deadline"
						subtitle={`The fastest possible arrival is ${fmtDateTime(options[0].arrival)}, after your deadline.`}
						hideCloseButton />
				{/if}
				{#if acceptError}
					<InlineNotification kind="error" title="Couldn't accept" subtitle={acceptError} on:close={() => (acceptError = '')} />
				{/if}

				<div class="options">
					{#each options as option, i}
						{@const badge = optionBadge(i)}
						<Tile class={i === focusedIndex ? 'fmad-card focused' : 'fmad-card'}>
							<div class="opt-head">
								<div class="opt-metrics">
									<span class="metric" title={costBreakdown(option)}>
										<Money size={16} /> <strong>{formatMoney(option.totalCost)}</strong>
										<em>est. you pay</em>
									</span>
									<span class="metric"><Cloud size={16} /> <strong>{Math.round(option.totalCo2Kg)} kg</strong> CO₂</span>
									{#if badge}<Tag type={badge.color}>{badge.label}</Tag>{/if}
								</div>
								<div class="opt-money">
									Carrier {formatMoney(option.carrierCostTotal)}
									<span class="fee">+ {formatMoney(option.commissionTotal)} platform fee</span>
									{#if option.transferCostTotal > 0}
										<span class="handling" title="Terminal handling: getting cargo off one leg and onto the next. Charged per interchange at the terminal's rate, and billed on the itinerary you book.">
											+ {formatMoney(option.transferCostTotal)} handling
										</span>
									{/if}
								</div>
							</div>

							<div class="legs">
								{#each option.legs as leg, li}
									<div class="leg">
										<Tag type={modeTagColor(leg.mode)} size="sm">{transportModeLabel(leg.mode)}</Tag>
										<span class="leg-route">{leg.originLocationName} <ArrowRight size={12} /> {leg.destinationLocationName}</span>
										{#if !leg.scheduled}<span class="leg-kind">road</span>{/if}
									</div>
								{/each}
							</div>

							<div class="opt-times">
								<span>Hand over by <strong>{fmtDateTime(option.handoverBy)}</strong></span>
								<span>Arrives <strong>{fmtDateTime(option.arrival)}</strong></span>
							</div>

							<div class="opt-actions">
								<Button size="small" kind="ghost" icon={MapIcon}
									disabled={i === focusedIndex}
									on:click={() => viewOnMap(i)}>
									{i === focusedIndex ? 'On map' : 'View on map'}
								</Button>
								<Button size="small" icon={CheckmarkOutline}
									disabled={acceptingIndex !== null}
									on:click={() => acceptRoute(option, i)}>
									{acceptingIndex === i ? 'Booking…' : 'Accept & create itinerary'}
								</Button>
							</div>
						</Tile>
					{/each}
				</div>
			{:else}
				<Tile>
					<p class="placeholder">Your proposed routes will appear here.</p>
				</Tile>
			{/if}
		</Column>
	</Row>
</Grid>

<style>
	.page-header { margin-bottom: 1.5rem; }
	.page-header h1 { margin: 0.25rem 0 0.5rem; }
	.lede { max-width: 46rem; color: var(--cds-text-secondary); font-size: 0.9375rem; }
	.hint { font-size: 0.75rem; color: var(--cds-text-secondary); margin: 0.4rem 0 0.9rem; }
	.cargo-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; margin-top: 1rem; }
	:global(.route-planner .bx--form-item) { margin-bottom: 0.9rem; }
	/* Sticky so the map stays visible while browsing the option list below it. */
	.map-wrap { margin-bottom: 1rem; position: sticky; top: 1rem; z-index: 1; }
	.options { display: flex; flex-direction: column; gap: 1rem; }
	:global(.route-planner .fmad-card.focused),
	.options :global(.fmad-card.focused) {
		outline: 2px solid var(--fmad-accent, #0f62fe);
		outline-offset: -2px;
	}
	.opt-head { display: flex; justify-content: space-between; align-items: flex-start; }
	.opt-metrics { display: flex; align-items: center; gap: 1.25rem; flex-wrap: wrap; }
	.metric { display: inline-flex; align-items: center; gap: 0.35rem; font-size: 0.9375rem; }
	.metric em { color: var(--cds-text-secondary); font-style: normal; font-size: 0.75rem; }
	.opt-money {
		margin-top: 0.25rem;
		font-size: 0.75rem;
		color: var(--cds-text-secondary);
		display: flex; gap: 0.4rem; flex-wrap: wrap;
	}
	.opt-money .handling {
		border-bottom: 1px dotted var(--cds-text-secondary, #6f6f6f);
		cursor: help;
	}
	.legs { display: flex; flex-direction: column; gap: 0.35rem; margin: 0.85rem 0; }
	.leg {
		display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap;
		padding: 0.4rem 0.5rem; background: var(--cds-layer, #f4f4f4); font-size: 0.8125rem;
	}
	.leg-route { display: inline-flex; align-items: center; gap: 0.3rem; }
	.leg-kind {
		font-size: 0.6875rem; color: var(--cds-text-secondary);
		padding: 0.05rem 0.4rem; border-radius: 3px; background: var(--cds-layer-accent, #e0e0e0);
	}
	.opt-times {
		display: flex; gap: 1.5rem; flex-wrap: wrap; font-size: 0.8125rem;
		color: var(--cds-text-secondary);
		border-top: 1px solid var(--cds-border-subtle, #e0e0e0); padding-top: 0.6rem;
	}
	.opt-actions { display: flex; justify-content: flex-end; margin-top: 0.75rem; }
	.placeholder { color: var(--cds-text-secondary); text-align: center; padding: 2rem 0; }
</style>
