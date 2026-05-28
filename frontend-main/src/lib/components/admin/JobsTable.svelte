<script lang="ts">
	import {
		DataTable, Tag, Toolbar, ToolbarContent, Button
	} from 'carbon-components-svelte';
	import { Add } from 'carbon-icons-svelte';
	import type { Snippet } from 'svelte';
	import type { Job } from '$lib/types';

	let {
		jobs,
		actions,
		addHref,
		addLabel = 'Add Job'
	}: {
		jobs: Job[];
		actions?: Snippet<[Job]>;
		addHref?: string;
		addLabel?: string;
	} = $props();

	function statusKind(status: string): 'blue' | 'green' | 'red' | 'gray' | 'cyan' {
		switch (status) {
			case 'OPEN': return 'green';
			case 'ASSIGNED': return 'blue';
			case 'IN_PROGRESS': return 'cyan';
			case 'COMPLETED': return 'gray';
			case 'CANCELLED': return 'red';
			default: return 'gray';
		}
	}

	let baseHeaders = [
		{ key: 'id', value: 'ID' },
		{ key: 'title', value: 'Title' },
		{ key: 'employerCompanyName', value: 'Employer' },
		{ key: 'pickupLocation', value: 'From' },
		{ key: 'deliveryLocation', value: 'To' },
		{ key: 'dateNeeded', value: 'Date' },
		{ key: 'status', value: 'Status' },
		{ key: 'applicationCount', value: 'Applications' },
		{ key: 'hours', value: 'Hours' },
		{ key: 'ratePerHour', value: 'Rate' },
		{ key: 'value', value: 'Value' },
		{ key: 'completedSpend', value: 'Completed spend' }
	];

	let headers = $derived(actions
		? [...baseHeaders, { key: 'actions', value: 'Actions' }]
		: baseHeaders);

	function jobValue(j: Job): number {
		const h = j.estimatedDurationHours ?? 0;
		const r = Number(j.ratePerHour ?? 0);
		return h * r;
	}

	let rows = $derived(jobs.map(j => {
		const v = jobValue(j);
		return {
			id: String(j.id),
			title: j.title,
			employerCompanyName: j.employerCompanyName,
			pickupLocation: j.pickupLocation || '—',
			deliveryLocation: j.deliveryLocation || '—',
			dateNeeded: j.dateNeeded,
			status: j.status,
			applicationCount: j.applicationCount,
			hours: j.estimatedDurationHours != null ? `${j.estimatedDurationHours.toFixed(1)}h` : '—',
			ratePerHour: j.ratePerHour != null ? `€${Number(j.ratePerHour).toFixed(0)}/h` : '—',
			value: v > 0 ? `€${v.toFixed(0)}` : '—',
			completedSpend: j.status === 'COMPLETED' && v > 0 ? `€${v.toFixed(0)}` : '—',
			actions: ''
		};
	}));

	let jobsById = $derived(new Map(jobs.map(j => [String(j.id), j])));
</script>

<DataTable
	{headers}
	{rows}
	sortable
	size="short"
>
	{#if addHref}
		<Toolbar>
			<ToolbarContent>
				<Button href={addHref} icon={Add} size="small">{addLabel}</Button>
			</ToolbarContent>
		</Toolbar>
	{/if}
	<svelte:fragment slot="cell" let:row let:cell>
		{#if cell.key === 'status'}
			<Tag type={statusKind(cell.value)} size="sm">{cell.value}</Tag>
		{:else if cell.key === 'actions' && actions}
			{@const job = jobsById.get(row.id)}
			{#if job}
				{@render actions(job)}
			{/if}
		{:else}
			{cell.value}
		{/if}
	</svelte:fragment>
</DataTable>
