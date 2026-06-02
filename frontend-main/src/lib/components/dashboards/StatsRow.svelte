<script lang="ts">
	import { Row, Column, Tile, Tag } from 'carbon-components-svelte';
	import {
		UserMultiple, Van, Document as DocumentIcon, CheckmarkOutline,
		Search, StarFilled, CertificateCheck
	} from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
	import { api } from '$lib/api';
	import { carrierState } from '$lib/stores/carrierState.svelte';
	import { shipperState } from '$lib/stores/shipperState.svelte';
	import type {
		PlatformStats, Load, CarrierComplianceSummary
	} from '$lib/types';
	import { onMount } from 'svelte';

	let platformStats = $state<PlatformStats | null>(null);
	let carrierAvailableLoads = $state<Load[]>([]);
	let carrierCompliance = $state<CarrierComplianceSummary | null>(null);

	// Both shipper and carrier data come from shared stores so dashboard actions
	// (apply/withdraw, accept/reject, status updates) refresh these tiles reactively.
	let carrierApplications = $derived(carrierState.applications);
	let shipperLoads = $derived(shipperState.loads);
	let shipperAvgRating = $derived(shipperState.averageRating);

	let shipperCounts = $derived.by(() => {
		const c = { open: 0, assigned: 0, inProgress: 0, completed: 0, cancelled: 0 };
		for (const j of shipperLoads) {
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
		for (const j of shipperLoads) {
			applications += j.applicationCount ?? 0;
			const h = j.estimatedDurationHours ?? 0;
			const r = Number(j.ratePerHour ?? 0);
			hours += h;
			value += h * r;
			if (j.status === 'COMPLETED') completedSpend += h * r;
		}
		return { applications, hours, value, completedSpend };
	});

	let carrierAppCounts = $derived.by(() => {
		const c = { pending: 0, accepted: 0, rejected: 0, withdrawn: 0 };
		for (const a of carrierApplications) {
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
					shipperState.reloadLoads(),
					shipperState.reloadRatings()
				]);
			} else if (auth.isCarrier) {
				const [, loads, compliance] = await Promise.all([
					carrierState.reloadApplications(),
					api.get<Load[]>('/api/carrier/loads').catch(() => [] as Load[]),
					api.get<CarrierComplianceSummary>('/api/carrier/compliance')
						.catch(() => null)
				]);
				carrierAvailableLoads = loads;
				carrierCompliance = compliance;
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
						<Tag type="blue" size="sm">{platformStats.totalCarriers} carriers</Tag>
						<Tag type="green" size="sm">{platformStats.totalShippers} shippers</Tag>
					</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<Van size={24} />
					<div class="stat-value">{platformStats.totalLoads}</div>
					<div class="stat-label">Total Loads</div>
					<div class="stat-breakdown">
						<Tag type="green" size="sm">{platformStats.openLoads} open</Tag>
						<Tag type="blue" size="sm">{platformStats.assignedLoads} assigned</Tag>
						<Tag type="cyan" size="sm">{platformStats.inProgressLoads} in progress</Tag>
					</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<CheckmarkOutline size={24} />
					<div class="stat-value">{platformStats.completedLoads}</div>
					<div class="stat-label">Completed Loads</div>
					<div class="stat-breakdown">
						<Tag type="red" size="sm">{platformStats.cancelledLoads} cancelled</Tag>
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
					<div class="stat-value">{shipperLoads.length}</div>
					<div class="stat-label">My Loads</div>
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
							<div class="metric-label">Total load value</div>
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
{:else if auth.isCarrier}
	<Row>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<Search size={24} />
					<div class="stat-value">{carrierAvailableLoads.length}</div>
					<div class="stat-label">Available Loads</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<DocumentIcon size={24} />
					<div class="stat-value">
						{carrierAppCounts.pending + carrierAppCounts.accepted + carrierAppCounts.rejected}
					</div>
					<div class="stat-label">Active Applications</div>
					<div class="stat-breakdown">
						<Tag type="blue" size="sm">{carrierAppCounts.pending} pending</Tag>
						<Tag type="green" size="sm">{carrierAppCounts.accepted} accepted</Tag>
						{#if carrierAppCounts.withdrawn > 0}
							<Tag type="magenta" size="sm">{carrierAppCounts.withdrawn} withdrawn</Tag>
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
						{carrierCompliance ? `${carrierCompliance.verifiedCount}/${carrierCompliance.totalCount}` : '—'}
					</div>
					<div class="stat-label">Compliance</div>
					<div class="stat-breakdown">
						<Tag type={carrierCompliance?.allVerified ? 'green' : 'red'} size="sm">
							{carrierCompliance?.allVerified ? 'All verified' : 'Action needed'}
						</Tag>
					</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="stat-tile">
				<div class="stat-card">
					<CheckmarkOutline size={24} />
					<div class="stat-value">{carrierAppCounts.accepted}</div>
					<div class="stat-label">Accepted Loads</div>
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
