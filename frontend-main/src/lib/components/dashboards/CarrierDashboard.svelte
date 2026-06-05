<script lang="ts">
	import {
		Grid, Row, Column, Tile, Tabs, Tab, TabContent,
		Button, NumberInput, InlineNotification, Tag,
		Modal, TextArea, TextInput, Select, SelectItem
	} from 'carbon-components-svelte';
	import { Time, Search, Document, Checkmark, Close, Undo, StarFilled, Star, CertificateCheck } from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
	import { api } from '$lib/api';
	import { carrierState } from '$lib/stores/carrierState.svelte';
	import type { AvailabilityResponse, Load, LoadApplication, CarrierComplianceSummary, TimeSlot, CarrierLane, CabotageExposure } from '$lib/types';
	import { transportModeLabel, modeTagColor } from '$lib/transport-modes';
	import { HAULAGE_COUNTRIES, countryName } from '$lib/countries';
	import { onMount } from 'svelte';
	import { page } from '$app/state';

	const tabMap: Record<string, number> = {
		availability: 0,
		compliance: 1,
		loads: 2,
		applications: 3
	};

	let selectedTab = $state(0);

	$effect(() => {
		const tab = page.url.searchParams.get('tab');
		if (tab && tab in tabMap) {
			selectedTab = tabMap[tab];
		}
	});

	// Availability state
	let availability = $state<AvailabilityResponse | null>(null);
	let weekDays = $state<{ date: string; dayName: string; hours: number }[]>([]);
	let availMode = $state('ROAD');     // which mode's calendar the editor declares
	const barPct = (v: number, max: number) => (max > 0 ? Math.min(100, Math.round((v / max) * 100)) : 0);

	// Calendar view state
	let calendarMonth = $state(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
	let monthSlots = $state<TimeSlot[]>([]);
	let dayModalOpen = $state(false);
	let dayModalDate = $state<string>('');
	let dayModalError = $state('');
	let dayModalSaving = $state(false);
	let newSlotStart = $state('09:00');
	let newSlotEnd = $state('17:00');

	let slotsByDate = $derived.by(() => {
		const m = new Map<string, TimeSlot[]>();
		for (const s of monthSlots) {
			if (!m.has(s.date)) m.set(s.date, []);
			m.get(s.date)!.push(s);
		}
		return m;
	});

	let dayModalSlots = $derived(slotsByDate.get(dayModalDate) ?? []);
	let availError = $state('');
	let availSuccess = $state('');
	let availLoading = $state(false);

	// Compliance state
	let compliance = $state<CarrierComplianceSummary | null>(null);
	let complianceLoading = $state(false);
	let docForm = $state({ documentType: 'DRIVING_LICENCE', documentNumber: '', expiryDate: '' });
	let docError = $state('');
	let docSuccess = $state('');

	// Loads state
	let loads = $state<Load[]>([]);
	let loadsLoading = $state(false);
	let applyModalOpen = $state(false);
	let selectedLoad = $state<Load | null>(null);
	let coverNote = $state('');
	let applyError = $state('');

	// Lanes state
	let lanes = $state<CarrierLane[]>([]);
	let laneForm = $state({ originCountry: 'IE', destinationCountry: 'GB' });
	let laneError = $state('');

	// Cabotage state
	let cabotage = $state<CabotageExposure[]>([]);
	let homeCountry = $state('');
	let homeCountryDraft = $state('IE');
	let homeCountrySaving = $state(false);
	let homeCountryError = $state('');

	// Applications state
	let applicationsLoading = $state(false);
	// Backed by the shared store so apply/withdraw is reflected in StatsRow too.
	let applications = $derived(carrierState.applications);
	let applicationByLoadId = $derived(new Map(applications.map(a => [a.loadId, a])));

	// Rating state
	let ratingModalOpen = $state(false);
	let ratingLoadId = $state<number | null>(null);
	let ratingLoadTitle = $state('');
	let ratingScore = $state(0);
	let ratingComment = $state('');
	let ratingError = $state('');

	function getMonday(d: Date): Date {
		const date = new Date(d);
		const day = date.getDay();
		const diff = date.getDate() - day + (day === 0 ? -6 : 1);
		date.setDate(diff);
		return date;
	}

	function formatDate(d: Date): string {
		return d.toISOString().split('T')[0];
	}

	function initWeekDays() {
		const monday = getMonday(new Date());
		const dayNames = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
		weekDays = dayNames.map((name, i) => {
			const date = new Date(monday);
			date.setDate(monday.getDate() + i);
			const existing = availability?.days.find(d => d.date === formatDate(date) && d.mode === availMode);
			return { date: formatDate(date), dayName: name, hours: existing?.availableHours ?? 0 };
		});
	}

	async function loadAvailability() {
		try {
			const monday = getMonday(new Date());
			const sunday = new Date(monday);
			sunday.setDate(monday.getDate() + 6);
			availability = await api.get<AvailabilityResponse>(
				`/api/carrier/availability?start=${formatDate(monday)}&end=${formatDate(sunday)}`
			);
			const clocks = availability?.dutyClocks ?? [];
			if (clocks.length && !clocks.some(c => c.mode === availMode)) availMode = clocks[0].mode;
			initWeekDays();
		} catch {
			initWeekDays();
		}
	}

	async function saveAvailability() {
		availError = '';
		availSuccess = '';
		availLoading = true;
		try {
			const entries = weekDays
				.filter(d => d.hours > 0)
				.map(d => ({ date: d.date, mode: availMode, availableHours: d.hours }));
			availability = await api.put<AvailabilityResponse>('/api/carrier/availability', { entries });
			availSuccess = 'Availability saved successfully';
			initWeekDays();
		} catch (e: any) {
			availError = e.message || 'Failed to save availability';
		} finally {
			availLoading = false;
		}
	}

	// ---- Calendar helpers ----

	type CalendarCell = { date: string; day: number; inMonth: boolean; slots: TimeSlot[] };

	let calendarGrid = $derived.by<CalendarCell[]>(() => {
		const year = calendarMonth.getFullYear();
		const month = calendarMonth.getMonth();
		const first = new Date(year, month, 1);
		const start = new Date(first);
		// Start grid on Monday: getDay() is 0=Sun..6=Sat
		const offset = (first.getDay() + 6) % 7;
		start.setDate(first.getDate() - offset);
		const cells: CalendarCell[] = [];
		for (let i = 0; i < 42; i++) {
			const d = new Date(start);
			d.setDate(start.getDate() + i);
			const iso = formatDate(d);
			cells.push({
				date: iso,
				day: d.getDate(),
				inMonth: d.getMonth() === month,
				slots: slotsByDate.get(iso) ?? []
			});
		}
		return cells;
	});

	function formatSlotShort(s: TimeSlot): string {
		return `${s.startTime.slice(0,5)}–${s.endTime.slice(0,5)}`;
	}

	let calendarMonthLabel = $derived(
		calendarMonth.toLocaleDateString('en-IE', { month: 'long', year: 'numeric' })
	);

	async function loadCalendarMonth() {
		const year = calendarMonth.getFullYear();
		const month = calendarMonth.getMonth();
		const first = new Date(year, month, 1);
		const offset = (first.getDay() + 6) % 7;
		const gridStart = new Date(first);
		gridStart.setDate(first.getDate() - offset);
		const gridEnd = new Date(gridStart);
		gridEnd.setDate(gridStart.getDate() + 41);
		try {
			monthSlots = await api.get<TimeSlot[]>(
				`/api/carrier/timeslots?start=${formatDate(gridStart)}&end=${formatDate(gridEnd)}`
			);
		} catch {
			monthSlots = [];
		}
	}

	function shiftMonth(delta: number) {
		const m = new Date(calendarMonth);
		m.setMonth(m.getMonth() + delta);
		calendarMonth = m;
		loadCalendarMonth();
	}

	function openDayModal(cell: CalendarCell) {
		dayModalDate = cell.date;
		dayModalError = '';
		newSlotStart = '09:00';
		newSlotEnd = '17:00';
		dayModalOpen = true;
	}

	async function addSlot() {
		if (!dayModalDate) return;
		if (newSlotEnd <= newSlotStart) {
			dayModalError = 'End time must be after start time.';
			return;
		}
		dayModalError = '';
		dayModalSaving = true;
		try {
			const created = await api.post<TimeSlot>('/api/carrier/timeslots', {
				date: dayModalDate,
				startTime: newSlotStart,
				endTime: newSlotEnd
			});
			monthSlots = [...monthSlots, created];
			await loadAvailability();
		} catch (e: any) {
			dayModalError = e?.error || e?.message || 'Failed to add slot';
		} finally {
			dayModalSaving = false;
		}
	}

	async function deleteSlot(id: number) {
		dayModalError = '';
		try {
			await api.delete(`/api/carrier/timeslots/${id}`);
			monthSlots = monthSlots.filter(s => s.id !== id);
			await loadAvailability();
		} catch (e: any) {
			dayModalError = e?.error || e?.message || 'Failed to delete slot';
		}
	}

	async function loadCompliance() {
		complianceLoading = true;
		try {
			compliance = await api.get<CarrierComplianceSummary>('/api/carrier/compliance');
		} catch {
			compliance = null;
		} finally {
			complianceLoading = false;
		}
	}

	async function addDocument() {
		docError = '';
		docSuccess = '';
		try {
			await api.post('/api/carrier/compliance', docForm);
			docSuccess = 'Document submitted for verification';
			docForm = { documentType: 'DRIVING_LICENCE', documentNumber: '', expiryDate: '' };
			loadCompliance();
		} catch (e: any) {
			docError = e.message || 'Failed to add document';
		}
	}

	async function deleteDocument(id: number) {
		try {
			await api.delete(`/api/carrier/compliance/${id}`);
			loadCompliance();
		} catch { /* ignore */ }
	}

	async function loadLoads() {
		loadsLoading = true;
		try {
			loads = await api.get<Load[]>('/api/carrier/loads');
		} catch {
			loads = [];
		} finally {
			loadsLoading = false;
		}
	}

	async function loadLanes() {
		try {
			lanes = await api.get<CarrierLane[]>('/api/carrier/lanes');
		} catch {
			lanes = [];
		}
	}

	async function addLane() {
		laneError = '';
		if (laneForm.originCountry === laneForm.destinationCountry) {
			// Same-country lanes are allowed server-side (domestic-only opt-in)
			// but warn so the carrier realises it's not a cross-border lane.
		}
		try {
			await api.post('/api/carrier/lanes', laneForm);
			await loadLanes();
			await loadLoads();
		} catch (e: any) {
			laneError = e.message || 'Failed to add lane';
		}
	}

	async function deleteLane(id: number) {
		try {
			await api.delete(`/api/carrier/lanes/${id}`);
			await loadLanes();
			await loadLoads();
		} catch { /* ignore */ }
	}

	async function loadCabotage() {
		try {
			const r = await api.get<{ homeCountry: string | null; exposures: CabotageExposure[] }>(
				'/api/carrier/cabotage-exposure'
			);
			cabotage = r.exposures ?? [];
			homeCountry = r.homeCountry ?? '';
			if (homeCountry) homeCountryDraft = homeCountry;
		} catch {
			cabotage = [];
		}
	}

	async function saveHomeCountry() {
		homeCountryError = '';
		homeCountrySaving = true;
		try {
			await api.put('/api/carrier/home-country', { country: homeCountryDraft });
			await loadCabotage();
		} catch (e: any) {
			homeCountryError = e.message || 'Failed to set home country';
		} finally {
			homeCountrySaving = false;
		}
	}

	function openApplyModal(load: Load) {
		selectedLoad = load;
		coverNote = '';
		applyError = '';
		applyModalOpen = true;
	}

	async function submitApplication() {
		if (!selectedLoad) return;
		applyError = '';
		try {
			await api.post(`/api/carrier/loads/${selectedLoad.id}/apply`, { coverNote });
			applyModalOpen = false;
			loadLoads();
			loadApplications();
		} catch (e: any) {
			applyError = e.message || 'Failed to apply';
		}
	}

	async function loadApplications() {
		applicationsLoading = true;
		try {
			await carrierState.reloadApplications();
		} finally {
			applicationsLoading = false;
		}
	}

	async function withdrawApplication(id: number) {
		try {
			await api.put(`/api/carrier/applications/${id}/withdraw`, {});
			loadApplications();
			loadLoads();
		} catch { /* ignore */ }
	}

	function openRatingModal(loadId: number, loadTitle: string) {
		ratingLoadId = loadId;
		ratingLoadTitle = loadTitle;
		ratingScore = 0;
		ratingComment = '';
		ratingError = '';
		ratingModalOpen = true;
	}

	async function submitRating() {
		if (!ratingLoadId || ratingScore === 0) return;
		ratingError = '';
		try {
			await api.post(`/api/carrier/loads/${ratingLoadId}/rate`, {
				score: ratingScore,
				comment: ratingComment
			});
			ratingModalOpen = false;
			loadApplications();
		} catch (e: any) {
			ratingError = e.message || 'Failed to submit rating';
		}
	}

	function appStatusKind(status: string): 'blue' | 'green' | 'red' | 'magenta' | 'gray' {
		switch (status) {
			case 'PENDING': return 'blue';
			case 'ACCEPTED': return 'green';
			case 'REJECTED': return 'red';
			case 'WITHDRAWN': return 'magenta';
			default: return 'gray';
		}
	}

	function appStatusIcon(status: string) {
		switch (status) {
			case 'ACCEPTED': return Checkmark;
			case 'REJECTED': return Close;
			case 'WITHDRAWN': return Undo;
			default: return undefined;
		}
	}

	function appStatusLabel(status: string): string {
		switch (status) {
			case 'PENDING': return 'Pending';
			case 'ACCEPTED': return 'Accepted';
			case 'REJECTED': return 'Rejected';
			case 'WITHDRAWN': return 'Withdrawn';
			default: return status;
		}
	}

	function docStatusKind(status: string): 'green' | 'blue' | 'red' {
		switch (status) {
			case 'VERIFIED': return 'green';
			case 'EXPIRED': return 'red';
			default: return 'blue';
		}
	}

	function docTypeLabel(type: string): string {
		switch (type) {
			case 'DRIVING_LICENCE': return 'Driving Licence';
			case 'INSURANCE': return 'Insurance';
			case 'CPC_CARD': return 'CPC Card';
			case 'TACHOGRAPH_CARD': return 'Tachograph Card';
			default: return 'Other';
		}
	}

	$effect(() => {
		if (selectedTab === 1) loadCompliance();
		if (selectedTab === 2) { loadLoads(); loadApplications(); loadLanes(); loadCabotage(); }
		if (selectedTab === 3) loadApplications();
	});

	onMount(() => {
		loadAvailability();
		loadCalendarMonth();
	});
</script>

<Grid>
	<Row>
		<Column>
			<h1>Welcome, {auth.user?.firstName}!</h1>
			<p class="dashboard-subtitle">Your carrier dashboard</p>
			<Button size="small" kind="tertiary" href="/dashboard/capabilities">
				Modes &amp; credentials
			</Button>
		</Column>
	</Row>

	<Row>
		<Column>
			<Tabs bind:selected={selectedTab}>
				<Tab label="Availability" />
				<Tab label="Compliance" />
				<Tab label="Browse Loads" />
				<Tab label="My Applications" />
				<svelte:fragment slot="content">
					<!-- Availability Tab -->
					<TabContent>
						<div class="tab-content">
							<h3><Time size={20} /> Set Your Available Hours</h3>
							<p class="info-text">
								Each transport mode has its own duty/rest clock — declare hours per mode below.
								Hours committed to assigned loads are netted off automatically.
							</p>

							{#if availError}
								<InlineNotification kind="error" title="Error" subtitle={availError}
									on:close={() => availError = ''} />
							{/if}
							{#if availSuccess}
								<InlineNotification kind="success" title="Saved" subtitle={availSuccess}
									on:close={() => availSuccess = ''} />
							{/if}

							<div class="calendar">
								<div class="calendar-header">
									<Button size="small" kind="ghost"
										on:click={() => shiftMonth(-1)}>
										&larr; Prev
									</Button>
									<div class="calendar-title">{calendarMonthLabel}</div>
									<Button size="small" kind="ghost"
										on:click={() => shiftMonth(1)}>
										Next &rarr;
									</Button>
								</div>

								<div class="calendar-weekdays">
									{#each ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'] as wd}
										<div class="calendar-weekday">{wd}</div>
									{/each}
								</div>

								<div class="calendar-grid">
									{#each calendarGrid as cell}
										<!-- svelte-ignore a11y_click_events_have_key_events -->
										<!-- svelte-ignore a11y_no_static_element_interactions -->
										<div class="calendar-cell"
											class:other-month={!cell.inMonth}
											class:has-hours={cell.slots.length > 0}
											onclick={() => openDayModal(cell)}>
											<div class="calendar-day">{cell.day}</div>
											{#each cell.slots as slot}
												<div class="calendar-slot">{formatSlotShort(slot)}</div>
											{/each}
										</div>
									{/each}
								</div>
							</div>

							{#if availability}
								<div class="avail-editor">
									<div class="avail-editor-head">
										<Select size="sm" labelText="Declare hours for mode"
											bind:selected={availMode} on:change={() => initWeekDays()}>
											{#each availability.dutyClocks as clock}
												<SelectItem value={clock.mode} text={transportModeLabel(clock.mode)} />
											{/each}
										</Select>
										<Button size="small" on:click={saveAvailability} disabled={availLoading}>
											{availLoading ? 'Saving…' : `Save ${transportModeLabel(availMode)} hours`}
										</Button>
									</div>
									<div class="weekday-row">
										{#each weekDays as day, i}
											<div class="weekday-cell">
												<NumberInput size="sm" label={day.dayName}
													bind:value={weekDays[i].hours} min={0} step={1} allowEmpty />
											</div>
										{/each}
									</div>
								</div>

								<div class="duty-clocks">
									<h4>Duty clocks by mode</h4>
									<div class="clock-grid">
										{#each availability.dutyClocks as clock}
											<Tile class="clock-tile">
												<div class="clock-head">
													<Tag type={modeTagColor(clock.mode)}>{transportModeLabel(clock.mode)}</Tag>
													<span class="clock-reg">{clock.regulation}</span>
												</div>
												<div class="clock-bar"
													title="{clock.committedThisWeek}h committed + {clock.remainingThisWeek}h free of {clock.maxWeeklyHours}h/wk cap">
													<div class="seg committed" style="width:{barPct(clock.committedThisWeek, clock.maxWeeklyHours)}%"></div>
													<div class="seg free" style="width:{barPct(clock.remainingThisWeek, clock.maxWeeklyHours)}%"></div>
												</div>
												<div class="clock-nums">
													<strong>{clock.remainingThisWeek}h free</strong> this week
													<span class="muted">· {clock.committedThisWeek}h committed · {clock.declaredThisWeek}h declared · {clock.maxWeeklyHours}h/wk cap</span>
												</div>
											</Tile>
										{/each}
									</div>
								</div>
							{/if}
						</div>
					</TabContent>

					<!-- Compliance Tab -->
					<TabContent>
						<div class="tab-content">
							<h3><CertificateCheck size={20} /> Compliance Documents</h3>
							<p class="info-text">
								Upload your documents to get verified. Verified carriers get a trust badge visible to shippers.
							</p>

							{#if compliance}
								<Tile class="compliance-summary">
									<strong>{compliance.verifiedCount}</strong> of <strong>{compliance.totalCount}</strong> documents verified
									{#if compliance.allVerified && compliance.totalCount > 0}
										<Tag type="green">Fully Verified</Tag>
									{/if}
								</Tile>

								{#if compliance.documents.length > 0}
									<div class="doc-list">
										{#each compliance.documents as doc}
											<Tile class="doc-tile">
												<div class="doc-header">
													<strong>{docTypeLabel(doc.documentType)}</strong>
													<Tag type={docStatusKind(doc.status)}>{doc.status}</Tag>
												</div>
												<p class="doc-detail">Number: {doc.documentNumber}</p>
												<p class="doc-detail">Expires: {doc.expiryDate}</p>
												{#if doc.notes}
													<p class="doc-detail">Notes: {doc.notes}</p>
												{/if}
												<Button size="small" kind="danger-ghost"
													on:click={() => deleteDocument(doc.id)}>Remove</Button>
											</Tile>
										{/each}
									</div>
								{/if}
							{/if}

							<h4 class="form-heading">Add Document</h4>

							{#if docError}
								<InlineNotification kind="error" title="Error" subtitle={docError}
									on:close={() => docError = ''} />
							{/if}
							{#if docSuccess}
								<InlineNotification kind="success" title="Success" subtitle={docSuccess}
									on:close={() => docSuccess = ''} />
							{/if}

							<div class="doc-form">
								<Select bind:selected={docForm.documentType} labelText="Document Type">
									<SelectItem value="DRIVING_LICENCE" text="Driving Licence" />
									<SelectItem value="INSURANCE" text="Insurance" />
									<SelectItem value="CPC_CARD" text="CPC Card" />
									<SelectItem value="TACHOGRAPH_CARD" text="Tachograph Card" />
									<SelectItem value="OTHER" text="Other" />
								</Select>
								<TextInput bind:value={docForm.documentNumber}
									labelText="Document Number" placeholder="e.g. DL-12345" />
								<TextInput bind:value={docForm.expiryDate}
									labelText="Expiry Date" type="date" />
								<Button on:click={addDocument}
									disabled={!docForm.documentNumber || !docForm.expiryDate}>
									Submit Document
								</Button>
							</div>
						</div>
					</TabContent>

					<!-- Browse Loads Tab -->
					<TabContent>
						<div class="tab-content">
							<h3><Search size={20} /> Loads Matching Your Profile</h3>
							<p class="info-text">
								Showing loads whose transport mode you support and that you're credentialed for,
								where that mode's duty clock still has hours free on the date needed (committed
								hours are netted off)
								{#if lanes.length > 0}
									, restricted to your {lanes.length} configured lane{lanes.length === 1 ? '' : 's'}.
								{:else}
									. Add lanes below to filter to specific country pairs.
								{/if}
							</p>

							<div class="lanes-panel">
								<div class="lanes-header">
									<h4>My Lanes</h4>
									{#if lanes.length === 0}
										<span class="lanes-empty">No lanes set — showing all matching loads.</span>
									{/if}
								</div>

								{#if lanes.length > 0}
									<div class="lane-tags">
										{#each lanes as lane}
											<Tag type="cool-gray" filter on:close={() => deleteLane(lane.id)}>
												{lane.originCountry} &rarr; {lane.destinationCountry}
												<span class="lane-country-name">
													({countryName(lane.originCountry)} → {countryName(lane.destinationCountry)})
												</span>
											</Tag>
										{/each}
									</div>
								{/if}

								<div class="lane-add">
									<Select bind:selected={laneForm.originCountry} labelText="From" hideLabel>
										{#each HAULAGE_COUNTRIES as c}
											<SelectItem value={c.code} text="{c.code} — {c.name}" />
										{/each}
									</Select>
									<span class="lane-arrow">&rarr;</span>
									<Select bind:selected={laneForm.destinationCountry} labelText="To" hideLabel>
										{#each HAULAGE_COUNTRIES as c}
											<SelectItem value={c.code} text="{c.code} — {c.name}" />
										{/each}
									</Select>
									<Button size="small" on:click={addLane}>Add lane</Button>
								</div>
								{#if laneError}
									<InlineNotification kind="error" subtitle={laneError}
										on:close={() => laneError = ''} />
								{/if}
							</div>

							<div class="cabotage-panel">
								<div class="cabotage-header">
									<h4><CertificateCheck size={16} /> Cabotage exposure (last 7 days)</h4>
								</div>

								{#if !homeCountry}
									<p class="cabotage-empty">
										Set your home country to enable cabotage compliance tracking.
										A foreign-based carrier is limited to 3 cabotage ops per host country
										per rolling 7-day window (EU 1072/2009).
									</p>
									<div class="home-country-form">
										<Select bind:selected={homeCountryDraft} labelText="Home country" hideLabel>
											{#each HAULAGE_COUNTRIES as c}
												<SelectItem value={c.code} text="{c.code} — {c.name}" />
											{/each}
										</Select>
										<Button size="small" on:click={saveHomeCountry} disabled={homeCountrySaving}>
											{homeCountrySaving ? 'Saving...' : 'Set home country'}
										</Button>
									</div>
								{:else}
									<p class="cabotage-home">
										Home country: <strong>{homeCountry}</strong>
										<span class="cabotage-home-name">({countryName(homeCountry)})</span>
									</p>
									{#if cabotage.length === 0}
										<p class="cabotage-empty">No cabotage ops in the last 7 days.</p>
									{:else}
										<div class="cabotage-rows">
											{#each cabotage as ex}
												{@const atLimit = ex.opsInWindow >= ex.limit}
												<div class="cabotage-row" class:at-limit={atLimit}>
													<Tag type={atLimit ? 'red' : 'cool-gray'}>
														{ex.country}
													</Tag>
													<span class="cabotage-count">
														{ex.opsInWindow} / {ex.limit} ops
													</span>
													{#if ex.newestOpDate}
														<span class="cabotage-meta">
															latest {ex.newestOpDate}{#if ex.newestOpLocation} · {ex.newestOpLocation}{/if}
														</span>
													{/if}
													{#if atLimit}
														<Tag type="red" size="sm">At limit — new applies blocked</Tag>
													{/if}
												</div>
											{/each}
										</div>
									{/if}
								{/if}

								{#if homeCountryError}
									<InlineNotification kind="error" subtitle={homeCountryError}
										on:close={() => homeCountryError = ''} />
								{/if}
							</div>

							{#if loadsLoading}
								<p>Loading loads...</p>
							{:else if loads.length === 0}
								<InlineNotification kind="info" title="No loads found"
									subtitle="Declare available hours for the modes you support, then loads whose mode still has free hours will appear here."
									hideCloseButton />
							{:else}
								<div class="load-list">
									{#each loads as load}
										{@const existing = applicationByLoadId.get(load.id)}
										{@const isWithdrawn = existing?.status === 'WITHDRAWN'}
										{@const blocksApply = !!existing && !isWithdrawn}
										<Tile class="load-tile">
											<div class="load-header">
												<h4>{load.title}</h4>
												<div class="header-tags">
													<Tag type={modeTagColor(load.transportMode)}>{transportModeLabel(load.transportMode)}</Tag>
													<Tag type="blue">{load.requiredLicenceCategory ?? load.requiredCdlType ?? 'Any licence'}</Tag>
													{#if isWithdrawn}
														<Tag type="magenta" icon={Undo}>Withdrawn</Tag>
													{:else if blocksApply}
														<Tag type="green" icon={Checkmark}>Applied</Tag>
													{/if}
												</div>
											</div>
											<p class="load-company">{load.shipperCompanyName}</p>
											<p>{load.description}</p>
											<div class="load-details">
												{#if load.stops && load.stops.length > 0}
													<span class="route-multi">
														<strong>Route:</strong>
														{#each load.stops as s, i}
															<span class="route-stop" title={s.type}>
																{#if s.location?.country}<span class="route-cc">{s.location.country}</span>{/if}
																{s.location?.name ?? '?'}
															</span>
															{#if i < load.stops.length - 1}<span class="route-arrow">&rarr;</span>{/if}
														{/each}
													</span>
												{:else}
													<span><strong>Route:</strong> {load.pickupLocation} &rarr; {load.deliveryLocation}</span>
												{/if}
												<span><strong>Date:</strong> {load.dateNeeded}</span>
												<span><strong>Duration:</strong> {load.estimatedDurationHours}h</span>
												<span><strong>Rate:</strong> &euro;{load.ratePerHour}/hr</span>
											</div>
											<Button size="small" disabled={blocksApply}
												on:click={() => openApplyModal(load)}>
												{blocksApply ? 'Already applied' : isWithdrawn ? 'Re-apply' : 'Apply'}
											</Button>
										</Tile>
									{/each}
								</div>
							{/if}
						</div>
					</TabContent>

					<!-- My Applications Tab -->
					<TabContent>
						<div class="tab-content">
							<h3><Document size={20} /> My Applications</h3>

							{#if applicationsLoading}
								<p>Loading applications...</p>
							{:else if applications.length === 0}
								<InlineNotification kind="info" title="No applications yet"
									subtitle="Browse loads and apply to see your applications here."
									hideCloseButton />
							{:else}
								<div class="applications-list">
									{#each applications as app}
										<Tile class="app-tile">
											<div class="app-header">
												<h4>{app.loadTitle}</h4>
												<div class="app-tags">
													<Tag type={appStatusKind(app.status)} icon={appStatusIcon(app.status)}>
														{appStatusLabel(app.status)}
													</Tag>
													{#if app.loadStatus && app.loadStatus !== 'OPEN'}
														<Tag type="gray">Load: {app.loadStatus}</Tag>
													{/if}
												</div>
											</div>
											{#if app.coverNote}
												<p class="cover-note">"{app.coverNote}"</p>
											{/if}
											<p class="app-date">Applied: {new Date(app.appliedAt).toLocaleDateString()}</p>
											<div class="app-actions">
												{#if app.status === 'PENDING'}
													<Button kind="danger-tertiary" size="small"
														on:click={() => withdrawApplication(app.id)}>
														Withdraw
													</Button>
												{/if}
												{#if app.status === 'ACCEPTED' && app.loadStatus === 'COMPLETED'}
													<Button size="small" kind="secondary"
														on:click={() => openRatingModal(app.loadId, app.loadTitle)}>
														Rate Shipper
													</Button>
												{/if}
											</div>
										</Tile>
									{/each}
								</div>
							{/if}
						</div>
					</TabContent>
				</svelte:fragment>
			</Tabs>
		</Column>
	</Row>
</Grid>

<!-- Apply Modal -->
<Modal
	bind:open={applyModalOpen}
	modalHeading="Apply for {selectedLoad?.title}"
	primaryButtonText="Submit Application"
	secondaryButtonText="Cancel"
	on:click:button--primary={submitApplication}
	on:click:button--secondary={() => applyModalOpen = false}
>
	{#if applyError}
		<InlineNotification kind="error" title="Error" subtitle={applyError} />
	{/if}
	{#if selectedLoad}
		<p><strong>Company:</strong> {selectedLoad.shipperCompanyName}</p>
		<p><strong>Rate:</strong> &euro;{selectedLoad.ratePerHour}/hr &middot; {selectedLoad.estimatedDurationHours}h</p>
		<p><strong>Date:</strong> {selectedLoad.dateNeeded}</p>
		<br />
		<TextArea
			bind:value={coverNote}
			labelText="Cover note (optional)"
			placeholder="Tell the shipper why you're a good fit..."
			rows={3}
		/>
	{/if}
</Modal>

<!-- Rating Modal -->
<Modal
	bind:open={ratingModalOpen}
	modalHeading="Rate Shipper - {ratingLoadTitle}"
	primaryButtonText="Submit Rating"
	secondaryButtonText="Cancel"
	on:click:button--primary={submitRating}
	on:click:button--secondary={() => ratingModalOpen = false}
>
	{#if ratingError}
		<InlineNotification kind="error" title="Error" subtitle={ratingError} />
	{/if}
	<p class="rating-prompt">How was your experience?</p>
	<div class="star-rating">
		{#each [1, 2, 3, 4, 5] as s}
			<button class="star-btn" onclick={() => ratingScore = s} aria-label="Rate {s} stars">
				{#if s <= ratingScore}
					<StarFilled size={32} class="star-filled" />
				{:else}
					<Star size={32} class="star-empty" />
				{/if}
			</button>
		{/each}
		<span class="score-label">{ratingScore > 0 ? `${ratingScore}/5` : ''}</span>
	</div>
	<br />
	<TextArea
		bind:value={ratingComment}
		labelText="Comment (optional)"
		placeholder="Share your experience..."
		rows={3}
	/>
</Modal>

<!-- Day Time-Slots Modal -->
<Modal
	bind:open={dayModalOpen}
	modalHeading={dayModalDate ? `Time slots — ${dayModalDate}` : 'Time slots'}
	passiveModal
>
	{#if dayModalError}
		<InlineNotification kind="error" title="Error" subtitle={dayModalError}
			on:close={() => dayModalError = ''} />
	{/if}
	<p class="modal-hint">EU cap: max 9h/day standard (10h up to twice/week).</p>

	{#if dayModalSlots.length === 0}
		<p class="modal-hint">No slots yet for this day. Add one below.</p>
	{:else}
		<ul class="slot-list">
			{#each dayModalSlots as slot}
				<li class="slot-row">
					<span class="slot-time">{formatSlotShort(slot)}</span>
					<span class="slot-hours">{slot.hours.toFixed(1)}h</span>
					<Button kind="danger-tertiary" size="small"
						on:click={() => deleteSlot(slot.id)}>
						Remove
					</Button>
				</li>
			{/each}
		</ul>
	{/if}

	<div class="add-slot">
		<label class="slot-label">
			<span>Start</span>
			<input type="time" bind:value={newSlotStart} class="time-input" />
		</label>
		<label class="slot-label">
			<span>End</span>
			<input type="time" bind:value={newSlotEnd} class="time-input" />
		</label>
		<Button kind="primary" size="small" disabled={dayModalSaving}
			on:click={addSlot}>
			{dayModalSaving ? 'Adding...' : 'Add slot'}
		</Button>
	</div>
</Modal>

<style>
	.dashboard-subtitle {
		color: var(--cds-text-secondary);
		margin-bottom: 1.5rem;
	}
	.tab-content {
		padding: 1.5rem 0;
	}
	.tab-content h3 {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		margin-bottom: 0.5rem;
	}
	.info-text {
		color: var(--cds-text-secondary);
		margin-bottom: 1rem;
		font-size: 0.875rem;
	}
	.calendar {
		margin-bottom: 1rem;
	}
	.calendar-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 0.5rem;
	}
	.calendar-title {
		font-weight: 600;
		font-size: 1rem;
	}
	.calendar-weekdays {
		display: grid;
		grid-template-columns: repeat(7, 1fr);
		gap: 0.25rem;
		margin-bottom: 0.25rem;
	}
	.calendar-weekday {
		text-align: center;
		font-size: 0.75rem;
		font-weight: 600;
		color: var(--cds-text-secondary);
		padding: 0.25rem 0;
	}
	.calendar-grid {
		display: grid;
		grid-template-columns: repeat(7, 1fr);
		gap: 0.25rem;
	}
	.calendar-cell {
		min-height: 4rem;
		padding: 0.5rem;
		background: var(--cds-layer, #f4f4f4);
		border: 1px solid var(--cds-border-subtle, #e0e0e0);
		cursor: pointer;
		transition: background 120ms ease;
	}
	.calendar-cell:hover {
		background: var(--cds-layer-hover, #e5e5e5);
	}
	.calendar-cell.other-month {
		opacity: 0.4;
	}
	.calendar-cell.has-hours {
		border-left: 3px solid var(--cds-interactive, #0f62fe);
	}
	.calendar-day {
		font-size: 0.875rem;
		font-weight: 500;
	}
	.calendar-slot {
		margin-top: 0.125rem;
		font-size: 0.6875rem;
		font-weight: 500;
		color: var(--cds-interactive, #0f62fe);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.modal-hint {
		font-size: 0.875rem;
		color: var(--cds-text-secondary);
		margin-bottom: 0.75rem;
	}
	.slot-list {
		list-style: none;
		padding: 0;
		margin: 0 0 1rem;
	}
	.slot-row {
		display: flex;
		align-items: center;
		gap: 1rem;
		padding: 0.5rem 0.75rem;
		background: var(--cds-layer, #f4f4f4);
		margin-bottom: 0.25rem;
	}
	.slot-time {
		font-weight: 600;
		min-width: 7rem;
	}
	.slot-hours {
		flex: 1;
		color: var(--cds-text-secondary);
	}
	.add-slot {
		display: flex;
		align-items: end;
		gap: 1rem;
		flex-wrap: wrap;
	}
	.slot-label {
		display: flex;
		flex-direction: column;
		gap: 0.25rem;
		font-size: 0.75rem;
		color: var(--cds-text-secondary);
	}
	.time-input {
		font-size: 0.875rem;
		height: 2.5rem;
		padding: 0 0.75rem;
		border: none;
		border-bottom: 1px solid var(--cds-border-strong, #8d8d8d);
		background-color: var(--cds-field, #f4f4f4);
	}
	.time-input:focus {
		outline: 2px solid var(--cds-focus, #0f62fe);
		outline-offset: -2px;
	}
	.avail-editor {
		margin: 1rem 0;
		padding: 1rem;
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-interactive, #0f62fe);
	}
	.avail-editor-head {
		display: flex;
		align-items: flex-end;
		gap: 1rem;
		margin-bottom: 0.75rem;
		flex-wrap: wrap;
	}
	.weekday-row {
		display: grid;
		grid-template-columns: repeat(7, 1fr);
		gap: 0.5rem;
	}
	.duty-clocks {
		margin-top: 1rem;
	}
	.duty-clocks h4 {
		margin: 0 0 0.5rem;
		font-size: 0.875rem;
		text-transform: uppercase;
		letter-spacing: 0.02em;
		color: var(--cds-text-secondary);
	}
	.clock-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
		gap: 0.75rem;
	}
	.clock-head {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		margin-bottom: 0.5rem;
	}
	.clock-reg {
		font-size: 0.75rem;
		color: var(--cds-text-secondary);
	}
	.clock-bar {
		display: flex;
		height: 0.5rem;
		border-radius: 0.25rem;
		overflow: hidden;
		background: var(--cds-border-subtle, #e0e0e0);
		margin-bottom: 0.5rem;
	}
	.clock-bar .seg.committed {
		background: var(--cds-support-warning, #f1c21b);
	}
	.clock-bar .seg.free {
		background: var(--cds-support-success, #24a148);
	}
	.clock-nums {
		font-size: 0.8125rem;
	}
	.clock-nums .muted {
		color: var(--cds-text-secondary);
	}
	:global(.compliance-summary) {
		margin-bottom: 1rem;
		display: flex;
		align-items: center;
		gap: 0.75rem;
	}
	.doc-list {
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
		margin-bottom: 1.5rem;
	}
	.doc-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 0.25rem;
	}
	.doc-detail {
		font-size: 0.875rem;
		color: var(--cds-text-secondary);
		margin: 0.125rem 0;
	}
	.form-heading {
		margin: 1rem 0 0.75rem;
	}
	.doc-form {
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
		max-width: 400px;
	}
	.load-list, .applications-list {
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}
	.load-header, .app-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 0.5rem;
	}
	.header-tags {
		display: flex;
		gap: 0.25rem;
		flex-wrap: wrap;
	}
	.app-tags {
		display: flex;
		gap: 0.25rem;
	}
	.load-company {
		color: var(--cds-text-secondary);
		margin-bottom: 0.5rem;
	}
	.load-details {
		display: flex;
		flex-wrap: wrap;
		gap: 1rem;
		margin: 0.75rem 0;
		font-size: 0.875rem;
	}
	.route-multi {
		display: inline-flex;
		flex-wrap: wrap;
		gap: 0.25rem;
		align-items: center;
	}
	.route-stop {
		padding: 0.0625rem 0.375rem;
		background: var(--cds-layer-accent, #e0e0e0);
		border-radius: 0.25rem;
	}
	.route-cc {
		font-weight: 600;
		font-size: 0.75rem;
		margin-right: 0.25rem;
		color: var(--cds-text-secondary);
	}
	.lanes-panel {
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-interactive, #0f62fe);
		padding: 0.75rem 1rem;
		margin-bottom: 1rem;
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}
	.lanes-header {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
	}
	.lanes-header h4 {
		margin: 0;
		font-size: 0.9375rem;
	}
	.lanes-empty {
		font-size: 0.8125rem;
		color: var(--cds-text-secondary);
	}
	.lane-tags {
		display: flex;
		flex-wrap: wrap;
		gap: 0.25rem;
	}
	.lane-country-name {
		font-size: 0.75rem;
		color: var(--cds-text-secondary);
		margin-left: 0.25rem;
	}
	.lane-add {
		display: flex;
		align-items: end;
		gap: 0.5rem;
	}
	.lane-add :global(.bx--select),
	.lane-add :global(.bx--form-item) {
		min-width: 11rem;
	}
	.lane-arrow {
		font-size: 1.25rem;
		color: var(--cds-text-secondary);
		padding-bottom: 0.5rem;
	}
	.cabotage-panel {
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-support-warning, #f1c21b);
		padding: 0.75rem 1rem;
		margin-bottom: 1rem;
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}
	.cabotage-header h4 {
		margin: 0;
		font-size: 0.9375rem;
		display: flex;
		align-items: center;
		gap: 0.25rem;
	}
	.cabotage-empty {
		font-size: 0.8125rem;
		color: var(--cds-text-secondary);
		margin: 0;
	}
	.cabotage-home {
		font-size: 0.875rem;
		margin: 0;
	}
	.cabotage-home-name {
		color: var(--cds-text-secondary);
		margin-left: 0.25rem;
	}
	.cabotage-rows {
		display: flex;
		flex-direction: column;
		gap: 0.25rem;
	}
	.cabotage-row {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		font-size: 0.875rem;
	}
	.cabotage-row.at-limit {
		font-weight: 600;
	}
	.cabotage-count {
		font-variant-numeric: tabular-nums;
	}
	.cabotage-meta {
		color: var(--cds-text-secondary);
		font-size: 0.8125rem;
	}
	.home-country-form {
		display: flex;
		align-items: end;
		gap: 0.5rem;
	}
	.home-country-form :global(.bx--select),
	.home-country-form :global(.bx--form-item) {
		min-width: 12rem;
	}
	.route-arrow {
		color: var(--cds-text-secondary);
	}
	.cover-note {
		font-style: italic;
		color: var(--cds-text-secondary);
	}
	.app-date {
		font-size: 0.875rem;
		color: var(--cds-text-secondary);
		margin-bottom: 0.5rem;
	}
	.app-actions {
		display: flex;
		gap: 0.5rem;
	}
	.rating-prompt {
		margin-bottom: 0.5rem;
	}
	.star-rating {
		display: flex;
		align-items: center;
		gap: 0.25rem;
	}
	.star-btn {
		background: none;
		border: none;
		cursor: pointer;
		padding: 0;
		color: #f1c40f;
	}
	.score-label {
		margin-left: 0.5rem;
		font-weight: 600;
	}
	@media (max-width: 672px) {
		.weekday-row,
		.clock-grid {
			grid-template-columns: 1fr;
		}
	}
</style>
