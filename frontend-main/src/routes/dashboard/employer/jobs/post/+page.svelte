<script lang="ts">
	import {
		Grid, Row, Column,
		Button, TextInput, TextArea, NumberInput, Select, SelectItem,
		InlineNotification
	} from 'carbon-components-svelte';
	import { ArrowLeft, Add } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import { goto } from '$app/navigation';
	import LocationPicker from '$lib/components/LocationPicker.svelte';
	import { calculateRoute, type RouteInfo } from '$lib/google-maps';

	let jobForm = $state({
		title: '',
		description: '',
		pickupLocation: '',
		deliveryLocation: '',
		estimatedDurationHours: 4,
		dateNeeded: '',
		ratePerHour: 25,
		requiredCdlType: 'CLASS_A'
	});
	let postError = $state('');
	let postSuccess = $state('');
	let postLoading = $state(false);

	let pickupCoords = $state<{ lat: number; lng: number } | null>(null);
	let deliveryCoords = $state<{ lat: number; lng: number } | null>(null);
	let routeInfo = $state<RouteInfo | null>(null);
	let routeLoading = $state(false);

	async function recalculateRoute() {
		if (!pickupCoords || !deliveryCoords) {
			routeInfo = null;
			return;
		}
		routeLoading = true;
		try {
			routeInfo = await calculateRoute(pickupCoords, deliveryCoords);
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

	function onPickupSelected(place: { address: string; lat: number; lng: number }) {
		jobForm.pickupLocation = place.address;
		pickupCoords = { lat: place.lat, lng: place.lng };
		recalculateRoute();
	}

	function onDeliverySelected(place: { address: string; lat: number; lng: number }) {
		jobForm.deliveryLocation = place.address;
		deliveryCoords = { lat: place.lat, lng: place.lng };
		recalculateRoute();
	}

	async function postJob() {
		postError = '';
		postSuccess = '';
		postLoading = true;
		try {
			await api.post('/api/employer/jobs', {
				...jobForm,
				ratePerHour: jobForm.ratePerHour
			});
			postSuccess = 'Job posted successfully! Redirecting...';
			setTimeout(() => goto('/dashboard/employer'), 1500);
		} catch (e: any) {
			postError = e.message || 'Failed to post job';
		} finally {
			postLoading = false;
		}
	}
</script>

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard/employer" icon={ArrowLeft}>
					Back to My Jobs
				</Button>
				<h1><Add size={24} /> Post a Delivery Job</h1>
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
				<TextInput bind:value={jobForm.title}
					labelText="Job Title" placeholder="e.g. Dublin to Cork delivery" />

				<TextArea bind:value={jobForm.description}
					labelText="Description" placeholder="Describe the delivery requirements..."
					rows={3} />

				<div class="form-row locations-row">
					<LocationPicker
						labelText="Pickup Location"
						placeholder="Address or Eircode (e.g. D01 F5P2)"
						bind:value={jobForm.pickupLocation}
						onPlaceSelected={onPickupSelected}
					/>
					<LocationPicker
						labelText="Delivery Location"
						placeholder="Address or Eircode (e.g. T12 YN60)"
						bind:value={jobForm.deliveryLocation}
						onPlaceSelected={onDeliverySelected}
					/>
				</div>

				{#if routeLoading}
					<div class="route-info">
						<p class="route-calculating">Calculating route...</p>
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
					<Select bind:selected={jobForm.requiredCdlType}
						labelText="Required CDL Type">
						<SelectItem value="CLASS_A" text="Class A" />
						<SelectItem value="CLASS_B" text="Class B" />
						<SelectItem value="CLASS_C" text="Class C" />
						<SelectItem value="NON_CDL" text="Non-CDL" />
					</Select>
				</div>

				<Button on:click={postJob} disabled={postLoading || !jobForm.title || !jobForm.dateNeeded}>
					{postLoading ? 'Posting...' : 'Post Job'}
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
		max-width: 640px;
	}
	.form-row {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 1rem;
	}
	.locations-row {
		align-items: start;
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
	@media (max-width: 672px) {
		.form-row {
			grid-template-columns: 1fr;
		}
	}
</style>
