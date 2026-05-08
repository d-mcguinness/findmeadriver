<script lang="ts">
	import { DataTable, Tag } from 'carbon-components-svelte';
	import type { Job } from '$lib/types';

	let { jobs }: { jobs: Job[] } = $props();

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

	const headers = [
		{ key: 'id', value: 'ID' },
		{ key: 'title', value: 'Title' },
		{ key: 'employerCompanyName', value: 'Employer' },
		{ key: 'pickupLocation', value: 'From' },
		{ key: 'deliveryLocation', value: 'To' },
		{ key: 'dateNeeded', value: 'Date' },
		{ key: 'status', value: 'Status' },
		{ key: 'applicationCount', value: 'Applications' }
	];

	let rows = $derived(jobs.map(j => ({
		id: String(j.id),
		title: j.title,
		employerCompanyName: j.employerCompanyName,
		pickupLocation: j.pickupLocation || '—',
		deliveryLocation: j.deliveryLocation || '—',
		dateNeeded: j.dateNeeded,
		status: j.status,
		applicationCount: j.applicationCount
	})));
</script>

<DataTable
	{headers}
	{rows}
	sortable
	size="short"
>
	<svelte:fragment slot="cell" let:cell>
		{#if cell.key === 'status'}
			<Tag type={statusKind(cell.value)} size="sm">{cell.value}</Tag>
		{:else}
			{cell.value}
		{/if}
	</svelte:fragment>
</DataTable>
