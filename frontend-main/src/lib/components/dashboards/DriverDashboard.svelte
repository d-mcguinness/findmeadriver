<script lang="ts">
	import {
		Grid, Row, Column, Tile, Tabs, Tab, TabContent,
		Button, NumberInput, InlineNotification, Tag,
		Modal, TextArea, TextInput, Select, SelectItem
	} from 'carbon-components-svelte';
	import { Time, Search, Document, Checkmark, Close, Undo, StarFilled, Star, CertificateCheck } from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
	import { api } from '$lib/api';
	import { driverState } from '$lib/stores/driverState.svelte';
	import type { AvailabilityResponse, Job, JobApplication, DriverComplianceSummary } from '$lib/types';
	import { onMount } from 'svelte';
	import { page } from '$app/state';

	const tabMap: Record<string, number> = {
		availability: 0,
		compliance: 1,
		jobs: 2,
		applications: 3
	};

	let selectedTab = $state(0);

	$effect(() => {
		const tab = page.url.searchParams.get('tab');
		if (tab && tab in tabMap) {
			selectedTab = tabMap[tab];
		}
	});

	// Availability state
	let availability = $state<AvailabilityResponse | null>(null);
	let weekDays = $state<{ date: string; dayName: string; hours: number }[]>([]);
	let availError = $state('');
	let availSuccess = $state('');
	let availLoading = $state(false);

	// Compliance state
	let compliance = $state<DriverComplianceSummary | null>(null);
	let complianceLoading = $state(false);
	let docForm = $state({ documentType: 'DRIVING_LICENCE', documentNumber: '', expiryDate: '' });
	let docError = $state('');
	let docSuccess = $state('');

	// Jobs state
	let jobs = $state<Job[]>([]);
	let jobsLoading = $state(false);
	let applyModalOpen = $state(false);
	let selectedJob = $state<Job | null>(null);
	let coverNote = $state('');
	let applyError = $state('');

	// Applications state
	let applicationsLoading = $state(false);
	// Backed by the shared store so apply/withdraw is reflected in StatsRow too.
	let applications = $derived(driverState.applications);
	let applicationByJobId = $derived(new Map(applications.map(a => [a.jobId, a])));

	// Rating state
	let ratingModalOpen = $state(false);
	let ratingJobId = $state<number | null>(null);
	let ratingJobTitle = $state('');
	let ratingScore = $state(0);
	let ratingComment = $state('');
	let ratingError = $state('');

	function getMonday(d: Date): Date {
		const date = new Date(d);
		const day = date.getDay();
		const diff = date.getDate() - day + (day === 0 ? -6 : 1);
		date.setDate(diff);
		return date;
	}

	function formatDate(d: Date): string {
		return d.toISOString().split('T')[0];
	}

	function initWeekDays() {
		const monday = getMonday(new Date());
		const dayNames = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
		weekDays = dayNames.map((name, i) => {
			const date = new Date(monday);
			date.setDate(monday.getDate() + i);
			const existing = availability?.days.find(d => d.date === formatDate(date));
			return { date: formatDate(date), dayName: name, hours: existing?.availableHours ?? 0 };
		});
	}

	async function loadAvailability() {
		try {
			const monday = getMonday(new Date());
			const sunday = new Date(monday);
			sunday.setDate(monday.getDate() + 6);
			availability = await api.get<AvailabilityResponse>(
				`/api/driver/availability?start=${formatDate(monday)}&end=${formatDate(sunday)}`
			);
			initWeekDays();
		} catch {
			initWeekDays();
		}
	}

	async function saveAvailability() {
		availError = '';
		availSuccess = '';
		availLoading = true;
		try {
			const entries = weekDays
				.filter(d => d.hours > 0)
				.map(d => ({ date: d.date, availableHours: d.hours }));
			availability = await api.put<AvailabilityResponse>('/api/driver/availability', { entries });
			availSuccess = 'Availability saved successfully';
			initWeekDays();
		} catch (e: any) {
			availError = e.message || 'Failed to save availability';
		} finally {
			availLoading = false;
		}
	}

	async function loadCompliance() {
		complianceLoading = true;
		try {
			compliance = await api.get<DriverComplianceSummary>('/api/driver/compliance');
		} catch {
			compliance = null;
		} finally {
			complianceLoading = false;
		}
	}

	async function addDocument() {
		docError = '';
		docSuccess = '';
		try {
			await api.post('/api/driver/compliance', docForm);
			docSuccess = 'Document submitted for verification';
			docForm = { documentType: 'DRIVING_LICENCE', documentNumber: '', expiryDate: '' };
			loadCompliance();
		} catch (e: any) {
			docError = e.message || 'Failed to add document';
		}
	}

	async function deleteDocument(id: number) {
		try {
			await api.delete(`/api/driver/compliance/${id}`);
			loadCompliance();
		} catch { /* ignore */ }
	}

	async function loadJobs() {
		jobsLoading = true;
		try {
			jobs = await api.get<Job[]>('/api/driver/jobs');
		} catch {
			jobs = [];
		} finally {
			jobsLoading = false;
		}
	}

	function openApplyModal(job: Job) {
		selectedJob = job;
		coverNote = '';
		applyError = '';
		applyModalOpen = true;
	}

	async function submitApplication() {
		if (!selectedJob) return;
		applyError = '';
		try {
			await api.post(`/api/driver/jobs/${selectedJob.id}/apply`, { coverNote });
			applyModalOpen = false;
			loadJobs();
			loadApplications();
		} catch (e: any) {
			applyError = e.message || 'Failed to apply';
		}
	}

	async function loadApplications() {
		applicationsLoading = true;
		try {
			await driverState.reloadApplications();
		} finally {
			applicationsLoading = false;
		}
	}

	async function withdrawApplication(id: number) {
		try {
			await api.put(`/api/driver/applications/${id}/withdraw`, {});
			loadApplications();
			loadJobs();
		} catch { /* ignore */ }
	}

	function openRatingModal(jobId: number, jobTitle: string) {
		ratingJobId = jobId;
		ratingJobTitle = jobTitle;
		ratingScore = 0;
		ratingComment = '';
		ratingError = '';
		ratingModalOpen = true;
	}

	async function submitRating() {
		if (!ratingJobId || ratingScore === 0) return;
		ratingError = '';
		try {
			await api.post(`/api/driver/jobs/${ratingJobId}/rate`, {
				score: ratingScore,
				comment: ratingComment
			});
			ratingModalOpen = false;
			loadApplications();
		} catch (e: any) {
			ratingError = e.message || 'Failed to submit rating';
		}
	}

	function appStatusKind(status: string): 'blue' | 'green' | 'red' | 'magenta' | 'gray' {
		switch (status) {
			case 'PENDING': return 'blue';
			case 'ACCEPTED': return 'green';
			case 'REJECTED': return 'red';
			case 'WITHDRAWN': return 'magenta';
			default: return 'gray';
		}
	}

	function appStatusIcon(status: string) {
		switch (status) {
			case 'ACCEPTED': return Checkmark;
			case 'REJECTED': return Close;
			case 'WITHDRAWN': return Undo;
			default: return undefined;
		}
	}

	function appStatusLabel(status: string): string {
		switch (status) {
			case 'PENDING': return 'Pending';
			case 'ACCEPTED': return 'Accepted';
			case 'REJECTED': return 'Rejected';
			case 'WITHDRAWN': return 'Withdrawn';
			default: return status;
		}
	}

	function docStatusKind(status: string): 'green' | 'blue' | 'red' {
		switch (status) {
			case 'VERIFIED': return 'green';
			case 'EXPIRED': return 'red';
			default: return 'blue';
		}
	}

	function docTypeLabel(type: string): string {
		switch (type) {
			case 'DRIVING_LICENCE': return 'Driving Licence';
			case 'INSURANCE': return 'Insurance';
			case 'CPC_CARD': return 'CPC Card';
			case 'TACHOGRAPH_CARD': return 'Tachograph Card';
			default: return 'Other';
		}
	}

	$effect(() => {
		if (selectedTab === 1) loadCompliance();
		if (selectedTab === 2) { loadJobs(); loadApplications(); }
		if (selectedTab === 3) loadApplications();
	});

	onMount(() => {
		loadAvailability();
	});
</script>

<Grid>
	<Row>
		<Column>
			<h1>Welcome, {auth.user?.firstName}!</h1>
			<p class="dashboard-subtitle">Your driver dashboard</p>
		</Column>
	</Row>

	<Row>
		<Column>
			<Tabs bind:selected={selectedTab}>
				<Tab label="Availability" />
				<Tab label="Compliance" />
				<Tab label="Browse Jobs" />
				<Tab label="My Applications" />
				<svelte:fragment slot="content">
					<!-- Availability Tab -->
					<TabContent>
						<div class="tab-content">
							<h3><Time size={20} /> Set Your Available Hours</h3>
							<p class="info-text">
								EU tachograph rules: max 9h/day (10h twice/week), 56h/week, 90h/fortnight
							</p>

							{#if availError}
								<InlineNotification kind="error" title="Error" subtitle={availError}
									on:close={() => availError = ''} />
							{/if}
							{#if availSuccess}
								<InlineNotification kind="success" title="Saved" subtitle={availSuccess}
									on:close={() => availSuccess = ''} />
							{/if}

							<div class="week-grid">
								{#each weekDays as day, i}
									<div class="day-card">
										<div class="day-label">{day.dayName}</div>
										<div class="day-date">{day.date}</div>
										<NumberInput
											bind:value={weekDays[i].hours}
											min={0}
											max={10}
											step={0.5}
											size="sm"
											hideLabel
											label="Hours"
										/>
									</div>
								{/each}
							</div>

							{#if availability}
								<div class="totals">
									<Tile class="total-tile">
										<strong>Weekly:</strong> {availability.weeklyTotal}h / 56h
										<span class="remaining">({availability.weeklyRemaining}h remaining)</span>
									</Tile>
									<Tile class="total-tile">
										<strong>Fortnightly:</strong> {availability.fortnightlyTotal}h / 90h
										<span class="remaining">({availability.fortnightlyRemaining}h remaining)</span>
									</Tile>
								</div>
							{/if}

							<Button on:click={saveAvailability} disabled={availLoading}>
								{availLoading ? 'Saving...' : 'Save Availability'}
							</Button>
						</div>
					</TabContent>

					<!-- Compliance Tab -->
					<TabContent>
						<div class="tab-content">
							<h3><CertificateCheck size={20} /> Compliance Documents</h3>
							<p class="info-text">
								Upload your documents to get verified. Verified drivers get a trust badge visible to employers.
							</p>

							{#if compliance}
								<Tile class="compliance-summary">
									<strong>{compliance.verifiedCount}</strong> of <strong>{compliance.totalCount}</strong> documents verified
									{#if compliance.allVerified && compliance.totalCount > 0}
										<Tag type="green">Fully Verified</Tag>
									{/if}
								</Tile>

								{#if compliance.documents.length > 0}
									<div class="doc-list">
										{#each compliance.documents as doc}
											<Tile class="doc-tile">
												<div class="doc-header">
													<strong>{docTypeLabel(doc.documentType)}</strong>
													<Tag type={docStatusKind(doc.status)}>{doc.status}</Tag>
												</div>
												<p class="doc-detail">Number: {doc.documentNumber}</p>
												<p class="doc-detail">Expires: {doc.expiryDate}</p>
												{#if doc.notes}
													<p class="doc-detail">Notes: {doc.notes}</p>
												{/if}
												<Button size="small" kind="danger-ghost"
													on:click={() => deleteDocument(doc.id)}>Remove</Button>
											</Tile>
										{/each}
									</div>
								{/if}
							{/if}

							<h4 class="form-heading">Add Document</h4>

							{#if docError}
								<InlineNotification kind="error" title="Error" subtitle={docError}
									on:close={() => docError = ''} />
							{/if}
							{#if docSuccess}
								<InlineNotification kind="success" title="Success" subtitle={docSuccess}
									on:close={() => docSuccess = ''} />
							{/if}

							<div class="doc-form">
								<Select bind:selected={docForm.documentType} labelText="Document Type">
									<SelectItem value="DRIVING_LICENCE" text="Driving Licence" />
									<SelectItem value="INSURANCE" text="Insurance" />
									<SelectItem value="CPC_CARD" text="CPC Card" />
									<SelectItem value="TACHOGRAPH_CARD" text="Tachograph Card" />
									<SelectItem value="OTHER" text="Other" />
								</Select>
								<TextInput bind:value={docForm.documentNumber}
									labelText="Document Number" placeholder="e.g. DL-12345" />
								<TextInput bind:value={docForm.expiryDate}
									labelText="Expiry Date" type="date" />
								<Button on:click={addDocument}
									disabled={!docForm.documentNumber || !docForm.expiryDate}>
									Submit Document
								</Button>
							</div>
						</div>
					</TabContent>

					<!-- Browse Jobs Tab -->
					<TabContent>
						<div class="tab-content">
							<h3><Search size={20} /> Jobs Matching Your Profile</h3>
							<p class="info-text">
								Showing jobs that match your CDL type and available hours
							</p>

							{#if jobsLoading}
								<p>Loading jobs...</p>
							{:else if jobs.length === 0}
								<InlineNotification kind="info" title="No jobs found"
									subtitle="Set your availability first, then matching jobs will appear here."
									hideCloseButton />
							{:else}
								<div class="job-list">
									{#each jobs as job}
										{@const existing = applicationByJobId.get(job.id)}
										{@const isWithdrawn = existing?.status === 'WITHDRAWN'}
										{@const blocksApply = !!existing && !isWithdrawn}
										<Tile class="job-tile">
											<div class="job-header">
												<h4>{job.title}</h4>
												<div class="header-tags">
													<Tag type="blue">{job.requiredCdlType || 'Any CDL'}</Tag>
													{#if isWithdrawn}
														<Tag type="magenta" icon={Undo}>Withdrawn</Tag>
													{:else if blocksApply}
														<Tag type="green" icon={Checkmark}>Applied</Tag>
													{/if}
												</div>
											</div>
											<p class="job-company">{job.employerCompanyName}</p>
											<p>{job.description}</p>
											<div class="job-details">
												<span><strong>Route:</strong> {job.pickupLocation} &rarr; {job.deliveryLocation}</span>
												<span><strong>Date:</strong> {job.dateNeeded}</span>
												<span><strong>Duration:</strong> {job.estimatedDurationHours}h</span>
												<span><strong>Rate:</strong> &euro;{job.ratePerHour}/hr</span>
											</div>
											<Button size="small" disabled={blocksApply}
												on:click={() => openApplyModal(job)}>
												{blocksApply ? 'Already applied' : isWithdrawn ? 'Re-apply' : 'Apply'}
											</Button>
										</Tile>
									{/each}
								</div>
							{/if}
						</div>
					</TabContent>

					<!-- My Applications Tab -->
					<TabContent>
						<div class="tab-content">
							<h3><Document size={20} /> My Applications</h3>

							{#if applicationsLoading}
								<p>Loading applications...</p>
							{:else if applications.length === 0}
								<InlineNotification kind="info" title="No applications yet"
									subtitle="Browse jobs and apply to see your applications here."
									hideCloseButton />
							{:else}
								<div class="applications-list">
									{#each applications as app}
										<Tile class="app-tile">
											<div class="app-header">
												<h4>{app.jobTitle}</h4>
												<div class="app-tags">
													<Tag type={appStatusKind(app.status)} icon={appStatusIcon(app.status)}>
														{appStatusLabel(app.status)}
													</Tag>
													{#if app.jobStatus && app.jobStatus !== 'OPEN'}
														<Tag type="gray">Job: {app.jobStatus}</Tag>
													{/if}
												</div>
											</div>
											{#if app.coverNote}
												<p class="cover-note">"{app.coverNote}"</p>
											{/if}
											<p class="app-date">Applied: {new Date(app.appliedAt).toLocaleDateString()}</p>
											<div class="app-actions">
												{#if app.status === 'PENDING'}
													<Button kind="danger-tertiary" size="small"
														on:click={() => withdrawApplication(app.id)}>
														Withdraw
													</Button>
												{/if}
												{#if app.status === 'ACCEPTED' && app.jobStatus === 'COMPLETED'}
													<Button size="small" kind="secondary"
														on:click={() => openRatingModal(app.jobId, app.jobTitle)}>
														Rate Employer
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

<!-- Apply Modal -->
<Modal
	bind:open={applyModalOpen}
	modalHeading="Apply for {selectedJob?.title}"
	primaryButtonText="Submit Application"
	secondaryButtonText="Cancel"
	on:click:button--primary={submitApplication}
	on:click:button--secondary={() => applyModalOpen = false}
>
	{#if applyError}
		<InlineNotification kind="error" title="Error" subtitle={applyError} />
	{/if}
	{#if selectedJob}
		<p><strong>Company:</strong> {selectedJob.employerCompanyName}</p>
		<p><strong>Rate:</strong> &euro;{selectedJob.ratePerHour}/hr &middot; {selectedJob.estimatedDurationHours}h</p>
		<p><strong>Date:</strong> {selectedJob.dateNeeded}</p>
		<br />
		<TextArea
			bind:value={coverNote}
			labelText="Cover note (optional)"
			placeholder="Tell the employer why you're a good fit..."
			rows={3}
		/>
	{/if}
</Modal>

<!-- Rating Modal -->
<Modal
	bind:open={ratingModalOpen}
	modalHeading="Rate Employer - {ratingJobTitle}"
	primaryButtonText="Submit Rating"
	secondaryButtonText="Cancel"
	on:click:button--primary={submitRating}
	on:click:button--secondary={() => ratingModalOpen = false}
>
	{#if ratingError}
		<InlineNotification kind="error" title="Error" subtitle={ratingError} />
	{/if}
	<p class="rating-prompt">How was your experience?</p>
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
		placeholder="Share your experience..."
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
		margin-bottom: 0.5rem;
	}
	.info-text {
		color: var(--cds-text-secondary);
		margin-bottom: 1rem;
		font-size: 0.875rem;
	}
	.week-grid {
		display: grid;
		grid-template-columns: repeat(7, 1fr);
		gap: 0.75rem;
		margin-bottom: 1rem;
	}
	.day-card {
		text-align: center;
		padding: 0.75rem;
		background: var(--cds-layer);
		border: 1px solid var(--cds-border-subtle);
	}
	.day-label {
		font-weight: 600;
		margin-bottom: 0.25rem;
	}
	.day-date {
		font-size: 0.75rem;
		color: var(--cds-text-secondary);
		margin-bottom: 0.5rem;
	}
	.totals {
		display: flex;
		gap: 1rem;
		margin-bottom: 1rem;
	}
	.remaining {
		color: var(--cds-text-secondary);
		font-size: 0.875rem;
		margin-left: 0.5rem;
	}
	.compliance-summary {
		margin-bottom: 1rem;
		display: flex;
		align-items: center;
		gap: 0.75rem;
	}
	.doc-list {
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
		margin-bottom: 1.5rem;
	}
	.doc-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 0.25rem;
	}
	.doc-detail {
		font-size: 0.875rem;
		color: var(--cds-text-secondary);
		margin: 0.125rem 0;
	}
	.form-heading {
		margin: 1rem 0 0.75rem;
	}
	.doc-form {
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
		max-width: 400px;
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
	.header-tags {
		display: flex;
		gap: 0.25rem;
		flex-wrap: wrap;
	}
	.app-tags {
		display: flex;
		gap: 0.25rem;
	}
	.job-company {
		color: var(--cds-text-secondary);
		margin-bottom: 0.5rem;
	}
	.job-details {
		display: flex;
		flex-wrap: wrap;
		gap: 1rem;
		margin: 0.75rem 0;
		font-size: 0.875rem;
	}
	.cover-note {
		font-style: italic;
		color: var(--cds-text-secondary);
	}
	.app-date {
		font-size: 0.875rem;
		color: var(--cds-text-secondary);
		margin-bottom: 0.5rem;
	}
	.app-actions {
		display: flex;
		gap: 0.5rem;
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
		.week-grid {
			grid-template-columns: repeat(2, 1fr);
		}
		.totals {
			flex-direction: column;
		}
	}
</style>
