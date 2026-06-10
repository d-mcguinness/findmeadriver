<script lang="ts">
	import 'carbon-components-svelte/css/g10.css';
	import '../app.css';
	import {
		Header,
		HeaderUtilities,
		HeaderAction,
		HeaderNavItem,
		SideNav,
		SideNavItems,
		SideNavLink,
		Content,
		SkipToContent,
		Button
	} from 'carbon-components-svelte';
	import { LogoGithub, Login, Logout, UserAvatar, Dashboard, Time, Search, Document, Add, Enterprise, CertificateCheck, UserFollow } from 'carbon-icons-svelte';
	import { goto } from '$app/navigation';
	import { auth } from '$lib/stores/auth.svelte';

	let { children } = $props();
	let isSideNavOpen = $state(false);

	// Auth is rehydrated at module load in auth.svelte.ts (before any component
	// mounts), so no onMount initialize is needed here.

	function handleLogout() {
		auth.logout();
		goto('/');
	}

	function returnToAdmin() {
		auth.stopImpersonating();
		goto('/dashboard/users');
	}
</script>

<Header platformName="FindMeADriver" href="/" bind:isSideNavOpen>
	<svelte:fragment slot="skip-to-content">
		<SkipToContent />
	</svelte:fragment>
	<HeaderUtilities>
		{#if auth.isAuthenticated}
			<HeaderNavItem href="/dashboard" text={auth.user?.firstName ?? 'Account'} />
			<HeaderNavItem on:click={handleLogout} text="Logout" />
		{:else}
			<HeaderNavItem href="/login" text="Login" />
			<HeaderNavItem href="/register" text="Register" />
		{/if}
	</HeaderUtilities>
</Header>

{#if auth.isAuthenticated}
	<SideNav bind:isOpen={isSideNavOpen}>
		<SideNavItems>
			<SideNavLink icon={LogoGithub} text="Home" href="/" />
			<SideNavLink icon={Dashboard} text="Dashboard" href="/dashboard" />
			{#if auth.isCarrier}
				<SideNavLink icon={Time} text="Availability" href="/dashboard?tab=availability" />
				<SideNavLink icon={CertificateCheck} text="Compliance" href="/dashboard?tab=compliance" />
				<SideNavLink icon={Search} text="Browse Loads" href="/dashboard?tab=loads" />
				<SideNavLink icon={Document} text="My Applications" href="/dashboard?tab=applications" />
			{/if}
			{#if auth.isShipper}
				<SideNavLink icon={Add} text="Create a Load" href="/dashboard/loads/post" />
				<SideNavLink icon={Enterprise} text="My Loads" href="/dashboard" />
			{/if}
			<SideNavLink icon={Logout} text="Logout" on:click={handleLogout} />
		</SideNavItems>
	</SideNav>
{/if}

<Content>
	{#if auth.isImpersonating}
		<div class="impersonation-banner">
			<UserFollow size={16} />
			<span>You are mimicking <strong>{auth.impersonatedLabel}</strong>.</span>
			<Button size="small" kind="danger-tertiary" on:click={returnToAdmin}>Return to admin</Button>
		</div>
	{/if}
	{@render children()}
</Content>

<style>
	.impersonation-banner {
		position: sticky;
		top: 0;
		z-index: 100;
		display: flex;
		align-items: center;
		gap: 0.75rem;
		padding: 0.6rem 1rem;
		margin-bottom: 1rem;
		border-radius: 8px;
		background: var(--cds-support-warning, #f1c21b);
		color: #161616;
		font-size: 0.875rem;
	}
</style>
