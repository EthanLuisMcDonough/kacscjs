(() => {
	const contestDiv = document.getElementById("contest-div");
	
	const contestRoute = jsRoutes.controllers.ContestApiController.getContest(CONTEST_ID);
	fetch(contestRoute.url, {
		method: contestRoute.method,
		credentials: "same-origin"
	}).then(response => response.status >= 200 && response.status < 300 ? response.json() : Promise.reject(response)).then(data => {
		const contestTitle = document.createElement("h2");
		contestTitle.textContent = data.name;
		contestDiv.appendChild(contestTitle);
		
		const description = document.createElement("p");
		description.textContent = data.description;
		contestDiv.appendChild(description);
		
		const bracketsTitle = document.createElement("h4");
		bracketsTitle.textContent = "Brackets";
		contestDiv.appendChild(bracketsTitle);
		
		const brackets = document.createElement("ul");
		data.brackets.forEach(bracket => {
			const item = document.createElement("li");
			item.textContent = bracket.name;
			brackets.appendChild(item);
		})
		contestDiv.appendChild(brackets);
		
		const criteriaTitle = document.createElement("h4");
		criteriaTitle.textContent = "Criteria";
		contestDiv.appendChild(criteriaTitle);
		
		const criteriaTable = new MDLTable(
			["Name", "Description", "Weight"], 
			data.criteria
				.sort((a, b) => b.weight - a.weight)
				.map(criterion => new TableRow(criterion.name, criterion.description, criterion.weight))
		);
		criteriaTable.appendTo(contestDiv);
		
		const buttonDiv = document.createElement("div");
		buttonDiv.className = "button-div";
		
		if(data.userCanViewResults) {
			const viewResultsBtn = new MDLAccentRippleBtn("View results");
			const viewResultsLink = document.createElement("a");
			viewResultsLink.href = jsRoutes.controllers.ContestUIController.contestResults(CONTEST_ID).url;
			viewResultsBtn.appendTo(viewResultsLink);
			buttonDiv.appendChild(viewResultsLink);
		}
		
		if(data.userCanJudge) {
			const randomEntryBtn = new MDLAccentRippleBtn("Random entry");
			randomEntryBtn.addOnClick(e => {
				const randomEntryRoute = jsRoutes.controllers.ContestApiController.randomEntry(CONTEST_ID);
				fetch(randomEntryRoute.url, {
					method: randomEntryRoute.method,
					credentials: "same-origin"
				}).then(response => response.status >= 200 && response.status < 300 ? response.json() : Promise.reject(response)).then(data => {
					if(!data.judgingFinished) {
						window.location.href = data.url;
					}
				}).catch(console.error);
			});
			randomEntryBtn.appendTo(buttonDiv);
		}
		
		if(User.LEVEL.ordinal() >= UserLevel.ADMIN.ordinal()) {
			const editBtn = new MDLAccentRippleBtn("Edit");
			editBtn.addOnClick(e => {
				alert("EDIT");
			});
			editBtn.appendTo(buttonDiv);
			
			const manageBtn = new MDLAccentRippleBtn("Manage Entries");
			manageBtn.addOnClick(e => {
				window.location.replace(jsRoutes.controllers.ContestUIController.entries(CONTEST_ID).url);
			});
			manageBtn.appendTo(buttonDiv);
		}
		
		contestDiv.appendChild(buttonDiv);
		
	}).catch(console.error);
})();