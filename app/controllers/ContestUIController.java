package controllers;

import models.User;
import models.UserLevel;
import play.mvc.Controller;
import play.mvc.Result;

public class ContestUIController extends Controller {
    public Result contests() {
        User user = User.getFromSession(session());
        return user == null || user.getLevel() == UserLevel.REMOVED ? forbidden(views.html.error403.render(user))
                : ok(views.html.contests.render(user));
    }

    public Result newContest() {
        User user = User.getFromSession(session());
        return user != null && user.getLevel() == UserLevel.ADMIN ? ok(views.html.newcontest.render(user))
                : forbidden(views.html.error403.render(user));
    }

    public Result contest(int id) {
        User user = User.getFromSession(session());
        return user != null && user.getLevel().ordinal() >= UserLevel.MEMBER.ordinal()
                ? ok(views.html.contest.render(id, user))
                : forbidden(views.html.error403.render(user));
    }

    public Result entry(int contestId, int entryId) {
        User user = User.getFromSession(session());
        return user == null || user.getLevel().ordinal() < UserLevel.MEMBER.ordinal()
                ? forbidden(views.html.error403.render(user))
                : ok(views.html.entry.render(contestId, entryId, user));
    }

    public Result editContest(int id) {
        User user = User.getFromSession(session());
        return user == null || user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()
                ? forbidden(views.html.error403.render(user))
                : ok(views.html.editcontest.render(id, user));
    }

    public Result contestResults(int id) {
        User user = User.getFromSession(session());
        return user == null || user.getLevel().ordinal() < UserLevel.MEMBER.ordinal()
                ? forbidden(views.html.error403.render(user))
                : ok(views.html.contestresults.render(id, user));
    }

    public Result entries(int id) {
        User user = User.getFromSession(session());
        return user == null || user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()
                ? forbidden(views.html.error403.render(user))
                : ok(views.html.entries.render(user, id));
    }
}
