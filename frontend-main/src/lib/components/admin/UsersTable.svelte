<script lang="ts">
	import {
		DataTable, Tag, Toolbar, ToolbarContent, ToolbarSearch
	} from 'carbon-components-svelte';
	import type { AdminUser } from '$lib/types';

	let {
		users,
		searchable = true
	}: {
		users: AdminUser[];
		searchable?: boolean;
	} = $props();

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
</script>

<DataTable
	{headers}
	{rows}
	sortable
	size="short"
>
	{#if searchable}
		<Toolbar>
			<ToolbarContent>
				<ToolbarSearch persistent />
			</ToolbarContent>
		</Toolbar>
	{/if}
	<svelte:fragment slot="cell" let:cell>
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

<style>
	.roles-cell {
		display: flex;
		gap: 0.25rem;
		flex-wrap: wrap;
	}
</style>
