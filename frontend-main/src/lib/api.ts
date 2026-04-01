const BASE_URL = '';

interface ApiOptions {
	method?: string;
	body?: unknown;
	headers?: Record<string, string>;
}

export async function apiFetch<T = unknown>(path: string, options: ApiOptions = {}): Promise<T> {
	const token = localStorage.getItem('token');

	const headers: Record<string, string> = {
		'Content-Type': 'application/json',
		...options.headers
	};

	if (token) {
		headers['Authorization'] = `Bearer ${token}`;
	}

	const response = await fetch(`${BASE_URL}${path}`, {
		method: options.method || 'GET',
		headers,
		body: options.body ? JSON.stringify(options.body) : undefined
	});

	const data = await response.json();

	if (!response.ok) {
		throw { status: response.status, ...data };
	}

	return data as T;
}

export const api = {
	get: <T = unknown>(path: string) => apiFetch<T>(path),
	post: <T = unknown>(path: string, body: unknown) => apiFetch<T>(path, { method: 'POST', body }),
	put: <T = unknown>(path: string, body: unknown) => apiFetch<T>(path, { method: 'PUT', body }),
	delete: <T = unknown>(path: string) => apiFetch<T>(path, { method: 'DELETE' })
};
