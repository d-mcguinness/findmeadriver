<script lang="ts">
	import {
		Grid, Row, Column, Button, Checkbox, Tag, TextInput, Select, SelectItem, InlineNotification
	} from 'carbon-components-svelte';
	import { ArrowLeft, Add } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import { onMount } from 'svelte';
	import { TRANSPORT_MODE_OPTIONS, transportModeLabel, modeTagColor } from '$lib/transport-modes';

	interface Capabilities {
		supportedModes: string[];
		credentials: string[];
	}

	// One boolean per mode (source of truth for the checkboxes).
	let modeSel = $state<Record<string, boolean>>({});
	let credentials = $state<string[]>([]);

	let newCredMode = $state('AIR');
	let newCredName = $state('');

	let loading = $state(true);
	let saving = $state(false);
	let error = $state('');
	let success = $state('');

	onMount(async () => {
		try {
			const caps = await api.get<Capabilities>('/api/carrier/capabilities');
			const modes = caps.supportedModes ?? [];
			for (const opt of TRANSPORT_MODE_OPTIONS) modeSel[opt.value] = modes.includes(opt.value);
			credentials = caps.credentials ?? [];
		} catch (e: any) {
			error = e.message || 'Failed to load capabilities';
		} finally {
			loading = false;
		}
	});

	function addCredential() {
		const name = newCredName.trim().toUpperCase();
		if (!name) return;
		const tag = `${newCredMode}:${name}`;
		if (!credentials.includes(tag)) credentials = [...credentials, tag];
		newCredName = '';
	}

	function removeCredential(c: string) {
		credentials = credentials.filter((x) => x !== c);
	}

	async function save() {
		error = '';
		success = '';
		saving = true;
		try {
			const supportedModes = TRANSPORT_MODE_OPTIONS.filter((o) => modeSel[o.value]).map((o) => o.value);
			const caps = await api.put<Capabilities>('/api/carrier/capabilities', {
				supportedModes,
				credentials
			});
			for (const opt of TRANSPORT_MODE_OPTIONS) modeSel[opt.value] = caps.supportedModes.includes(opt.value);
			credentials = caps.credentials ?? [];
			success = 'Capabilities saved.';
		} catch (e: any) {
			error = e.message || 'Failed to save';
		} finally {
			saving = false;
		}
	}
</script>

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard" icon={ArrowLeft}>Back to Dashboard</Button>
				<h1 class="section-heading">Modes &amp; Credentials</h1>
				<p class="sub">
					Choose which transport modes you operate and the credentials you hold. You can only be
					matched to a load whose mode you support &mdash; and air, sea and rail loads also require a
					credential for that mode on file.
				</p>
			</div>
		</Column>
	</Row>

	<Row>
		<Column lg={10} md={8} sm={4}>
			{#if error}
				<InlineNotification kind="error" title="Error" subtitle={error} on:close={() => (error = '')} />
			{/if}
			{#if success}
				<InlineNotification kind="success" title="Saved" subtitle={success} on:close={() => (success = '')} />
			{/if}

			{#if loading}
				<p>Loading...</p>
			{:else}
				<section class="block">
					<h3>Transport modes</h3>
					<p class="hint">Modes you're set up to operate.</p>
					<div class="modes">
						{#each TRANSPORT_MODE_OPTIONS as opt}
							<Checkbox labelText={opt.label} bind:checked={modeSel[opt.value]} />
						{/each}
					</div>
				</section>

				<section class="block">
					<h3>Credentials</h3>
					<p class="hint">e.g. air-crew (ATPL), maritime (STCW), rail (RUL). Road uses your licence category.</p>
					{#if credentials.length === 0}
						<p class="empty">No credentials on file yet.</p>
					{:else}
						<div class="creds">
							{#each credentials as c}
								<Tag type={modeTagColor(c.split(':')[0])} filter on:close={() => removeCredential(c)}>{c}</Tag>
							{/each}
						</div>
					{/if}
					<div class="add-cred">
						<Select bind:selected={newCredMode} labelText="Mode" hideLabel>
							{#each TRANSPORT_MODE_OPTIONS as opt}
								<SelectItem value={opt.value} text={opt.label} />
							{/each}
						</Select>
						<TextInput bind:value={newCredName} labelText="Credential" hideLabel
							placeholder="e.g. ATPL" />
						<Button kind="tertiary" size="small" icon={Add} on:click={addCredential}>Add</Button>
					</div>
				</section>

				<Button on:click={save} disabled={saving}>{saving ? 'Saving...' : 'Save capabilities'}</Button>
			{/if}
		</Column>
	</Row>
</Grid>

<style>
	.page-header { margin-bottom: 1.5rem; }
	.page-header h1 { margin-top: 0.5rem; }
	.sub { color: var(--cds-text-secondary); max-width: 680px; line-height: 1.5; }
	.block {
		padding: 1rem;
		margin-bottom: 1rem;
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-interactive, #0f62fe);
	}
	.block h3 { margin: 0 0 0.25rem; font-size: 1rem; }
	.hint { font-size: 0.8125rem; color: var(--cds-text-secondary); margin: 0 0 0.75rem; }
	.empty { font-size: 0.8125rem; color: var(--cds-text-secondary); font-style: italic; margin: 0 0 0.75rem; }
	.modes { display: flex; flex-wrap: wrap; gap: 1.25rem; }
	.creds { display: flex; flex-wrap: wrap; gap: 0.35rem; margin-bottom: 0.75rem; }
	.add-cred {
		display: grid;
		grid-template-columns: 9rem 1fr auto;
		gap: 0.5rem;
		align-items: end;
		max-width: 520px;
	}
	@media (max-width: 672px) {
		.add-cred { grid-template-columns: 1fr; }
	}
</style>
