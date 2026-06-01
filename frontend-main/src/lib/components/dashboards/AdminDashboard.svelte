<script lang="ts">
	import { Grid, Row, Column, ClickableTile, Tag } from 'carbon-components-svelte';
	import { UserAdmin, Van, Document, Settings } from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
	import { api } from '$lib/api';
	import type { PlatformStats } from '$lib/types';
	import { onMount } from 'svelte';

	let stats = $state<PlatformStats | null>(null);

	onMount(async () => {
		try {
			stats = await api.get<PlatformStats>('/api/admin/stats');
		} catch {
			stats = null;
		}
	});
</script>

<Grid>
	<Row>
		<Column>
			<h1>Admin Panel</h1>
			<p class="dashboard-subtitle">Welcome, {auth.user?.firstName}</p>
		</Column>
	</Row>

	<Row>
		<Column lg={5} md={4} sm={4}>
			<ClickableTile class="dashboard-tile" href="/dashboard/users">
				<div class="feature-tile">
					<UserAdmin size={32} />
					<h3>Manage Users</h3>
					{#if stats}
						<div class="tile-stat">{stats.totalUsers}</div>
						<div class="tile-breakdown">
							<Tag type="blue" size="sm">{stats.totalCarriers} carriers</Tag>
							<Tag type="green" size="sm">{stats.totalShippers} shippers</Tag>
						</div>
					{/if}
					<p>View, edit, and manage all registered carriers and shippers.</p>
				</div>
			</ClickableTile>
		</Column>
		<Column lg={5} md={4} sm={4}>
			<ClickableTile class="dashboard-tile" href="/dashboard/loads">
				<div class="feature-tile">
					<Van size={32} />
					<h3>Load Analytics</h3>
					{#if stats}
						<div class="tile-stat">{stats.totalLoads}</div>
						<div class="tile-breakdown">
							<Tag type="green" size="sm">{stats.openLoads} open</Tag>
							<Tag type="blue" size="sm">{stats.assignedLoads} assigned</Tag>
							<Tag type="cyan" size="sm">{stats.inProgressLoads} in progress</Tag>
							<Tag type="gray" size="sm">{stats.completedLoads} completed</Tag>
							<Tag type="red" size="sm">{stats.cancelledLoads} cancelled</Tag>
						</div>
					{/if}
					<p>All posted loads with status, applications, and route details.</p>
				</div>
			</ClickableTile>
		</Column>
		<Column lg={5} md={4} sm={4}>
			<ClickableTile class="dashboard-tile" href="/dashboard/documents">
				<div class="feature-tile">
					<Document size={32} />
					<h3>Document Compliance</h3>
					{#if stats}
						<div class="tile-stat">{stats.pendingDocuments}</div>
						<div class="tile-breakdown">
							<Tag type={stats.pendingDocuments > 0 ? 'red' : 'green'} size="sm">
								{stats.pendingDocuments > 0 ? 'Needs review' : 'All clear'}
							</Tag>
						</div>
					{/if}
					<p>Review pending licences, insurance, CPC and tachograph cards.</p>
				</div>
			</ClickableTile>
		</Column>
		<Column lg={5} md={4} sm={4}>
			<ClickableTile class="dashboard-tile" href="/dashboard/settings">
				<div class="feature-tile">
					<Settings size={32} />
					<h3>Settings</h3>
					<p>Configure platform settings and manage roles.</p>
				</div>
			</ClickableTile>
		</Column>
	</Row>
</Grid>

<style>
	.tile-stat {
		font-size: 2rem;
		font-weight: 600;
		margin: 0.25rem 0;
	}
	.tile-breakdown {
		display: flex;
		gap: 0.25rem;
		flex-wrap: wrap;
		margin-bottom: 0.5rem;
	}
</style>
