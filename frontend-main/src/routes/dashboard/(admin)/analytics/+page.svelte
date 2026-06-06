<script lang="ts">
	import {
		Grid, Row, Column, Tile, Tag, Button, InlineNotification
	} from 'carbon-components-svelte';
	import { ArrowLeft, Analytics, UserMultiple, Van, Document } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import type { PlatformStats, Load } from '$lib/types';
	import { onMount } from 'svelte';
	import LoadsTable from '$lib/components/admin/LoadsTable.svelte';

	let stats = $state<PlatformStats | null>(null);
	let loads = $state<Load[]>([]);
	let loading = $state(true);
	let error = $state('');

	async function loadData() {
		loading = true;
		error = '';
		try {
			const [s, j] = await Promise.all([
				api.get<PlatformStats>('/api/admin/stats'),
				api.get<Load[]>('/api/admin/loads')
			]);
			stats = s;
			loads = j;
		} catch (e: any) {
			error = e.message || 'Failed to load analytics';
		} finally {
			loading = false;
		}
	}

	onMount(loadData);
</script>

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard" icon={ArrowLeft}>
					Back
				</Button>
				<h1 class="section-heading"><span class="icon-badge sm"><Analytics size={20} /></span> Platform Analytics</h1>
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
				<div class="fmad-card">
					<Tile class="stat-tile">
						<div class="stat-card">
							<UserMultiple size={24} />
							<div class="stat-value">{stats.totalUsers}</div>
							<div class="stat-label">Total Users</div>
							<div class="stat-breakdown">
								<Tag type="blue" size="sm">{stats.totalCarriers} carriers</Tag>
								<Tag type="green" size="sm">{stats.totalShippers} shippers</Tag>
							</div>
						</div>
					</Tile>
				</div>
			</Column>
			<Column lg={4} md={4} sm={4}>
				<div class="fmad-card">
					<Tile class="stat-tile">
						<div class="stat-card">
							<Van size={24} />
							<div class="stat-value">{stats.totalLoads}</div>
							<div class="stat-label">Total Loads</div>
							<div class="stat-breakdown">
								<Tag type="green" size="sm">{stats.openLoads} open</Tag>
								<Tag type="blue" size="sm">{stats.assignedLoads} assigned</Tag>
								<Tag type="cyan" size="sm">{stats.inProgressLoads} in progress</Tag>
							</div>
						</div>
					</Tile>
				</div>
			</Column>
			<Column lg={4} md={4} sm={4}>
				<div class="fmad-card">
					<Tile class="stat-tile">
						<div class="stat-card">
							<Van size={24} />
							<div class="stat-value">{stats.completedLoads}</div>
							<div class="stat-label">Completed Loads</div>
							<div class="stat-breakdown">
								<Tag type="red" size="sm">{stats.cancelledLoads} cancelled</Tag>
							</div>
						</div>
					</Tile>
				</div>
			</Column>
			<Column lg={4} md={4} sm={4}>
				<div class="fmad-card">
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
				</div>
			</Column>
		</Row>

		<Row>
			<Column>
				<h2 id="loads" class="section-heading">All Loads</h2>
				{#if loads.length === 0}
					<InlineNotification kind="info" title="No loads" subtitle="No loads have been posted yet." hideCloseButton />
				{:else}
					<LoadsTable {loads} />
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
</style>
