<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { loadGoogleMaps } from '$lib/google-maps';

	let {
		labelText = 'Location',
		placeholder = 'Enter address or Eircode',
		value = $bindable(''),
		onPlaceSelected = (_place: { address: string; lat: number; lng: number }) => {}
	}: {
		labelText?: string;
		placeholder?: string;
		value?: string;
		onPlaceSelected?: (place: { address: string; lat: number; lng: number }) => void;
	} = $props();

	let pickerEl: HTMLDivElement;
	let mapEl: HTMLDivElement;
	let map: google.maps.Map | null = null;
	let marker: google.maps.marker.AdvancedMarkerElement | null = null;
	let mapsReady = $state(false);
	let mapOpen = $state(false);
	let mapInitialized = false;
	let loadError = $state('');
	// Tracks the last value we geocoded for the map so we don't re-fire on echoes.
	let lastGeocodedValue = '';
	const inputId = `location-picker-${Math.random().toString(36).slice(2, 9)}`;

	// Eircode: routing key (letter + 2 digits/chars) + optional space + 4 alphanumeric
	const EIRCODE_REGEX = /^[A-Za-z]\d[\dWw]\s?[A-Za-z0-9]{4}$/;

	function isEircode(val: string): boolean {
		return EIRCODE_REGEX.test(val.trim());
	}

	function extractEircode(results: google.maps.GeocoderResult[]): string | null {
		for (const result of results) {
			for (const component of result.address_components) {
				if (component.types.includes('postal_code') && isEircode(component.long_name)) {
					return component.long_name;
				}
			}
		}
		return null;
	}

	function setAutocompleteInputValue(text: string) {
		// The input is bound to `value`, so writing to `value` is enough — but we also
		// mark the trimmed text as already-geocoded so the $effect doesn't re-fire.
		lastGeocodedValue = text.trim();
	}

	async function geocodeEircode(eircode: string) {
		console.log('[LocationPicker] geocodeEircode →', eircode);
		const g = await loadGoogleMaps();
		const geocoder = new g.maps.Geocoder();
		try {
			const res = await geocoder.geocode({
				address: eircode.trim(),
				componentRestrictions: { country: 'IE' }
			});
			console.log('[LocationPicker] geocode results:', res.results.length);
			if (res.results[0]) {
				const loc = res.results[0].geometry.location;
				const lat = loc.lat();
				const lng = loc.lng();
				const address = res.results[0].formatted_address;
				const resolvedEircode = extractEircode(res.results);
				const display = resolvedEircode || address;

				value = display;
				setAutocompleteInputValue(display);
				mapOpen = true;
				await new Promise(r => setTimeout(r, 260));
				if (!mapInitialized) {
					await initMap({ lat, lng });
				}
				setMarker(lat, lng);
				onPlaceSelected({ address, lat, lng });
			}
		} catch (err) {
			console.error('Eircode geocoding failed:', err);
		}
	}

	async function initMap(initial?: { lat: number; lng: number }) {
		if (mapInitialized || !mapsReady) return;
		mapInitialized = true;

		try {
			const g = await loadGoogleMaps();

			const { AdvancedMarkerElement } = await g.maps.importLibrary('marker') as google.maps.MarkerLibrary;

			map = new g.maps.Map(mapEl, {
				center: initial ?? { lat: 53.35, lng: -6.26 },
				zoom: initial ? 14 : 7,
				mapId: 'FINDMEADRIVER_MAP',
				disableDefaultUI: true,
				zoomControl: true,
				gestureHandling: 'cooperative'
			});

			map.addListener('click', async (e: google.maps.MapMouseEvent) => {
				if (!e.latLng) return;
				const lat = e.latLng.lat();
				const lng = e.latLng.lng();

				setMarker(lat, lng);

				const geocoder = new g.maps.Geocoder();
				try {
					const res = await geocoder.geocode({ location: { lat, lng } });
					if (res.results[0]) {
						const address = res.results[0].formatted_address;
						const eircode = extractEircode(res.results);
						const display = eircode || address;
						value = display;
						setAutocompleteInputValue(display);
						onPlaceSelected({ address, lat, lng });
					}
				} catch { /* ignore */ }
			});
		} catch (err) {
			console.error('Failed to init map:', err);
			mapInitialized = false;
		}
	}

	async function setMarker(lat: number, lng: number) {
		if (!map) return;
		const pos = { lat, lng };
		// The map container transitions from 0 → 220 px when opening, which leaves the
		// map's internal viewport stale on the first paint. Nudge it to remeasure, then
		// set center/zoom atomically via setOptions so the redraw is reliable.
		google.maps.event.trigger(map, 'resize');
		map.setOptions({ center: pos, zoom: 14 });

		// Recreate the marker every time. Updating `.position` on AdvancedMarkerElement
		// can fail to repaint when the map is in a transitional state, so detaching and
		// re-adding is the most reliable way to guarantee a visible marker.
		const g = await loadGoogleMaps();
		const { AdvancedMarkerElement } = await g.maps.importLibrary('marker') as google.maps.MarkerLibrary;
		if (marker) marker.map = null;
		marker = new AdvancedMarkerElement({ map, position: pos });

		// Wait one frame, then nudge again — guards against the case where the container
		// height transition isn't quite finished when setOptions runs.
		requestAnimationFrame(() => {
			if (!map) return;
			google.maps.event.trigger(map, 'resize');
			map.setCenter(pos);
		});
	}

	async function openMap() {
		if (!mapsReady) return;
		mapOpen = true;

		await new Promise(r => setTimeout(r, 260));

		if (!mapInitialized) {
			await initMap();
		} else if (map) {
			google.maps.event.trigger(map, 'resize');
		}
	}

	function handleClickOutside(e: MouseEvent) {
		if (!pickerEl) return;
		if (pickerEl.contains(e.target as Node)) return;
		// PlaceAutocompleteElement dropdown renders in shadow DOM inside our container,
		// but Google's overlay may be outside — check for gmp elements
		const target = e.target as HTMLElement;
		if (target.closest('gmp-internal-place-autocomplete-overlay')) return;
		mapOpen = false;
	}

	// React to `value` changes (typed text, external prop updates, programmatic writes):
	// when it parses as an Eircode, geocode and drop a marker on the map.
	$effect(() => {
		const current = value;

		if (!mapsReady) return;
		const trimmed = current.trim();
		if (!trimmed) {
			if (marker) marker.position = null;
			lastGeocodedValue = '';
			return;
		}
		if (trimmed === lastGeocodedValue) return;
		if (isEircode(trimmed)) {
			lastGeocodedValue = trimmed;
			geocodeEircode(trimmed);
		}
	});

	onDestroy(() => {
		document.removeEventListener('mousedown', handleClickOutside);
	});

	async function geocodeAddress(query: string) {
		const trimmed = query.trim();
		if (!trimmed) return;
		console.log('[LocationPicker] geocodeAddress →', trimmed);
		const g = await loadGoogleMaps();
		const geocoder = new g.maps.Geocoder();
		try {
			const res = await geocoder.geocode({
				address: trimmed,
				componentRestrictions: { country: 'IE' }
			});
			console.log('[LocationPicker] geocode results:', res.results.length);
			if (res.results[0]) {
				const loc = res.results[0].geometry.location;
				const lat = loc.lat();
				const lng = loc.lng();
				const address = res.results[0].formatted_address;
				const eircode = extractEircode(res.results);
				const display = eircode || address;

				value = display;
				lastGeocodedValue = display.trim();
				mapOpen = true;
				await new Promise(r => setTimeout(r, 260));
				if (!mapInitialized) await initMap({ lat, lng });
				setMarker(lat, lng);
				onPlaceSelected({ address, lat, lng });
			}
		} catch (err) {
			console.error('[LocationPicker] geocode failed:', err);
		}
	}

	async function handleInputKeydown(e: KeyboardEvent) {
		if (e.key !== 'Enter') return;
		e.preventDefault();
		const trimmed = value.trim();
		if (!trimmed) return;
		if (isEircode(trimmed)) {
			await geocodeEircode(trimmed);
		} else {
			await geocodeAddress(trimmed);
		}
	}

	onMount(async () => {
		document.addEventListener('mousedown', handleClickOutside);
		try {
			await loadGoogleMaps();
			mapsReady = true;
		} catch (err) {
			console.error('Google Maps load failed:', err);
			loadError = 'Maps failed to load. You can still type an address manually.';
		}
	});
</script>

<div class="location-picker" bind:this={pickerEl}>
	<label class="picker-label" for={inputId}>{labelText}</label>
	<input
		id={inputId}
		class="bx--text-input"
		type="text"
		{placeholder}
		bind:value
		onfocus={openMap}
		onkeydown={handleInputKeydown}
	/>
	{#if loadError}
		<p class="load-error">{loadError}</p>
	{/if}
	<div class="map-container" class:open={mapOpen && mapsReady} bind:this={mapEl}></div>
</div>

<style>
	.location-picker {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
		position: relative;
	}
	.picker-label {
		font-size: 0.75rem;
		color: var(--cds-text-secondary, #525252);
		font-weight: 400;
		letter-spacing: 0.32px;
	}
	.map-container {
		width: 100%;
		height: 0;
		overflow: hidden;
		border: 1px solid var(--cds-border-subtle, #e0e0e0);
		border-top: none;
		transition: height 0.25s ease;
	}
	.map-container.open {
		height: 220px;
		overflow: visible;
	}
	.load-error {
		font-size: 0.75rem;
		color: #da1e28;
		margin: 0;
	}
	.bx--text-input {
		font-size: 0.875rem;
		height: 2.5rem;
		padding: 0 1rem;
		border: none;
		border-bottom: 1px solid var(--cds-border-strong, #8d8d8d);
		background-color: var(--cds-field, #f4f4f4);
		color: var(--cds-text-primary, #161616);
		width: 100%;
		outline: none;
	}
	.bx--text-input:focus {
		outline: 2px solid var(--cds-focus, #0f62fe);
		outline-offset: -2px;
	}
</style>
