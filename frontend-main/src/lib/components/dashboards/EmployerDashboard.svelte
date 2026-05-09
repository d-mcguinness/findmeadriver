<script lang="ts">
	import {
		Grid, Row, Column, Tile,
		Button, TextArea,
		InlineNotification, Tag, Modal
	} from 'carbon-components-svelte';
	import { Enterprise, Add, Checkmark, Close, StarFilled, Star } from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
	import { api } from '$lib/api';
	import type { Job, JobApplication } from '$lib/types';
	import { onMount } from 'svelte';
	import JobsTable from '$lib/components/admin/JobsTable.svelte';

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

	onMount(loadJobs);
</script>

{#snippet employerActions(job: Job)}
	<div class="row-actions">
		{#if job.applicationCount > 0}
			<Button size="small" kind="secondary"
				on:click={() => viewApplications(job)}>
				Applications ({job.applicationCount})
			</Button>
		{/if}
		{#if job.status === 'ASSIGNED'}
			<Button size="small" kind="primary"
				on:click={() => startJob(job.id)}>
				Start
			</Button>
		{/if}
		{#if job.status === 'IN_PROGRESS'}
			<Button size="small" kind="primary"
				on:click={() => completeJob(job.id, job.title)}>
				Complete
			</Button>
		{/if}
		{#if job.status === 'OPEN' || job.status === 'ASSIGNED'}
			<Button size="small" kind="danger-tertiary"
				on:click={() => cancelJob(job.id)}>
				Cancel
			</Button>
		{/if}
	</div>
{/snippet}

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<h1><Enterprise size={24} /> Welcome, {auth.user?.firstName}!</h1>
				<p class="dashboard-subtitle">Your employer dashboard</p>
			</div>
		</Column>
	</Row>

	<Row>
		<Column>
			<h2>My Posted Jobs</h2>

			{#if jobsLoading}
				<p>Loading jobs...</p>
			{:else if jobs.length === 0}
				<InlineNotification kind="info" title="No jobs yet"
					subtitle="Post your first job to start finding drivers."
					hideCloseButton />
				<Button href="/dashboard/jobs/post" icon={Add}>Post a Job</Button>
			{:else}
				<JobsTable {jobs} actions={employerActions} addHref="/dashboard/jobs/post" />
			{/if}
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
	.page-header h1 {
		display: flex;
		align-items: center;
		gap: 0.5rem;
	}
	.dashboard-subtitle {
		color: var(--cds-text-secondary);
		margin-bottom: 1.5rem;
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
</style>
