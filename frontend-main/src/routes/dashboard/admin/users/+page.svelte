<script lang="ts">
	import {
		Grid, Row, Column, DataTable, Tag, Button,
		InlineNotification, Toolbar, ToolbarContent, ToolbarSearch
	} from 'carbon-components-svelte';
	import { ArrowLeft, UserAdmin } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import type { AdminUser } from '$lib/types';
	import { onMount } from 'svelte';

	let users = $state<AdminUser[]>([]);
	let loading = $state(true);
	let error = $state('');

	async function loadUsers() {
		loading = true;
		error = '';
		try {
			users = await api.get<AdminUser[]>('/api/admin/users');
		} catch (e: any) {
			error = e.message || 'Failed to load users';
		} finally {
			loading = false;
		}
	}

	function roleKind(role: string): 'blue' | 'green' | 'red' | 'cyan' | 'gray' {
		switch (role) {
			case 'ROLE_ADMIN': return 'red';
			case 'ROLE_DRIVER': return 'blue';
			case 'ROLE_EMPLOYER': return 'green';
			default: return 'gray';
		}
	}

	function roleLabel(role: string): string {
		return role.replace('ROLE_', '');
	}

	function typeKind(type: string): 'blue' | 'green' | 'cyan' | 'gray' {
		switch (type) {
			case 'DRIVER': return 'blue';
			case 'EMPLOYER': return 'green';
			case 'ADMIN': return 'cyan';
			default: return 'gray';
		}
	}

	const headers = [
		{ key: 'id', value: 'ID' },
		{ key: 'name', value: 'Name' },
		{ key: 'email', value: 'Email' },
		{ key: 'userType', value: 'Type' },
		{ key: 'detail', value: 'Detail' },
		{ key: 'roles', value: 'Roles' },
		{ key: 'enabled', value: 'Status' }
	];

	let rows = $derived(users.map(u => ({
		id: String(u.id),
		name: `${u.firstName || ''} ${u.lastName || ''}`.trim() || '—',
		email: u.email,
		userType: u.userType,
		detail: u.userType === 'DRIVER'
			? `CDL: ${u.cdlType || '—'}, Lic: ${u.licenseNumber || '—'}`
			: u.userType === 'EMPLOYER'
				? u.companyName || '—'
				: '—',
		roles: u.roles,
		enabled: u.enabled
	})));

	onMount(loadUsers);
</script>

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard/admin" icon={ArrowLeft}>
					Back
				</Button>
				<h1><UserAdmin size={24} /> Manage Users</h1>
				<p class="page-subtitle">{users.length} registered user{users.length !== 1 ? 's' : ''}</p>
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
				<p>Loading users...</p>
			{:else}
				<DataTable
					{headers}
					{rows}
					sortable
					size="short"
				>
					<Toolbar>
						<ToolbarContent>
							<ToolbarSearch persistent />
						</ToolbarContent>
					</Toolbar>
					<svelte:fragment slot="cell" let:row let:cell>
						{#if cell.key === 'userType'}
							<Tag type={typeKind(cell.value)} size="sm">{cell.value}</Tag>
						{:else if cell.key === 'roles'}
							<div class="roles-cell">
								{#each cell.value as role}
									<Tag type={roleKind(role)} size="sm">{roleLabel(role)}</Tag>
								{/each}
							</div>
						{:else if cell.key === 'enabled'}
							<Tag type={cell.value ? 'green' : 'red'} size="sm">
								{cell.value ? 'Active' : 'Disabled'}
							</Tag>
						{:else}
							{cell.value}
						{/if}
					</svelte:fragment>
				</DataTable>
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
	.roles-cell {
		display: flex;
		gap: 0.25rem;
		flex-wrap: wrap;
	}
</style>
