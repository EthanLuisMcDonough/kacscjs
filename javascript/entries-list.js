(() => {
	const content = document.getElementById("entries-area");
	const brackets = [];
	
	const entryManager = (() => {
		const limit = 10;
		const iterator = new PageIterator((page, limit) => jsRoutes.controllers.ContestApiController.getEntries(CONTEST_ID, page, limit), limit);
		
		const entryIds = {};
		
		const homeButton = new MDLAccentRippleBtn("Back");
		homeButton.addOnClick(e => {
			window.location.href = jsRoutes.controllers.ContestUIController.contest(CONTEST_ID).url;
		});
		
		function forEachItem(item) {
			if(!entryIds[item.id]) {
				const img = document.createElement("img");
				img.src = `https://www.khanacademy.org/computer-programming/i/${item.programId}/latest.png`;
				img.alt = "Entry thumbnail";
				img.width = img.height = 100;
				
				const programLink = document.createElement("a");
				programLink.href = `https://www.khanacademy.org/computer-programming/i/${item.programId}`;
				programLink.setAttribute("target", "_blank");
				programLink.textContent = item.programId;
				
				const deleteEntry = document.createElement("i");
				deleteEntry.className = "material-icons";
				deleteEntry.textContent = "delete";
				deleteEntry.style.cursor = "pointer";
				
				const bracketSelect = document.createElement("select");
				[ { id: null, name: "NONE" }, ...brackets ].forEach(bracket => {
					const option = document.createElement("option");
					option.value = bracket.id;
					option.text = bracket.name;
					if (item.bracket && item.bracket.id == bracket.id) { option.selected = true; }
					bracketSelect.appendChild(option);
				});
				bracketSelect.addEventListener("input", e => {
					const route = jsRoutes.controllers.ContestApiController.setBracket(CONTEST_ID, item.id);
					fetch(route.url, {
						method: route.method,
						headers: {
							"X-Requested-With": "fetch",
							"Content-type": "application/json",
							[CSRF_HEADER]: CSRF_TOKEN
						},
						body: JSON.stringify({
							bracket: isNaN(bracketSelect.value) ? null : +bracketSelect.value
						}),
						credentials: "same-origin"
					}).catch(console.error);
				});
				
				const row = new TableRow(img, item.id, programLink, bracketSelect, deleteEntry);
				
				deleteEntry.addEventListener("click", e => {
					const route = jsRoutes.controllers.ContestApiController.deleteEntry(CONTEST_ID, item.id);
					fetch(route.url, {
						method: route.method,
						headers: {
							"X-Requested-With": "fetch",
							[CSRF_HEADER]: CSRF_TOKEN
						},
						credentials: "same-origin"
					}).catch(console.error);
					row.remove();
					delete entryIds[item.id];
				});
				
				this.table.addRow(row);
				
				entryIds[item.id] = true;
			}
		}
		
		return new IteratorTable(new MDLTable([
			"Thumbnail",
			"Entry ID",
			"Program ID",
			"Bracket", 
			"Remove"
		]), iterator, forEachItem, [ homeButton ]);
	})();
	
	const contestRoute = jsRoutes.controllers.ContestApiController.getContest(CONTEST_ID);
	fetch(contestRoute.url, {
		method: contestRoute.method,
		credentials: "same-origin"
	}).then(response => {
		console.log(response)
		if (response.status >= 200 && response.status < 300) {
			response.json().then(data => {
				brackets.push(...data.brackets);
				entryManager.init();
				entryManager.appendTo(content);
			}).catch(console.error);
		} else {
			console.log(content.innerHTML);
			content.innerHTML = `
				<h2>OOPS!</h2>
				<p>${response.status == 404 ? "That contest doesn't exist" : "Error"}</p>
			`;
		}
	}).catch(e => {
		console.error(e);
		content.innerHTML = `
			<h2>OOPS!</h2>
			<p>Error</p>
		`;
	})
})();