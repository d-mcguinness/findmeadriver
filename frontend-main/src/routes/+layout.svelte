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
		SkipToContent
	} from 'carbon-components-svelte';
	import { LogoGithub, Login, Logout, UserAvatar, Dashboard, Time, Search, Document, Add, Enterprise, CertificateCheck } from 'carbon-icons-svelte';
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
			{#if auth.isDriver}
				<SideNavLink icon={Time} text="Availability" href="/dashboard?tab=availability" />
				<SideNavLink icon={CertificateCheck} text="Compliance" href="/dashboard?tab=compliance" />
				<SideNavLink icon={Search} text="Browse Jobs" href="/dashboard?tab=jobs" />
				<SideNavLink icon={Document} text="My Applications" href="/dashboard?tab=applications" />
			{/if}
			{#if auth.isShipper}
				<SideNavLink icon={Add} text="Create a Job" href="/dashboard/jobs/post" />
				<SideNavLink icon={Enterprise} text="My Jobs" href="/dashboard" />
			{/if}
			<SideNavLink icon={Logout} text="Logout" on:click={handleLogout} />
		</SideNavItems>
	</SideNav>
{/if}

<Content>
	{@render children()}
</Content>
