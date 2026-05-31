import { browser } from '$app/environment';
import { api } from '$lib/api';

interface User {
	email: string;
	firstName: string;
	lastName: string;
	phone: string;
	roles: string[];
}

interface LoginResponse {
	message: string;
	user: unknown;
	token: string;
}

function parseJwt(token: string): Record<string, unknown> {
	const base64Url = token.split('.')[1];
	const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
	const jsonPayload = decodeURIComponent(
		atob(base64)
			.split('')
			.map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
			.join('')
	);
	return JSON.parse(jsonPayload);
}

function isTokenExpired(token: string): boolean {
	try {
		const payload = parseJwt(token);
		const exp = payload.exp as number;
		return Date.now() >= exp * 1000;
	} catch {
		return true;
	}
}

function createAuth() {
	let token = $state<string | null>(null);
	let user = $state<User | null>(null);

	function hydrateFromToken(jwt: string) {
		const claims = parseJwt(jwt);
		token = jwt;
		user = {
			email: claims.email as string,
			firstName: claims.firstName as string,
			lastName: claims.lastName as string,
			phone: claims.phone as string,
			roles: claims.roles as string[]
		};
	}

	function initialize() {
		const stored = localStorage.getItem('token');
		if (stored && !isTokenExpired(stored)) {
			hydrateFromToken(stored);
		} else {
			localStorage.removeItem('token');
		}
	}

	async function login(email: string, password: string) {
		const response = await api.post<LoginResponse>('/api/user/login', { email, password });
		localStorage.setItem('token', response.token);
		hydrateFromToken(response.token);
	}

	function logout() {
		token = null;
		user = null;
		localStorage.removeItem('token');
	}

	return {
		get token() { return token; },
		get user() { return user; },
		get isAuthenticated() { return token !== null; },
		get isDriver() { return user?.roles?.includes('ROLE_DRIVER') ?? false; },
		get isShipper() { return user?.roles?.includes('ROLE_SHIPPER') ?? false; },
		get isAdmin() { return user?.roles?.includes('ROLE_ADMIN') ?? false; },
		initialize,
		login,
		logout
	};
}

export const auth = createAuth();

// Rehydrate from localStorage at module load — before any component mounts.
// This must not wait for onMount: child layouts mount before parents, so a
// guard in dashboard/+layout.svelte would otherwise run before a root-layout
// onMount could restore the token, bouncing cold loads of authed routes to
// /login. Guarded by `browser` so it's a no-op under SSR/prerender.
if (browser) {
	auth.initialize();
}
