<script lang="ts">
	import 'carbon-components-svelte/css/g10.css';
	import '../app.css';
	import {
		Header,
		HeaderUtilities,
		HeaderAction,
		HeaderNav,
		HeaderNavItem,
		SideNav,
		SideNavItems,
		SideNavLink,
		Content,
		SkipToContent
	} from 'carbon-components-svelte';
	import { LogoGithub, Login, Logout, UserAvatar, Dashboard } from 'carbon-icons-svelte';
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { auth } from '$lib/stores/auth.svelte';

	let { children } = $props();
	let isSideNavOpen = $state(false);

	onMount(() => {
		auth.initialize();
	});

	function handleLogout() {
		auth.logout();
		goto('/');
	}
</script>

<Header platformName="Driver Direct" bind:isSideNavOpen>
	<svelte:fragment slot="skip-to-content">
		<SkipToContent />
	</svelte:fragment>
	<HeaderNav>
		<HeaderNavItem href="/" text="Home" />
		{#if auth.isAuthenticated}
			<HeaderNavItem href="/dashboard" text="Dashboard" />
		{:else}
			<HeaderNavItem href="/login" text="Login" />
			<HeaderNavItem href="/register" text="Register" />
		{/if}
	</HeaderNav>
	<HeaderUtilities>
		{#if auth.isAuthenticated}
			<HeaderNavItem href="/dashboard" text={auth.user?.firstName ?? 'Account'} />
			<HeaderNavItem on:click={handleLogout} text="Logout" />
		{/if}
	</HeaderUtilities>
</Header>

<SideNav bind:isOpen={isSideNavOpen}>
	<SideNavItems>
		<SideNavLink icon={LogoGithub} text="Home" href="/" />
		{#if auth.isAuthenticated}
			<SideNavLink icon={Dashboard} text="Dashboard" href="/dashboard" />
			<SideNavLink icon={Logout} text="Logout" on:click={handleLogout} />
		{:else}
			<SideNavLink icon={Login} text="Login" href="/login" />
			<SideNavLink icon={UserAvatar} text="Register" href="/register" />
		{/if}
	</SideNavItems>
</SideNav>

<Content>
	{@render children()}
</Content>
