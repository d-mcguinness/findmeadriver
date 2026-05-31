<script lang="ts">
	import {
		Grid, Row, Column,
		Button, TextInput, TextArea, NumberInput, Select, SelectItem,
		InlineNotification, Tag
	} from 'carbon-components-svelte';
	import { ArrowLeft, Add, TrashCan, ArrowUp, ArrowDown } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import { auth } from '$lib/stores/auth.svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { onMount } from 'svelte';
	import LocationPicker from '$lib/components/LocationPicker.svelte';
	import { calculateRoute, type RouteInfo } from '$lib/google-maps';
	import { licenceCategoriesFor } from '$lib/licence-categories';
	import { TRANSPORT_MODE_OPTIONS, transportModeLabel, estimatedCommissionPct } from '$lib/transport-modes';
	import { formatMoney } from '$lib/money';
	import { HAULAGE_COUNTRIES } from '$lib/countries';
	import type { JobStopType } from '$lib/types';

	type ShipperOption = { id: number; companyName: string; email: string; country?: string };

	type StopDraft = {
		clientId: string;
		type: JobStopType;
		country: string;
		address: string;
		coords: { lat: number; lng: number } | null;
	};

	const STOP_TYPE_OPTIONS: { value: JobStopType; label: string }[] = [
		{ value: 'PICKUP', label: 'Pickup' },
		{ value: 'DELIVERY', label: 'Delivery' },
		{ value: 'WAYPOINT', label: 'Waypoint' },
		{ value: 'REST', label: 'Rest stop' },
		{ value: 'BORDER', label: 'Border crossing' },
		{ value: 'FERRY_TERMINAL', label: 'Ferry terminal' },
		{ value: 'EUROTUNNEL', label: 'Eurotunnel' }
	];

	const STOP_TAG_COLOR: Record<JobStopType, 'green' | 'red' | 'blue' | 'purple' | 'cyan' | 'teal' | 'magenta'> = {
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

	let jobForm = $state({
		title: '',
		description: '',
		transportMode: 'ROAD',
		estimatedDurationHours: 4,
		dateNeeded: '',
		ratePerHour: 25,
		requiredLicenceCategory: 'C'
	});

	// Live estimate of what the shipper will be charged. Carrier cost is
	// rate × hours; the platform fee varies by transport mode. The backend
	// recomputes the authoritative figure on save.
	let pricingPreview = $derived.by(() => {
		const hours = Number(jobForm.estimatedDurationHours) || 0;
		const rate = Number(jobForm.ratePerHour) || 0;
		const carrierCost = hours * rate;
		const pct = estimatedCommissionPct(jobForm.transportMode);
		const fee = carrierCost * (pct / 100);
		return { carrierCost, pct, fee, total: carrierCost + fee };
	});

	function newStop(type: JobStopType): StopDraft {
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
				jobForm = { ...jobForm, estimatedDurationHours: hours };
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
		if (!jobForm.title.trim()) return 'Title is required.';
		if (!jobForm.dateNeeded) return 'Date is required.';
		if (!firstPickup || !firstPickup.address.trim()) return 'At least one pickup with an address is required.';
		if (!lastDelivery || !lastDelivery.address.trim()) return 'At least one delivery with an address is required.';
		const blank = stops.findIndex(s => !s.address.trim());
		if (blank !== -1) return `Stop ${blank + 1} has no address.`;
		return null;
	}

	async function postJob() {
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
				...jobForm,
				pickupLocation: firstPickup!.address,
				deliveryLocation: lastDelivery!.address,
				ratePerHour: jobForm.ratePerHour,
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
					postError = 'Please choose an shipper to create the job under.';
					postLoading = false;
					return;
				}
				await api.post(
					`/api/admin/jobs?shipperId=${encodeURIComponent(selectedShipperId)}`,
					payload
				);
			} else {
				await api.post('/api/shipper/jobs', payload);
			}
			postSuccess = 'Job created successfully! Redirecting...';
			setTimeout(() => goto('/dashboard'), 1500);
		} catch (e: any) {
			postError = e.message || 'Failed to create job';
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
					Back to My Jobs
				</Button>
				<h1><Add size={24} /> Create a Job</h1>
				<Button kind="ghost" size="small" href="/dashboard/jobs/post-intermodal">
					Shipping across multiple modes? Post an intermodal job &rarr;
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
				{#if auth.isAdmin}
					<Select bind:selected={selectedShipperId}
						labelText="Create Job On Behalf Of (Shipper)">
						{#each shippers as emp}
							<SelectItem value={String(emp.id)}
								text="{emp.companyName} ({emp.email})" />
						{/each}
					</Select>
				{/if}

				<TextInput bind:value={jobForm.title}
					labelText="Job Title" placeholder="e.g. Dublin to Cork delivery" />

				<TextArea bind:value={jobForm.description}
					labelText="Description" placeholder="Describe the delivery requirements..."
					rows={3} />

				<Select bind:selected={jobForm.transportMode} labelText="Transport Mode">
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

				<div class="form-row">
					<TextInput bind:value={jobForm.dateNeeded}
						labelText="Date Needed (YYYY-MM-DD)" placeholder="2026-04-10"
						type="date" />
					<NumberInput bind:value={jobForm.estimatedDurationHours}
						label="Estimated Hours" min={0.01} max={10} step={0.01} />
				</div>

				<div class="form-row">
					<NumberInput bind:value={jobForm.ratePerHour}
						label="Rate per Hour (&euro;)" min={10} max={200} step={1} />
					<Select bind:selected={jobForm.requiredLicenceCategory}
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
							<span>Carrier cost ({jobForm.estimatedDurationHours}h × {formatMoney(jobForm.ratePerHour)})</span>
							<strong>{formatMoney(pricingPreview.carrierCost)}</strong>
						</div>
						<div class="pricing-line">
							<span>Platform fee · {transportModeLabel(jobForm.transportMode)} ({pricingPreview.pct}%)</span>
							<strong>{formatMoney(pricingPreview.fee)}</strong>
						</div>
						<div class="pricing-line pricing-total">
							<span>You pay</span>
							<strong>{formatMoney(pricingPreview.total)}</strong>
						</div>
					</div>
					<p class="pricing-hint">
						Estimate — the exact platform fee is confirmed on the server when you post.
					</p>
				</div>

				<Button on:click={postJob} disabled={postLoading || !jobForm.title || !jobForm.dateNeeded}>
					{postLoading ? 'Creating...' : 'Create a Job'}
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
