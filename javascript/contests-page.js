(() => {
	class ContestItem extends Component {
		constructor(contestJson) {
			const { name, id, description, 
				programId, endDate, unjudgedEntryCount } = contestJson;
			super(name, id, description, programId, new Date(endDate), unjudgedEntryCount);
		}
		generateDom(name, id, description, programId, endDate, unjudgedEntryCount) {
			const section = document.createElement("section");
			section.className = "section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp contest-item";
			const that = this;
			
			const dialog = new MDLDialog("Are you sure?", `Are you sure you want to delete "${name}"?  All of this contest's entries, results, criteria, etc. will be lost.  This action can't be undone.`, [
				{
					label: "Cancel",
					onClick(e) {
						dialog.close();
					}
				}, 
				{
					color: "#ff5555",
					label: "Delete",
					onClick(e) {
						let reqInf = jsRoutes.controllers.ContestApiController.deleteContest(id);
						fetch(reqInf.url, {
							method: reqInf.method,
							credentials: "same-origin",
							headers: { [CSRF_HEADER]: CSRF_TOKEN }
						}).then(response => 
							response.status >= 200 && response.status < 300 && 
							(that.remove() || dialog.close())).catch(console.error);
					}
				}
			]);
			
			dialog.appendTo(document.body);
			
			const header = document.createElement("header");
			header.className = "section__play-btn mdl-cell mdl-cell--3-col-desktop mdl-cell--2-col-tablet mdl-cell--4-col-phone mdl-color--white-100 mdl-color-text--white contest-icon";
			
			header.style.backgroundImage = `url(https://www.khanacademy.org/computer-programming/contest-fantasy-landscape/${programId}/latest.png`;
			
			section.appendChild(header);
			
			const body = document.createElement("div");
			body.className = "mdl-card mdl-cell mdl-cell--9-col-desktop mdl-cell--6-col-tablet mdl-cell--4-col-phone";
			const content = document.createElement("div");
			content.className = "mdl-card__supporting-text";
			const title = document.createElement("h4");
			title.textContent = name;
			const desc = document.createElement("span");
			desc.textContent = description;
			
			content.appendChild(title);
			content.appendChild(desc);
			
			const actions = document.createElement("div");
			actions.className = "mdl-card__actions";
			const link = document.createElement("a");
			link.href = jsRoutes.controllers.ContestUIController.contest(id).url;
			link.textContent = "View contest";
			link.className = "mdl-button";
			actions.appendChild(link);
			
			body.appendChild(content);
			body.appendChild(actions);
			
			section.appendChild(body);

			const button = new DomComponent(document.createElement("button"));
			button.dom.className = "mdl-button mdl-js-button mdl-js-ripple-effect mdl-button--icon";
			button.dom.innerHTML = "<i class=\"material-icons\">more_vert</i>";
			
			const contextMenu = document.createElement("ul");
			contextMenu.className = "mdl-menu mdl-js-menu mdl-menu--bottom-right";
			contextMenu.setAttribute("for", button.ID);
			
			[
				{
					text: "View",
					onClick(e) {
						window.location.href = link.href;
					},
					disabled: false
				},
				{
					text: "Edit",
					onClick(e) {
						window.location.href = jsRoutes.controllers.ContestUIController.editContest(id).url;
					},
					disabled: User.LEVEL.ordinal() < UserLevel.ADMIN.ordinal()
				},
				{
					text: "Delete",
					onClick(e) {
						dialog.showModal();
					},
					disabled: User.LEVEL.ordinal() < UserLevel.ADMIN.ordinal()
				}
			].forEach(item => {
				const el = document.createElement("li");
				el.textContent = item.text;
				el.className = "mdl-menu__item";
				if(item.disabled) 
					el.setAttribute("disabled", true);
				else
					el.addEventListener("click", item.onClick);
				contextMenu.appendChild(el);
			});
			
			button.appendTo(section);
			section.appendChild(contextMenu);
			
			this.__button = button;
			this.__contextMenu = contextMenu;
			this.__section = section;
			
			return section;
		}
		update() {
			componentHandler.upgradeElement(this.__button.dom);
			componentHandler.upgradeElement(this.__contextMenu);
			componentHandler.upgradeElement(this.__section);
		}
	}
	
	const contestManager = (() => {
		let page = 0;
		const limit = 5;
		
		const contestsEl = document.getElementById("contests");
		
		const loadMoreSection = document.getElementById("load-more-section");
		const loadMoreButton = document.getElementById("load-more");
		const noMoreSection = document.getElementById("no-contests");
		
		return {
			next() {
				return new Promise((res, rej) => {
					fetch(jsRoutes.controllers.ContestApiController.getContests(page++, limit).url, {
						method: "GET",
						credentials: "same-origin"
					}).then(response => response.status >= 200 && response.status < 300 ? 
							response.json() : Promise.reject(response)).then(data => {
								data.forEach(i => {
									const c = new ContestItem(i);
									c.appendTo(contestsEl);
									c.update();
								});
								res(data);
					}).catch(rej);
				});
			},
			init() {
				loadMoreButton.addEventListener("click", e => {
					loadMoreButton.disabled = true;
					this.next().then(data => {
						if (data.length < limit) 
							loadMoreSection.style.display = "none";
						else
							loadMoreButton.disabled = false;
					});
				});
				this.next().then(data => {
					if (data.length == 0) {
						noMoreSection.style.display = "block";
					} else if (data.length == limit) {
						loadMoreSection.style.display = "block";
					}
				}).catch(console.error);
			}
		};
	})();
	contestManager.init();
})();