(() => {
	const content = document.getElementById("entries-area");
	const brackets = [];
	
	const entryManager = (() => {
		const limit = 10;
		const iterator = new PageIterator((page, limit) => jsRoutes.controllers.ContestApiController.getEntries(CONTEST_ID, page, limit), limit);
		
		const entryIds = {};
		
		const addEntry = new MDLAccentRippleBtn("Add entry");
		const allSpinOffs = new MDLAccentRippleBtn("Add all spin-offs");
		
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
		
		const iTable = new IteratorTable(new MDLTable([
			"Thumbnail",
			"Entry ID",
			"Program ID",
			"Bracket", 
			"Remove"
		]), iterator, forEachItem, [ addEntry, allSpinOffs, homeButton ]);
		
		const addProgramDialogDiv = document.createElement("div");
		const programIdInput = new MDLTextfield("Program ID", true, "\\d{9,16}", "Invalid program ID");
		programIdInput.appendTo(addProgramDialogDiv);
		const programIdInputErrorText = document.createElement("p");
		programIdInputErrorText.className = "error";
		addProgramDialogDiv.appendChild(programIdInputErrorText);
		let addProgramEnabled = true;
		
		const addEntryDialog = new MDLDialog("Add an entry", addProgramDialogDiv, [
			{
				label: "Cancel",
				onClick() {
					addEntryDialog.close();
				}
			},
			{
				label: "Add entry",
				color: "#00b200",
				onClick() {
					if (addProgramEnabled) {
						programIdInputErrorText.textContent = "";
						addProgramEnabled = false;
						const route = jsRoutes.controllers.ContestApiController.newEntry(CONTEST_ID, programIdInput.getTextContent());
						fetch(route.url, {
							method: route.method,
							headers: {
								"X-Requested-With": "fetch",
								[CSRF_HEADER]: CSRF_TOKEN
							},
							credentials: "same-origin"
						}).then(response => response.status >= 200 && response.status < 300 ? response.json() : Promise.reject(response)).then(data => {
							iTable.insertItems([data]);
							addEntryDialog.close();
							addProgramEnabled = true;
						}).catch(e => {
							console.error(e);
							programIdInputErrorText.textConent = e.message ? e.message : "Error";
							addProgramEnabled = true;
						})
					}
				},
			}
		]);
		addEntryDialog.appendTo(document.body);
		
		const spinOffErrorDialog = new MDLDialog(
			"Error", 
			"There was an issue adding the contest's spin-offs.  Check the debugger console for more info.", 
			[
				{ 
					label: "Ok",
					onClick() {
						spinOffErrorDialog.close();
					}
				}
			]
		);
		spinOffErrorDialog.appendTo(document.body);
		
		addEntry.addOnClick(addEntryDialog.showModal.bind(addEntryDialog));
		
		allSpinOffs.addOnClick(e => {
			allSpinOffs.disable();
			const route = jsRoutes.controllers.ContestApiController.addAllSpinOffs(CONTEST_ID);
			fetch(route.url, {
				method: route.method,
				headers: {
					"X-Requested-With": "fetch",
					[CSRF_HEADER]: CSRF_TOKEN
				},
				credentials: "same-origin"
			}).then(response => response.status >= 200 && response.status < 300 ? response.json() : Promise.reject(response)).then(data => {
				allSpinOffs.enable();
				iTable.insertItems(data);
			}).catch(e => {
				console.error(e);
				allSpinOffs.enable();
				spinOffErrorDialog.showModal();
			})
		});
		
		return iTable;
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