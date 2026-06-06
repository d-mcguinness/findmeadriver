<script lang="ts">
	import {
		Grid, Row, Column, Tile, Button, Tag, TextInput, Modal,
		InlineNotification
	} from 'carbon-components-svelte';
	import { ArrowLeft, Settings, Checkmark, CertificateCheck } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import type { ComplianceDocument } from '$lib/types';
	import { onMount } from 'svelte';

	let pendingDocs = $state<ComplianceDocument[]>([]);
	let loading = $state(true);
	let error = $state('');

	// Verify modal
	let verifyModalOpen = $state(false);
	let verifyDocId = $state<number | null>(null);
	let verifyDocType = $state('');
	let verifyNotes = $state('');
	let verifyLoading = $state(false);
	let verifyError = $state('');
	let successMsg = $state('');

	async function loadPending() {
		loading = true;
		error = '';
		try {
			pendingDocs = await api.get<ComplianceDocument[]>('/api/admin/compliance/pending');
		} catch (e: any) {
			error = e.message || 'Failed to load pending documents';
		} finally {
			loading = false;
		}
	}

	function openVerify(doc: ComplianceDocument) {
		verifyDocId = doc.id;
		verifyDocType = doc.documentType;
		verifyNotes = '';
		verifyError = '';
		verifyModalOpen = true;
	}

	async function submitVerify() {
		if (!verifyDocId) return;
		verifyLoading = true;
		verifyError = '';
		try {
			await api.put(`/api/admin/compliance/${verifyDocId}/verify`, {
				notes: verifyNotes || null
			});
			verifyModalOpen = false;
			successMsg = `Document verified successfully.`;
			loadPending();
		} catch (e: any) {
			verifyError = e.message || 'Failed to verify document';
		} finally {
			verifyLoading = false;
		}
	}

	function formatDocType(type: string): string {
		return type.replace(/_/g, ' ');
	}

	onMount(loadPending);
</script>

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard" icon={ArrowLeft}>
					Back
				</Button>
				<h1><span class="icon-badge sm"><Settings size={24} /></span> Settings</h1>
				<p class="page-subtitle">Platform configuration and compliance review</p>
			</div>
		</Column>
	</Row>

	{#if error}
		<Row>
			<Column>
				<InlineNotification kind="error" title="Error" subtitle={error}
					on:close={() => error = ''} />
			</Column>
		</Row>
	{/if}
	{#if successMsg}
		<Row>
			<Column>
				<InlineNotification kind="success" title="Success" subtitle={successMsg}
					on:close={() => successMsg = ''} />
			</Column>
		</Row>
	{/if}

	<Row>
		<Column>
			<h2 class="section-heading">
				<CertificateCheck size={20} /> Pending Compliance Documents
			</h2>

			{#if loading}
				<p>Loading pending documents...</p>
			{:else if pendingDocs.length === 0}
				<InlineNotification kind="info" title="All clear"
					subtitle="No pending compliance documents to review."
					hideCloseButton />
			{:else}
				<p class="pending-count">{pendingDocs.length} document{pendingDocs.length !== 1 ? 's' : ''} awaiting review</p>
				<div class="doc-list">
					{#each pendingDocs as doc}
						<div class="fmad-card">
							<Tile class="doc-tile">
								<div class="doc-header">
									<div>
										<h4>{formatDocType(doc.documentType)}</h4>
										<p class="doc-number">#{doc.documentNumber}</p>
									</div>
									<Tag type="blue" size="sm">{doc.status}</Tag>
								</div>
								<div class="doc-details">
									<span><strong>Expiry:</strong> {doc.expiryDate}</span>
									<span><strong>Uploaded:</strong> {new Date(doc.uploadedAt).toLocaleDateString()}</span>
								</div>
								<div class="doc-actions">
									<Button size="small" kind="primary" icon={Checkmark}
										on:click={() => openVerify(doc)}>
										Verify
									</Button>
								</div>
							</Tile>
						</div>
					{/each}
				</div>
			{/if}
		</Column>
	</Row>
</Grid>

<Modal
	bind:open={verifyModalOpen}
	modalHeading="Verify {formatDocType(verifyDocType)}"
	primaryButtonText={verifyLoading ? 'Verifying...' : 'Verify Document'}
	secondaryButtonText="Cancel"
	primaryButtonDisabled={verifyLoading}
	on:click:button--primary={submitVerify}
	on:click:button--secondary={() => verifyModalOpen = false}
>
	{#if verifyError}
		<InlineNotification kind="error" title="Error" subtitle={verifyError} />
	{/if}
	<p>Confirm this compliance document has been reviewed and is valid.</p>
	<br />
	<TextInput
		bind:value={verifyNotes}
		labelText="Notes (optional)"
		placeholder="Any notes about the verification..."
	/>
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
	.section-heading {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		margin-bottom: 1rem;
		font-size: 1.25rem;
	}
	.pending-count {
		color: var(--cds-text-secondary);
		margin-bottom: 1rem;
	}
	.doc-list {
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}
	.doc-header {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		margin-bottom: 0.5rem;
	}
	.doc-header h4 {
		text-transform: capitalize;
	}
	.doc-number {
		color: var(--cds-text-secondary);
		font-size: 0.875rem;
	}
	.doc-details {
		display: flex;
		gap: 1.5rem;
		font-size: 0.875rem;
		margin-bottom: 0.75rem;
	}
	.doc-actions {
		display: flex;
		gap: 0.5rem;
	}
</style>
