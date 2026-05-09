<script lang="ts">
	import { Row, Column, Tile, Tag } from 'carbon-components-svelte';
	import {
		UserMultiple, Van, Document as DocumentIcon, CheckmarkOutline,
		Time, Search, StarFilled, CertificateCheck
	} from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
	import { api } from '$lib/api';
	import type {
		PlatformStats, Job, JobApplication, DriverComplianceSummary
	} from '$lib/types';
	import { onMount } from 'svelte';

	let platformStats = $state<PlatformStats | null>(null);
	let employerJobs = $state<Job[]>([]);
	let employerAvgRating = $state<number | null>(null);
	let driverApplications = $state<JobApplication[]>([]);
	let driverAvailableJobs = $state<Job[]>([]);
	let driverCompliance = $state<DriverComplianceSummary | null>(null);

	let employerCounts = $derived.by(() => {
		const c = { open: 0, assigned: 0, inProgress: 0, completed: 0, cancelled: 0 };
		for (const j of employerJobs) {
			if (j.status === 'OPEN') c.open++;
			else if (j.status === 'ASSIGNED') c.assigned++;
			else if (j.status === 'IN_PROGRESS') c.inProgress++;
			else if (j.status === 'COMPLETED') c.completed++;
			else if (j.status === 'CANCELLED') c.cancelled++;
		}
		return c;
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
			} else if (auth.isEmployer) {
				const [jobs, ratings] = await Promise.all([
					api.get<Job[]>('/api/employer/jobs'),
					api.get<{ averageRating: number; totalRatings: number }>('/api/employer/ratings')
						.catch(() => ({ averageRating: 0, totalRatings: 0 }))
				]);
				employerJobs = jobs;
				employerAvgRating = ratings.totalRatings > 0 ? ratings.averageRating : null;
			} else if (auth.isDriver) {
				const [apps, jobs, compliance] = await Promise.all([
					api.get<JobApplication[]>('/api/driver/applications'),
					api.get<Job[]>('/api/driver/jobs').catch(() => [] as Job[]),
					api.get<DriverComplianceSummary>('/api/driver/compliance')
						.catch(() => null)
				]);
				driverApplications = apps;
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
						<Tag type="green" size="sm">{platformStats.totalEmployers} employers</Tag>
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
{:else if auth.isEmployer}
	<Row>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<Van size={24} />
					<div class="stat-value">{employerJobs.length}</div>
					<div class="stat-label">My Jobs</div>
					<div class="stat-breakdown">
						<Tag type="green" size="sm">{employerCounts.open} open</Tag>
						<Tag type="blue" size="sm">{employerCounts.assigned} assigned</Tag>
					</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<Time size={24} />
					<div class="stat-value">{employerCounts.inProgress}</div>
					<div class="stat-label">In Progress</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<CheckmarkOutline size={24} />
					<div class="stat-value">{employerCounts.completed}</div>
					<div class="stat-label">Completed</div>
					<div class="stat-breakdown">
						<Tag type="red" size="sm">{employerCounts.cancelled} cancelled</Tag>
					</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<StarFilled size={24} />
					<div class="stat-value">
						{employerAvgRating != null ? employerAvgRating.toFixed(1) : '—'}
					</div>
					<div class="stat-label">Avg Rating</div>
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
					<div class="stat-value">{driverApplications.length}</div>
					<div class="stat-label">My Applications</div>
					<div class="stat-breakdown">
						<Tag type="blue" size="sm">{driverAppCounts.pending} pending</Tag>
						<Tag type="green" size="sm">{driverAppCounts.accepted} accepted</Tag>
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
</style>
