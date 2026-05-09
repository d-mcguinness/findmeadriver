<script lang="ts">
	import {
		Grid, Row, Column, Button, InlineNotification
	} from 'carbon-components-svelte';
	import { ArrowLeft, Van, Add } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import type { Job } from '$lib/types';
	import { onMount } from 'svelte';
	import JobsTable from '$lib/components/admin/JobsTable.svelte';

	let jobs = $state<Job[]>([]);
	let loading = $state(true);
	let error = $state('');

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

	async function cancelJob(id: number) {
		try {
			await api.put(`/api/admin/jobs/${id}/cancel`, {});
			loadJobs();
		} catch (e: any) {
			error = e.message || 'Failed to cancel job';
		}
	}

	onMount(loadJobs);
</script>

{#snippet adminActions(job: Job)}
	<div class="row-actions">
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
</style>
