import { redirect } from '@sveltejs/kit';
import type { PageLoad } from './$types';

// Merged into the single post-a-load page — Multimodal is now a mode option
// there instead of a separate route. Kept as a redirect so old links/bookmarks
// still work.
export const load: PageLoad = ({ url }) => {
	throw redirect(308, `/dashboard/loads/post?mode=INTERMODAL${url.search ? '&' + url.search.slice(1) : ''}`);
};
