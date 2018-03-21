(() => {
	const content = document.getElementById("users-area");
	
	const userManager = (() => {
		const limit = 10;
		const iterator = new PageIterator((page, limit) => jsRoutes.controllers.UserApiController.getUsers(page, limit), limit);
		
		const userIds = {};
		
		function forEachItem(item) {
			if(!userIds[item.id]) {
				const linkc = document.createElement("span");
				const link = document.createElement("a");
				link.setAttribute("target", "_blank");
				link.textContent = item.name;
				link.href = `https://www.khanacademy.org/profile/${item.kaid}`;
				linkc.appendChild(link);
				
				const promote = document.createElement("i");
				const remove = document.createElement("i");
				
				const row = new TableRow(linkc, promote, remove);
				
				if (item.level < UserLevel.ADMIN.ordinal()) {
					promote.style.cursor = remove.style.cursor = "pointer";
					
					const promoteNotice = new MDLDialog(
						"Are you sure?", 
						`Are you sure you want to promote "${item.name}"?  Promoted users can't be demoted throught the UI`, 
						[
							{
								label: "Cancel", 
								onClick() {
									promoteNotice.close();
								}
							},
							{
								label: "Promote",
								color: "#4CB74C",
								onClick() {
									const route = jsRoutes.controllers.UserApiController.promoteUser(item.id);
									fetch(route.url, {
										method: route.method,
										headers: {
											"X-Requested-With": "fetch",
											[CSRF_HEADER]: CSRF_TOKEN
										},
										credentials: "same-origin"
									}).catch(console.error);
									promote.className = remove.className = "";
									promote.textContent = remove.textContent = "N/a";
									const adminTag = document.createElement("span");
									adminTag.textContent = " [Admin]";
									linkc.appendChild(adminTag);
									promoteNotice.close();
								}
							}
						]
					);
					promoteNotice.appendTo(document.body);
					
					const deleteNotice = new MDLDialog(
						"Are you sure?", 
						`Are you sure you want to remove "${item.name}"?`, 
						[
							{
								label: "Cancel", 
								onClick() {
									deleteNotice.close();
								}
							},
							{
								label: "Remove user",
								color: "#ff5555",
								onClick() {
									const route = jsRoutes.controllers.UserApiController.removeUser(item.id);
									fetch(route.url, {
										method: route.method,
										headers: {
											"X-Requested-With": "fetch",
											[CSRF_HEADER]: CSRF_TOKEN
										},
										credentials: "same-origin"
									}).catch(console.error);
									row.remove();
									deleteNotice.close();
								}
							}
						]
					);
					deleteNotice.appendTo(document.body);
					
					promote.className = "material-icons";
					promote.textContent = "keyboard_arrow_up";
					promote.addEventListener("click", e => promoteNotice.showModal());
					
					remove.className = "material-icons";
					remove.textContent = "delete";
					remove.addEventListener("click", e => deleteNotice.showModal());
				} else {
					promote.textContent = remove.textContent = "N/a";
					const adminTag = document.createElement("span");
					adminTag.textContent = " [Admin]";
					linkc.appendChild(adminTag);
				}
				
				this.table.addRow(row);
				
				userIds[item.id] = true;
			}
		}
		
		const newUser = new MDLAccentRippleBtn("New user");
		newUser.addOnClick(e => window.location.replace(jsRoutes.controllers.UserUIController.newUser().url));
		
		return new IteratorTable(new MDLTable([
			"User", 
			"Promote to admin", 
			"Remove"
		]), iterator, forEachItem, [ newUser ]);
	})();
	
	userManager.init();
	userManager.appendTo(content);
})();