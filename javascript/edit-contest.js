(() => {
	const content = document.getElementById("content");
	
	const contestRoute = jsRoutes.controllers.ContestApiController.getContest(CONTEST_ID);
	const replaceCriteriaRoute = jsRoutes.controllers.ContestApiController.replaceCriteria(CONTEST_ID);
	
	const Judge = (() => {
		let count = 0;
		return class Judge extends Component {
			constructor(json) {
				count++;
				super(json);
				this.userId = json.id;
			}
			generateDom(json) {
				const dialog = new MDLDialog("WARNING", `Removing ${json.name} from this contest's juding panel will erase all of their judgements.  This action can't be undone.`, [
					{
						label: "Cancel",
						onClick() {
							dialog.close();
						}
					},
					{
						label: "Remove",
						color: "#ff5555",
						onClick: () => {
							const route = jsRoutes.controllers.ContestApiController.deleteJudge(CONTEST_ID, json.id);
							fetch(route.url, {
								method: route.method,
								headers: {
									"X-Requested-With": "fetch",
									[CSRF_HEADER]: CSRF_TOKEN
								},
								credentials: "same-origin"
							}).catch(console.error);
							this.remove();
							dialog.close();
						}
					}
				]);
				dialog.appendTo(document.body);
				const chip = document.createElement("span");
				chip.className = "mdl-chip mdl-chip--deletable judge-chip";
				const label = document.createElement("span");
				label.className = "mdl-chip__text";
				const link = document.createElement("a");
				link.href = `https://www.khanacademy.org/profile/${json.kaid}`;
				link.textContent = json.name;
				link.setAttribute("target", "_blank");
				label.appendChild(link);
				const close = document.createElement("button");
				close.addEventListener("click", e => {
					if(count > 1) {
						dialog.showModal();
					}
				});
				close.setAttribute("type", "button");
				close.className = "mdl-chip__action";
				close.innerHTML = `<i class="material-icons">cancel</i>`;
		        chip.appendChild(label);
		        chip.appendChild(close);
		        return chip;
			}
			remove() {
				count--;
				super.remove();
			}
		}
	})();
	
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
			const judges = document.createElement("div");
			judges.id = "judges";
			judges.className = "new-contest-fieldset mdl-card";
			judges.innerHTML = "<h3>Judges</h3>";
			
			const chips = document.createElement("div");
			chips.className = "judge-chips";
			
			const addUserDialog = (() => {
				const insertForm = document.createElement("form");
				const userId = new MDLTextfield("User ID", true, "\\d+", "Invalid ID");
				userId.appendTo(insertForm);
				const errorText = document.createElement("p");
				errorText.className = "error-text";
				insertForm.appendChild(errorText);
				
				function insert() {
					errorText.textContent = "";
					if (userId.value.length != 0) {
						const addJudgeRoute = jsRoutes.controllers.ContestApiController.addJudge(CONTEST_ID, userId.value);
						fetch(addJudgeRoute.url, {
							method: addJudgeRoute.method,
							headers: {
								"X-Requested-With": "fetch",
								[CSRF_HEADER]: CSRF_TOKEN
							},
							credentials: "same-origin"
						}).then(response => {
							response.json().then(data => {
								if (response.status >= 200 && response.status < 300) {
									(new Judge(data)).appendTo(chips);
									addUserDialog.close();
									insertForm.reset();
								} else {
									errorText.textContent = data.message;
								}
							}).catch(e => {
								errorText.textContent = "Error";
								console.log(e);
							});
						}).catch(e => {
							errorText.textContent = "Error";
							console.log(e); 
						});
					}
				}
				
				insertForm.addEventListener("submit", e => {
					e.preventDefault();
					insert();
				})
				
				return new MDLDialog("Add judge", insertForm, [
					{
						label: "Cancel",
						onClick() {
							addUserDialog.close();
						}
					},
					{
						label: "Add judge",
						color: "#00b200",
						onClick: insert
					}
				]);
			})();
			addUserDialog.appendTo(document.body);
			
			data.judges.forEach(e => (new Judge(e)).appendTo(chips))
			judges.appendChild(chips);
			const buttonDiv = document.createElement("div");
			buttonDiv.className = "button-div";
			const addUser = new MDLAccentRippleBtn("Add judge");
			addUser.appendTo(buttonDiv);
			addUser.addOnClick(addUserDialog.showModal.bind(addUserDialog));
			judges.appendChild(buttonDiv);
			return judges;
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