<script lang="ts">
	import {
		Grid, Row, Column,
		Button, TextInput, TextArea, NumberInput, Select, SelectItem,
		InlineNotification, Tag
	} from 'carbon-components-svelte';
	import { ArrowLeft, Add, TrashCan, ArrowUp, ArrowDown, MagicWand } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import { auth } from '$lib/stores/auth.svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { onMount } from 'svelte';
	import LocationPicker from '$lib/components/LocationPicker.svelte';
	import { calculateRoute, type RouteInfo } from '$lib/google-maps';
	import { licenceCategoriesFor } from '$lib/licence-categories';
	import { TRANSPORT_MODE_OPTIONS, transportModeLabel, estimatedCommissionPct } from '$lib/transport-modes';
	import { estimateLegCarrierCost, chargeableQuantity, chargeUnitForMode, type LegQuantities } from '$lib/pricing';
	import { formatMoney } from '$lib/money';
	import { HAULAGE_COUNTRIES } from '$lib/countries';
	import type { LoadStopType } from '$lib/types';

	type ShipperOption = { id: number; companyName: string; email: string; country?: string };

	type StopDraft = {
		clientId: string;
		type: LoadStopType;
		country: string;
		address: string;
		coords: { lat: number; lng: number } | null;
	};

	const STOP_TYPE_OPTIONS: { value: LoadStopType; label: string }[] = [
		{ value: 'PICKUP', label: 'Pickup' },
		{ value: 'DELIVERY', label: 'Delivery' },
		{ value: 'WAYPOINT', label: 'Waypoint' },
		{ value: 'REST', label: 'Rest stop' },
		{ value: 'BORDER', label: 'Border crossing' },
		{ value: 'FERRY_TERMINAL', label: 'Ferry terminal' },
		{ value: 'EUROTUNNEL', label: 'Eurotunnel' }
	];

	const STOP_TAG_COLOR: Record<LoadStopType, 'green' | 'red' | 'blue' | 'purple' | 'cyan' | 'teal' | 'magenta'> = {
		PICKUP: 'green',
		DELIVERY: 'red',
		WAYPOINT: 'blue',
		REST: 'purple',
		BORDER: 'magenta',
		FERRY_TERMINAL: 'cyan',
		EUROTUNNEL: 'teal'
	};

	let shipperCountry = $state('IE');
	let licenceOptions = $derived(licenceCategoriesFor(shipperCountry));

	let loadForm = $state({
		title: '',
		description: '',
		transportMode: 'ROAD',
		estimatedDurationHours: 4,
		dateNeeded: '',
		ratePerHour: 25,
		requiredLicenceCategory: 'C'
	});

	// Per-mode quantity inputs that drive the rate card (per-km / per-container /
	// per-chargeable-kg). Left blank → the server prices on rate × hours instead.
	let quantities = $state<{
		distanceKm: number | null;
		weightKg: number | null;
		volumeM3: number | null;
		containerCount: number | null;
		pieceCount: number | null;
	}>({ distanceKm: null, weightKg: null, volumeM3: null, containerCount: null, pieceCount: null });

	const UNIT_LABELS: Record<string, string> = {
		PER_KM: 'per km', PER_CONTAINER: 'per container',
		PER_CHARGEABLE_KG: 'per chargeable-kg', PER_PIECE: 'per piece',
		PER_HOUR: 'per hour', FLAT: 'flat'
	};
	const UNIT_QTY: Record<string, string> = {
		PER_KM: 'km', PER_CONTAINER: 'containers',
		PER_CHARGEABLE_KG: 'chargeable-kg', PER_PIECE: 'pieces'
	};
	const unitLabel = (u: string) => UNIT_LABELS[u] ?? u;
	const unitQty = (u: string) => UNIT_QTY[u] ?? '';

	// LegQuantities for the rate-card mirror (null → undefined = "not provided").
	let legQuantities: LegQuantities = $derived({
		distanceKm: quantities.distanceKm ?? undefined,
		weightKg: quantities.weightKg ?? undefined,
		volumeM3: quantities.volumeM3 ?? undefined,
		containerCount: quantities.containerCount ?? undefined,
		pieceCount: quantities.pieceCount ?? undefined
	});

	// Live estimate of what the shipper will be charged. Carrier cost comes from
	// the mode's rate card when the relevant quantity is present (mirrors the
	// server's PricingPolicy); otherwise it falls back to rate × hours. The
	// platform fee varies by mode; the backend recomputes the authoritative figure.
	let pricingPreview = $derived.by(() => {
		const mode = loadForm.transportMode;
		const hours = Number(loadForm.estimatedDurationHours) || 0;
		const rate = Number(loadForm.ratePerHour) || 0;
		const rateCardCost = estimateLegCarrierCost(mode, legQuantities);
		const usingRateCard = rateCardCost != null;
		const carrierCost = rateCardCost ?? hours * rate;
		const pct = estimatedCommissionPct(mode);
		const fee = carrierCost * (pct / 100);
		return {
			carrierCost, pct, fee, total: carrierCost + fee,
			usingRateCard, unit: chargeUnitForMode(mode), qty: chargeableQuantity(mode, legQuantities)
		};
	});

	function newStop(type: LoadStopType): StopDraft {
		return {
			clientId: `stop-${Math.random().toString(36).slice(2, 9)}`,
			type,
			country: shipperCountry,
			address: '',
			coords: null
		};
	}

	let stops = $state<StopDraft[]>([newStop('PICKUP'), newStop('DELIVERY')]);

	// First PICKUP / last DELIVERY drive the legacy API shape and the route calc.
	let firstPickup = $derived(stops.find(s => s.type === 'PICKUP'));
	let lastDelivery = $derived([...stops].reverse().find(s => s.type === 'DELIVERY'));

	let postError = $state('');
	let postSuccess = $state('');
	let postLoading = $state(false);

	let shippers = $state<ShipperOption[]>([]);
	let selectedShipperId = $state<string>('');

	onMount(async () => {
		if (auth.isAdmin) {
			try {
				shippers = await api.get<ShipperOption[]>('/api/admin/shippers');
				const hinted = page.url.searchParams.get('shipperId');
				if (hinted && shippers.some(e => String(e.id) === hinted)) {
					selectedShipperId = hinted;
				} else if (shippers.length > 0) {
					selectedShipperId = String(shippers[0].id);
				}
			} catch {
				shippers = [];
			}
		}
	});

	let routeInfo = $state<RouteInfo | null>(null);
	let routeLoading = $state(false);

	async function recalculateRoute() {
		// Route through every stop that has coordinates, in their current order,
		// so waypoints (and border/ferry/rest stops) count toward distance + time.
		const points = stops.filter((s) => s.coords).map((s) => s.coords!);
		if (points.length < 2) {
			routeInfo = null;
			return;
		}
		const origin = points[0];
		const destination = points[points.length - 1];
		const intermediates = points.slice(1, -1);
		routeLoading = true;
		try {
			routeInfo = await calculateRoute(origin, destination, intermediates);
			if (routeInfo) {
				const hours = Math.round((routeInfo.durationSeconds / 3600) * 100) / 100;
				loadForm = { ...loadForm, estimatedDurationHours: hours };
				// Feed the road rate card — distance is its chargeable quantity.
				quantities = { ...quantities, distanceKm: Math.round(routeInfo.distanceKm * 10) / 10 };
			}
		} catch {
			routeInfo = null;
		} finally {
			routeLoading = false;
		}
	}

	function onStopPlaceSelected(index: number, place: { address: string; lat: number; lng: number }) {
		stops[index].address = place.address;
		stops[index].coords = { lat: place.lat, lng: place.lng };
		recalculateRoute();
	}

	function addStop() {
		// Insert before the final DELIVERY when there is one, otherwise append.
		const lastDeliveryIdx = stops.map(s => s.type).lastIndexOf('DELIVERY');
		const insertAt = lastDeliveryIdx === -1 ? stops.length : lastDeliveryIdx;
		stops = [...stops.slice(0, insertAt), newStop('WAYPOINT'), ...stops.slice(insertAt)];
	}

	function removeStop(index: number) {
		if (stops.length <= 2) return; // keep at least pickup + delivery
		stops = stops.filter((_, i) => i !== index);
		recalculateRoute();
	}

	function moveStop(index: number, delta: -1 | 1) {
		const target = index + delta;
		if (target < 0 || target >= stops.length) return;
		const copy = [...stops];
		[copy[index], copy[target]] = [copy[target], copy[index]];
		stops = copy;
		recalculateRoute();
	}

	function validate(): string | null {
		if (!loadForm.title.trim()) return 'Title is required.';
		if (!loadForm.dateNeeded) return 'Date is required.';
		if (!firstPickup || !firstPickup.address.trim()) return 'At least one pickup with an address is required.';
		if (!lastDelivery || !lastDelivery.address.trim()) return 'At least one delivery with an address is required.';
		const blank = stops.findIndex(s => !s.address.trim());
		if (blank !== -1) return `Stop ${blank + 1} has no address.`;
		return null;
	}

	// --- Demo convenience: fill the form with a realistic sample load. Shown to
	// shippers/admins only — handy for exercising the pricing preview per mode. ---
	type LoadSample = {
		mode: string; title: string; description: string;
		pickup: string; pickupCountry: string; delivery: string; deliveryCountry: string;
		hours: number; rate: number; licence: string;
		q: Partial<{ distanceKm: number; weightKg: number; volumeM3: number; containerCount: number; pieceCount: number }>;
	};

	const LOAD_SAMPLES: LoadSample[] = [
		{ mode: 'ROAD', title: 'Dublin → Cork pallet run',
		  description: '12 ambient pallets, tail-lift required. Same-day delivery.',
		  pickup: 'Dublin, Ireland', pickupCountry: 'IE', delivery: 'Cork, Ireland', deliveryCountry: 'IE',
		  hours: 3.5, rate: 30, licence: 'C', q: { distanceKm: 255 } },
		{ mode: 'OCEAN', title: 'Rotterdam → Dublin FCL',
		  description: '2 × 40ft containers of machinery parts, full-container load.',
		  pickup: 'Port of Rotterdam, Netherlands', pickupCountry: 'NL', delivery: 'Dublin Port, Ireland', deliveryCountry: 'IE',
		  hours: 8, rate: 40, licence: 'C', q: { containerCount: 2 } },
		{ mode: 'AIR', title: 'Cologne → Dublin air freight',
		  description: 'Time-critical pharma, temperature-controlled, 450 kg.',
		  pickup: 'Cologne Bonn Airport, Germany', pickupCountry: 'DE', delivery: 'Dublin Airport, Ireland', deliveryCountry: 'IE',
		  hours: 5, rate: 50, licence: 'C', q: { weightKg: 450, volumeM3: 1.8 } },
		{ mode: 'RAIL', title: 'Duisburg → Lyon intermodal rail',
		  description: '3 containers, freight-all-kinds, scheduled service.',
		  pickup: 'Duisburg Terminal, Germany', pickupCountry: 'DE', delivery: 'Lyon Terminal, France', deliveryCountry: 'FR',
		  hours: 14, rate: 35, licence: 'C', q: { containerCount: 3 } }
	];

	function autofillForm() {
		const s = LOAD_SAMPLES[Math.floor(Math.random() * LOAD_SAMPLES.length)];
		const due = new Date();
		due.setDate(due.getDate() + 7);
		loadForm = {
			title: s.title,
			description: s.description,
			transportMode: s.mode,
			estimatedDurationHours: s.hours,
			dateNeeded: due.toISOString().split('T')[0],
			ratePerHour: s.rate,
			requiredLicenceCategory: s.licence
		};
		quantities = {
			distanceKm: s.q.distanceKm ?? null,
			weightKg: s.q.weightKg ?? null,
			volumeM3: s.q.volumeM3 ?? null,
			containerCount: s.q.containerCount ?? null,
			pieceCount: s.q.pieceCount ?? null
		};
		stops = [
			{ ...newStop('PICKUP'), country: s.pickupCountry, address: s.pickup },
			{ ...newStop('DELIVERY'), country: s.deliveryCountry, address: s.delivery }
		];
		routeInfo = null;
		postError = '';
		postSuccess = '';
	}

	async function postLoad() {
		postError = '';
		postSuccess = '';
		const v = validate();
		if (v) { postError = v; return; }

		postLoading = true;
		try {
			// Send the full ordered Stops list. Legacy pickup/delivery fields are
			// still populated for any consumer that hasn't migrated; the backend
			// uses stops when present and ignores them.
			const payload = {
				...loadForm,
				pickupLocation: firstPickup!.address,
				deliveryLocation: lastDelivery!.address,
				ratePerHour: loadForm.ratePerHour,
				// Per-mode quantities so the server prices on the rate card.
				// Only the populated ones are sent (undefined is dropped by JSON).
				distanceKm: quantities.distanceKm ?? undefined,
				weightKg: quantities.weightKg ?? undefined,
				volumeM3: quantities.volumeM3 ?? undefined,
				containerCount: quantities.containerCount ?? undefined,
				pieceCount: quantities.pieceCount ?? undefined,
				stops: stops.map(s => ({
					type: s.type,
					locationName: s.address,
					addressLine: s.address,
					country: s.country,
					latitude: s.coords?.lat,
					longitude: s.coords?.lng
				}))
			};

			if (auth.isAdmin) {
				if (!selectedShipperId) {
					postError = 'Please choose an shipper to create the load under.';
					postLoading = false;
					return;
				}
				await api.post(
					`/api/admin/loads?shipperId=${encodeURIComponent(selectedShipperId)}`,
					payload
				);
			} else {
				await api.post('/api/shipper/loads', payload);
			}
			postSuccess = 'Load created successfully! Redirecting...';
			setTimeout(() => goto('/dashboard'), 1500);
		} catch (e: any) {
			postError = e.message || 'Failed to create load';
		} finally {
			postLoading = false;
		}
	}
</script>

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard" icon={ArrowLeft}>
					Back to My Loads
				</Button>
				<h1 class="section-heading"><span class="icon-badge sm"><Add size={24} /></span> Create a Load</h1>
				<Button kind="ghost" size="small" href="/dashboard/loads/post-intermodal">
					Shipping across multiple modes? Post an intermodal load &rarr;
				</Button>
			</div>
		</Column>
	</Row>

	<Row>
		<Column lg={10} md={8} sm={4}>
			{#if postError}
				<InlineNotification kind="error" title="Error" subtitle={postError}
					on:close={() => postError = ''} />
			{/if}
			{#if postSuccess}
				<InlineNotification kind="success" title="Success" subtitle={postSuccess}
					on:close={() => postSuccess = ''} />
			{/if}

			<div class="form-grid">
				{#if auth.isShipper || auth.isAdmin}
					<div class="autofill-bar">
						<Button kind="tertiary" size="small" icon={MagicWand} on:click={autofillForm}>
							Autofill with sample data
						</Button>
						<span class="autofill-hint">Fills a random demo load — handy for quick testing.</span>
					</div>
				{/if}

				{#if auth.isAdmin}
					<Select bind:selected={selectedShipperId}
						labelText="Create Load On Behalf Of (Shipper)">
						{#each shippers as emp}
							<SelectItem value={String(emp.id)}
								text="{emp.companyName} ({emp.email})" />
						{/each}
					</Select>
				{/if}

				<TextInput bind:value={loadForm.title}
					labelText="Load Title" placeholder="e.g. Dublin to Cork delivery" />

				<TextArea bind:value={loadForm.description}
					labelText="Description" placeholder="Describe the delivery requirements..."
					rows={3} />

				<Select bind:selected={loadForm.transportMode} labelText="Transport Mode">
					{#each TRANSPORT_MODE_OPTIONS as opt}
						<SelectItem value={opt.value} text={opt.label} />
					{/each}
				</Select>

				<div class="stops-section">
					<div class="stops-header">
						<h3>Route</h3>
						<Button kind="tertiary" size="small" icon={Add} on:click={addStop}>
							Add stop
						</Button>
					</div>
					<p class="stops-hint">
						Order matters — drag-free reordering via the arrow buttons. International
						routes can include border, ferry, or Eurotunnel stops between pickup and
						delivery.
					</p>

					{#each stops as stop, i (stop.clientId)}
						<div class="stop-row">
							<div class="stop-index">
								<span class="seq">{i + 1}</span>
								<Tag type={STOP_TAG_COLOR[stop.type]} size="sm">{stop.type}</Tag>
							</div>

							<div class="stop-fields">
								<Select bind:selected={stop.type} labelText="Type" hideLabel>
									{#each STOP_TYPE_OPTIONS as opt}
										<SelectItem value={opt.value} text={opt.label} />
									{/each}
								</Select>
								<Select bind:selected={stop.country} labelText="Country" hideLabel>
									{#each HAULAGE_COUNTRIES as c}
										<SelectItem value={c.code} text="{c.code} — {c.name}" />
									{/each}
								</Select>
								<LocationPicker
									labelText={`Stop ${i + 1} address`}
									placeholder="Address, Eircode, or postcode"
									bind:value={stop.address}
									onPlaceSelected={(place) => onStopPlaceSelected(i, place)}
								/>
							</div>

							<div class="stop-actions">
								<Button kind="ghost" size="small" icon={ArrowUp}
									iconDescription="Move up"
									disabled={i === 0}
									on:click={() => moveStop(i, -1)} />
								<Button kind="ghost" size="small" icon={ArrowDown}
									iconDescription="Move down"
									disabled={i === stops.length - 1}
									on:click={() => moveStop(i, 1)} />
								<Button kind="danger-ghost" size="small" icon={TrashCan}
									iconDescription="Remove stop"
									disabled={stops.length <= 2}
									on:click={() => removeStop(i)} />
							</div>
						</div>
					{/each}
				</div>

				{#if routeLoading}
					<div class="route-info">
						<p class="route-calculating">Calculating route through all stops...</p>
					</div>
				{:else if routeInfo}
					<div class="route-info">
						<div class="route-detail">
							<strong>Distance:</strong> {routeInfo.distanceText} ({routeInfo.distanceKm.toFixed(1)} km)
						</div>
						<div class="route-detail">
							<strong>Estimated Drive Time:</strong> {routeInfo.durationText}
						</div>
					</div>
				{/if}

				<div class="quantity-section">
					<h3>Shipment quantity</h3>
					<p class="quantity-hint">
						Priced on the {transportModeLabel(loadForm.transportMode)} rate card
						({unitLabel(pricingPreview.unit)}). Leave blank to fall back to rate &times; hours.
					</p>
					{#if loadForm.transportMode === 'ROAD'}
						<NumberInput bind:value={quantities.distanceKm}
							label="Distance (km)" min={0} step={1} allowEmpty
							helperText="Auto-filled from the route above — override if needed." />
					{:else if loadForm.transportMode === 'RAIL' || loadForm.transportMode === 'OCEAN'}
						<NumberInput bind:value={quantities.containerCount}
							label="Containers" min={0} step={1} allowEmpty />
					{:else if loadForm.transportMode === 'AIR'}
						<div class="form-row">
							<NumberInput bind:value={quantities.weightKg}
								label="Weight (kg)" min={0} step={1} allowEmpty />
							<NumberInput bind:value={quantities.volumeM3}
								label="Volume (m³)" min={0} step={0.1} allowEmpty
								helperText="Chargeable kg = max(actual, volume × 167)." />
						</div>
					{/if}
				</div>

				<div class="form-row">
					<TextInput bind:value={loadForm.dateNeeded}
						labelText="Date Needed (YYYY-MM-DD)" placeholder="2026-04-10"
						type="date" />
					<NumberInput bind:value={loadForm.estimatedDurationHours}
						label="Estimated Hours" min={0.01} max={10} step={0.01} />
				</div>

				<div class="form-row">
					<NumberInput bind:value={loadForm.ratePerHour}
						label="Rate per Hour (&euro;)" min={10} max={200} step={1} />
					<Select bind:selected={loadForm.requiredLicenceCategory}
						labelText="Required Licence Category">
						{#each licenceOptions as opt}
							<SelectItem value={opt.code} text={opt.label} />
						{/each}
					</Select>
				</div>

				<div class="pricing-preview">
					<h4>Pricing preview</h4>
					<div class="pricing-rows">
						<div class="pricing-line">
							{#if pricingPreview.usingRateCard}
								<span>Carrier cost · {unitLabel(pricingPreview.unit)} ({Math.round(pricingPreview.qty ?? 0)} {unitQty(pricingPreview.unit)})</span>
							{:else}
								<span>Carrier cost · hourly ({loadForm.estimatedDurationHours}h × {formatMoney(loadForm.ratePerHour)})</span>
							{/if}
							<strong>{formatMoney(pricingPreview.carrierCost)}</strong>
						</div>
						<div class="pricing-line">
							<span>Platform fee · {transportModeLabel(loadForm.transportMode)} ({pricingPreview.pct}%)</span>
							<strong>{formatMoney(pricingPreview.fee)}</strong>
						</div>
						<div class="pricing-line pricing-total">
							<span>You pay</span>
							<strong>{formatMoney(pricingPreview.total)}</strong>
						</div>
					</div>
					<p class="pricing-hint">
						{pricingPreview.usingRateCard
							? 'Priced on the mode rate card — the server confirms the exact figure when you post.'
							: 'Falling back to rate × hours — enter the shipment quantity above to price on the rate card.'}
					</p>
				</div>

				<Button on:click={postLoad} disabled={postLoading || !loadForm.title || !loadForm.dateNeeded}>
					{postLoading ? 'Creating...' : 'Create a Load'}
				</Button>
			</div>
		</Column>
	</Row>
</Grid>

<style>
	.page-header {
		margin-bottom: 1.5rem;
	}
	.page-header h1 {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		margin-top: 0.5rem;
	}
	.form-grid {
		display: flex;
		flex-direction: column;
		gap: 1rem;
		max-width: 760px;
	}
	.form-row {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 1rem;
	}
	.autofill-bar {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		flex-wrap: wrap;
	}
	.autofill-hint {
		font-size: 0.8125rem;
		color: var(--cds-text-secondary);
	}
	.stops-section {
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
		padding: 1rem;
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-interactive, #0f62fe);
	}
	.stops-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.stops-header h3 {
		margin: 0;
		font-size: 1rem;
	}
	.stops-hint {
		font-size: 0.8125rem;
		color: var(--cds-text-secondary);
		margin: 0;
	}
	.stop-row {
		display: grid;
		grid-template-columns: 6rem 1fr auto;
		gap: 0.75rem;
		align-items: start;
		padding: 0.5rem;
		background: var(--cds-background, #fff);
	}
	.stop-index {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.25rem;
		padding-top: 0.25rem;
	}
	.stop-index .seq {
		font-weight: 600;
		font-size: 0.875rem;
	}
	.stop-fields {
		display: grid;
		grid-template-columns: 8rem 9rem 1fr;
		gap: 0.5rem;
	}
	.stop-actions {
		display: flex;
		gap: 0.125rem;
		align-items: center;
	}
	.route-info {
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-interactive, #0f62fe);
		padding: 0.75rem 1rem;
		display: flex;
		gap: 2rem;
		flex-wrap: wrap;
	}
	.route-detail {
		font-size: 0.875rem;
	}
	.route-calculating {
		font-size: 0.875rem;
		color: var(--cds-text-secondary);
		font-style: italic;
		margin: 0;
	}
	.quantity-section {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
		padding: 1rem;
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-interactive, #0f62fe);
	}
	.quantity-section h3 {
		margin: 0;
		font-size: 1rem;
	}
	.quantity-hint {
		font-size: 0.8125rem;
		color: var(--cds-text-secondary);
		margin: 0;
	}
	.pricing-preview {
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-support-success, #24a148);
		padding: 0.75rem 1rem;
	}
	.pricing-preview h4 {
		margin: 0 0 0.5rem;
		font-size: 0.875rem;
		text-transform: uppercase;
		letter-spacing: 0.02em;
		color: var(--cds-text-secondary);
	}
	.pricing-rows {
		display: flex;
		flex-direction: column;
		gap: 0.25rem;
	}
	.pricing-line {
		display: flex;
		justify-content: space-between;
		gap: 1rem;
		font-size: 0.875rem;
	}
	.pricing-total {
		border-top: 1px solid var(--cds-border-subtle, #e0e0e0);
		margin-top: 0.25rem;
		padding-top: 0.375rem;
		font-size: 1rem;
	}
	.pricing-hint {
		font-size: 0.75rem;
		color: var(--cds-text-secondary);
		margin: 0.5rem 0 0;
	}
	@media (max-width: 672px) {
		.form-row,
		.stop-fields {
			grid-template-columns: 1fr;
		}
		.stop-row {
			grid-template-columns: 1fr;
		}
	}
</style>
