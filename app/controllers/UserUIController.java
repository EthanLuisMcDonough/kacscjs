package controllers;

import models.User;
import models.UserLevel;
import play.mvc.Controller;
import play.mvc.Result;

public class UserUIController extends Controller {
	public Result users() {
		User user = User.getFromSession(session());
		return user != null && user.getLevel().ordinal() >= UserLevel.ADMIN.ordinal()
				? ok(views.html.users.render(user))
				: forbidden(views.html.error403.render(user));
	}

	public Result newUser() {
		User user = User.getFromSession(session());
		return user != null && user.getLevel().ordinal() >= UserLevel.ADMIN.ordinal()
				? ok(views.html.newuser.render(user))
				: forbidden(views.html.error403.render(user));
	}
}
