(() => {
	const content = document.getElementById("content");
	
	const contestRoute = jsRoutes.controllers.ContestApiController.getContest(CONTEST_ID);
	const replaceCriteriaRoute = jsRoutes.controllers.ContestApiController.replaceCriteria(CONTEST_ID);
	
	function genBracket(bracket) {
		const route = jsRoutes.controllers.ContestApiController.removeBracket(CONTEST_ID, bracket.id);
		const b = new Bracket(() => {
			fetch(route.url, {
				method: route.method,
				headers: {
					"X-Requested-With": "fetch",
					[CSRF_HEADER]: CSRF_TOKEN
				},
				credentials: "same-origin"
			}).catch(console.error);
		});
		const s = document.createElement("span");
		s.textContent = bracket.name;
		b.textfield.dom.outerHTML = s.outerHTML;
		return b;
	}
	
	function init(data) {
		content.appendChild((() => {
			const h2 = document.createElement("h2");
			h2.textContent = `Edit ${data.name}`;
			return h2;
		})());
		content.appendChild((() => {
			const bDiv = document.createElement("div");
			bDiv.innerHTML = "<h3>Brackets</h3>";
			
			const bracketDiv = document.createElement("div");
			data.brackets.forEach(bracket => genBracket(bracket).appendTo(bracketDiv));
			
			const form = document.createElement("form");
			
			const inputContainer = document.createElement("div");
			inputContainer.className = "add-bracket";
			
			const input = new MDLTextfield("Bracket name", true);
			input.appendTo(inputContainer);
			
			const addBracket = new MDLAccentRippleBtn("Add bracket");
			addBracket.dom.setAttribute("type", "submit");
			addBracket.appendTo(inputContainer);
			form.appendChild(inputContainer);
			
			form.addEventListener("submit", e => {
				e.preventDefault();
				const route = jsRoutes.controllers.ContestApiController.addBracket(CONTEST_ID);
				fetch(route.url, {
					method: route.method,
					headers: {
						"X-Requested-With": "fetch",
						[CSRF_HEADER]: CSRF_TOKEN,
						"Content-type": "application/json"
					},
					credentials: "same-origin",
					body: JSON.stringify({
						name: input.value
					})
				}).then(response => response.status >= 200 && response.status < 300 ? response.json() : Promise.reject(response)).then(bracket => {
					genBracket(bracket).appendTo(bracketDiv);
					form.reset();
				}).catch(console.error);
			});
			
			bDiv.appendChild(bracketDiv);
			bDiv.appendChild(form);
			
			const brackets = document.createElement("div");
			brackets.id = "brackets";
			brackets.className = "new-contest-fieldset mdl-card";
			brackets.appendChild(bDiv);
			
			return brackets;
		})());
		
		content.appendChild((() => {
			const table = new MDLTable([ "Name", "Description", "Weight percentage", "" ]); 
			data.criteria.forEach(e => (new Criterion(e.name, e.description, e.weight)).appendTo(table.tbody))
			
			const criteria = document.createElement("form");
			criteria.id = "criteria";
			criteria.className = "new-contest-fieldset mdl-card";
			criteria.innerHTML = "<h3>Criteria</h3>";
			
			table.appendTo(criteria);
			
			const warningDialog = new MDLDialog("WARNING", "Replacing this contest's criteria will erase all of its current judgings.  This action cannot be undone.", [
				{
					label: "Cancel", 
					onClick() {
						warningDialog.close();
					}
				},
				{
					label: "Proceed", 
					color: "#ff5555",
					onClick() {
						fetch(replaceCriteriaRoute.url, {
							method: replaceCriteriaRoute.method,
							headers: {
								"X-Requested-With": "fetch",
								[CSRF_HEADER]: CSRF_TOKEN,
								"Content-type": "application/json"
							},
							credentials: "same-origin",
							body: JSON.stringify(Criterion.values)
						}).then(response => response.status >= 200 && response.status < 300 ? response.json() : Promise.reject(response)).catch(console.error);
						warningDialog.close();
					}
				},
			]);
			warningDialog.appendTo(document.body);
			
			criteria.addEventListener("submit", e => {
				errorText.textContent = "";
				e.preventDefault();
				if (Criterion.weightSum == 100) {
					warningDialog.showModal();
				} else {
					errorText.textContent = "Your weights don't add up to 100";
				}
			});
			
			const buttonDiv = document.createElement("div");
			buttonDiv.className = "button-div";
			const addCriterion = new MDLAccentRippleBtn("Add criterion");
			addCriterion.addOnClick(e => (new Criterion()).appendTo(table.tbody));
			addCriterion.appendTo(buttonDiv);
			const replaceCriteriaBtn = new MDLAccentRippleBtn("Replace criteria");
			replaceCriteriaBtn.dom.setAttribute("type", "submit");
			replaceCriteriaBtn.appendTo(buttonDiv);
			criteria.appendChild(buttonDiv);
			
			const errorText = document.createElement("p");
			errorText.className = "error-text";
			criteria.appendChild(errorText);
			
			
			return criteria;
		})());
		
		(() => {
			const back = new MDLAccentRippleBtn("Back");
			back.addOnClick(e => window.location.replace(jsRoutes.controllers.ContestUIController.contest(CONTEST_ID).url));
			return back;
		})().appendTo(content);
	}
	
	fetch(contestRoute.url, {
		method: contestRoute.method,
		credentials: "same-origin"
	}).then(response => response.status >= 200 && response.status < 300 ? response.json() : Promise.reject(response)).then(init).catch(e => {
		content.innerHTML = `
			<h2>OOPS!</h2>
			<p>${e.status == 404 ? "That contest doesn't exist" : (e.status == 403 ? "You're not allowed to see this page" : "Error")}</p>
		`;
		console.error(e);
	})
})();