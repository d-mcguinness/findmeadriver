import { redirect } from '@sveltejs/kit';
import { auth } from '$lib/stores/auth.svelte';
import type { LayoutLoad } from './$types';

export const load: LayoutLoad = () => {
	if (!auth.isCarrier) {
		redirect(307, auth.homePath);
	}
};
