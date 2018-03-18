(() => {
	class ResultManager extends Component {
		constructor(bracket = null) {
			const limit = 10;
			super(limit, bracket);
			this.page = 0;
			this.limit = limit;
			this.bracket = bracket;
			this.placeCounter = 0;
			this.prev = Infinity;
		}
		generateDom(limit, bracket) {
			const div = document.createElement("div");
			
			const backButton = this.backButton = new MDLAccentRippleBtn("Back");
			const loadMore = this.loadMore = new MDLAccentRippleBtn("Load more");
			const buttonDiv = this.buttonDiv = document.createElement("div");
			
			buttonDiv.className = "button-div";
			
			const resultsTable = this.resultsTable = new MDLTable([
				"Place", 
				"Entry ID", 
				"Score", 
				"Program ID"
			]);
			
			backButton.addOnClick(e => {
				window.location.href = jsRoutes.controllers.ContestUIController.contest(CONTEST_ID).url;
			});
			
			loadMore.addOnClick(e => {
				loadMore.disable();
				this.next().then(data => {
					if (data.length < limit) 
						loadMore.remove();
					else
						loadMore.enable();
				}).catch(error);
			});
			
			resultsTable.appendTo(div);
			div.appendChild(buttonDiv);
			
			div.className = "result-set";
			div.setAttribute("data-bracket", bracket);
			
			return div;
		}
		next() {
			return new Promise((res, rej) => {
				const route = jsRoutes.controllers.ContestApiController.entryScores(CONTEST_ID, this.page++, this.limit);
				fetch(route.url + (this.bracket != null ? `?bracket=${this.bracket}` : ""), {
					method: route.method,
					credentials: "same-origin"
				}).then(response => response.status >= 200 && response.status < 300 ? 
						response.json() : Promise.reject(response)).then(data => {
							data.forEach(item => {
								const link = document.createElement("a");
								link.href = `https://www.khanacademy.org/computer-programming/i/${item.programId}`;
								link.textContent = item.programId;
								link.setAttribute("target", "_blank");
								this.resultsTable.addRow(
										new TableRow((this.placeCounter += (item.result < this.prev)), 
												item.entryId, `${item.result}/100`, link));
								this.prev = item.result;
							});
							res(data);
				}).catch(rej);
			});
		}
		init() {
			this.next().then(data => {
				if (data.length == this.limit)
					this.loadMore.appendTo(this.buttonDiv);
				this.backButton.appendTo(this.buttonDiv);
			}).catch(error);
		}
	} 
	
	function error(e) {
		console.error(e);
		resultsEl.innerHTML = `
			<h2>OOPS!</h2>
			<p>Error.  Check the debugger console for more info</p>`;
	}
	
	const resultsEl = document.getElementById("results-content");
	
	const contestRoute = jsRoutes.controllers.ContestApiController.getContest(CONTEST_ID);
	fetch(contestRoute.url, {
		method: contestRoute.method,
		credentials: "same-origin"
	}).then(response => response.status >= 200 && response.status < 300 ? response.json() : Promise.reject(response)).then(data => {
		const select = document.createElement("select");
		select.className = "s-select";
		["All brackets", ...data.brackets.map(e => e.name)].forEach(e => {
			const item = document.createElement("option");
			item.text = e;
			item.value = e;
			select.appendChild(item);
		});
		
		resultsEl.appendChild(select);
		
		const resultManagers = [new ResultManager(), ...data.brackets.map(e => new ResultManager(e.id))];
		resultManagers.forEach((e, i) => {
			if (i != 0) { e.dom.style.display = "none"; }
			e.init();
			e.appendTo(resultsEl);
		});
		
		select.addEventListener("change", e => {
			resultManagers.forEach(e => e.dom.style.display = "none");
			resultManagers[select.selectedIndex].dom.style.display = "block";
		});
	}).catch(e => {
		if (e.status && e.status == 404)
			resultsEl.innerHTML = `
				<h2>OOPS!</h2>
				<p>You're not allowed to see this page</p>`;
		else
			error(e);
	});
})();