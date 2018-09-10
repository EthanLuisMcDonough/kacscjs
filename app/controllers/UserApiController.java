package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.User;
import models.UserLevel;
import contexts.DBContext;
import play.Logger;
import play.db.Database;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import play.libs.ws.WSClient;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class UserApiController extends Controller {
    private final Pattern kaidRegex = Pattern.compile("kaid_\\d{20,30}");
    private final Database db;
    private final DBContext dbCtx;
    private final WSClient ws;

    @Inject
    public UserApiController(Database db, DBContext dbCtx, WSClient ws) {
        this.db = db;
        this.dbCtx = dbCtx;
        this.ws = ws;
    }

    private ObjectNode jsonMsg(String message) {
        ObjectNode json = Json.newObject();
        json.put("message", message);
        return json;
    }

    private Result internalServerErrorApiCallback(Throwable e) {
        Logger.error(e.getMessage(), e);
        return internalServerError(jsonMsg("Internal server error"));
    }

    public CompletionStage<Result> getUsers(int page, int limit) {
        User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        } else if (user.getLevel().ordinal() <= UserLevel.REMOVED.ordinal()) {
            return completedFuture(forbidden(jsonMsg("Forbidden")));
        }

        return CompletableFuture.supplyAsync(() -> {
            List<User> users;
            try (Connection connection = db.getConnection(true)) {
                users = User.getAllUsers(page, limit, connection);
            } catch (SQLException e) {
                Logger.error("Error", e);
                return internalServerError(jsonMsg("Internal server error"));
            }

            ArrayNode usersJson = Json.newArray();
            Iterator<User> userIter = users.iterator();
            while (userIter.hasNext())
                usersJson.add(userIter.next().asJson());

            return ok(usersJson);
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> createUser(String kaid) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        } else if (user.getLevel() != UserLevel.ADMIN) {
            return completedFuture(forbidden(jsonMsg("Forbidden")));
        }

        if (!kaidRegex.matcher(kaid).matches()) {
            return completedFuture(badRequest(jsonMsg("Invalid kaid")));
        }

        return ws.url("https://www.khanacademy.org/api/internal/user/profile")
                .addQueryParameter("kaid", kaid)
                .addQueryParameter("projection", "{\"nickname\":1}")
                .get()
                .thenApply(response -> {
                    JsonNode body = response.asJson();
                    if (body.isObject() && body.hasNonNull("nickname") && body.get("nickname").isTextual()) {
                        return body.get("nickname").asText("");
                    } else {
                        return "";
                    }
                })
                .thenApplyAsync(name -> {
                    if (name.isEmpty()) {
                        return badRequest(jsonMsg("Could not find a user with that kaid"));
                    }

                    try (Connection connection = db.getConnection(true)) {
                        User newUser = user.createUser(name, kaid, UserLevel.MEMBER, connection);
                        return newUser == null ? badRequest(jsonMsg("That user already exists")) : ok(newUser.asJson());
                    } catch (SQLException e) {
                        Logger.error(e.getMessage(), e);
                        return internalServerError(jsonMsg("Internal server error"));
                    }
                }, dbCtx)
                .exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> removeUser(int id) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized());
        } else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
            return completedFuture(forbidden());
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                User userToRemove = User.getUserById(id, connection);
                if (userToRemove == null)
                    return notFound();
                if (userToRemove.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
                    user.setOtherUserLevel(userToRemove, UserLevel.REMOVED, connection);
                    return ok("");
                } else
                    return forbidden();
            } catch (SQLException e) {
                Logger.error("Error", e);
                return internalServerError();
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> promoteUser(int id) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized());
        } else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
            return completedFuture(forbidden());
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                User userToRemove = User.getUserById(id, connection);
                if (userToRemove == null)
                    return notFound();
                if (userToRemove.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
                    user.setOtherUserLevel(userToRemove, UserLevel.ADMIN, connection);
                    return ok("");
                } else
                    return badRequest();
            } catch (SQLException e) {
                Logger.error("Error", e);
                return internalServerError();
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }
}
