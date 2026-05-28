<script lang="ts">
	import {
		Grid, Row, Column, Button, InlineNotification,
		Modal, Select, SelectItem, TextArea
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

	let jobs = $state<Job[]>([]);
	let loading = $state(true);
	let error = $state('');

	// Apply-on-behalf state
	let drivers = $state<DriverOption[]>([]);
	let applyJob = $state<Job | null>(null);
	let selectedDriverId = $state('');
	let applyCoverNote = $state('');
	let applyError = $state('');
	let applySubmitting = $state(false);

	function driverLabel(d: DriverOption): string {
		const name = [d.firstName, d.lastName].filter(Boolean).join(' ').trim();
		const who = name || d.email;
		return d.licenceCategory ? `${who} — ${d.licenceCategory}` : who;
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

	function openApplyModal(job: Job) {
		applyJob = job;
		applyError = '';
		applyCoverNote = '';
		selectedDriverId = drivers.length > 0 ? String(drivers[0].id) : '';
	}

	function closeApplyModal() {
		applyJob = null;
		applySubmitting = false;
	}

	async function submitApply() {
		if (!applyJob || !selectedDriverId) return;
		applyError = '';
		applySubmitting = true;
		try {
			await api.post(
				`/api/admin/applications?driverId=${encodeURIComponent(selectedDriverId)}` +
					`&jobId=${encodeURIComponent(applyJob.id)}`,
				{ coverNote: applyCoverNote }
			);
			closeApplyModal();
			loadJobs();
		} catch (e: any) {
			applyError = e.message || 'Failed to apply on behalf of the driver';
		} finally {
			applySubmitting = false;
		}
	}

	onMount(() => {
		loadJobs();
		loadDrivers();
	});
</script>

{#snippet adminActions(job: Job)}
	<div class="row-actions">
		{#if job.status === 'OPEN'}
			<Button size="small" kind="tertiary" on:click={() => openApplyModal(job)}>
				Apply
			</Button>
		{/if}
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
	open={applyJob !== null}
	modalHeading="Apply on behalf of a driver"
	primaryButtonText={applySubmitting ? 'Applying...' : 'Apply'}
	secondaryButtonText="Cancel"
	primaryButtonDisabled={applySubmitting || !selectedDriverId}
	on:click:button--secondary={closeApplyModal}
	on:close={closeApplyModal}
	on:submit={submitApply}
>
	{#if applyJob}
		<p class="apply-job">
			<strong>{applyJob.title}</strong>
			{#if applyJob.requiredLicenceCategory}
				· requires <strong>{applyJob.requiredLicenceCategory}</strong>
			{/if}
			· {applyJob.dateNeeded} · {applyJob.estimatedDurationHours}h
		</p>

		{#if drivers.length === 0}
			<InlineNotification kind="warning" hideCloseButton
				subtitle="No drivers available to apply." />
		{:else}
			<Select labelText="Driver" bind:selected={selectedDriverId}>
				{#each drivers as d}
					<SelectItem value={String(d.id)} text={driverLabel(d)} />
				{/each}
			</Select>
		{/if}

		<TextArea labelText="Cover note (optional)" bind:value={applyCoverNote}
			placeholder="Optional note for this application" rows={2} />

		{#if applyError}
			<InlineNotification kind="error" subtitle={applyError}
				on:close={() => applyError = ''} />
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
	.apply-job {
		margin-bottom: 1rem;
		font-size: 0.875rem;
		color: var(--cds-text-secondary);
	}
</style>
