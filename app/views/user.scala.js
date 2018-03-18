@import models.User

@(user: User = null)

@if(user != null) {
	Object.defineProperty(window, "User", {
		value: Object.freeze({
			KAID: "@user.getKaid",
			ID: +"@user.getId",
			LEVEL: UserLevel.values[+"@user.getLevel().ordinal"]
		}),
		writable: false, 
		enumerable: true, 
		configurable: true
	});
}
