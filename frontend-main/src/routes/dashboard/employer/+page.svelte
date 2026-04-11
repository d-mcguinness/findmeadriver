<script lang="ts">
	import {
		Grid, Row, Column, Tile, Tabs, Tab, TabContent,
		Button, TextInput, TextArea, NumberInput, Select, SelectItem,
		InlineNotification, Tag, Modal
	} from 'carbon-components-svelte';
	import { Enterprise, Add, Checkmark, Close, StarFilled, Star, CertificateCheck } from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
	import { api } from '$lib/api';
	import type { Job, JobApplication } from '$lib/types';
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import LocationPicker from '$lib/components/LocationPicker.svelte';
	import { calculateRoute, type RouteInfo } from '$lib/google-maps';

	const tabMap: Record<string, number> = {
		post: 0,
		jobs: 1
	};

	let selectedTab = $state(0);

	$effect(() => {
		const tab = page.url.searchParams.get('tab');
		if (tab && tab in tabMap) {
			selectedTab = tabMap[tab];
		}
	});

	// Job posting form
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

	// Location coordinates for route calculation
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
				// Auto-fill estimated hours from drive time
				// Use tick + full reassignment to force Carbon NumberInput to update
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

	// My Jobs state
	let jobs = $state<Job[]>([]);
	let jobsLoading = $state(false);

	// Applications state
	let selectedJobForApps = $state<Job | null>(null);
	let applications = $state<JobApplication[]>([]);
	let appsLoading = $state(false);
	let appsModalOpen = $state(false);

	// Rating state
	let ratingModalOpen = $state(false);
	let ratingJobId = $state<number | null>(null);
	let ratingJobTitle = $state('');
	let ratingScore = $state(0);
	let ratingComment = $state('');
	let ratingError = $state('');

	async function postJob() {
		postError = '';
		postSuccess = '';
		postLoading = true;
		try {
			await api.post('/api/employer/jobs', {
				...jobForm,
				ratePerHour: jobForm.ratePerHour
			});
			postSuccess = 'Job posted successfully!';
			jobForm = {
				title: '', description: '', pickupLocation: '', deliveryLocation: '',
				estimatedDurationHours: 4, dateNeeded: '', ratePerHour: 25, requiredCdlType: 'CLASS_A'
			};
			pickupCoords = null;
			deliveryCoords = null;
			routeInfo = null;
			loadJobs();
		} catch (e: any) {
			postError = e.message || 'Failed to post job';
		} finally {
			postLoading = false;
		}
	}

	async function loadJobs() {
		jobsLoading = true;
		try {
			jobs = await api.get<Job[]>('/api/employer/jobs');
		} catch {
			jobs = [];
		} finally {
			jobsLoading = false;
		}
	}

	async function cancelJob(id: number) {
		try {
			await api.put(`/api/employer/jobs/${id}/cancel`, {});
			loadJobs();
		} catch { /* ignore */ }
	}

	async function startJob(id: number) {
		try {
			await api.put(`/api/employer/jobs/${id}/status`, { status: 'IN_PROGRESS' });
			loadJobs();
		} catch { /* ignore */ }
	}

	async function completeJob(id: number, title: string) {
		try {
			await api.put(`/api/employer/jobs/${id}/status`, { status: 'COMPLETED' });
			loadJobs();
			// Open rating modal
			ratingJobId = id;
			ratingJobTitle = title;
			ratingScore = 0;
			ratingComment = '';
			ratingError = '';
			ratingModalOpen = true;
		} catch { /* ignore */ }
	}

	async function submitRating() {
		if (!ratingJobId || ratingScore === 0) return;
		ratingError = '';
		try {
			await api.post(`/api/employer/jobs/${ratingJobId}/rate`, {
				score: ratingScore,
				comment: ratingComment
			});
			ratingModalOpen = false;
		} catch (e: any) {
			ratingError = e.message || 'Failed to submit rating';
		}
	}

	async function viewApplications(job: Job) {
		selectedJobForApps = job;
		appsModalOpen = true;
		appsLoading = true;
		try {
			applications = await api.get<JobApplication[]>(`/api/employer/jobs/${job.id}/applications`);
		} catch {
			applications = [];
		} finally {
			appsLoading = false;
		}
	}

	async function acceptApplication(id: number) {
		try {
			await api.put(`/api/employer/applications/${id}/accept`, {});
			if (selectedJobForApps) viewApplications(selectedJobForApps);
			loadJobs();
		} catch { /* ignore */ }
	}

	async function rejectApplication(id: number) {
		try {
			await api.put(`/api/employer/applications/${id}/reject`, {});
			if (selectedJobForApps) viewApplications(selectedJobForApps);
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

	$effect(() => {
		if (selectedTab === 1) loadJobs();
	});

	onMount(() => {
		loadJobs();
	});
</script>

<Grid>
	<Row>
		<Column>
			<h1>Welcome, {auth.user?.firstName}!</h1>
			<p class="dashboard-subtitle">Your employer dashboard</p>
		</Column>
	</Row>

	<Row>
		<Column>
			<Tabs bind:selected={selectedTab}>
				<Tab label="Post a Job" />
				<Tab label="My Jobs" />
				<svelte:fragment slot="content">
					<!-- Post Job Tab -->
					<TabContent>
						<div class="tab-content">
							<h3><Add size={20} /> Post a Delivery Job</h3>

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
						</div>
					</TabContent>

					<!-- My Jobs Tab -->
					<TabContent>
						<div class="tab-content">
							<h3><Enterprise size={20} /> My Posted Jobs</h3>

							{#if jobsLoading}
								<p>Loading jobs...</p>
							{:else if jobs.length === 0}
								<InlineNotification kind="info" title="No jobs yet"
									subtitle="Post your first job to start finding drivers."
									hideCloseButton />
							{:else}
								<div class="job-list">
									{#each jobs as job}
										<Tile class="job-tile">
											<div class="job-header">
												<h4>{job.title}</h4>
												<Tag type={statusKind(job.status)}>{job.status}</Tag>
											</div>
											{#if job.assignedDriverName}
												<p class="assigned-driver">Assigned to: <strong>{job.assignedDriverName}</strong></p>
											{/if}
											<div class="job-details">
												<span><strong>Route:</strong> {job.pickupLocation} &rarr; {job.deliveryLocation}</span>
												<span><strong>Date:</strong> {job.dateNeeded}</span>
												<span><strong>Duration:</strong> {job.estimatedDurationHours}h</span>
												<span><strong>Rate:</strong> &euro;{job.ratePerHour}/hr</span>
												<span><strong>CDL:</strong> {job.requiredCdlType}</span>
												<span><strong>Applications:</strong> {job.applicationCount}</span>
											</div>
											<div class="job-actions">
												{#if job.applicationCount > 0}
													<Button size="small" kind="secondary"
														on:click={() => viewApplications(job)}>
														View Applications ({job.applicationCount})
													</Button>
												{/if}
												{#if job.status === 'ASSIGNED'}
													<Button size="small" kind="primary"
														on:click={() => startJob(job.id)}>
														Start Job
													</Button>
												{/if}
												{#if job.status === 'IN_PROGRESS'}
													<Button size="small" kind="primary"
														on:click={() => completeJob(job.id, job.title)}>
														Mark Complete
													</Button>
												{/if}
												{#if job.status === 'OPEN' || job.status === 'ASSIGNED'}
													<Button size="small" kind="danger-tertiary"
														on:click={() => cancelJob(job.id)}>
														Cancel Job
													</Button>
												{/if}
											</div>
										</Tile>
									{/each}
								</div>
							{/if}
						</div>
					</TabContent>
				</svelte:fragment>
			</Tabs>
		</Column>
	</Row>
</Grid>

<!-- Applications Modal -->
<Modal
	bind:open={appsModalOpen}
	modalHeading="Applications for {selectedJobForApps?.title}"
	passiveModal
	size="lg"
>
	{#if appsLoading}
		<p>Loading applications...</p>
	{:else if applications.length === 0}
		<p>No applications yet for this job.</p>
	{:else}
		<div class="applications-list">
			{#each applications as app}
				<Tile class="app-tile">
					<div class="app-header">
						<div class="driver-info">
							<strong>{app.driverName}</strong>
							{#if app.driverVerified}
								<span class="verified-badge" title="All documents verified">
									<Checkmark size={16} /> Verified
								</span>
							{/if}
							{#if app.driverAverageRating}
								<span class="driver-rating" title="{app.driverRatingCount} ratings">
									{renderStars(app.driverAverageRating)} ({app.driverAverageRating.toFixed(1)})
								</span>
							{/if}
						</div>
						<Tag type={appStatusKind(app.status)}>{app.status}</Tag>
					</div>
					<p class="app-email">{app.driverEmail}</p>
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
	modalHeading="Rate Driver - {ratingJobTitle}"
	primaryButtonText="Submit Rating"
	secondaryButtonText="Skip"
	on:click:button--primary={submitRating}
	on:click:button--secondary={() => ratingModalOpen = false}
>
	{#if ratingError}
		<InlineNotification kind="error" title="Error" subtitle={ratingError} />
	{/if}
	<p class="rating-prompt">How was the driver's performance?</p>
	<div class="star-rating">
		{#each [1, 2, 3, 4, 5] as s}
			<button class="star-btn" on:click={() => ratingScore = s} aria-label="Rate {s} stars">
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
		placeholder="Share your experience with this driver..."
		rows={3}
	/>
</Modal>

<style>
	.dashboard-subtitle {
		color: var(--cds-text-secondary);
		margin-bottom: 1.5rem;
	}
	.tab-content {
		padding: 1.5rem 0;
	}
	.tab-content h3 {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		margin-bottom: 1rem;
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
	.job-list, .applications-list {
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}
	.job-header, .app-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 0.5rem;
	}
	.assigned-driver {
		color: var(--cds-text-secondary);
		margin-bottom: 0.5rem;
		font-size: 0.875rem;
	}
	.job-details {
		display: flex;
		flex-wrap: wrap;
		gap: 1rem;
		margin: 0.75rem 0;
		font-size: 0.875rem;
	}
	.job-actions, .app-actions {
		display: flex;
		gap: 0.5rem;
		margin-top: 0.5rem;
	}
	.driver-info {
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
	.driver-rating {
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
	@media (max-width: 672px) {
		.form-row {
			grid-template-columns: 1fr;
		}
	}
</style>
