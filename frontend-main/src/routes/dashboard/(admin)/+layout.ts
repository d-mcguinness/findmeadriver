import { redirect } from '@sveltejs/kit';
import { auth } from '$lib/stores/auth.svelte';
import type { LayoutLoad } from './$types';

export const load: LayoutLoad = () => {
	if (!auth.isAdmin) {
		redirect(307, auth.homePath);
	}
};
