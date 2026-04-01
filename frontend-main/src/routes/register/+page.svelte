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

	// Driver fields
	let driverEmail = $state('');
	let driverPassword = $state('');
	let driverFirstName = $state('');
	let driverLastName = $state('');
	let driverPhone = $state('');
	let licenseNumber = $state('');
	let licenseExpiration = $state('');
	let cdlType = $state('');
	let yearsExperience = $state(0);
	let licenseState = $state('');

	// Employer fields
	let employerEmail = $state('');
	let employerPassword = $state('');
	let employerFirstName = $state('');
	let employerLastName = $state('');
	let employerPhone = $state('');
	let companyName = $state('');
	let industry = $state('');
	let companyWebsite = $state('');

	async function registerDriver() {
		error = '';
		success = '';
		loading = true;
		try {
			await api.post('/api/user/register/driver', {
				email: driverEmail,
				password: driverPassword,
				firstName: driverFirstName,
				lastName: driverLastName,
				phone: driverPhone,
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

	async function registerEmployer() {
		error = '';
		success = '';
		loading = true;
		try {
			await api.post('/api/user/register/employer', {
				email: employerEmail,
				password: employerPassword,
				firstName: employerFirstName,
				lastName: employerLastName,
				phone: employerPhone,
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
					<p class="form-subtitle">Join Driver Direct as a driver or employer</p>

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
						<Tab label="Driver" />
						<Tab label="Employer" />
						<svelte:fragment slot="content">
							<TabContent>
								<form onsubmit={(e: Event) => { e.preventDefault(); registerDriver(); }}>
									<h4>Personal Information</h4>
									<div class="form-row">
										<div class="form-field">
											<TextInput labelText="First Name" bind:value={driverFirstName} />
										</div>
										<div class="form-field">
											<TextInput labelText="Last Name" bind:value={driverLastName} />
										</div>
									</div>
									<div class="form-field">
										<TextInput labelText="Email" type="email" bind:value={driverEmail} required />
									</div>
									<div class="form-field">
										<PasswordInput labelText="Password" bind:value={driverPassword} required />
									</div>
									<div class="form-field">
										<TextInput labelText="Phone" type="tel" bind:value={driverPhone} />
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
												if (detail.dateStr) licenseExpiration = detail.dateStr;
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
											{loading ? 'Registering...' : 'Register as Driver'}
										</Button>
									</div>
								</form>
							</TabContent>
							<TabContent>
								<form onsubmit={(e: Event) => { e.preventDefault(); registerEmployer(); }}>
									<h4>Personal Information</h4>
									<div class="form-row">
										<div class="form-field">
											<TextInput labelText="First Name" bind:value={employerFirstName} />
										</div>
										<div class="form-field">
											<TextInput labelText="Last Name" bind:value={employerLastName} />
										</div>
									</div>
									<div class="form-field">
										<TextInput labelText="Email" type="email" bind:value={employerEmail} required />
									</div>
									<div class="form-field">
										<PasswordInput labelText="Password" bind:value={employerPassword} required />
									</div>
									<div class="form-field">
										<TextInput labelText="Phone" type="tel" bind:value={employerPhone} />
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
											{loading ? 'Registering...' : 'Register as Employer'}
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
