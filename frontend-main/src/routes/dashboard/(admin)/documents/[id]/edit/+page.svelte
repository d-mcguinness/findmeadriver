<script lang="ts">
	import {
		Grid, Row, Column, Button, InlineNotification, TextArea, Tag, Tile
	} from 'carbon-components-svelte';
	import { ArrowLeft, CheckmarkOutline, Document } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { onMount } from 'svelte';
	import type { ComplianceDocument } from '$lib/types';

	const docId = page.params.id;

	let doc = $state<ComplianceDocument | null>(null);
	let loading = $state(true);
	let loadError = $state('');
	let notes = $state('');
	let saving = $state(false);
	let saveError = $state('');
	let saved = $state(false);

	function statusKind(status: string): 'blue' | 'green' | 'red' | 'gray' {
		switch (status) {
			case 'VERIFIED': return 'green';
			case 'PENDING': return 'blue';
			case 'EXPIRED': return 'red';
			default: return 'gray';
		}
	}

	function typeLabel(type: string): string {
		switch (type) {
			case 'DRIVING_LICENCE': return 'Driving Licence';
			case 'INSURANCE': return 'Insurance';
			case 'CPC_CARD': return 'CPC Card';
			case 'TACHOGRAPH_CARD': return 'Tachograph Card';
			default: return 'Other';
		}
	}

	// No GET-by-id endpoint exists; the review flow is for pending docs, so find
	// this one in the pending list. Approving removes it from that list.
	onMount(async () => {
		loading = true;
		loadError = '';
		try {
			const pending = await api.get<ComplianceDocument[]>('/api/admin/compliance/pending');
			doc = pending.find((d) => String(d.id) === docId) ?? null;
			if (doc) notes = doc.notes ?? '';
		} catch (e: any) {
			loadError = e.message || 'Failed to load the document.';
		} finally {
			loading = false;
		}
	});

	async function approve() {
		if (!doc) return;
		saveError = '';
		saving = true;
		try {
			await api.put(`/api/admin/compliance/${doc.id}/verify`, { notes });
			saved = true;
			setTimeout(() => goto('/dashboard/documents'), 1000);
		} catch (e: any) {
			saveError = e?.error || e?.message || 'Failed to approve the document';
		} finally {
			saving = false;
		}
	}
</script>

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard/documents" icon={ArrowLeft}>
					Back to documents
				</Button>
				<h1 class="section-heading">
					<span class="icon-badge sm"><Document size={20} /></span> Review Document
				</h1>
			</div>
		</Column>
	</Row>

	<Row>
		<Column lg={8} md={6} sm={4}>
			{#if loading}
				<p>Loading document…</p>
			{:else if loadError}
				<InlineNotification kind="error" title="Error" subtitle={loadError} hideCloseButton />
				<Button kind="tertiary" href="/dashboard/documents">Back to documents</Button>
			{:else if !doc}
				<InlineNotification kind="info" title="Not pending review"
					subtitle="This document isn't in the pending queue — it may already have been reviewed."
					hideCloseButton />
				<Button kind="tertiary" href="/dashboard/documents">Back to documents</Button>
			{:else}
				{#if saveError}
					<InlineNotification kind="error" title="Error" subtitle={saveError}
						on:close={() => saveError = ''} />
				{/if}
				{#if saved}
					<InlineNotification kind="success" title="Approved"
						subtitle="Document verified — returning to the list…" hideCloseButton />
				{/if}

				<Tile>
					<div class="doc-grid">
						<div><span class="lbl">Type</span><strong>{typeLabel(doc.documentType)}</strong></div>
						<div><span class="lbl">Number</span><strong>{doc.documentNumber || '—'}</strong></div>
						<div><span class="lbl">Expires</span><strong>{doc.expiryDate || '—'}</strong></div>
						<div><span class="lbl">Uploaded</span><strong>{doc.uploadedAt ? doc.uploadedAt.slice(0, 10) : '—'}</strong></div>
						<div><span class="lbl">Status</span><Tag type={statusKind(doc.status)} size="sm">{doc.status}</Tag></div>
					</div>
				</Tile>

				<div class="approve-form">
					<TextArea bind:value={notes} labelText="Review notes (optional)"
						placeholder="Any notes to record with this verification…" rows={3} />
					<Button icon={CheckmarkOutline} on:click={approve} disabled={saving || saved}>
						{saving ? 'Approving…' : 'Approve document'}
					</Button>
				</div>
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
	.doc-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(8rem, 1fr));
		gap: 1.25rem;
	}
	.doc-grid .lbl {
		display: block;
		font-size: 0.75rem;
		text-transform: uppercase;
		letter-spacing: 0.02em;
		color: var(--cds-text-secondary);
		margin-bottom: 0.25rem;
	}
	.approve-form {
		display: flex;
		flex-direction: column;
		gap: 1rem;
		margin-top: 1.5rem;
		max-width: 520px;
	}
</style>
