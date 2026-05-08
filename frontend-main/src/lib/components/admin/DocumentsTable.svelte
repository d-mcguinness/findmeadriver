<script lang="ts">
	import { DataTable, Tag } from 'carbon-components-svelte';
	import type { ComplianceDocument } from '$lib/types';

	let { documents }: { documents: ComplianceDocument[] } = $props();

	function statusKind(status: string): 'blue' | 'green' | 'red' | 'gray' {
		switch (status) {
			case 'VERIFIED': return 'green';
			case 'PENDING': return 'blue';
			case 'EXPIRED': return 'red';
			default: return 'gray';
		}
	}

	const headers = [
		{ key: 'id', value: 'ID' },
		{ key: 'documentType', value: 'Type' },
		{ key: 'documentNumber', value: 'Number' },
		{ key: 'expiryDate', value: 'Expires' },
		{ key: 'status', value: 'Status' },
		{ key: 'uploadedAt', value: 'Uploaded' }
	];

	let rows = $derived(documents.map(d => ({
		id: String(d.id),
		documentType: d.documentType,
		documentNumber: d.documentNumber || '—',
		expiryDate: d.expiryDate || '—',
		status: d.status,
		uploadedAt: d.uploadedAt ? d.uploadedAt.slice(0, 10) : '—'
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
