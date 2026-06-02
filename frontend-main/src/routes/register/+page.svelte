<script lang="ts">
	import {
		Grid,
		Row,
		Column,
		Tile,
		Tabs,
		Tab,
		TabContent,
		TextInput,
		PasswordInput,
		Select,
		SelectItem,
		NumberInput,
		DatePicker,
		DatePickerInput,
		Button,
		InlineNotification
	} from 'carbon-components-svelte';
	import { UserAvatar } from 'carbon-icons-svelte';
	import { goto } from '$app/navigation';
	import { api } from '$lib/api';

	let selectedTab = $state(0);
	let loading = $state(false);
	let error = $state('');
	let success = $state('');

	// Carrier fields
	let carrierEmail = $state('');
	let carrierPassword = $state('');
	let carrierFirstName = $state('');
	let carrierLastName = $state('');
	let carrierPhone = $state('');
	let licenseNumber = $state('');
	let licenseExpiration = $state('');
	let cdlType = $state('');
	let yearsExperience = $state(0);
	let licenseState = $state('');

	// Shipper fields
	let shipperEmail = $state('');
	let shipperPassword = $state('');
	let shipperFirstName = $state('');
	let shipperLastName = $state('');
	let shipperPhone = $state('');
	let companyName = $state('');
	let industry = $state('');
	let companyWebsite = $state('');

	const isDev = import.meta.env.DEV;

	const firstNames = ['Liam', 'Sean', 'Conor', 'Ciara', 'Aoife', 'Niamh', 'Oisin', 'Roisin', 'Padraig', 'Sinead'];
	const lastNames = ['Murphy', 'Kelly', 'Byrne', 'Ryan', 'Walsh', 'Sullivan', 'Doyle', 'Gallagher', 'Nolan', 'Brennan'];
	const counties = ['Dublin', 'Cork', 'Galway', 'Limerick', 'Waterford', 'Kerry', 'Wexford', 'Donegal', 'Meath', 'Kildare'];
	const cdlTypes = ['CLASS_A', 'CLASS_B', 'CLASS_C', 'NON_CDL'];
	const industries = ['LOGISTICS', 'TRANSPORTATION', 'MANUFACTURING', 'RETAIL', 'CONSTRUCTION', 'FOOD_SERVICE'];
	const companyPrefixes = ['Swift', 'Emerald', 'Celtic', 'Atlantic', 'Premier', 'Express', 'Rapid', 'National'];
	const companySuffixes = ['Logistics', 'Transport', 'Haulage', 'Freight', 'Deliveries', 'Distribution'];

	function pick<T>(arr: T[]): T { return arr[Math.floor(Math.random() * arr.length)]; }
	function randInt(min: number, max: number) { return Math.floor(Math.random() * (max - min + 1)) + min; }
	function randPhone() { return '08' + randInt(1, 9) + String(randInt(1000000, 9999999)); }
	function randId() { return String(randInt(10000, 99999)); }

	function fillCarrier() {
		const first = pick(firstNames);
		const last = pick(lastNames);
		carrierFirstName = first;
		carrierLastName = last;
		carrierEmail = `${first.toLowerCase()}.${last.toLowerCase()}${randInt(1, 999)}@test.com`;
		carrierPassword = 'password123';
		carrierPhone = randPhone();
		licenseNumber = 'DL-' + randId();
		licenseExpiration = `${randInt(2026, 2030)}-${String(randInt(1, 12)).padStart(2, '0')}-${String(randInt(1, 28)).padStart(2, '0')}`;
		cdlType = pick(cdlTypes);
		yearsExperience = randInt(1, 25);
		licenseState = pick(counties);
	}

	function fillShipper() {
		const first = pick(firstNames);
		const last = pick(lastNames);
		const company = `${pick(companyPrefixes)} ${pick(companySuffixes)}`;
		shipperFirstName = first;
		shipperLastName = last;
		shipperEmail = `${first.toLowerCase()}.${last.toLowerCase()}${randInt(1, 999)}@test.com`;
		shipperPassword = 'password123';
		shipperPhone = randPhone();
		companyName = company;
		industry = pick(industries);
		companyWebsite = `https://${company.toLowerCase().replace(/\s/g, '')}.ie`;
	}

	async function registerCarrier() {
		error = '';
		success = '';
		loading = true;
		try {
			await api.post('/api/user/register/carrier', {
				email: carrierEmail,
				password: carrierPassword,
				firstName: carrierFirstName,
				lastName: carrierLastName,
				phone: carrierPhone,
				licenseNumber,
				licenseExpiration: licenseExpiration || undefined,
				cdlType: cdlType || undefined,
				yearsExperience,
				licenseState: licenseState || undefined
			});
			success = 'Registration successful! Redirecting to login...';
			setTimeout(() => goto('/login'), 2000);
		} catch (e: any) {
			error = e?.error || 'Registration failed. Please try again.';
		} finally {
			loading = false;
		}
	}

	async function registerShipper() {
		error = '';
		success = '';
		loading = true;
		try {
			await api.post('/api/user/register/shipper', {
				email: shipperEmail,
				password: shipperPassword,
				firstName: shipperFirstName,
				lastName: shipperLastName,
				phone: shipperPhone,
				companyName,
				industry: industry || undefined,
				companyWebsite: companyWebsite || undefined
			});
			success = 'Registration successful! Redirecting to login...';
			setTimeout(() => goto('/login'), 2000);
		} catch (e: any) {
			error = e?.error || 'Registration failed. Please try again.';
		} finally {
			loading = false;
		}
	}
</script>

<Grid>
	<Row>
		<Column lg={{ span: 8, offset: 4 }} md={{ span: 6, offset: 1 }} sm={4}>
			<div class="form-container">
				<Tile>
					<h2>Create an Account</h2>
					<p class="form-subtitle">Join Carrier Direct as a carrier or shipper</p>

					{#if error}
						<InlineNotification
							kind="error"
							title="Error"
							subtitle={error}
							on:close={() => (error = '')}
						/>
					{/if}
					{#if success}
						<InlineNotification
							kind="success"
							title="Success"
							subtitle={success}
						/>
					{/if}

					<Tabs bind:selected={selectedTab}>
						<Tab label="Carrier" />
						<Tab label="Shipper" />
						<svelte:fragment slot="content">
							<TabContent>
								<form onsubmit={(e: Event) => { e.preventDefault(); registerCarrier(); }}>
									{#if isDev}
										<div class="dev-fill">
											<Button size="small" kind="ghost" on:click={fillCarrier}>Dev: Fill Carrier</Button>
										</div>
									{/if}
									<h4>Personal Information</h4>
									<div class="form-row">
										<div class="form-field">
											<TextInput labelText="First Name" bind:value={carrierFirstName} />
										</div>
										<div class="form-field">
											<TextInput labelText="Last Name" bind:value={carrierLastName} />
										</div>
									</div>
									<div class="form-field">
										<TextInput labelText="Email" type="email" bind:value={carrierEmail} required />
									</div>
									<div class="form-field">
										<PasswordInput labelText="Password" bind:value={carrierPassword} required />
									</div>
									<div class="form-field">
										<TextInput labelText="Phone" type="tel" bind:value={carrierPhone} />
									</div>

									<h4>License Information</h4>
									<div class="form-row">
										<div class="form-field">
											<TextInput labelText="License Number" bind:value={licenseNumber} required />
										</div>
										<div class="form-field">
											<TextInput labelText="License State" placeholder="e.g. CA" bind:value={licenseState} />
										</div>
									</div>
									<div class="form-row">
										<div class="form-field">
											<DatePicker datePickerType="single" on:change={(e) => {
												const detail = e.detail;
												if (detail && typeof detail === 'object' && typeof detail.dateStr === 'string') licenseExpiration = detail.dateStr;
											}}>
												<DatePickerInput labelText="License Expiration" placeholder="mm/dd/yyyy" />
											</DatePicker>
										</div>
										<div class="form-field">
											<Select labelText="CDL Type" bind:selected={cdlType}>
												<SelectItem value="" text="Select CDL type" />
												<SelectItem value="CLASS_A" text="Class A" />
												<SelectItem value="CLASS_B" text="Class B" />
												<SelectItem value="CLASS_C" text="Class C" />
												<SelectItem value="NON_CDL" text="Non-CDL" />
											</Select>
										</div>
									</div>
									<div class="form-field">
										<NumberInput label="Years of Experience" min={0} bind:value={yearsExperience} />
									</div>

									<div class="form-actions">
										<Button type="submit" icon={UserAvatar} disabled={loading}>
											{loading ? 'Registering...' : 'Register as Carrier'}
										</Button>
									</div>
								</form>
							</TabContent>
							<TabContent>
								<form onsubmit={(e: Event) => { e.preventDefault(); registerShipper(); }}>
									{#if isDev}
										<div class="dev-fill">
											<Button size="small" kind="ghost" on:click={fillShipper}>Dev: Fill Shipper</Button>
										</div>
									{/if}
									<h4>Personal Information</h4>
									<div class="form-row">
										<div class="form-field">
											<TextInput labelText="First Name" bind:value={shipperFirstName} />
										</div>
										<div class="form-field">
											<TextInput labelText="Last Name" bind:value={shipperLastName} />
										</div>
									</div>
									<div class="form-field">
										<TextInput labelText="Email" type="email" bind:value={shipperEmail} required />
									</div>
									<div class="form-field">
										<PasswordInput labelText="Password" bind:value={shipperPassword} required />
									</div>
									<div class="form-field">
										<TextInput labelText="Phone" type="tel" bind:value={shipperPhone} />
									</div>

									<h4>Company Information</h4>
									<div class="form-field">
										<TextInput labelText="Company Name" bind:value={companyName} required />
									</div>
									<div class="form-row">
										<div class="form-field">
											<Select labelText="Industry" bind:selected={industry}>
												<SelectItem value="" text="Select industry" />
												<SelectItem value="LOGISTICS" text="Logistics" />
												<SelectItem value="TRANSPORTATION" text="Transportation" />
												<SelectItem value="MANUFACTURING" text="Manufacturing" />
												<SelectItem value="RETAIL" text="Retail" />
												<SelectItem value="CONSTRUCTION" text="Construction" />
												<SelectItem value="AGRICULTURE" text="Agriculture" />
												<SelectItem value="FOOD_SERVICE" text="Food Service" />
												<SelectItem value="ENERGY" text="Energy" />
												<SelectItem value="OTHER" text="Other" />
											</Select>
										</div>
										<div class="form-field">
											<TextInput labelText="Company Website" placeholder="https://" bind:value={companyWebsite} />
										</div>
									</div>

									<div class="form-actions">
										<Button type="submit" icon={UserAvatar} disabled={loading}>
											{loading ? 'Registering...' : 'Register as Shipper'}
										</Button>
									</div>
								</form>
							</TabContent>
						</svelte:fragment>
					</Tabs>

					<p class="form-footer">
						Already have an account? <a href="/login">Sign in here</a>
					</p>
				</Tile>
			</div>
		</Column>
	</Row>
</Grid>
