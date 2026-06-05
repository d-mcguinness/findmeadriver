<script lang="ts">
	import {
		Grid, Row, Column, Button, InlineNotification, Modal, Tag
	} from 'carbon-components-svelte';
	import { ArrowLeft, Van, Add, Edit } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import type { Load } from '$lib/types';
	import { onMount } from 'svelte';
	import LoadsTable from '$lib/components/admin/LoadsTable.svelte';

	type CarrierOption = {
		id: number;
		firstName?: string;
		lastName?: string;
		email: string;
		licenceCategory?: string;
	};

	type Application = {
		id: number;
		carrierId: number;
		status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN';
	};

	type EligibilityReason =
		| 'OK' | 'LOAD_NOT_OPEN' | 'ALREADY_APPLIED' | 'LICENCE' | 'AVAILABILITY' | 'CABOTAGE';
	type Eligibility = { carrierId: number; eligible: boolean; reason: EligibilityReason };

	// Human label for why a carrier can't be applied (OK/ALREADY_APPLIED/
	// LOAD_NOT_OPEN render as a plain dash — status/closed is shown elsewhere).
	const REASON_LABEL: Record<EligibilityReason, string> = {
		OK: '',
		LOAD_NOT_OPEN: '',
		ALREADY_APPLIED: '',
		LICENCE: 'licence n/a',
		AVAILABILITY: 'no hours on date',
		CABOTAGE: 'cabotage limit'
	};

	let loads = $state<Load[]>([]);
	let loading = $state(true);
	let error = $state('');

	// Applications-modal state
	let carriers = $state<CarrierOption[]>([]);
	let appsLoad = $state<Load | null>(null);
	let loadApps = $state<Application[]>([]);
	let eligibility = $state<Eligibility[]>([]);
	let appsLoading = $state(false);
	let appsError = $state('');
	let applyingCarrierId = $state<number | null>(null);

	// carrierId -> their application / eligibility for the open load
	let appByCarrier = $derived(new Map(loadApps.map((a) => [a.carrierId, a])));
	let eligByCarrier = $derived(new Map(eligibility.map((e) => [e.carrierId, e])));

	function carrierLabel(d: CarrierOption): string {
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

	// Eligibility is computed server-side (same rules applyForLoad enforces:
	// status, duplicate, licence, availability, cabotage), so we only offer
	// Apply where it would actually succeed.
	function canApply(d: CarrierOption): boolean {
		return eligByCarrier.get(d.id)?.eligible ?? false;
	}

	// Short label for an ineligible carrier (empty for OK / already-applied /
	// closed-load — those read from the status column or the load header).
	function ineligibleLabel(d: CarrierOption): string {
		const e = eligByCarrier.get(d.id);
		return e ? REASON_LABEL[e.reason] : '';
	}

	async function loadLoads() {
		loading = true;
		error = '';
		try {
			loads = await api.get<Load[]>('/api/admin/loads');
		} catch (e: any) {
			error = e.message || 'Failed to load loads';
		} finally {
			loading = false;
		}
	}

	async function loadCarriers() {
		try {
			carriers = await api.get<CarrierOption[]>('/api/admin/carriers');
		} catch {
			carriers = [];
		}
	}

	async function cancelLoad(id: number) {
		try {
			await api.put(`/api/admin/loads/${id}/cancel`, {});
			loadLoads();
		} catch (e: any) {
			error = e.message || 'Failed to cancel load';
		}
	}

	async function openApplicationsModal(load: Load) {
		appsLoad = load;
		appsError = '';
		loadApps = [];
		eligibility = [];
		await loadLoadApps(load.id);
	}

	function closeApplicationsModal() {
		appsLoad = null;
		loadApps = [];
		eligibility = [];
		applyingCarrierId = null;
	}

	async function loadLoadApps(loadId: number) {
		appsLoading = true;
		try {
			[loadApps, eligibility] = await Promise.all([
				api.get<Application[]>(`/api/admin/loads/${loadId}/applications`),
				api.get<Eligibility[]>(`/api/admin/loads/${loadId}/carrier-eligibility`)
			]);
		} catch (e: any) {
			appsError = e.message || 'Failed to load applications';
			loadApps = [];
			eligibility = [];
		} finally {
			appsLoading = false;
		}
	}

	async function applyForCarrier(carrierId: number) {
		if (!appsLoad) return;
		appsError = '';
		applyingCarrierId = carrierId;
		try {
			await api.post(
				`/api/admin/applications?carrierId=${carrierId}&loadId=${appsLoad.id}`,
				{ coverNote: '' }
			);
			await loadLoadApps(appsLoad.id); // refresh statuses + eligibility in the table
			loadLoads(); // refresh applicationCount in the main table
		} catch (e: any) {
			appsError = e.message || 'Failed to apply on behalf of the carrier';
		} finally {
			applyingCarrierId = null;
		}
	}

	onMount(() => {
		loadLoads();
		loadCarriers();
	});
</script>

{#snippet adminActions(load: Load)}
	<div class="row-actions">
		{#if load.status === 'OPEN'}
			<Button size="small" kind="ghost" icon={Edit} iconDescription="Edit load"
				href={`/dashboard/loads/${load.id}/edit`} />
		{/if}
		<Button size="small" kind="tertiary" on:click={() => openApplicationsModal(load)}>
			Applications ({load.applicationCount})
		</Button>
		{#if load.status === 'OPEN' || load.status === 'ASSIGNED' || load.status === 'IN_PROGRESS'}
			<Button size="small" kind="danger-tertiary" on:click={() => cancelLoad(load.id)}>
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
				<h1><Van size={24} /> All Loads</h1>
				<p class="page-subtitle">{loads.length} posted load{loads.length !== 1 ? 's' : ''}</p>
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
				<p>Loading loads...</p>
			{:else if loads.length === 0}
				<InlineNotification kind="info" title="No loads" subtitle="No loads have been posted yet." hideCloseButton />
				<Button href="/dashboard/loads/post" icon={Add}>Add Load</Button>
			{:else}
				<LoadsTable {loads} actions={adminActions} addHref="/dashboard/loads/post" />
			{/if}
		</Column>
	</Row>
</Grid>

<Modal
	open={appsLoad !== null}
	passiveModal
	modalHeading="Applications{appsLoad ? ` — ${appsLoad.title}` : ''}"
	on:close={closeApplicationsModal}
>
	{#if appsLoad}
		<p class="apps-meta">
			{#if appsLoad.requiredLicenceCategory}requires <strong>{appsLoad.requiredLicenceCategory}</strong> · {/if}
			status <strong>{appsLoad.status}</strong>
			{#if appsLoad.status !== 'OPEN'}<span class="apps-note"> · not open for new applications</span>{/if}
		</p>

		{#if appsError}
			<InlineNotification kind="error" subtitle={appsError} on:close={() => appsError = ''} />
		{/if}

		{#if appsLoading}
			<p>Loading applications...</p>
		{:else}
			<table class="apps-table">
				<thead>
					<tr><th>Carrier</th><th>Status</th><th>Action</th></tr>
				</thead>
				<tbody>
					{#each carriers as d}
						{@const app = appByCarrier.get(d.id)}
						<tr>
							<td>{carrierLabel(d)}</td>
							<td>
								{#if app}
									<Tag type={statusColor(app.status)} size="sm">{app.status}</Tag>
								{:else}
									<span class="dash">—</span>
								{/if}
							</td>
							<td>
								{#if canApply(d)}
									<Button size="small" kind="tertiary"
										disabled={applyingCarrierId !== null}
										on:click={() => applyForCarrier(d.id)}>
										{applyingCarrierId === d.id ? 'Applying…' : 'Apply'}
									</Button>
								{:else if ineligibleLabel(d)}
									<span class="dash" title="Carrier does not meet this load's requirements">
										{ineligibleLabel(d)}
									</span>
								{:else}
									<span class="dash">—</span>
								{/if}
							</td>
						</tr>
					{/each}
					{#if carriers.length === 0}
						<tr><td colspan="3">No carriers available.</td></tr>
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
