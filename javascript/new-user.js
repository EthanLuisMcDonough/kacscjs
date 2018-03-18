(() => {
	const form = document.getElementById("add-user");
	
	const userKaid = document.getElementById("new-user-kaid");
	
	const error = document.getElementById("error");
	const success = document.getElementById("success");
	
	error.style.color = "#ff5555";
	success.style.color = "#00b200";
	
	function err(msg) {
		error.textContent = msg;
		success.textContent = "";
	}
	
	function suc(msg) {
		error.textContent = "";
		success.textContent = msg;
	}
	
	form.addEventListener("submit", e => {
		e.preventDefault();
		error.textContent = "";
		
		const route = jsRoutes.controllers.UserApiController.createUser(userKaid.value);
		fetch(route.url, {
			method: route.method,
			headers: {
				"Content-Type": "application/json",
				[CSRF_HEADER]: CSRF_TOKEN
			},
			body: JSON.stringify({}),
			credentials: "same-origin"
		}).then(response => {
			if (response.status >= 200 && response.status < 300) {
				suc("Success");
				window.setTimeout(() => window.location.replace(jsRoutes.controllers.UserUIController.users().url), 1000);
			} else {
				response.json()
					.then(data => err(data.message))
					.catch(err.bind(this, "Error"))
			}
		}).catch(e => err("Error"))
	});
})();