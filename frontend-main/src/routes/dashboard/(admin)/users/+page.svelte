<script lang="ts">
	import {
		Grid, Row, Column, Button, InlineNotification
	} from 'carbon-components-svelte';
	import { ArrowLeft, UserAdmin } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import type { AdminUser } from '$lib/types';
	import { onMount } from 'svelte';
	import UsersTable from '$lib/components/admin/UsersTable.svelte';

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

	onMount(loadUsers);
</script>

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard" icon={ArrowLeft}>
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
				<UsersTable {users} />
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
</style>
