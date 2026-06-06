<script lang="ts">
	import {
		Grid, Row, Column, Button, InlineNotification,
		Modal, Select, SelectItem, TextArea
	} from 'carbon-components-svelte';
	import { ArrowLeft, UserAdmin, Add, Document } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import type { AdminUser, Load, LoadApplication } from '$lib/types';
	import { onMount } from 'svelte';
	import UsersTable from '$lib/components/admin/UsersTable.svelte';

	let users = $state<AdminUser[]>([]);
	let loading = $state(true);
	let error = $state('');

	// Apply-on-behalf modal state
	let applyOpen = $state(false);
	let applyCarrier = $state<AdminUser | null>(null);
	let openLoads = $state<Load[]>([]);
	let applyLoadId = $state<string>('');
	let applyNote = $state('');
	let applyError = $state('');
	let applySuccess = $state('');
	let applyLoading = $state(false);

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

	async function openApplyModal(carrier: AdminUser) {
		applyCarrier = carrier;
		applyError = '';
		applySuccess = '';
		applyNote = '';
		applyLoadId = '';
		applyOpen = true;
		try {
			const [allLoads, carrierApps] = await Promise.all([
				api.get<Load[]>('/api/admin/loads'),
				api.get<LoadApplication[]>(`/api/admin/carriers/${carrier.id}/applications`)
			]);
			// Block loads with a non-withdrawn application; withdrawn ones are eligible
			// for re-apply (backend revives the existing row).
			const blockedLoadIds = new Set(
				carrierApps.filter(a => a.status !== 'WITHDRAWN').map(a => a.loadId)
			);
			const loadLicence = (j: Load) => j.requiredLicenceCategory ?? j.requiredCdlType;
			const carrierLicence = carrier.licenceCategory ?? carrier.cdlType;
			openLoads = allLoads.filter(j =>
				j.status === 'OPEN' &&
				!blockedLoadIds.has(j.id) &&
				(!loadLicence(j) || !carrierLicence || loadLicence(j) === carrierLicence)
			);
			if (openLoads.length > 0) applyLoadId = String(openLoads[0].id);
		} catch (e: any) {
			applyError = e?.error || e?.message || 'Failed to load loads';
			openLoads = [];
		}
	}

	async function submitApply() {
		if (!applyCarrier || !applyLoadId) return;
		applyError = '';
		applySuccess = '';
		applyLoading = true;
		try {
			await api.post(
				`/api/admin/applications?carrierId=${applyCarrier.id}&loadId=${applyLoadId}`,
				{ coverNote: applyNote }
			);
			applySuccess = 'Application submitted on behalf of ' +
				`${applyCarrier.firstName} ${applyCarrier.lastName}.`;
			setTimeout(() => { applyOpen = false; }, 1200);
		} catch (e: any) {
			applyError = e?.error || e?.message || 'Failed to apply';
		} finally {
			applyLoading = false;
		}
	}

	onMount(loadUsers);
</script>

{#snippet userActions(user: AdminUser)}
	<div class="row-actions">
		{#if user.userType === 'SHIPPER'}
			<Button size="small" kind="primary" icon={Add}
				href="/dashboard/loads/post?shipperId={user.id}">
				Post Load
			</Button>
		{:else if user.userType === 'CARRIER'}
			<Button size="small" kind="tertiary" icon={Document}
				on:click={() => openApplyModal(user)}>
				Apply for Load
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
				<h1 class="section-heading"><span class="icon-badge sm"><UserAdmin size={24} /></span> Manage Users</h1>
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
				<UsersTable {users} actions={userActions} />
			{/if}
		</Column>
	</Row>
</Grid>

<Modal
	bind:open={applyOpen}
	modalHeading={applyCarrier
		? `Apply for a load — ${applyCarrier.firstName} ${applyCarrier.lastName}`
		: 'Apply for a load'}
	primaryButtonText="Submit Application"
	secondaryButtonText="Cancel"
	primaryButtonDisabled={!applyLoadId || applyLoading || openLoads.length === 0}
	on:click:button--primary={submitApply}
	on:click:button--secondary={() => applyOpen = false}
>
	{#if applyError}
		<InlineNotification kind="error" title="Error" subtitle={applyError}
			on:close={() => applyError = ''} />
	{/if}
	{#if applySuccess}
		<InlineNotification kind="success" title="Submitted" subtitle={applySuccess}
			hideCloseButton />
	{/if}

	{#if openLoads.length === 0}
		<p>No open loads match this carrier's licence category ({applyCarrier?.licenceCategory ?? applyCarrier?.cdlType ?? 'none set'}).</p>
	{:else}
		<Select bind:selected={applyLoadId} labelText="Choose an open load">
			{#each openLoads as load}
				<SelectItem value={String(load.id)}
					text="#{load.id} · {load.title} · {load.pickupLocation} → {load.deliveryLocation} · {load.dateNeeded}" />
			{/each}
		</Select>
		<br />
		<TextArea bind:value={applyNote} labelText="Cover note (optional)"
			placeholder="Why is this carrier a good fit?" rows={3} />
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
</style>
