<script lang="ts">
	import {
		Grid, Row, Column, Button, InlineNotification
	} from 'carbon-components-svelte';
	import { ArrowLeft, Document, View } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import type { ComplianceDocument } from '$lib/types';
	import { onMount } from 'svelte';
	import DocumentsTable from '$lib/components/admin/DocumentsTable.svelte';

	let documents = $state<ComplianceDocument[]>([]);
	let loading = $state(true);
	let error = $state('');

	async function loadDocuments() {
		loading = true;
		error = '';
		try {
			documents = await api.get<ComplianceDocument[]>('/api/admin/compliance/pending');
		} catch (e: any) {
			error = e.message || 'Failed to load documents';
		} finally {
			loading = false;
		}
	}

	onMount(loadDocuments);
</script>

{#snippet docActions(doc: ComplianceDocument)}
	<Button size="small" kind="ghost" icon={View}
		href="/dashboard/documents/{doc.id}/edit">
		Review
	</Button>
{/snippet}

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard/users" icon={ArrowLeft}>
					Back
				</Button>
				<h1 class="section-heading"><span class="icon-badge sm"><Document size={20} /></span> Pending Documents</h1>
				<p class="page-subtitle">
					{documents.length} document{documents.length !== 1 ? 's' : ''} awaiting review
				</p>
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
				<p>Loading documents...</p>
			{:else if documents.length === 0}
				<InlineNotification kind="info" title="No pending documents"
					subtitle="All compliance documents have been reviewed." hideCloseButton />
			{:else}
				<DocumentsTable {documents} actions={docActions} />
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
