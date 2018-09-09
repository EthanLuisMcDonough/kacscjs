(() => {
	const notAllowed = document.getElementById("not-allowed");
	const contentEl = document.getElementById("area-body");
	const alreadyJudged = document.getElementById("already-judged");
	
	const loadingArea = new DomComponent(document.getElementById("loading"));
	
	const rndEntryRoute = jsRoutes.controllers.ContestApiController.randomEntry(CONTEST_ID);
	const contestPageRoute = jsRoutes.controllers.ContestUIController.contest(CONTEST_ID);
	
	function error(e) {
		contentEl.innerHTML = `
			<h2>OOPS!</h2>
			<p>Error</p>`;
		console.error(e);
	}
	
	function randomEntry() {
		fetch(rndEntryRoute.url, {
			method: rndEntryRoute.method,
			credentials: "same-origin"
		}).then(response => response.status >= 200 && response.status < 300 ? response.json() : Promise.reject(response)).then(data => {
			window.location.href = data.judgingFinished ? contestPageRoute.url : data.url;
		}).catch(error);
	}
	
	const rndEntry = new MDLAccentRippleBtn("Random entry");
	rndEntry.addOnClick(randomEntry);
	
	const contestRoute = jsRoutes.controllers.ContestApiController.getContest(CONTEST_ID);
	fetch(contestRoute.url, {
		method: contestRoute.method,
		credentials: "same-origin"
	}).then(response => response.status >= 200 && response.status < 300 ? response.json() : Promise.reject(response)).then(contestData => {
		if (contestData.userCanJudge) {
			const entryRoute = jsRoutes.controllers.ContestApiController.getEntry(CONTEST_ID, ENTRY_ID);
			fetch(entryRoute.url, {
				method: entryRoute.method,
				credentials: "same-origin"
			}).then(response => response.status >= 200 && response.status < 300 ? response.json() : Promise.reject(response)).then(entryData => {
				if (entryData.userHasJudged) {
					contentEl.innerHTML = `
						<h2>OOPS!</h2>
						<p>You already judged this entry</p>`;
					rndEntry.appendTo(contentEl);
				} else {
					fetch(`https://www.khanacademy.org/api/labs/scratchpads/${entryData.programId}?projection=${encodeURIComponent(JSON.stringify({height:1}))}`, {
						method: "GET"
					}).then(response => {
						if(response.status >= 200 && response.status < 300) { 
							response.json().then(programData => {
								loadingArea.remove();
								
								const iframe = document.createElement("iframe");
								iframe.setAttribute("src", `https://www.khanacademy.org/computer-programming/v/${entryData.programId}/embedded?id=1520892209642-0.3369276392099292&buttons=no&embed=yes&editor=yes&author=no`);
								iframe.setAttribute("sandboxed", "");
								iframe.style.height = `${programData.height + 25}px`;
								iframe.id = "program";
								contentEl.appendChild(iframe);
								
								const form = document.createElement("form");
								form.id = "submit";
								
								const votes = [];
								const table = new MDLTable(["Criterion", "Score"], contestData.criteria.map(criterion => {
									const slider = new MDLSlider(0, 100, 50);
									
									const sliderArea = document.createElement("span");
									sliderArea.className = "slider-area";
									const sliderLabel = document.createElement("label");
									sliderLabel.textContent = `(${slider.value}%)`;
									
									slider.addOnInput(e => sliderLabel.textContent = `(${slider.value}%)`);
									
									sliderArea.appendChild(sliderLabel);
									slider.appendTo(sliderArea);
									slider.update();
									
									const descriptionArea = document.createElement("div");
									const descriptionDesc = document.createElement("p");
									const descriptionWeight = document.createElement("p");
									
									descriptionDesc.textContent = `Description: ${criterion.description}`;
									descriptionWeight.textContent = `Weight: ${criterion.weight}%`;
									
									descriptionArea.appendChild(descriptionDesc);
									descriptionArea.appendChild(descriptionWeight);
									
									const description = new MDLDialog(criterion.name, descriptionArea, [
										{
											label: "Close",
											onClick(e) {
												description.close();
											}
										}
									]);
									
									const criterionLink = document.createElement("a");
									criterionLink.className = "criterion";
									criterionLink.href = "#0";
									criterionLink.textContent = criterion.name;
									criterionLink.addEventListener("click", e => 
											e.preventDefault() || description.showModal());
									
									votes.push({
										id: criterion.id,
										get score() {
											return +slider.value;
										}
									});
									
									description.appendTo(document.body);
									
									return new TableRow(criterionLink, sliderArea);
								}));
								
								table.appendTo(form);
								
								const feedback = new MDLTextarea("Feedback (optionally tell the contestant how they can improve)", 7, false);
								feedback.dom.className += " feedback";
								
								feedback.appendTo(form);
								
								const buttons = document.createElement("div");
								buttons.className = "buttons-area";
								
								const submit = document.createElement("input");
								submit.setAttribute("type", "submit");
								submit.setAttribute("value", "Submit");
								submit.className = "mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect mdl-button--accent";
								componentHandler.upgradeElement(submit);
								buttons.appendChild(submit);
								
								form.appendChild(buttons);
								
								const errorMessage = document.createElement("p");
								errorMessage.className = "error-message";
								
								form.addEventListener("submit", e => {
									e.preventDefault();
									
									const voteRoute = jsRoutes.controllers.ContestApiController.voteEntry(CONTEST_ID, ENTRY_ID);
									
									fetch(voteRoute.url, {
										method: voteRoute.method,
										credentials: "same-origin",
										headers: {
											"Content-type": "application/json",
											[CSRF_HEADER]: CSRF_TOKEN
										},
										body: JSON.stringify({
											votes, feedback: feedback.getValue()
										})
									}).then(response => {
										if (response.status >= 200 && response.status < 300)
											randomEntry();
										else {
											errorMessage.textContent = "Error";
										}
									}).catch(error);
								});
								
								contentEl.appendChild(form);
							}).catch(error);
						} else {
							contentEl.innerHTML = `
								<h2>OOPS!</h2>
								<p>One of two things went wrong:</p>
								<ul>
									<li>The KA API is broken</li>
									<li>A program with the ID ${(entryData.programId + "").replace(/[^\d]/g, "")} does not exist</li>
								</ul>`;
							rndEntry.appendTo(contentEl);
						}
					}).catch(error)
				}
			}).catch(e => {
				if(e.status == 404) {
					contentEl.innerHTML = `
						<h2>OOPS!</h2>
						<p>That entry doesn't exist</p>`;
				}
				console.error(e);
			});
		} else {
			contentEl.innerHTML = `<h2>OOPS!</h2>
				<p>You're not allowed to see this page</p>`;
		}
	}).catch(error);
})();