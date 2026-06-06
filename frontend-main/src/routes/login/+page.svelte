<script lang="ts">
	import {
		Grid,
		Row,
		Column,
		Tile,
		TextInput,
		PasswordInput,
		Button,
		InlineNotification
	} from 'carbon-components-svelte';
	import { Login } from 'carbon-icons-svelte';
	import { dev } from '$app/environment';
	import { goto } from '$app/navigation';
	import { auth } from '$lib/stores/auth.svelte';

	const testUsers = [
		{ label: 'Admin', email: 'admin@driverdirect.com', password: 'admin123' },
		{ label: 'Shipper', email: 'employer@company.com', password: 'employer123' },
		{ label: 'Carrier', email: 'driver@example.com', password: 'driver123' }
	];

	let email = $state('');
	let password = $state('');
	let error = $state('');
	let loading = $state(false);

	async function handleLogin() {
		error = '';
		loading = true;
		try {
			await auth.login(email, password);
			goto('/dashboard');
		} catch (e: any) {
			error = e?.error || 'Login failed. Please check your credentials.';
		} finally {
			loading = false;
		}
	}
</script>

<Grid>
	<Row>
		<Column lg={{ span: 6, offset: 5 }} md={{ span: 6, offset: 1 }} sm={4}>
			<div class="form-container">
				<Tile>
					<span class="eyebrow">FindMeADriver</span>
					<h2>Sign In</h2>
					<p class="form-subtitle">Welcome back to FindMeADriver</p>

					{#if error}
						<InlineNotification
							kind="error"
							title="Error"
							subtitle={error}
							on:close={() => (error = '')}
						/>
					{/if}

					<form onsubmit={(e: Event) => { e.preventDefault(); handleLogin(); }}>
						<div class="form-field">
							<TextInput
								labelText="Email"
								placeholder="you@example.com"
								type="email"
								bind:value={email}
								required
							/>
						</div>
						<div class="form-field">
							<PasswordInput
								labelText="Password"
								placeholder="Enter your password"
								bind:value={password}
								required
							/>
						</div>
						<div class="form-actions">
							<Button type="submit" icon={Login} disabled={loading}>
								{loading ? 'Signing in...' : 'Sign In'}
							</Button>
						</div>
					</form>

					{#if dev}
						<div class="dev-autofill">
							<p><strong>Dev quick login:</strong></p>
							<div class="dev-buttons">
								{#each testUsers as user}
									<Button kind="ghost" size="small" on:click={() => { email = user.email; password = user.password; }}>
										{user.label}
									</Button>
								{/each}
							</div>
						</div>
					{/if}

					<p class="form-footer">
						Don't have an account? <a href="/register">Register here</a>
					</p>
				</Tile>
			</div>
		</Column>
	</Row>
</Grid>

<style>
	.dev-autofill {
		margin-top: 1.5rem;
		padding: 1rem;
		border: 1px dashed var(--cds-border-subtle, #e0e0e0);
		background: var(--cds-layer-accent, #f4f4f4);
	}

	.dev-autofill p {
		margin-bottom: 0.5rem;
		font-size: 0.875rem;
	}

	.dev-buttons {
		display: flex;
		gap: 0.5rem;
	}
</style>
