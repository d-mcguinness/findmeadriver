<script lang="ts">
	import {
		Grid, Row, Column, Tile,
		Button, TextArea,
		InlineNotification, Tag, Modal
	} from 'carbon-components-svelte';
	import { Enterprise, Add, Checkmark, Close, StarFilled, Star, Edit } from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
	import { api } from '$lib/api';
	import { shipperState } from '$lib/stores/shipperState.svelte';
	import type { Load, LoadApplication } from '$lib/types';
	import { onMount } from 'svelte';
	import LoadsTable from '$lib/components/admin/LoadsTable.svelte';

	// My Loads state — backed by the shared store so mutations refresh StatsRow too.
	let loads = $derived(shipperState.loads);
	let loadsLoading = $state(false);

	// Aggregate metrics for the summary row above the table.
	let metrics = $derived.by(() => {
		let applications = 0, hours = 0, value = 0, completedSpend = 0;
		for (const j of loads) {
			applications += j.applicationCount ?? 0;
			const h = j.estimatedDurationHours ?? 0;
			const r = Number(j.ratePerHour ?? 0);
			hours += h;
			value += h * r;
			if (j.status === 'COMPLETED') completedSpend += h * r;
		}
		return { applications, hours, value, completedSpend };
	});

	// Applications state
	let selectedLoadForApps = $state<Load | null>(null);
	let applications = $state<LoadApplication[]>([]);
	let appsLoading = $state(false);
	let appsModalOpen = $state(false);

	// Rating state
	let ratingModalOpen = $state(false);
	let ratingLoadId = $state<number | null>(null);
	let ratingLoadTitle = $state('');
	let ratingScore = $state(0);
	let ratingComment = $state('');
	let ratingError = $state('');



	async function loadLoads() {
		loadsLoading = true;
		try {
			await shipperState.reloadLoads();
		} finally {
			loadsLoading = false;
		}
	}

	async function cancelLoad(id: number) {
		try {
			await api.put(`/api/shipper/loads/${id}/cancel`, {});
			loadLoads();
		} catch { /* ignore */ }
	}

	async function startLoad(id: number) {
		try {
			await api.put(`/api/shipper/loads/${id}/status`, { status: 'IN_PROGRESS' });
			loadLoads();
		} catch { /* ignore */ }
	}

	async function completeLoad(id: number, title: string) {
		try {
			await api.put(`/api/shipper/loads/${id}/status`, { status: 'COMPLETED' });
			loadLoads();
			ratingLoadId = id;
			ratingLoadTitle = title;
			ratingScore = 0;
			ratingComment = '';
			ratingError = '';
			ratingModalOpen = true;
		} catch { /* ignore */ }
	}

	async function submitRating() {
		if (!ratingLoadId || ratingScore === 0) return;
		ratingError = '';
		try {
			await api.post(`/api/shipper/loads/${ratingLoadId}/rate`, {
				score: ratingScore,
				comment: ratingComment
			});
			ratingModalOpen = false;
		} catch (e: any) {
			ratingError = e.message || 'Failed to submit rating';
		}
	}

	async function viewApplications(load: Load) {
		selectedLoadForApps = load;
		appsModalOpen = true;
		appsLoading = true;
		try {
			applications = await api.get<LoadApplication[]>(`/api/shipper/loads/${load.id}/applications`);
		} catch {
			applications = [];
		} finally {
			appsLoading = false;
		}
	}

	async function acceptApplication(id: number) {
		try {
			await api.put(`/api/shipper/applications/${id}/accept`, {});
			if (selectedLoadForApps) viewApplications(selectedLoadForApps);
			loadLoads();
		} catch { /* ignore */ }
	}

	async function rejectApplication(id: number) {
		try {
			await api.put(`/api/shipper/applications/${id}/reject`, {});
			if (selectedLoadForApps) viewApplications(selectedLoadForApps);
		} catch { /* ignore */ }
	}

	function statusKind(status: string): 'blue' | 'green' | 'red' | 'gray' | 'cyan' {
		switch (status) {
			case 'OPEN': return 'green';
			case 'ASSIGNED': return 'blue';
			case 'IN_PROGRESS': return 'cyan';
			case 'COMPLETED': return 'gray';
			case 'CANCELLED': return 'red';
			default: return 'gray';
		}
	}

	function appStatusKind(status: string): 'blue' | 'green' | 'red' | 'gray' {
		switch (status) {
			case 'PENDING': return 'blue';
			case 'ACCEPTED': return 'green';
			case 'REJECTED': return 'red';
			default: return 'gray';
		}
	}

	function renderStars(rating: number): string {
		const full = Math.round(rating);
		return '\u2605'.repeat(full) + '\u2606'.repeat(5 - full);
	}

	onMount(loadLoads);
</script>

{#snippet shipperActions(load: Load)}
	<div class="row-actions">
		{#if load.status === 'OPEN'}
			<Button size="small" kind="ghost" icon={Edit} iconDescription="Edit load"
				href={`/dashboard/loads/${load.id}/edit`} />
		{/if}
		{#if load.applicationCount > 0}
			<Button size="small" kind="secondary"
				on:click={() => viewApplications(load)}>
				Applications ({load.applicationCount})
			</Button>
		{/if}
		{#if load.status === 'ASSIGNED'}
			<Button size="small" kind="primary"
				on:click={() => startLoad(load.id)}>
				Start
			</Button>
		{/if}
		{#if load.status === 'IN_PROGRESS'}
			<Button size="small" kind="primary"
				on:click={() => completeLoad(load.id, load.title)}>
				Complete
			</Button>
		{/if}
		{#if load.status === 'OPEN' || load.status === 'ASSIGNED'}
			<Button size="small" kind="danger-tertiary"
				on:click={() => cancelLoad(load.id)}>
				Cancel
			</Button>
		{/if}
	</div>
{/snippet}

<Grid>
	<Row>
		<Column>
			<h2>My Loads</h2>
			<div class="dash-actions">
				<Button size="small" kind="tertiary" href="/dashboard/loads/post-intermodal">
					Post intermodal load
				</Button>
				<Button size="small" kind="ghost" href="/dashboard/itineraries">
					View itineraries
				</Button>
			</div>

			{#if loadsLoading}
				<p>Loading loads...</p>
			{:else if loads.length === 0}
				<InlineNotification kind="info" title="No loads yet"
					subtitle="Create your first load to start finding carriers."
					hideCloseButton />
				<Button href="/dashboard/loads/post" icon={Add}>Create a Load</Button>
			{:else}
				<div class="metrics-row">
					<div class="metric">
						<div class="metric-value">{metrics.applications}</div>
						<div class="metric-label">Applications received</div>
					</div>
					<div class="metric">
						<div class="metric-value">{metrics.hours.toFixed(1)}h</div>
						<div class="metric-label">Hours posted</div>
					</div>
					<div class="metric">
						<div class="metric-value">&euro;{metrics.value.toFixed(0)}</div>
						<div class="metric-label">Total load value</div>
					</div>
					<div class="metric">
						<div class="metric-value">&euro;{metrics.completedSpend.toFixed(0)}</div>
						<div class="metric-label">Completed spend</div>
					</div>
				</div>
				<LoadsTable {loads} actions={shipperActions} addHref="/dashboard/loads/post" addLabel="Create a Load" />
			{/if}
		</Column>
	</Row>
</Grid>

<!-- Applications Modal -->
<Modal
	bind:open={appsModalOpen}
	modalHeading="Applications for {selectedLoadForApps?.title}"
	passiveModal
	size="lg"
>
	{#if appsLoading}
		<p>Loading applications...</p>
	{:else if applications.length === 0}
		<p>No applications yet for this load.</p>
	{:else}
		<div class="applications-list">
			{#each applications as app}
				<Tile class="app-tile">
					<div class="app-header">
						<div class="carrier-info">
							<strong>{app.carrierName}</strong>
							{#if app.carrierVerified}
								<span class="verified-badge" title="All documents verified">
									<Checkmark size={16} /> Verified
								</span>
							{/if}
							{#if app.carrierAverageRating}
								<span class="carrier-rating" title="{app.carrierRatingCount} ratings">
									{renderStars(app.carrierAverageRating)} ({app.carrierAverageRating.toFixed(1)})
								</span>
							{/if}
						</div>
						<Tag type={appStatusKind(app.status)}>{app.status}</Tag>
					</div>
					<p class="app-email">{app.carrierEmail}</p>
					{#if app.coverNote}
						<p class="cover-note">"{app.coverNote}"</p>
					{/if}
					<p class="app-date">Applied: {new Date(app.appliedAt).toLocaleDateString()}</p>
					{#if app.status === 'PENDING'}
						<div class="app-actions">
							<Button size="small" kind="primary" icon={Checkmark}
								on:click={() => acceptApplication(app.id)}>
								Accept
							</Button>
							<Button size="small" kind="danger-tertiary" icon={Close}
								on:click={() => rejectApplication(app.id)}>
								Reject
							</Button>
						</div>
					{/if}
				</Tile>
			{/each}
		</div>
	{/if}
</Modal>

<!-- Rating Modal -->
<Modal
	bind:open={ratingModalOpen}
	modalHeading="Rate Carrier - {ratingLoadTitle}"
	primaryButtonText="Submit Rating"
	secondaryButtonText="Skip"
	on:click:button--primary={submitRating}
	on:click:button--secondary={() => ratingModalOpen = false}
>
	{#if ratingError}
		<InlineNotification kind="error" title="Error" subtitle={ratingError} />
	{/if}
	<p class="rating-prompt">How was the carrier's performance?</p>
	<div class="star-rating">
		{#each [1, 2, 3, 4, 5] as s}
			<button class="star-btn" onclick={() => ratingScore = s} aria-label="Rate {s} stars">
				{#if s <= ratingScore}
					<StarFilled size={32} class="star-filled" />
				{:else}
					<Star size={32} class="star-empty" />
				{/if}
			</button>
		{/each}
		<span class="score-label">{ratingScore > 0 ? `${ratingScore}/5` : ''}</span>
	</div>
	<br />
	<TextArea
		bind:value={ratingComment}
		labelText="Comment (optional)"
		placeholder="Share your experience with this carrier..."
		rows={3}
	/>
</Modal>

<style>
	.dash-actions {
		display: flex;
		gap: 0.5rem;
		flex-wrap: wrap;
		margin: 0.25rem 0 1.25rem;
	}
	.metrics-row {
		display: flex;
		gap: 2rem;
		flex-wrap: wrap;
		padding: 1rem;
		margin-bottom: 0.5rem;
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-interactive, #0f62fe);
	}
	.metric {
		min-width: 8rem;
	}
	.metric-value {
		font-size: 1.5rem;
		font-weight: 600;
	}
	.metric-label {
		font-size: 0.75rem;
		color: var(--cds-text-secondary);
	}
	.applications-list {
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}
	.app-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 0.5rem;
	}
	.app-actions {
		display: flex;
		gap: 0.5rem;
		margin-top: 0.5rem;
	}
	.row-actions {
		display: flex;
		gap: 0.25rem;
		flex-wrap: wrap;
	}
	.carrier-info {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		flex-wrap: wrap;
	}
	.verified-badge {
		display: inline-flex;
		align-items: center;
		gap: 0.25rem;
		color: #198038;
		font-size: 0.8125rem;
		font-weight: 600;
	}
	.carrier-rating {
		color: #f1c40f;
		font-size: 0.875rem;
	}
	.app-email {
		color: var(--cds-text-secondary);
		font-size: 0.875rem;
		margin-bottom: 0.25rem;
	}
	.cover-note {
		font-style: italic;
		color: var(--cds-text-secondary);
	}
	.app-date {
		font-size: 0.875rem;
		color: var(--cds-text-secondary);
	}
	.rating-prompt {
		margin-bottom: 0.5rem;
	}
	.star-rating {
		display: flex;
		align-items: center;
		gap: 0.25rem;
	}
	.star-btn {
		background: none;
		border: none;
		cursor: pointer;
		padding: 0;
		color: #f1c40f;
	}
	.score-label {
		margin-left: 0.5rem;
		font-weight: 600;
	}
</style>
