<script lang="ts">
	import {
		Grid, Row, Column, Button, InlineNotification, Modal, Tag
	} from 'carbon-components-svelte';
	import { ArrowLeft, Van, Add } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import type { Job } from '$lib/types';
	import { onMount } from 'svelte';
	import JobsTable from '$lib/components/admin/JobsTable.svelte';

	type DriverOption = {
		id: number;
		firstName?: string;
		lastName?: string;
		email: string;
		licenceCategory?: string;
	};

	type Application = {
		id: number;
		driverId: number;
		status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN';
	};

	let jobs = $state<Job[]>([]);
	let loading = $state(true);
	let error = $state('');

	// Applications-modal state
	let drivers = $state<DriverOption[]>([]);
	let appsJob = $state<Job | null>(null);
	let jobApps = $state<Application[]>([]);
	let appsLoading = $state(false);
	let appsError = $state('');
	let applyingDriverId = $state<number | null>(null);

	// driverId -> their application for the open job
	let appByDriver = $derived(new Map(jobApps.map((a) => [a.driverId, a])));

	function driverLabel(d: DriverOption): string {
		const name = [d.firstName, d.lastName].filter(Boolean).join(' ').trim();
		const who = name || d.email;
		return d.licenceCategory ? `${who} (${d.licenceCategory})` : who;
	}

	function statusColor(s: string): 'blue' | 'green' | 'red' | 'gray' {
		switch (s) {
			case 'ACCEPTED': return 'green';
			case 'REJECTED': return 'red';
			case 'WITHDRAWN': return 'gray';
			default: return 'blue'; // PENDING
		}
	}

	// Apply is allowed when the job is open to applications and the driver has no
	// active application (a withdrawn one can be revived — mirrors the backend).
	function canApply(driverId: number): boolean {
		if (!appsJob || appsJob.status !== 'OPEN') return false;
		const app = appByDriver.get(driverId);
		return !app || app.status === 'WITHDRAWN';
	}

	async function loadJobs() {
		loading = true;
		error = '';
		try {
			jobs = await api.get<Job[]>('/api/admin/jobs');
		} catch (e: any) {
			error = e.message || 'Failed to load jobs';
		} finally {
			loading = false;
		}
	}

	async function loadDrivers() {
		try {
			drivers = await api.get<DriverOption[]>('/api/admin/drivers');
		} catch {
			drivers = [];
		}
	}

	async function cancelJob(id: number) {
		try {
			await api.put(`/api/admin/jobs/${id}/cancel`, {});
			loadJobs();
		} catch (e: any) {
			error = e.message || 'Failed to cancel job';
		}
	}

	async function openApplicationsModal(job: Job) {
		appsJob = job;
		appsError = '';
		await loadJobApps(job.id);
	}

	function closeApplicationsModal() {
		appsJob = null;
		jobApps = [];
		applyingDriverId = null;
	}

	async function loadJobApps(jobId: number) {
		appsLoading = true;
		try {
			jobApps = await api.get<Application[]>(`/api/admin/jobs/${jobId}/applications`);
		} catch (e: any) {
			appsError = e.message || 'Failed to load applications';
			jobApps = [];
		} finally {
			appsLoading = false;
		}
	}

	async function applyForDriver(driverId: number) {
		if (!appsJob) return;
		appsError = '';
		applyingDriverId = driverId;
		try {
			await api.post(
				`/api/admin/applications?driverId=${driverId}&jobId=${appsJob.id}`,
				{ coverNote: '' }
			);
			await loadJobApps(appsJob.id); // refresh statuses in the table
			loadJobs(); // refresh applicationCount in the main table
		} catch (e: any) {
			appsError = e.message || 'Failed to apply on behalf of the driver';
		} finally {
			applyingDriverId = null;
		}
	}

	onMount(() => {
		loadJobs();
		loadDrivers();
	});
</script>

{#snippet adminActions(job: Job)}
	<div class="row-actions">
		<Button size="small" kind="tertiary" on:click={() => openApplicationsModal(job)}>
			Applications ({job.applicationCount})
		</Button>
		{#if job.status === 'OPEN' || job.status === 'ASSIGNED' || job.status === 'IN_PROGRESS'}
			<Button size="small" kind="danger-tertiary" on:click={() => cancelJob(job.id)}>
				Cancel
			</Button>
		{/if}
	</div>
{/snippet}

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard" icon={ArrowLeft}>
					Back
				</Button>
				<h1><Van size={24} /> All Jobs</h1>
				<p class="page-subtitle">{jobs.length} posted job{jobs.length !== 1 ? 's' : ''}</p>
			</div>
		</Column>
	</Row>

	<Row>
		<Column>
			{#if error}
				<InlineNotification kind="error" title="Error" subtitle={error}
					on:close={() => error = ''} />
			{/if}

			{#if loading}
				<p>Loading jobs...</p>
			{:else if jobs.length === 0}
				<InlineNotification kind="info" title="No jobs" subtitle="No jobs have been posted yet." hideCloseButton />
				<Button href="/dashboard/jobs/post" icon={Add}>Add Job</Button>
			{:else}
				<JobsTable {jobs} actions={adminActions} addHref="/dashboard/jobs/post" />
			{/if}
		</Column>
	</Row>
</Grid>

<Modal
	open={appsJob !== null}
	passiveModal
	modalHeading="Applications{appsJob ? ` — ${appsJob.title}` : ''}"
	on:close={closeApplicationsModal}
>
	{#if appsJob}
		<p class="apps-meta">
			{#if appsJob.requiredLicenceCategory}requires <strong>{appsJob.requiredLicenceCategory}</strong> · {/if}
			status <strong>{appsJob.status}</strong>
			{#if appsJob.status !== 'OPEN'}<span class="apps-note"> · not open for new applications</span>{/if}
		</p>

		{#if appsError}
			<InlineNotification kind="error" subtitle={appsError} on:close={() => appsError = ''} />
		{/if}

		{#if appsLoading}
			<p>Loading applications...</p>
		{:else}
			<table class="apps-table">
				<thead>
					<tr><th>Driver</th><th>Status</th><th>Action</th></tr>
				</thead>
				<tbody>
					{#each drivers as d}
						{@const app = appByDriver.get(d.id)}
						<tr>
							<td>{driverLabel(d)}</td>
							<td>
								{#if app}
									<Tag type={statusColor(app.status)} size="sm">{app.status}</Tag>
								{:else}
									<span class="dash">—</span>
								{/if}
							</td>
							<td>
								{#if canApply(d.id)}
									<Button size="small" kind="tertiary"
										disabled={applyingDriverId !== null}
										on:click={() => applyForDriver(d.id)}>
										{applyingDriverId === d.id ? 'Applying…' : 'Apply'}
									</Button>
								{:else}
									<span class="dash">—</span>
								{/if}
							</td>
						</tr>
					{/each}
					{#if drivers.length === 0}
						<tr><td colspan="3">No drivers available.</td></tr>
					{/if}
				</tbody>
			</table>
		{/if}
	{/if}
</Modal>

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
	.page-subtitle {
		color: var(--cds-text-secondary);
		margin-top: 0.25rem;
	}
	.row-actions {
		display: flex;
		gap: 0.25rem;
		flex-wrap: wrap;
	}
	.apps-meta {
		margin-bottom: 1rem;
		font-size: 0.875rem;
		color: var(--cds-text-secondary);
	}
	.apps-note {
		color: var(--cds-text-error, #da1e28);
	}
	.apps-table {
		width: 100%;
		border-collapse: collapse;
		font-size: 0.875rem;
	}
	.apps-table th,
	.apps-table td {
		text-align: left;
		padding: 0.5rem 0.75rem;
		border-bottom: 1px solid var(--cds-border-subtle, #e0e0e0);
		vertical-align: middle;
	}
	.apps-table th {
		font-weight: 600;
	}
	.dash {
		color: var(--cds-text-secondary);
	}
</style>
