<script lang="ts">
	import {
		Grid, Row, Column, Button, InlineNotification,
		Modal, Select, SelectItem, TextArea
	} from 'carbon-components-svelte';
	import { ArrowLeft, UserAdmin, Add, Document } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import type { AdminUser, Job, JobApplication } from '$lib/types';
	import { onMount } from 'svelte';
	import UsersTable from '$lib/components/admin/UsersTable.svelte';

	let users = $state<AdminUser[]>([]);
	let loading = $state(true);
	let error = $state('');

	// Apply-on-behalf modal state
	let applyOpen = $state(false);
	let applyDriver = $state<AdminUser | null>(null);
	let openJobs = $state<Job[]>([]);
	let applyJobId = $state<string>('');
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

	async function openApplyModal(driver: AdminUser) {
		applyDriver = driver;
		applyError = '';
		applySuccess = '';
		applyNote = '';
		applyJobId = '';
		applyOpen = true;
		try {
			const [allJobs, driverApps] = await Promise.all([
				api.get<Job[]>('/api/admin/jobs'),
				api.get<JobApplication[]>(`/api/admin/drivers/${driver.id}/applications`)
			]);
			// Block jobs with a non-withdrawn application; withdrawn ones are eligible
			// for re-apply (backend revives the existing row).
			const blockedJobIds = new Set(
				driverApps.filter(a => a.status !== 'WITHDRAWN').map(a => a.jobId)
			);
			const jobLicence = (j: Job) => j.requiredLicenceCategory ?? j.requiredCdlType;
			const driverLicence = driver.licenceCategory ?? driver.cdlType;
			openJobs = allJobs.filter(j =>
				j.status === 'OPEN' &&
				!blockedJobIds.has(j.id) &&
				(!jobLicence(j) || !driverLicence || jobLicence(j) === driverLicence)
			);
			if (openJobs.length > 0) applyJobId = String(openJobs[0].id);
		} catch (e: any) {
			applyError = e?.error || e?.message || 'Failed to load jobs';
			openJobs = [];
		}
	}

	async function submitApply() {
		if (!applyDriver || !applyJobId) return;
		applyError = '';
		applySuccess = '';
		applyLoading = true;
		try {
			await api.post(
				`/api/admin/applications?driverId=${applyDriver.id}&jobId=${applyJobId}`,
				{ coverNote: applyNote }
			);
			applySuccess = 'Application submitted on behalf of ' +
				`${applyDriver.firstName} ${applyDriver.lastName}.`;
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
				href="/dashboard/jobs/post?shipperId={user.id}">
				Post Job
			</Button>
		{:else if user.userType === 'DRIVER'}
			<Button size="small" kind="tertiary" icon={Document}
				on:click={() => openApplyModal(user)}>
				Apply for Job
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
				<UsersTable {users} actions={userActions} />
			{/if}
		</Column>
	</Row>
</Grid>

<Modal
	bind:open={applyOpen}
	modalHeading={applyDriver
		? `Apply for a job — ${applyDriver.firstName} ${applyDriver.lastName}`
		: 'Apply for a job'}
	primaryButtonText="Submit Application"
	secondaryButtonText="Cancel"
	primaryButtonDisabled={!applyJobId || applyLoading || openJobs.length === 0}
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

	{#if openJobs.length === 0}
		<p>No open jobs match this driver's licence category ({applyDriver?.licenceCategory ?? applyDriver?.cdlType ?? 'none set'}).</p>
	{:else}
		<Select bind:selected={applyJobId} labelText="Choose an open job">
			{#each openJobs as job}
				<SelectItem value={String(job.id)}
					text="#{job.id} · {job.title} · {job.pickupLocation} → {job.deliveryLocation} · {job.dateNeeded}" />
			{/each}
		</Select>
		<br />
		<TextArea bind:value={applyNote} labelText="Cover note (optional)"
			placeholder="Why is this driver a good fit?" rows={3} />
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
