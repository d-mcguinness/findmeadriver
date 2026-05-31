<script lang="ts">
	import { Row, Column, Tile, Tag } from 'carbon-components-svelte';
	import {
		UserMultiple, Van, Document as DocumentIcon, CheckmarkOutline,
		Search, StarFilled, CertificateCheck
	} from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
	import { api } from '$lib/api';
	import { driverState } from '$lib/stores/driverState.svelte';
	import { shipperState } from '$lib/stores/shipperState.svelte';
	import type {
		PlatformStats, Job, DriverComplianceSummary
	} from '$lib/types';
	import { onMount } from 'svelte';

	let platformStats = $state<PlatformStats | null>(null);
	let driverAvailableJobs = $state<Job[]>([]);
	let driverCompliance = $state<DriverComplianceSummary | null>(null);

	// Both shipper and driver data come from shared stores so dashboard actions
	// (apply/withdraw, accept/reject, status updates) refresh these tiles reactively.
	let driverApplications = $derived(driverState.applications);
	let shipperJobs = $derived(shipperState.jobs);
	let shipperAvgRating = $derived(shipperState.averageRating);

	let shipperCounts = $derived.by(() => {
		const c = { open: 0, assigned: 0, inProgress: 0, completed: 0, cancelled: 0 };
		for (const j of shipperJobs) {
			if (j.status === 'OPEN') c.open++;
			else if (j.status === 'ASSIGNED') c.assigned++;
			else if (j.status === 'IN_PROGRESS') c.inProgress++;
			else if (j.status === 'COMPLETED') c.completed++;
			else if (j.status === 'CANCELLED') c.cancelled++;
		}
		return c;
	});

	let shipperMetrics = $derived.by(() => {
		let applications = 0;
		let hours = 0;
		let value = 0;
		let completedSpend = 0;
		for (const j of shipperJobs) {
			applications += j.applicationCount ?? 0;
			const h = j.estimatedDurationHours ?? 0;
			const r = Number(j.ratePerHour ?? 0);
			hours += h;
			value += h * r;
			if (j.status === 'COMPLETED') completedSpend += h * r;
		}
		return { applications, hours, value, completedSpend };
	});

	let driverAppCounts = $derived.by(() => {
		const c = { pending: 0, accepted: 0, rejected: 0, withdrawn: 0 };
		for (const a of driverApplications) {
			if (a.status === 'PENDING') c.pending++;
			else if (a.status === 'ACCEPTED') c.accepted++;
			else if (a.status === 'REJECTED') c.rejected++;
			else if (a.status === 'WITHDRAWN') c.withdrawn++;
		}
		return c;
	});

	onMount(async () => {
		try {
			if (auth.isAdmin) {
				platformStats = await api.get<PlatformStats>('/api/admin/stats');
			} else if (auth.isShipper) {
				await Promise.all([
					shipperState.reloadJobs(),
					shipperState.reloadRatings()
				]);
			} else if (auth.isDriver) {
				const [, jobs, compliance] = await Promise.all([
					driverState.reloadApplications(),
					api.get<Job[]>('/api/driver/jobs').catch(() => [] as Job[]),
					api.get<DriverComplianceSummary>('/api/driver/compliance')
						.catch(() => null)
				]);
				driverAvailableJobs = jobs;
				driverCompliance = compliance;
			}
		} catch {
			// silent — keep tiles empty
		}
	});
</script>

{#if auth.isAdmin && platformStats}
	<Row>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<UserMultiple size={24} />
					<div class="stat-value">{platformStats.totalUsers}</div>
					<div class="stat-label">Total Users</div>
					<div class="stat-breakdown">
						<Tag type="blue" size="sm">{platformStats.totalDrivers} drivers</Tag>
						<Tag type="green" size="sm">{platformStats.totalShippers} shippers</Tag>
					</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<Van size={24} />
					<div class="stat-value">{platformStats.totalJobs}</div>
					<div class="stat-label">Total Jobs</div>
					<div class="stat-breakdown">
						<Tag type="green" size="sm">{platformStats.openJobs} open</Tag>
						<Tag type="blue" size="sm">{platformStats.assignedJobs} assigned</Tag>
						<Tag type="cyan" size="sm">{platformStats.inProgressJobs} in progress</Tag>
					</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<CheckmarkOutline size={24} />
					<div class="stat-value">{platformStats.completedJobs}</div>
					<div class="stat-label">Completed Jobs</div>
					<div class="stat-breakdown">
						<Tag type="red" size="sm">{platformStats.cancelledJobs} cancelled</Tag>
					</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<DocumentIcon size={24} />
					<div class="stat-value">{platformStats.pendingDocuments}</div>
					<div class="stat-label">Pending Documents</div>
					<div class="stat-breakdown">
						<Tag type={platformStats.pendingDocuments > 0 ? 'red' : 'green'} size="sm">
							{platformStats.pendingDocuments > 0 ? 'Needs review' : 'All clear'}
						</Tag>
					</div>
				</div>
			</Tile>
		</Column>
	</Row>
{:else if auth.isShipper}
	<Row>
		<Column>
			<Tile class="stat-tile">
				<div class="stat-card">
					<Van size={24} />
					<div class="stat-value">{shipperJobs.length}</div>
					<div class="stat-label">My Jobs</div>
					<div class="metrics-row">
						<div class="metric">
							<div class="metric-value">{shipperMetrics.applications}</div>
							<div class="metric-label">Applications received</div>
						</div>
						<div class="metric">
							<div class="metric-value">{shipperMetrics.hours.toFixed(1)}h</div>
							<div class="metric-label">Hours posted</div>
						</div>
						<div class="metric">
							<div class="metric-value">&euro;{shipperMetrics.value.toFixed(0)}</div>
							<div class="metric-label">Total job value</div>
						</div>
						<div class="metric">
							<div class="metric-value">&euro;{shipperMetrics.completedSpend.toFixed(0)}</div>
							<div class="metric-label">Completed spend</div>
						</div>
					</div>
					<div class="stat-breakdown">
						<Tag type="green" size="sm">{shipperCounts.open} open</Tag>
						<Tag type="blue" size="sm">{shipperCounts.assigned} assigned</Tag>
						<Tag type="cyan" size="sm">{shipperCounts.inProgress} in progress</Tag>
						<Tag type="gray" size="sm">{shipperCounts.completed} completed</Tag>
						<Tag type="red" size="sm">{shipperCounts.cancelled} cancelled</Tag>
						<Tag type="warm-gray" size="sm" icon={StarFilled}>
							{shipperAvgRating != null ? `${shipperAvgRating.toFixed(1)} avg rating` : 'No ratings'}
						</Tag>
					</div>
				</div>
			</Tile>
		</Column>
	</Row>
{:else if auth.isDriver}
	<Row>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<Search size={24} />
					<div class="stat-value">{driverAvailableJobs.length}</div>
					<div class="stat-label">Available Jobs</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<DocumentIcon size={24} />
					<div class="stat-value">
						{driverAppCounts.pending + driverAppCounts.accepted + driverAppCounts.rejected}
					</div>
					<div class="stat-label">Active Applications</div>
					<div class="stat-breakdown">
						<Tag type="blue" size="sm">{driverAppCounts.pending} pending</Tag>
						<Tag type="green" size="sm">{driverAppCounts.accepted} accepted</Tag>
						{#if driverAppCounts.withdrawn > 0}
							<Tag type="magenta" size="sm">{driverAppCounts.withdrawn} withdrawn</Tag>
						{/if}
					</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<CertificateCheck size={24} />
					<div class="stat-value">
						{driverCompliance ? `${driverCompliance.verifiedCount}/${driverCompliance.totalCount}` : '—'}
					</div>
					<div class="stat-label">Compliance</div>
					<div class="stat-breakdown">
						<Tag type={driverCompliance?.allVerified ? 'green' : 'red'} size="sm">
							{driverCompliance?.allVerified ? 'All verified' : 'Action needed'}
						</Tag>
					</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<CheckmarkOutline size={24} />
					<div class="stat-value">{driverAppCounts.accepted}</div>
					<div class="stat-label">Accepted Jobs</div>
				</div>
			</Tile>
		</Column>
	</Row>
{/if}

<style>
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
	.metrics-row {
		display: flex;
		justify-content: center;
		gap: 2rem;
		flex-wrap: wrap;
		margin: 0.5rem 0 0.75rem;
	}
	.metric {
		text-align: center;
		min-width: 6rem;
	}
	.metric-value {
		font-size: 1.5rem;
		font-weight: 600;
	}
	.metric-label {
		font-size: 0.75rem;
		color: var(--cds-text-secondary);
	}
</style>
