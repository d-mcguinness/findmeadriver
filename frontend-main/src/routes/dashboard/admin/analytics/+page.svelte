<script lang="ts">
	import {
		Grid, Row, Column, Tile, Tag, Button, InlineNotification,
		DataTable
	} from 'carbon-components-svelte';
	import { ArrowLeft, Analytics, UserMultiple, Van, Document } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import type { PlatformStats, Job } from '$lib/types';
	import { onMount } from 'svelte';

	let stats = $state<PlatformStats | null>(null);
	let jobs = $state<Job[]>([]);
	let loading = $state(true);
	let error = $state('');

	async function loadData() {
		loading = true;
		error = '';
		try {
			const [s, j] = await Promise.all([
				api.get<PlatformStats>('/api/admin/stats'),
				api.get<Job[]>('/api/admin/jobs')
			]);
			stats = s;
			jobs = j;
		} catch (e: any) {
			error = e.message || 'Failed to load analytics';
		} finally {
			loading = false;
		}
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

	const jobHeaders = [
		{ key: 'id', value: 'ID' },
		{ key: 'title', value: 'Title' },
		{ key: 'employerCompanyName', value: 'Employer' },
		{ key: 'pickupLocation', value: 'From' },
		{ key: 'deliveryLocation', value: 'To' },
		{ key: 'dateNeeded', value: 'Date' },
		{ key: 'status', value: 'Status' },
		{ key: 'applicationCount', value: 'Applications' }
	];

	let jobRows = $derived(jobs.map(j => ({
		id: String(j.id),
		title: j.title,
		employerCompanyName: j.employerCompanyName,
		pickupLocation: j.pickupLocation || '—',
		deliveryLocation: j.deliveryLocation || '—',
		dateNeeded: j.dateNeeded,
		status: j.status,
		applicationCount: j.applicationCount
	})));

	onMount(loadData);
</script>

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard/admin" icon={ArrowLeft}>
					Back
				</Button>
				<h1><Analytics size={24} /> Platform Analytics</h1>
				<p class="page-subtitle">Overview of platform activity and usage</p>
			</div>
		</Column>
	</Row>

	{#if error}
		<Row>
			<Column>
				<InlineNotification kind="error" title="Error" subtitle={error}
					on:close={() => error = ''} />
			</Column>
		</Row>
	{/if}

	{#if loading}
		<Row><Column><p>Loading analytics...</p></Column></Row>
	{:else if stats}
		<Row>
			<Column lg={4} md={4} sm={4}>
				<Tile class="stat-tile">
					<div class="stat-card">
						<UserMultiple size={24} />
						<div class="stat-value">{stats.totalUsers}</div>
						<div class="stat-label">Total Users</div>
						<div class="stat-breakdown">
							<Tag type="blue" size="sm">{stats.totalDrivers} drivers</Tag>
							<Tag type="green" size="sm">{stats.totalEmployers} employers</Tag>
						</div>
					</div>
				</Tile>
			</Column>
			<Column lg={4} md={4} sm={4}>
				<Tile class="stat-tile">
					<div class="stat-card">
						<Van size={24} />
						<div class="stat-value">{stats.totalJobs}</div>
						<div class="stat-label">Total Jobs</div>
						<div class="stat-breakdown">
							<Tag type="green" size="sm">{stats.openJobs} open</Tag>
							<Tag type="blue" size="sm">{stats.assignedJobs} assigned</Tag>
							<Tag type="cyan" size="sm">{stats.inProgressJobs} in progress</Tag>
						</div>
					</div>
				</Tile>
			</Column>
			<Column lg={4} md={4} sm={4}>
				<Tile class="stat-tile">
					<div class="stat-card">
						<Van size={24} />
						<div class="stat-value">{stats.completedJobs}</div>
						<div class="stat-label">Completed Jobs</div>
						<div class="stat-breakdown">
							<Tag type="red" size="sm">{stats.cancelledJobs} cancelled</Tag>
						</div>
					</div>
				</Tile>
			</Column>
			<Column lg={4} md={4} sm={4}>
				<Tile class="stat-tile">
					<div class="stat-card">
						<Document size={24} />
						<div class="stat-value">{stats.pendingDocuments}</div>
						<div class="stat-label">Pending Documents</div>
						<div class="stat-breakdown">
							<Tag type={stats.pendingDocuments > 0 ? 'red' : 'green'} size="sm">
								{stats.pendingDocuments > 0 ? 'Needs review' : 'All clear'}
							</Tag>
						</div>
					</div>
				</Tile>
			</Column>
		</Row>

		<Row>
			<Column>
				<h2 class="section-heading">All Jobs</h2>
				{#if jobs.length === 0}
					<InlineNotification kind="info" title="No jobs" subtitle="No jobs have been posted yet." hideCloseButton />
				{:else}
					<DataTable
						headers={jobHeaders}
						rows={jobRows}
						sortable
						size="short"
					>
						<svelte:fragment slot="cell" let:cell>
							{#if cell.key === 'status'}
								<Tag type={statusKind(cell.value)} size="sm">{cell.value}</Tag>
							{:else}
								{cell.value}
							{/if}
						</svelte:fragment>
					</DataTable>
				{/if}
			</Column>
		</Row>
	{/if}
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
	.stat-card {
		text-align: center;
		padding: 1rem 0;
	}
	.stat-value {
		font-size: 2.5rem;
		font-weight: 600;
		margin: 0.5rem 0 0.25rem;
	}
	.stat-label {
		font-size: 0.875rem;
		color: var(--cds-text-secondary);
		margin-bottom: 0.75rem;
	}
	.stat-breakdown {
		display: flex;
		justify-content: center;
		gap: 0.25rem;
		flex-wrap: wrap;
	}
	.section-heading {
		margin: 2rem 0 1rem;
		font-size: 1.25rem;
	}
</style>
