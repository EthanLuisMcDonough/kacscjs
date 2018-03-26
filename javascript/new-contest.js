(() => {
	const form = document.getElementById("create-contest");
	const criteriaEl = document.getElementById("criteria"),
		bracketsEl = document.getElementById("brackets");
	const addCriterionButton = document.getElementById("add-criterion"),
		addBracketButton = document.getElementById("add-bracket");
	const formError = document.getElementById("form-error");
	const timeRegex = /^(2[0-3]|[01]\d):([0-5]\d)$/;
	
	const nameField = document.getElementById("contest-name-field"), 
		descriptionField = document.getElementById("contest-description-field"), 
		programIdField = document.getElementById("contest-program-id-field"), 
		endDateField = document.getElementById("contest-end-date-field"),
		endTimeField = document.getElementById("contest-end-time-field");
	
	endDateField.addEventListener("keydown", e => e.preventDefault());
	
	const datePicker = new Pikaday({ 
		field: endDateField,
		onSelect(date) {
			endDateField.parentElement.className = endDateField.parentElement.className.replace(/is-invalid/, "is-dirty");
	    }
	});
	
	const UserItems = (() => {
		const card = document.getElementById("judges-card");
		
		const limit = 10;
		let page = 0;
		
		const table = new MDLCheckTable([ 
			"User"
		]);
		
		return {
			render() {
				table.appendTo(card);
			},
			newRow(userJson) {
				const profileUrl = document.createElement("a");
				profileUrl.setAttribute("target", "_blank");
				profileUrl.href = `https://www.khanacademy.org/profile/${userJson.kaid}`;
				profileUrl.textContent = userJson.name;
				profileUrl.className = "profile-url";
				profileUrl.setAttribute("data-id", userJson.id);
				
				table.addRow(profileUrl);
			},
			getIds() { 
				return table.checkedRows.map(e => +e.at(1).getAttribute("data-id"));
			},
			next() {
				const headers = {
					[CSRF_HEADER]: CSRF_TOKEN
				};
				return new Promise((res, rej) => {
					fetch(jsRoutes.controllers.UserApiController.getUsers(page++, limit).url, {
						method: "GET",
						headers,
						credentials: "same-origin",
					}).then(response => {
						if (response.status >= 200 && response.status < 300) {
							response.json().then(data => {
								data.forEach(user => UserItems.newRow(user));
								res(data);
							}).catch(console.error);
						} else 
							rej(response);
					}).catch(rej);
				});
			},
			loadAll() {
				const load = () => {
					this.next().then(e => {
						if(e.length == limit) 
							load();
					}).catch(console.error);
				};
				load();
			}
		};
	})();
	
	function addCriterion() {
		const criterion = new Criterion();
		criterion.appendTo(criteriaEl);
	}
	
	function addBracket() {
		const bracket = new Bracket();
		bracket.appendTo(bracketsEl);
	}
	
	addCriterion();
	addBracket();
	UserItems.render();
	
	UserItems.loadAll();
	
	addCriterionButton.addEventListener("click", addCriterion);
	addBracketButton.addEventListener("click", addBracket);
	
	form.addEventListener("submit", e => {
		e.preventDefault();
		
		const ids = UserItems.getIds();
		
		formError.textContent = "";
		
		const endDate = datePicker.getDate();
		
		if (!timeRegex.test(endTimeField.value)) {
			formError.textContent = "Invalid time formatting";
			return;
		}
		
		if (ids.length == 0) {
			formError.textContent = "Your contest needs at least one judge";
			return;
		}
		
		const [ , timeHour, timeMinute ] = timeRegex.exec(endTimeField.value)
		endDate.setHours(parseInt(timeHour, 10));
		endDate.setMinutes(parseInt(timeMinute, 10));
		
		if (Criterion.weightSum != 100) {
			formError.textContent = "Your weights must add up to 100";
			return;
		}
		
		const requestBodyJson = {
			name: nameField.value,
			description: descriptionField.value,
			id: +programIdField.value,
			endDate: endDate.getTime(),
			criteria: Criterion.values,
			brackets: Bracket.bracketNames,
			judges: ids
		};
		
		const headers = {
			"Content-Type": "application/json",
			"X-Requested-With": "fetch",
			[CSRF_HEADER]: CSRF_TOKEN
		};
		
		fetch(jsRoutes.controllers.ContestApiController.createContest().url, {
			method: "POST",
			body: JSON.stringify(requestBodyJson),
			headers,
			credentials: "same-origin"
		}).then(response => {
			if (response.status >= 200 && response.status < 300) {
				response.json()
					.then(data => window.location.href = jsRoutes.controllers.ContestUIController.contest(data.id).url)
					.catch(console.error);
			} else {
				response.json()
					.then(data => formError.textContent = data.message)
					.catch(console.error);
			}
		}).catch(e => {
			formError.textContent = formError.textContent || "Unable to create contest.  Check the JS console for more info.";
			console.error("Unable to create contest", e);
		});
	});
})();