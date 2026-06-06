<script lang="ts">
	import {
		Grid, Row, Column,
		Button, TextInput, TextArea, NumberInput, Select, SelectItem,
		InlineNotification, Tag
	} from 'carbon-components-svelte';
	import { ArrowLeft, Save, Add, TrashCan, ArrowUp, ArrowDown } from 'carbon-icons-svelte';
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
	import type { Load, LoadStopType } from '$lib/types';

	const loadId = page.params.id;

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

	let legQuantities: LegQuantities = $derived({
		distanceKm: quantities.distanceKm ?? undefined,
		weightKg: quantities.weightKg ?? undefined,
		volumeM3: quantities.volumeM3 ?? undefined,
		containerCount: quantities.containerCount ?? undefined,
		pieceCount: quantities.pieceCount ?? undefined
	});

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

	let stopSeq = 0;
	function newStop(type: LoadStopType): StopDraft {
		return {
			clientId: `stop-${(stopSeq++).toString(36)}-${type}`,
			type,
			country: shipperCountry,
			address: '',
			coords: null
		};
	}

	let stops = $state<StopDraft[]>([]);

	let firstPickup = $derived(stops.find(s => s.type === 'PICKUP'));
	let lastDelivery = $derived([...stops].reverse().find(s => s.type === 'DELIVERY'));

	let loadError = $state('');
	let loadLoading = $state(true);
	let postError = $state('');
	let postSuccess = $state('');
	let postLoading = $state(false);

	onMount(async () => {
		loadLoading = true;
		loadError = '';
		try {
			const path = auth.isAdmin ? `/api/admin/loads/${loadId}` : `/api/shipper/loads/${loadId}`;
			const load = await api.get<Load>(path);
			loadForm = {
				title: load.title ?? '',
				description: load.description ?? '',
				transportMode: load.transportMode ?? 'ROAD',
				estimatedDurationHours: load.estimatedDurationHours ?? 4,
				dateNeeded: load.dateNeeded ?? '',
				ratePerHour: load.ratePerHour ?? 25,
				requiredLicenceCategory: load.requiredLicenceCategory ?? load.requiredCdlType ?? 'C'
			};
			quantities = {
				distanceKm: load.distanceKm ?? null,
				weightKg: load.weightKg ?? null,
				volumeM3: load.volumeM3 ?? null,
				containerCount: load.containerCount ?? null,
				pieceCount: load.pieceCount ?? null
			};
			stops = stopsFromLoad(load);
		} catch (e: any) {
			loadError = e.message || 'Failed to load this load.';
		} finally {
			loadLoading = false;
		}
	});

	// Build the editable stop list from the response. Prefer the ordered Stops
	// tree; fall back to the legacy pickup/delivery pair for any unmigrated load.
	function stopsFromLoad(load: Load): StopDraft[] {
		if (load.stops && load.stops.length > 0) {
			return [...load.stops]
				.sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0))
				.map(s => ({
					clientId: `stop-${(stopSeq++).toString(36)}`,
					type: s.type,
					country: s.location?.country ?? shipperCountry,
					address: s.location?.addressLine || s.location?.name || '',
					coords: (s.location?.latitude != null && s.location?.longitude != null)
						? { lat: s.location.latitude, lng: s.location.longitude }
						: null
				}));
		}
		return [
			{ ...newStop('PICKUP'), country: load.pickupCountry ?? shipperCountry, address: load.pickupLocation ?? '' },
			{ ...newStop('DELIVERY'), country: load.deliveryCountry ?? shipperCountry, address: load.deliveryLocation ?? '' }
		];
	}

	let routeInfo = $state<RouteInfo | null>(null);
	let routeLoading = $state(false);

	async function recalculateRoute() {
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
		const lastDeliveryIdx = stops.map(s => s.type).lastIndexOf('DELIVERY');
		const insertAt = lastDeliveryIdx === -1 ? stops.length : lastDeliveryIdx;
		stops = [...stops.slice(0, insertAt), newStop('WAYPOINT'), ...stops.slice(insertAt)];
	}

	function removeStop(index: number) {
		if (stops.length <= 2) return;
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

	async function saveLoad() {
		postError = '';
		postSuccess = '';
		const v = validate();
		if (v) { postError = v; return; }

		postLoading = true;
		try {
			const payload = {
				...loadForm,
				pickupLocation: firstPickup!.address,
				deliveryLocation: lastDelivery!.address,
				ratePerHour: loadForm.ratePerHour,
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

			const path = auth.isAdmin ? `/api/admin/loads/${loadId}` : `/api/shipper/loads/${loadId}`;
			await api.put(path, payload);
			postSuccess = 'Load updated successfully! Redirecting...';
			setTimeout(() => goto(auth.isAdmin ? '/dashboard/loads' : '/dashboard'), 1200);
		} catch (e: any) {
			postError = e.message || 'Failed to update load';
		} finally {
			postLoading = false;
		}
	}

	const backHref = $derived(auth.isAdmin ? '/dashboard/loads' : '/dashboard');
</script>

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href={backHref} icon={ArrowLeft}>
					Back to loads
				</Button>
				<h1 class="section-heading"><span class="icon-badge sm"><Save size={20} /></span> Edit Load</h1>
			</div>
		</Column>
	</Row>

	<Row>
		<Column lg={10} md={8} sm={4}>
			{#if loadLoading}
				<p>Loading load…</p>
			{:else if loadError}
				<InlineNotification kind="error" title="Couldn't open this load" subtitle={loadError}
					hideCloseButton />
				<Button kind="tertiary" href={backHref}>Back to loads</Button>
			{:else}
				{#if postError}
					<InlineNotification kind="error" title="Error" subtitle={postError}
						on:close={() => postError = ''} />
				{/if}
				{#if postSuccess}
					<InlineNotification kind="success" title="Success" subtitle={postSuccess}
						on:close={() => postSuccess = ''} />
				{/if}

				<div class="form-grid">
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
							Order matters — reorder via the arrow buttons. International routes can include
							border, ferry, or Eurotunnel stops between pickup and delivery.
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
								<span>Shipper pays</span>
								<strong>{formatMoney(pricingPreview.total)}</strong>
							</div>
						</div>
						<p class="pricing-hint">
							{pricingPreview.usingRateCard
								? 'Priced on the mode rate card — the server confirms the exact figure when you save.'
								: 'Falling back to rate × hours — enter the shipment quantity above to price on the rate card.'}
						</p>
					</div>

					<Button icon={Save} on:click={saveLoad}
						disabled={postLoading || !loadForm.title || !loadForm.dateNeeded}>
						{postLoading ? 'Saving...' : 'Save changes'}
					</Button>
				</div>
			{/if}
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
