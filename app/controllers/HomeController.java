package controllers;

import models.User;
import play.mvc.Controller;
import play.mvc.Result;

public class HomeController extends Controller {
    public Result index() {
        User user = User.getFromSession(session());
        return user == null ? ok(views.html.index.render())
                : temporaryRedirect(routes.ContestUIController.contests().url());
    }
}
