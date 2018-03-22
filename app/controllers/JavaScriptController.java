package controllers;

import models.User;
import play.mvc.Controller;
import play.mvc.Result;
import play.routing.JavaScriptReverseRouter;

public class JavaScriptController extends Controller {
	public Result jsRoutes() {
		return ok(JavaScriptReverseRouter.create("jsRoutes", routes.javascript.ContestUIController.contest(),
				routes.javascript.ContestUIController.editContest(),
				routes.javascript.ContestUIController.contestResults(), routes.javascript.ContestUIController.entries(),

				routes.javascript.UserUIController.newUser(), routes.javascript.UserUIController.users(),

				routes.javascript.UserApiController.getUsers(), routes.javascript.UserApiController.createUser(),
				routes.javascript.UserApiController.removeUser(), routes.javascript.UserApiController.promoteUser(),

				routes.javascript.ContestApiController.setBracket(),
				routes.javascript.ContestApiController.getEntries(), routes.javascript.ContestApiController.getEntry(),
				routes.javascript.ContestApiController.voteEntry(),
				routes.javascript.ContestApiController.addAllSpinOffs(),
				routes.javascript.ContestApiController.newEntry(),
				routes.javascript.ContestApiController.createContest(),
				routes.javascript.ContestApiController.getContests(),
				routes.javascript.ContestApiController.deleteContest(),
				routes.javascript.ContestApiController.getContest(),
				routes.javascript.ContestApiController.randomEntry(),
				routes.javascript.ContestApiController.entryScores(),
				routes.javascript.ContestApiController.deleteEntry()));
	}

	public Result userJs() {
		return ok(views.js.user.render(User.getFromSession(session())));
	}

	public Result userLevelJS() {
		return ok(views.js.UserLevel.render());
	}

	public Result defineContest(int id) {
		return ok(views.js.definecontest.render(id));
	}

	public Result defineEntry(int id) {
		return ok(views.js.defineentry.render(id));
	}
}
