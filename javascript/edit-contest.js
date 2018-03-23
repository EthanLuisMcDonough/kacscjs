(() => {
	const content = document.getElementById("content");
	
	const contestRoute = jsRoutes.controllers.ContestApiController.getContest(CONTEST_ID);
	
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
			
			return bDiv;
		})());
	}
	
	fetch(contestRoute.url, {
		method: contestRoute.method,
		credentials: "same-origin"
	}).then(response => response.status >= 200 && response.status < 300 ? response.json() : Promise.reject(response)).then(init).catch(e => {
		console.error(e)
	})
})();